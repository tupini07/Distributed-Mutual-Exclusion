package com.tmds.project;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;

public class AkkaStartSystem {
    public static void main(String[] args) {
        final ActorSystem system = ActorSystem.create("DMX");

        try {
            // Resource node (this is what the actors will try to access)
            final ActorRef resourcn = system.actorOf(ResourceActor.props(), "Bathroom");

            // NOTE: Topology comes from Figure 7 of K. Raymond paper
            final ActorRef n1 = system.actorOf(NodeAct.props(1, resourcn), "node_1");
            final ActorRef n2 = system.actorOf(NodeAct.props(2, resourcn), "node_2");
            final ActorRef n3 = system.actorOf(NodeAct.props(3, resourcn), "node_3");
            final ActorRef n4 = system.actorOf(NodeAct.props(4, resourcn), "node_4");
            final ActorRef n5 = system.actorOf(NodeAct.props(5, resourcn), "node_5");
            final ActorRef n6 = system.actorOf(NodeAct.props(6, resourcn), "node_6");
            final ActorRef n7 = system.actorOf(NodeAct.props(7, resourcn), "node_7");
            final ActorRef n8 = system.actorOf(NodeAct.props(8, resourcn), "node_8");
            final ActorRef n9 = system.actorOf(NodeAct.props(9, resourcn), "node_9");
            final ActorRef n10 = system.actorOf(NodeAct.props(10, resourcn), "node_10");
//            final ActorRef n11 = system.actorOf(NodeAct.props(11), "node_11");
//            final ActorRef n12 = system.actorOf(NodeAct.props(12), "node_12");
//            final ActorRef n13 = system.actorOf(NodeAct.props(13), "node_13");
//            final ActorRef n14 = system.actorOf(NodeAct.props(14), "node_14");
//            final ActorRef n15 = system.actorOf(NodeAct.props(15), "node_15");
//            final ActorRef n16 = system.actorOf(NodeAct.props(16), "node_16");
//            final ActorRef n17 = system.actorOf(NodeAct.props(17), "node_17");
//            final ActorRef n18 = system.actorOf(NodeAct.props(18), "node_18");

            n1.tell(new NodeAct.SetNeighbors(new HashSet<ActorRef>(Arrays.asList(
                    n2, n3, n4
            ))), ActorRef.noSender());


            n2.tell(new NodeAct.SetNeighbors(new HashSet<ActorRef>(Arrays.asList(
                    n5, n6, n1
            ))), ActorRef.noSender());


            n3.tell(new NodeAct.SetNeighbors(new HashSet<ActorRef>(Arrays.asList(
                    n1, n7, n8
            ))), ActorRef.noSender());


            n4.tell(new NodeAct.SetNeighbors(new HashSet<ActorRef>(Arrays.asList(
                    n1, n9, n10
            ))), ActorRef.noSender());


            n5.tell(new NodeAct.SetNeighbors(new HashSet<ActorRef>(Arrays.asList(
                    n2
            ))), ActorRef.noSender());


            n6.tell(new NodeAct.SetNeighbors(new HashSet<ActorRef>(Arrays.asList(
                    n2
            ))), ActorRef.noSender());


            n7.tell(new NodeAct.SetNeighbors(new HashSet<ActorRef>(Arrays.asList(
                    n3
            ))), ActorRef.noSender());


            n8.tell(new NodeAct.SetNeighbors(new HashSet<ActorRef>(Arrays.asList(
                    n3
            ))), ActorRef.noSender());


            n9.tell(new NodeAct.SetNeighbors(new HashSet<ActorRef>(Arrays.asList(
                    n4
            ))), ActorRef.noSender());

            n10.tell(new NodeAct.SetNeighbors(new HashSet<ActorRef>(Arrays.asList(
                    n4
            ))), ActorRef.noSender());


            // Ensure that the tree has been built correctly
            Thread.sleep(2000);


            // -----------------------------------------------------
            // choose a random node as the initial possessor of the token
            n4.tell(new NodeAct.Initialize(true), ActorRef.noSender());

            // Ensure nodes have been initialized correctly
            Thread.sleep(2000);

            // -----------------------------------------------------
            // Enter Critical Section

            n7.tell(new NodeAct.UEnterCS(), ActorRef.noSender());

            System.out.println(">>> Press ENTER to exit <<<");
            System.in.read();
        } catch (IOException ioe) {
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            system.terminate();
        }

    }
}
