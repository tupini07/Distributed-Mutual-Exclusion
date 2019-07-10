package com.tmds.project;

import akka.actor.AbstractActorWithStash;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

public class NodeAct extends AbstractActorWithStash {

    private LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    // Private variables that identify this node
    private HashSet<ActorRef> neighbors;
    private final ActorRef resource_actor;

    // Variables used to implement the algorithm
    private ActorRef holder; // reference to self or to one of the neighbors
    private boolean using; // whether this node is using the token (in the CS)
    private LinkedList<ActorRef> request_q; // FIFO queue holding the requests for the token that this node is processing
    private boolean asked; // whether this node has asked a neighbor for the node

    // specific for the recovery part
    private boolean is_recovering; // tells if the current node is in recovery mode or not
    private HashMap<ActorRef, Advise> receivedAdvises; // to know which neighbors have sent an Advise message and what this message was

    public NodeAct(ActorRef resource_actor) {
        this.resource_actor = resource_actor;

        this.request_q = new LinkedList<ActorRef>();
        this.using = false;
        this.asked = false;

        this.is_recovering = false;
        this.receivedAdvises = new HashMap<>();
    }


    static public Props props(ActorRef resource_actor) {
        return Props.create(NodeAct.class, () -> new NodeAct(resource_actor));
    }

    // ----------------------------------------------------
    // Message classes that are handled

    /**
     * This is the Initialization message that is flooded through the network so that all nodes know
     * where the token is. It is sent by the user, and the first node receiving it is the holder of
     * the token.
     */
    static public class Initialize {
        public final boolean is_first; // whether this is the first node in the flood or not

        public Initialize(boolean is_first) {
            this.is_first = is_first;
        }
    }

    /**
     * Initialization message sent by the user to an actor so that the actor can know who its neighbors are
     */
    static public class SetNeighbors {
        public final HashSet<ActorRef> neighbors;

        public SetNeighbors(HashSet<ActorRef> neighbors) {
            this.neighbors = neighbors;
        }
    }

    /**
     * Message sent to an actor when the sender wants to receive the token from
     * said actor
     */
    static public class RequestToken {
    }

    /**
     * Message sent to an actor when the current node wants to send the token to said actor
     * The sending of this message implies that the sender (before sending) holds the token
     */
    static public class SendToken {
    }

    /**
     * Sent by an actor to itself to indicate that the token should be passed on.
     * The sending of this message implies that the actor holds the token and is not using it.
     */
    static public class InvokePriviledgeSend {
    }

    /**
     * Message that an actor sends to itself to signal that it can enter the critical section
     * This means that it has the token, and is using it
     */
    static public class EnterCriticalSection {
    }

    /**
     * Message that the resource actor sends to the actor currently in the critical section, once the execution has
     * finished. This message can contain the result obtained after executing the CS (if any)
     */
    static public class ExitCriticalSection {
    }

    /**
     * Message that an actor sends to all its neighbors after it crashes
     */
    static public class Restart {
    }

    /**
     * Message that neighbors send in respond to a `Restart` message. It contains the
     * information necessary for the actor who send `Restart` to partly reconstruct its state
     */
    static public class Advise {
        public final ActorRef holder_y; // who is the holder according to Y
        public final boolean asked_y; // if y has already "asked" for the token
        public final boolean x_in_y_request_q; // x is y's request_q

        public Advise(ActorRef holder, boolean asked, boolean x_in_y_request_q) {
            this.holder_y = holder;
            this.asked_y = asked;
            this.x_in_y_request_q = x_in_y_request_q;
        }
    }

    /**
     * A node sends this message to itself when it restarts after crashing. This message initializes the recovery
     * procedure
     */
    static public class InitializeRecovery {
    }

    /**
     * Message sent from the user to signal a specific actor to simulate a crash
     */
    static public class USimulateCrash {
    }

    /**
     * Message sent from the user to signal a specific actor to enter the CS
     */
    static public class UEnterCS {
    }

    /**
     * Message sent to a node to make it print it's internal state to the terminal
     *
     * @return
     */
    static public class InvokePrintInternalState {
    }

    // ----------------------------------------------------
    // mapping between message classes and methods for handling
    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(SetNeighbors.class, this::setNeighbors)

                .match(Initialize.class, this::handleInitialize)

                .match(RequestToken.class, this::handleTokenRequest)
                .match(SendToken.class, this::handleTokenReceive)

                .match(InvokePriviledgeSend.class, this::sendPriviledge)

                .match(EnterCriticalSection.class, this::handleEnterCS)
                .match(ExitCriticalSection.class, this::handleExitCS)

                .match(Restart.class, this::handleRestart)
                .match(Advise.class, this::handleAdvise)
                .match(InitializeRecovery.class, this::handleInitializeRecovery)

                .match(USimulateCrash.class, this::usimulateCrash)
                .match(UEnterCS.class, this::uenterCS)

                .match(InvokePrintInternalState.class, this::printInternalState)

                .build();
    }

    // ----------------------------------------------------
    // implementation of handling for messages

    private void handleInitialize(Initialize msg) {
        if (this.holder != null) {
            // if this node has already received the initialize message then don't
            // propagate it further
            return;
        }

        log.info("SM: Initializing node: {}", getSelf().path().name());

        if (msg.is_first) {
            this.holder = getSelf();
        } else {
            this.holder = getSender();
        }

        for (ActorRef neighbor : this.neighbors) {
            neighbor.tell(new Initialize(false), getSelf());
        }
    }

    private void setNeighbors(SetNeighbors msg) {
        log.info("Setting neighbors. Size: {}", msg.neighbors.size());
        this.neighbors = msg.neighbors;
    }

    /**
     * When this actor is requested to send the token to another actor
     * <p>
     * We simply add the requesting actor to the `request_q`
     *
     * @param msg
     */
    private void handleTokenRequest(RequestToken msg) {
        // if we're recovering then we delay the evaluation of the RequestToken message
        // until the recovering process is complete
        if (this.is_recovering) {
            stash(); // add current message to stash
            return;
        }

        ActorRef requester = getSender();
        log.info("Received token request from node {}", requester.path().name());

        if (!this.request_q.contains(requester)) {
            this.request_q.add(requester);
        } else {
            log.info("Already have a token request from {}. Ignoring this one", requester.path().name());
        }

        // ask for the token if needed
        if (!this.holder.equals(getSelf()) &&
                !this.request_q.isEmpty() &&
                !this.asked) {

            this.asked = true;
            log.info("SM: Asking '{}' for token on behalf of '{}'",
                    this.holder.path().name(),
                    requester.path().name());
            this.holder.tell(new RequestToken(), getSelf());
        }

        // if we have the token and we're not using it then send it over
        if (this.holder.equals(getSelf()) &&
                !this.using) {
            getSelf().tell(new InvokePriviledgeSend(), getSelf());
        }

        // if we have the token, we are the ones requesting it, and we're at the top of the
        // request_q then we can go ahead and use it
        if (this.holder.equals(getSelf())
                && requester.equals(getSelf())
                && !this.request_q.isEmpty()
                && this.request_q.getFirst().equals(getSelf())) {
            handleTokenReceive(new SendToken());
        }
    }

    /**
     * When we recieve the token
     *
     * @param msg
     */
    private void handleTokenReceive(SendToken msg) {
        log.info("Received the token from node {}", getSender().path().name());
        this.holder = getSelf(); // since we now own the token

        // if current actor needs it then use it. Else send it over
        if (!this.request_q.isEmpty() &&
                this.request_q.getFirst().equals(getSelf())) {
            this.request_q.pop();
            this.using = true;


            // Current actor will send InvokePriviledgeSend to itself
            // once it exits the CS
            getSelf().tell(new EnterCriticalSection(), getSelf());
        } else {
            getSelf().tell(new InvokePriviledgeSend(), getSelf());
        }
    }

    /**
     * Sends the privilege (token) to the next actor in `request_q`.
     *
     * @param msg
     */
    private void sendPriviledge(InvokePriviledgeSend msg) {
        // if we're recovering then we don't send privilege.
        // We delay until the recovering is complete.
        if (this.is_recovering) {
            stash(); // add current message to stash
            return;
        }


        if (this.holder.equals(getSelf())
                && !this.using
                && !this.request_q.isEmpty()
                && !(this.request_q.getFirst().equals(getSelf()))) {

            // set new holder
            this.holder = this.request_q.pop();
            this.asked = false;

            log.info("SM: Sending privilege to node: {}", this.holder.path().name());
            this.holder.tell(new SendToken(), getSelf());

        }

        // If after sending the token we still have other requesters in our request_q
        // then we also send a RequestToken message to the new holder so that we will
        // get back the token eventually.
        // if the holder is the current node then we don't want to re-request it since this would
        // make as re-enter the CS.
        if (!this.request_q.isEmpty() &&
                !this.asked &&
                !this.holder.equals(getSelf())) {
            log.info("SM: We still have nodes in our request_q, so asking '{}' to return the token", this.holder.path().name());
            this.holder.tell(new RequestToken(), getSelf());
        }

    }

    /**
     * "Enters" the critical section and accesses the {@link ResourceActor} resource
     *
     * @param msg
     */
    private void handleEnterCS(EnterCriticalSection msg) {
        this.using = true;

        log.info("SM: About to enter critical section. Sending access message");

        resource_actor.tell(new ResourceActor.AccessResource(), getSelf());
    }

    /**
     * Once the {@link ResourceActor} signals that the critical section has finished then this
     * method is invoked.
     * <p>
     * Actor no longer needs to enter critical section so the token can now be sent to other actors.
     *
     * @param msg
     */
    private void handleExitCS(ExitCriticalSection msg) {
        this.using = false;
        log.info("Just exited critical section");

        getSelf().tell(new InvokePriviledgeSend(), getSelf());
    }

    /**
     * When a neighbor of the current node fails it sends a {@link Restart} message to the current node
     * and expects this to return an {@link Advise} message with the information needed for it to
     * recover its internal state
     *
     * @param msg
     */
    private void handleRestart(Restart msg) {
        log.info("SM: Received a restart message from node {}. Sending an advise", getSender().path().name());

        getSender().tell(
                new Advise(this.holder,
                        this.asked,
                        this.request_q.contains(getSender())),
                getSelf());
    }

    /**
     * Invoked when the current node gets an advise message. It keeps a record of all received advises, and once an
     * advise has been received from all neighbors it starts the reconstruction of the local state.
     *
     * @param advise
     */
    private void handleAdvise(Advise advise) {

        this.receivedAdvises.putIfAbsent(getSender(), advise);

        log.info("Received advise message from {}", getSender().path().name());

        // stop execution if we don't posses an Advise from all of our neighbors
        for (ActorRef neighbor : this.neighbors) {
            if (!this.receivedAdvises.containsKey(neighbor)) {
                return;
            }
        }

        log.info("Received advise from all nodes! Starting internal state reconstruction");

        // if we have information from all neighbors then we proceed to
        // reconstruct our internal state
        this.using = false;

        // tells us if all neighbors think the current node is the holder
        // if true then it must mean that we're actually the holder
        boolean holderForAllNeighbors = true;
        boolean currentIsHolder = getSelf().equals(this.holder);

        for (HashMap.Entry mEntry : this.receivedAdvises.entrySet()) {

            ActorRef m_advisor = (ActorRef) mEntry.getKey();
            Advise m_advise = (Advise) mEntry.getValue();

            // this node is the holder of the token according to Y
            boolean holder_according_to_y = m_advise.holder_y.equals(getSelf());

            // if Y thinks we hold the token and Y has already asked us for the token
            if (holder_according_to_y && m_advise.asked_y) {
                this.request_q.add(m_advisor);
            }

            // if Y knows we don't have the token.
            // Note that if we have received the token previously then we will be the holder
            // this message may have been processed after the "SendToken" one, meaning that its
            // information is outdated and must be ignored
            if (!holder_according_to_y && !currentIsHolder) {

                // current node is NOT the holder according to Y
                // this means that Y must be the holder according to the current node
                this.holder = m_advisor;

                // we have asked Y for the token or not
                this.asked = m_advise.x_in_y_request_q;

            }

            holderForAllNeighbors = holderForAllNeighbors && holder_according_to_y;
        }


        if (holderForAllNeighbors) {
            this.holder = getSelf();
        }

        // after receiving advise from all neighbors
        this.receivedAdvises.clear();

        this.is_recovering = false;

        log.info("Recovery finished!");
        this.printInternalState(new InvokePrintInternalState());

        // unstash all messages. These will be added to the head of the current message queue
        // so that they're processed in the same order they came in
        // https://doc.akka.io/docs/akka/current/actors.html#stash
        unstashAll();
    }

    /**
     * Starts the recovery procedure for the current node. To be executed after a crash.
     *
     * @param msg
     */
    private void handleInitializeRecovery(InitializeRecovery msg) {

        // as per project assumptions, we can't crash while in the critical section
        // so if we're in CS (this.using) then just resquedule this message
        if (this.using) {
            log.info("Tried to crash but currently in CS. Ignoring");
            return;
        }
        if (this.is_recovering) {
            log.info("Tried to crash while already in recovery. Ignoring");
            return;
        }

        this.is_recovering = true;

        log.info("Node {} crashed! Initializing recovery procedure", getSelf().path().name());

        // reset local state
        this.holder = null;
        this.asked = false;
        this.using = false;
        this.request_q.clear();

        // setup datastructures for recovery procedure
        this.receivedAdvises.clear();

        // tell all neighbors that we crashed
        for (ActorRef neighbor : this.neighbors) {
            log.info("SM: Sending restart message to neighbor {}", neighbor.path().name());
            neighbor.tell(new Restart(), getSelf());
        }

    }

    private void uenterCS(UEnterCS msg) {
        log.info("User requested this node to enter the critical section");
        getSelf().tell(new RequestToken(), getSelf());
    }

    private void usimulateCrash(USimulateCrash msg) {
        log.info("User requested for this node to crash");
        getSelf().tell(new InitializeRecovery(), getSelf());
    }

    /**
     * Utility method to print the internal state of a node to the terminal
     *
     * @param msg
     */
    public void printInternalState(InvokePrintInternalState msg) {
        String request_q_nodes_names = "[ ";

        for (ActorRef ract : this.request_q) {
            request_q_nodes_names += ract.path().name() + " ";
        }
        request_q_nodes_names += "]";

        log.info("Printing internal state:\n" +
                        "\tHolder: {}\n" +
                        "\tAsked: {}\n" +
                        "\tRecovering: {}\n" +
                        "\tSize request_q: {}\n" +
                        "\trequest_q nodes: " + request_q_nodes_names,
                this.holder != null ? this.holder.path().name() : "null",
                this.asked,
                this.is_recovering,
                this.request_q.size());
    }

}
