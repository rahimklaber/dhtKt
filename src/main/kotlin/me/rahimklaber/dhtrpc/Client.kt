package me.rahimklaber.dhtrpc


import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import kotlin.math.absoluteValue




interface Iclient {
    suspend fun listRequest(addr: Ip): List<String>
    suspend fun predecessorRequest(addr: Ip): Services.tableEntry
    suspend fun successorRequest(addr: Ip, id: Int): Services.tableEntry
    suspend fun putRequest(addr: Ip, name: String, data: String)
    suspend fun getRequest(addr: Ip, name: String): Services.dataEntry
}

class Client : Iclient {
    private val logger = KotlinLogging.logger("Client")
    private val channelPool = ChannelPool()
    override suspend fun listRequest(addr: Ip): List<String> {
        logger.info { "sending list request to ${addr.host}:${addr.port}" }

        var keys: List<String>? = null
        channelPool.tryOrClose(addr.host, addr.port) {
            val channel = channelPool.getChannel(addr.host, addr.port)
            val stub = NodeGrpc.newBlockingStub(channel)
            keys = withContext(Dispatchers.IO) { stub.list(Services.empty.getDefaultInstance()).keyList }
        }

        return keys ?: emptyList()
    }

    override suspend fun predecessorRequest(addr: Ip): Services.tableEntry {
        logger.debug { "sending predecessor request to ${addr.host} : ${addr.port}" }
        var predecessor: Services.tableEntry? = null
        channelPool.tryOrClose(addr.host, addr.port) {
            val channel = channelPool.getChannel(addr.host, addr.port)
            val stub = NodeGrpc.newBlockingStub(channel)
            predecessor = withContext(Dispatchers.IO) { stub.predecessor(Services.empty.getDefaultInstance()) }
        }


        return predecessor ?: throw Exception("predecesor of $addr is null")
    }

    override suspend fun successorRequest(addr: Ip, id: Int): Services.tableEntry {
        logger.debug { "sending successor request to $addr of id $id" }
        val serviceId = Services.id.newBuilder().setId(id).build()
        val channel = channelPool.getChannel(addr.host, addr.port)
        val stub = NodeGrpc.newBlockingStub(channel)
        var successor: Services.tableEntry? = null

        channelPool.tryOrClose(addr.host, addr.port) {
            successor = withContext(Dispatchers.IO) { stub.successor(serviceId) }
        }

        return successor ?: throw Exception("successor of id: $id is null")
    }

    override suspend fun putRequest(addr: Ip, name: String, data: String) {
        logger.info { "Making Put request for key: $name, value: $data" }
        val hash = name.hashCode().absoluteValue % ChordNode.CHORD_SIZE
        channelPool.tryOrClose(addr.host, addr.port) {
            val channel = channelPool.getChannel(addr.host, addr.port)
            val stub = NodeGrpc.newBlockingStub(channel)
            withContext(Dispatchers.IO) {
                stub.put(
                    Services.dataEntry.newBuilder().setName(name).setData(data).build()
                )
            }
        }
    }


    override suspend fun getRequest(addr: Ip, name: String): Services.dataEntry {
        logger.info { "Making get request for : $name" }
        val hash = name.hashCode().absoluteValue % ChordNode.CHORD_SIZE

        var get: Services.dataEntry? = null
        channelPool.tryOrClose(addr.host, addr.port) {
            val channel = channelPool.getChannel(addr.host, addr.port)
            val stub = NodeGrpc.newBlockingStub(channel)
            get = withContext(Dispatchers.IO) { stub.get(Services.name.newBuilder().setName(name).build()) }
        }

        return get ?: Services.dataEntry.getDefaultInstance()
    }
}


