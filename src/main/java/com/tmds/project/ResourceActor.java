package com.tmds.project;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;

import java.util.HashSet;
import java.util.LinkedList;

public class ResourceActor extends AbstractActor {

    private LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    private String name;

    public ResourceActor(String name) {
        this.name = name;
    }

    static public Props props(String name) {
        return Props.create(ResourceActor.class, () -> new ResourceActor(name));
    }

    // ----------------------------------------------------
    // Message classes that are handled

    /**
     * Message sent from a {@link NodeAct} actor which signifies that said actor wants to use this
     * resource. The sending of this message implies that the sender holds the token and is using it.
     */
    static public class AccessResource {
        // message should include actor ID
    }

    // ----------------------------------------------------
    // implementation of handling for messages

    private void handleResourceAccess(AccessResource msg) {
        // this should potentially print something stating that the
        // resource is being accessed, and the id of the actor accessing it
    }

    // ----------------------------------------------------
    // mapping between message classes and methods for handling
    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(AccessResource.class, this::handleResourceAccess)
                .build();
    }
}
