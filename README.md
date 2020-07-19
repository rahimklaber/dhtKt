## Features
- [x]  get
- [x]  put
- [x]  successor of id
- [x]  predecessor of node
- [x]  notify our successor of us(stabilization)
- [x]  check for down nodes in figher table and replace them accordingly
- [ ]  key,value pairs have metadeta
- [ ]  ask node for it's keys
- [ ]  allow for `gracefull` exit
- [x]  allow for `abrupt` exit ( the keys are lost, but the dht will 'repair' itself)
- [ ]  replication for durability

## things to think about
* should the value of the key/value pair be mutable? If so this would make it hard to do replication.
* should probably make the server and client separate.
* If predecessor fails, it should be removed from the finger table.

## Bugs

1. When a node quits without shutting down the channels (sockets), It's seems that the sockects aren't closed.