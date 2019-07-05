package com.tmds.project;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;

import java.util.HashSet;
import java.util.LinkedList;

public class NodeAct extends AbstractActor {

    private LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    // Private variables that identify this node
    private final int node_id; // for debugging
    private HashSet<ActorRef> neighbors;

    // Variables used to implement the algorithm
    private ActorRef holder; // reference to self or to one of the neighbors
    private boolean using; // whether this node is using the token (in the CS)
    private LinkedList<ActorRef> request_q; // FIFO queue holding the requests for the token that this node is processing
    private boolean asked; // whether this node has asked a neighbor for the node


    public NodeAct(int node_id) {
        this.node_id = node_id;

        this.request_q = new LinkedList<ActorRef>();
        this.using = false;
        this.asked = false;
    }


    static public Props props(int node_id) {
        return Props.create(NodeAct.class, () -> new NodeAct(node_id));
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
     * Message that an actor sends to itself after it exits the critical section
     * This means that the agent is no longer using the token
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

    // ----------------------------------------------------
    // implementation of handling for messages

    private void handleInitialize(Initialize msg) {
        if (this.holder != null) {
            // if this node has already received the initialize message then don't
            // propagate it further
            return;
        }

        log.info("Initializing node: {}", this.node_id);

        if (msg.is_first) {
            this.holder = getSender();
        } else {
            this.holder = getSelf();
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
        ActorRef requester = getSender();

        if (!this.request_q.contains(requester)) {
            this.request_q.add(requester);
        }

        // ask for the token if needed
        if (this.holder != getSelf() &&
                !this.request_q.isEmpty() &&
                !this.asked) {

            this.asked = true;
            this.holder.tell(new RequestToken(), getSelf());

        } else if (this.holder == getSelf() ||
                // use custom conditions since we don't want to match
                // when `this.asked` is true (this latter is only a condition to
                // prevent flooding the network with unnecessary requests)
                this.request_q.isEmpty()) {
            log.error("Tried to send token request but one of the conditions was violated\n" +
                            "Is holder NOT the current actor: {}\n" +
                            "Is current actor's request_q NOT empty: {}",
                    this.holder != getSelf()
                    , !this.request_q.isEmpty());
        }
    }

    /**
     * When we recieve the token
     *
     * @param msg
     */
    private void handleTokenReceive(SendToken msg) {
        this.holder = getSelf(); // since we now own the token

        // if current actor needs it then use it. Else send it over
        if (this.request_q.getFirst() == getSelf()) {
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
        if (this.holder == getSelf()
                && !this.using
                && !this.request_q.isEmpty()
                && !(this.request_q.getFirst() == getSelf())) {

            // set new holder
            this.holder = this.request_q.pop();
            this.asked = false;

            log.info("Sending privilege to node: {}", this.holder.path().name());
            this.holder.tell(new SendToken(), getSelf());

        } else {
            log.error("Tried to send privilege but one of the conditions was violated\n" +
                            "Is current actor holder: {}\n" +
                            "Is current actor using: {}\n" +
                            "Is current actor's request_q empty: {}\n" +
                            "Is current actor at the head of its request_q: {}",
                    this.holder == getSelf()
                    , this.using
                    , this.request_q.isEmpty()
                    , (this.request_q.getFirst() == getSelf()));
        }
    }

    private void handleEnterCS(EnterCriticalSection msg) {
        this.using = true;

        // here we access the resource.
        // maybe we could use something like https://doc.akka.io/docs/akka/current/futures.html

        // send a message to the resource that symbolized that we're accessing it,
        // wait for the resource's response and once we get it then we can (exit CS) do:
        getSelf().tell(new ExitCriticalSection(), getSelf());
    }

    private void handleExitCS(ExitCriticalSection msg) {
        this.using = false;
        getSelf().tell(new InvokePriviledgeSend(), getSelf());
    }

    private void handleRestart(Restart msg) {
    }

    private void handleAdvise(Advise msg) {
    }

    private void usimulateCrash(USimulateCrash msg) {
    }

    private void uenterCS(UEnterCS msg) {
        getSelf().tell(new RequestToken(), getSelf());
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

                .match(USimulateCrash.class, this::usimulateCrash)
                .match(UEnterCS.class, this::uenterCS)
                .build();
    }
}
