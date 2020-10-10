## Features
- [x]  get
- [x]  put
- [x]  successor of id
- [x]  predecessor of node
- [x]  notify our successor of us(stabilization)
- [x]  check for down nodes in figher table and replace them accordingly
- [ ]  key,value pairs have metadeta
- [x]  ask node for it's keys
- [ ]  allow for `gracefull` exit
- [x]  allow for `abrupt` exit ( the keys are lost, but the dht will 'repair' itself)
- [ ]  replication for durability
- [ ]  create class for finger table
- [ ]  create class for data table

## Ideas
- Make a key associated to a list of values. You can then append to this list.

## things to think about
* should the value of the key/value pair be mutable? If so this would make it hard to do replication.
* should probably make the server and client separate.
* If predecessor fails, it should be removed from the finger table.
    - right now the predecessor is used for the `fixFingers` function, how to deal with this? use random peer maybe?
* memory consumption is really high when inserting a lot of stuff, first i thought that this was the sockets(or something relating to them)
, but now im thinking it might be the gui and display all of them at the same time without any pagination or something.
    - Just tested this went from 900MB to 600MB by not displaying the data.
        - should be able to reduce it further.

## Bugs

1. When a node quits without shutting down the channels (sockets), It's seems that the sockects aren't closed.
2. Grpc doesn't support null, use Defaultvalue as null?
3. Concurrent modification exception when spamming put request
4. leaving and then rejoining the DHT fucks up in the beginning. Everything is fine after a few seconds. 