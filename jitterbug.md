---
title: Jitterbug protocol
permalink: jitterbug.html
---
[[Index]](index.html)

# Jitterbug protocol

Jitterbug protocol is a yet another protocol for token-ring emulation.

It provides following garantees:

 * Liveness - if at least two nodes are alive, communication will not stop
   * Case with dead leader is handled via specific procedure, that decides, who should be a new token holder
 * Weak token-ring list consistency between nodes:
   * Only current leader (token holder) can add a new node to the list
   * No node can be deleted from the list
      * Dead nodes are handled via penalties
   * Up-to-date version of node list is transferred with token

Generally, protocol maintains a so-called merge/tree graph.

Let's consider a normal work process: token is passed around the subnet, eventually subnet splits to two or more subnets or these subnets merge back. These merges and splits form a directinal acyclic graph and correctly maintaining this graph is a purpose of Jitterbug protocol.

Core point to achieve this is application-supplied merge function. Protocol uses it when detects a split/merge event two merge two conflicting messages into one.

Note, that merge is applied only when split/merge occurs, normally we only generate new (next) message.

@TODO: token races (i.e. two tokens in subnet, not meeting anywhere, infinitely circulating in subnet)

## Node states

Protocol consists of several procedures, operating on fixed set of node states

Each node, participating in communication can be only in a single state:

  * orphan
  * waiter
  * leader

In next sections we describe each state in turn, referencing procedures available for use from these states. Later we describe procedures in detail.

Basic concept of Jitterbug protocol is to maintain a minimal set of subnets (i.e. connected graphs of nodes) with proper support them to be splitted/merged.

In each state, node maintains following *state variables* (updated only in node's leader state)

  * token_id
  * data
  * list of nodes
  * set of nodes to add
  * nodes' penalties

All nodes listen to specified (same for all nodes) udp port {udp_port}. Node is free to listen to any tcp port, but it shouldn't be changed during node's lifetime.

Every node has it's unique host_id identifier. It can be either hash of mac address or just a random value. The only restriction for it is to be unique.
This value is to be used to more easily navigate through node list (without messing around with addresses). Note, that node can possibly have few addresses (e.g. one IPv4 and another IPv6), which isn't a problem as far as it's reachable by every such address.

### Orphan state

Being orphan means that you are not associated yet with any subnet.
Node can have orphan state only in two cases:

 * when it was just initiated, i.e. haven't yet participated in any communication with other nodes
 * after {renew_timeout} occurred, i.e. node realizes that it haven't received messages but for too long

When node find itself an orphan, it tries to join active subnet (or create own if no yet exist).
To do so, node initiates *token_restore* procedure, after executing which it switches state to one of:

  * waiter
  * leader

This procedure requires waiting for replies from other nodes (which are checked with timeouts) and hence should be launched in parallel with usual waiting for token pass (that may be initiated by other node within these timeouts).

### Waiter state

It's a passive state of node. In this state node waits for either of events to occur:
  
  * {renew_timeout} occurs, node becomes an orphan
  * token received, node becomes a leader

### Leader
It's an active state of node. Being in this state node follows such flow:

  1. Throw a coin, with probability `1 / ( N * {token_loose_prob_base} )`
      1. if *true*, switch to *orphan* state
      2. if false
          1. Computes next message
          2. Updates *state variables*:
              1. token_id, data, penalties
              2. updates node list with new nodes, not yet in the list
          3. Launchs *token_pass* procedure

## Procedures

### token_restore

Token restore procedure's purpose is to get node acknowledged of current active token status.

It's launched by node, being in orphan state. For sender algo is following:

**token_restore_try** (*tryout_token_id*):

  1. Repeatedly send a UDP broadcast with message < TR1, *tryout_token_id*, host_id, tcp_port >
      * repeat interval is {tr_interval}
      * should repeat {tr_count} times
  2. Wait {tr_count}*{tr_interval} time for replies
      * replies would be of kind < TR2, holds_token, token_id >
  3. Analize replies
      * if within timeout we've switched our state to leader at least once, abort procedure (i.e. do nothing)
      * if received a message < TR2,  1 , token_id >, than there exist a leader
          * **return false**
      * if there exist a message of kind < TR2,  0 , token_id > with _token_id_ greater than *tryout_token_id*
          * **return false**
      * otherwise
          * **return true**

**token_restore** ():
  
  1. access_granted_1 = **token_restore_try** (self_token_id) 
      * //try to grab access on leadership with self token id
  2. if (access_granted_1)
      1. self_token_id = generate_new_token_id ()
          *  //generate new random token_id
      2. access_granted_2 = **token_restore_try** (self_token_id) 
          * //try to ensure we still have rights for leadership, i.e. there still exist no tuple greater after token_id generation
      3. if (access_granted_2)
          * switch state to *leader*
      4. else
          * switch state to *waiter*
  3. switch state to *waiter*

All other nodes should do following on receiving of < TR1, token_id, host_id, tcp_port > (for each message received):

  1. if in leader state, send < TR2, 1, self token_id >
  2. otherwise
      1. if received token_id is greater, than ours, do nothing
      2. otherwise
          1. send < TR2, 0, self token_id > as a reply (via UDP, only to sender's IP address)
          2. remember node <host_id, ip address, tcp port>  to be later added to node list

See **Appendix A** section for some additional remarks regarding **token_restore** procedure (explanation of why it won't end up into infinite loop).

### token_pass

Token pass procedure's purpose is to pass token from current leader to next node in a list.

It's launched by current leader when he's ready to pass token. It's split into two phases:
  
  1. Passing up-to-date node list to next node
  2. Passing token

Further in this section we will refer to next node as candidate.
All communications, described bellow are done via TCP.

More detailed, for a single candidate:

#### *token_pass_for_candidate ( candidate_i )*:

  0. Execute within timeout {token_pass_timeout}
      2. Leader passes message < TP1, token_id, node_list_hash > to candidate
            1. candidate checks node_list_hash with hash of his node list and replies:
            2. < TP2 >, if hashs differ
               1. Leader sends message < TP4, node_list >
               2. Candidate remembers node_list for the connection (but doesn't update variables)
            3. < TP3 >, if hashs are equal. This case, candidate remembers node_list for the connection
      3. Leader passes a message < TP5, data > to candidate
            1. token was passed
            2. candidate compares self's token_id value with one receieved
                1. if they're equal, data variable is updated with received data
                2. otherwise
                  1. token_id variable is updated with received token_id
                  2. data variable is assigned to result of merge of old data and received one
            3. candidate switches to leader state
      4. **return true**
  1. Timeut ticked, **return false**

Aforementioned algo is repeatedly tried for all candidates in turn (following node list from current node). We will describe this in detail after describing penalties (which play a key role in process of candidate selection).

#### Candidate selection and penalties

Every node locally stores a list of penalties for each node, participating in communication. Initially for every node:

  * penalty_threshold = 0
  * penalty_count = 0

The key idea of penalties is to disallow assumed-to-be-dead nodes from communication (not to waste time on them). Also, if node recover, we would like it to join the conversation again.
Let's consider candidate selection procedure. Assume we want to try candidate with ordinal number *i*:

First, we check for is candidate allowed to participate in current round:

**token_pass**:

  1. Leader computes new data from data variable, updates data variable
  2. for every `candidate_i`
      0. is_allowed_for_round = true
      1. if (penalty_count_i >= 2^penalty_threshold_i - 1)
          1. candidate was disallowed for enough time
          2. update: penalty_count_i = 0
      2. else
          1. is_allowed_for_round = false
      3. if (is_allowed_for_round)
          1. res = **token_pass_for_candidate** ( candidate_i )
          2. if (res)
              1. if (penalty_threshold_i > 0) penalty_threshold_i--
              2. break;
          3. else
              1. penalty_threshold_i++
      4. else
          1. penalty_count_i++
  3. node switches to waiter state

## Messages and variables

Message of each type starts with meta-information byte. It contains information about version and type encoded:
  * `first_byte & 0xF` - version of protocol (lowest four bits)
  * `first_byte & 0xF0` - type of message (highest four bits)

Type of message constants:
  
  * TR1 = 0
  * TR2 = 1
  * TP1 = 2
  * TP2 = 3
  * TP3 = 4
  * TP4 = 5
  * TP5 = 6

Rest bytes of each message should be encoded in following format:

  * TR1
    1. token_id
    2. host_id
    3. tcp_port
  * TR2
    1. token_id
  * TP1
    1. token_id
    2. node_list_hash
  * TP2
    1. < no data >
  * TP3
    1. < no data >
  * TP4
    1. node_list
  * TP5
    1. data

In above:

  * token_id - 4-byte integer. Meanfull token part (generated in *token_restore*) is stored in 31 bits, leadership is defined by sign:
    * positive value if node is a leader (holds token)
    * negative value otherwise
  * tcp_port - 2-byte integer. Port to which tcp listener is bound
  * host_id - 4-byte integer with highest (sign) bit not used (viewing host_id as a signed integer, it should be >= 0)
  * node_list
    * 4-byte size of list
    * nodes in format:
       * signed host_id - 4-byte integer
          * negative for IPv6 address
          * positive for IPv4 address
       * ip address
          * 4 bytes for IPv4
          * 16 bytes for IPv6
       * port, 2 bytes
  * node_list_hash - 4-byte integer
    * standard polynomial hash on base of 577 of node_list bytes
    * bytes should be taken as signed (i.e. -128..127)
    * refer to code bellow
  * data - data to send, application-provided array of bytes
    * size of data isn't defined anyhow, it's up to application to handle it if needed

All integers are written in Big-Endian.

### Code for hash function

        byte[] bytes = (..);
        int hash = 0;
        for(byte b : bytes){
          hash = hash * 577 + b;
        }


## Appendix A

Some remarks on **token_restore** procedure. From it's description it may seem, that it may fall into infinite loop. Now we will proof, why this won't happen.

First, let's note, how token_id numbers are generated. They are generally random numbers. Of course, in real environments their randomness is doubtfull. This property depends tightly on internals of random functions accross all nodes in system, seeds used to initialize these functions and so on. But in this section let's assume that distribution of generated numbers accross all nodes is close to uniform.

Given a uniform distribution accross all generated numbers, it's not hard to find upper bound of expected value of times, *token_pass* procedure would be launched before any node finally takes leadership (we consider now a single subnet, no merges/splits with other subnets occur).

If our token_id is greater than any other token_id in system and no leader exist, we take the leadership. Otherwise there exist at least one node, whoes token_id is greater. Token_ids are uniformly distributed within range 0..2^32-1, so possibility that one lower or equal, than other is 1/2. So with possibility 1/2 **token_restore** would be launched only once.

Same reasoning could be applied to further steps, which directly implies possibility for k-th launch to succeed as 1/2^k.

Taking sum of series 1/2^i from i=0 to oo, we conclude to expected value of **token_restore** launches be not greater than 2.
