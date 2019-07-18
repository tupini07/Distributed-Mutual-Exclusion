## Distributed Mutual Exclusion: Token passing on a tree ##

This is an implementation of [A Tree-Based Algorithm for Distributed Mutual
Exclusion](https://dl.acm.org/citation.cfm?doid=58564.59295) using the [Akka](https://akka.io/) framework. 
It includes both the *token passing* and mutual exclusion functionality, and the *recovering from failures* functionality.

The system is composed of three main components:

- [NodeAct](https://github.com/tupini07/Distributed-Mutual-Exclusion/blob/master/src/main/java/com/tmds/project/NodeAct.java)
  the actor implementation for the nodes in the network. These will *compete* among themselves to access a resource. All
  methods for ensuring mutual exclusion and to recover from failures are implemented here.
- [ResourceActor](https://github.com/tupini07/Distributed-Mutual-Exclusion/blob/master/src/main/java/com/tmds/project/ResourceActor.java)
  this is the actor representing the resource which the nodes will want to access.
- [AkkaStartSystem](https://github.com/tupini07/Distributed-Mutual-Exclusion/blob/master/src/main/java/com/tmds/project/AkkaStartSystem.java)
  here we set the up the nodes and the topology of the network. We also set up the resource actor and a small *command
  interface* with which the user can interact with the application.
  
More details about each of these, and the messages they use can be seen in the [report](https://github.com/tupini07/Distributed-Mutual-Exclusion/blob/master/report.pdf)
  
  
## Running ##
  
You can easily run the application using *Gradle* by executing the following from within the project's folder:
  
``` bash
gradle run
```
  
## Using the application ##

You can easily interact with the application by supplying it a series of commands. The *interpreter* for these commands
is defined in *AkkaStartSystem*. This is their documentations (which can also be seen by entering the `h` command):

```
Small interface to interact with the program
Inputs:
         'h' to print this message
         'q' to exit
         'cs node_name' so that `node_name` enters critical section
         'csall' so ALL nodes enter into the critical section
         'st node_name' to make `node_name` print its internal state
         'crash node_name' so that `node_name` simulates a crash

Note that multiple inputs can be entered at once by separating them with ; . For example:
         cs node_1; cs node_2; crash node_5
```

To execute a command you just type it in the terminal and press `Enter`.

