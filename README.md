## Software Defined Networking Applications - Layer-3 Routing and Distributed Load Balancer

### Overview

This project is a part of Lab 4 for CS640 Fall 2022 at the University of Wisconsin - Madison. The objective of this lab is to implement two control applications for a software-defined network (SDN) using the Floodlight OpenFlow controller and Mininet. The two applications are:

1. A Layer-3 Routing Application that installs rules in SDN switches to forward traffic using the shortest valid path through the network.
2. A Distributed Load Balancer Application that redirects new TCP connections to hosts in a round-robin order.

### Learning Objectives

- Contrast SDN applications and traditional network control planes.
- Create SDN applications that use proactive or reactive flow installation.

### Project Structure

- **src/**: Contains all the Java source files for the SDN applications.
- **README.md**: This file.

### Part 1: Getting Started

Your SDN applications will be written in Java and run atop the Floodlight OpenFlow controller. You will use Mininet to emulate a variety of network topologies.

#### Preparing Your Environment

1. Install required packages:
   ```
   sudo apt update
   sudo apt install -y curl traceroute ant openjdk-7-jdk git iputils-arping
   ```

2. Download Floodlight:
   ```
   cd ~
   git clone https://github.com/parthosa/floodlight-plus.git
   ```

3. Download the starter code:
   ```
   cd ~
   mkdir lab4
   cd lab4
   wget https://pages.cs.wisc.edu/~mgliu/CS640/F22/labs/lab4/lab4.tgz --no-check-certificate
   tar xzvf lab4.tgz
   ```

4. Symlink Floodlight:
   ```
   cd ~/lab4/
   ln -s ~/floodlight-plus
   ```

5. Patch Floodlight:
   ```
   cd ~/lab4/floodlight-plus
   patch -p1 < ~/lab4/floodlight.patch
   ```

#### Running Your Control Applications

1. Compile Floodlight and your applications:
   ```
   cd ~/lab4/
   ant clean && ant
   ```

2. Start Floodlight and your SDN applications:
   ```
   java -jar FloodlightWithApps.jar -cf loadbalancer.prop
   ```

3. Start Mininet:
   ```
   cd ~/lab4
   sudo python2 run_mininet.py single,3
   ```

### Part 2: Layer-3 Routing Application

You need to complete the TODOs in `L3Routing.java` to install and remove flow table entries from SDN switches such that traffic is forwarded to a host using the shortest path.

#### Computing Shortest Paths

Use the Bellman-Ford algorithm to compute the shortest paths to reach a host from every other host in the network. You can use the `getHosts()`, `getSwitches()`, and `getLinks()` methods to get the topology information.

#### Installing Rules

Install rules in the flow table in every switch in the path. The rule should match IP packets (Ethernet type is IPv4) whose destination IP is the IP address assigned to the host.

### Part 3: Distributed Load Balancer Application

You need to complete the TODOs in `LoadBalancer.java` to:

1. Install rules in every switch to:
   - Notify the controller when a client initiates a TCP connection with a virtual IP.
   - Notify the controller when a client issues an ARP request for the MAC address associated with a virtual IP.
   - Match all other packets against the rules in the next table in the switch.

2. Install connection-specific rules for each new connection to a virtual IP to:
   - Rewrite the destination IP and MAC address of TCP packets sent from a client to the virtual IP.
   - Rewrite the source IP and MAC address of TCP packets sent from server to client.

3. Construct and send an ARP reply packet when a client requests the MAC address associated with a virtual IP.

### Testing and Debugging

#### L3 Routing

Test your code by sending traffic between various hosts in the network topology. Use the `pingall` command in Mininet for testing.

To view the contents of an SDN switch’s flow tables, run:
```
sudo ovs-ofctl -O OpenFlow13 dump-flows s1
```

#### Load Balancer

Test your code by issuing web requests (using `curl`) from a client host to the virtual IPs. You can add or remove virtual IPs and hosts by modifying the `loadbalancer.prop` file.

To see which packets a host is sending/receiving, run:
```
tcpdump -v -n -i hN-eth0
```

### Submission Instructions

You must submit a single tar file of the `src` directory containing the Java source files for your SDN applications and a `README.md` file with group member names and NetIDs. To create the tar file, run the following command, replacing `username1` and `username2` with the WISC username (NetID) of each group member:
```
tar czvf username1_username2.tgz src README.md
```

Upload the tar file to the Lab4 tab on Canvas. Please submit only one tar file per group.

### References

- [OpenFlow 1.3 Standard](https://opennetworking.org/wp-content/uploads/2014/10/openflow-spec-v1.3.0.pdf)
- [Floodlight-plus Javadoc](https://pages.cs.wisc.edu/~mgliu/floodlight-plus-doc/index.html)

### Assumptions

- The virtual network environment is set up correctly according to the instructions provided.
- The Floodlight controller and Mininet are correctly configured and operational.
