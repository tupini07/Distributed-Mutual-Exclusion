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
    static public class Initialize {
        public final boolean is_first; // whether this is the first node in the flood or not

        public Initialize(boolean is_first) {
            this.is_first = is_first;
        }
    }

    static public class SetNeighbors {
        public final HashSet<ActorRef> neighbors;

        public SetNeighbors(HashSet<ActorRef> neighbors) {
            this.neighbors = neighbors;
        }
    }

    static public class RequestToken {
    }

    static public class SendToken {
    }

    static public class EnterCriticalSection {
    }

    static public class ExitCriticalSection {
    }

    static public class Restart {
    }

    static public class Advise {
    }


    // ----------------------------------------------------

    // implementation of handling for messages
    private void handleInitialize(Initialize msg) {
        if (this.holder != null) {
            // if this node has already recieved the initialize message then don't
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

    private void handleTokenRequest(RequestToken msg) {
    }

    private void handleTokenReceive(SendToken msg) {
    }

    private void handleEnterCS(EnterCriticalSection msg) {
    }

    private void handleExitCS(ExitCriticalSection msg) {
    }

    private void handleRestart(Restart msg) {
    }

    private void handleAdvise(Advise msg) {
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

                .match(EnterCriticalSection.class, this::handleEnterCS)
                .match(ExitCriticalSection.class, this::handleExitCS)

                .match(Restart.class, this::handleRestart)
                .match(Advise.class, this::handleAdvise)
                .build();
    }
}
