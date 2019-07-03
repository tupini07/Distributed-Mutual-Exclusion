package com.tmds.project;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;

//#printer-messages
public class ResourceActor extends AbstractActor {

  public ResourceActor(LoggingAdapter log) {
    this.log = log;
  }

  //#printer-messages
  static public Props props() {
    return Props.create(ResourceActor.class, () -> new ResourceActor());
  }

  //#printer-messages
  static public class Greeting {
    public final String message;

    public Greeting(String message) {
      this.message = message;
    }
  }
  //#printer-messages

  private LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

  public ResourceActor() {
  }



  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(Greeting.class, greeting -> {
            log.info(greeting.message);
        })
        .build();
  }
//#printer-messages
}
//#printer-messages
