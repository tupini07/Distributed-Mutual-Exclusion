package com.tmds.project;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;

import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedList;

public class ResourceActor extends AbstractActor {

    private LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);


    public ResourceActor() {
    }

    static public Props props() {
        return Props.create(ResourceActor.class, () -> new ResourceActor());
    }

    // ----------------------------------------------------
    // Message classes that are handled

    /**
     * Message sent from a {@link NodeAct} actor which signifies that said actor wants to use this
     * resource. The sending of this message implies that the sender holds the token and is using it.
     */
    static public class AccessResource {
    }

    // ----------------------------------------------------
    // implementation of handling for messages

    private void handleResourceAccess(AccessResource msg) {
        // this should potentially print something stating that the
        // resource is being accessed, and the id of the actor accessing it
        ActorRef resource_user = getSender();
        log.info("Node '' is currently accessing the resource", resource_user.path().name());

        getContext().getSystem().scheduler().scheduleOnce(
                Duration.ofMillis(5000),
                resource_user,
                new NodeAct.ExitCriticalSection(),
                getContext().getSystem().dispatcher(),
                getSelf());

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
