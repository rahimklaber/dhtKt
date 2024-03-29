package me.rahimklaber.dhtrpc

/**
 * TODO: stop using blocking calls; figure out how to send a request, close the connection and then the recipient will send us the response. This seems to be much better than making a request to a node and then waiting for that node to make a request etc etc.
 */

/**
 * Todo: cache channels instead of creating a new one every time.
 * Todo: Make public facing api use grpc but something else for inter-node communication.????
 */
import io.grpc.*
import kotlinx.coroutines.*
import me.rahimklaber.dhtrpc.datatable.InMemoryDataTable
import mu.KotlinLogging
import kotlin.math.absoluteValue
import kotlin.math.pow

class ChordNode(val host: String, val port: Int) : NodeGrpcKt.NodeCoroutineImplBase() {
    //    init {
//        System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "DEBUG")
//    }
    private val logger = KotlinLogging.logger("ChordNode")
    val self: Services.tableEntry = Services.tableEntry.newBuilder()
        .setHost(host)
        .setPort(port)
        .setId(hash())
        .build()

    val fingerTableWrapper: FingerTable = FingerTable(self.id, TABLE_SIZE)
    var predecessor by fingerTableWrapper::predecessor

    val dataTable = InMemoryDataTable<String, String>()


    val channelPool = ChannelPool()
    lateinit var server: Server

    val client = Client()

    init {
        fun gracefullShutdownHook() {
            server.shutdownNow()
            channelPool.shutdown()
        }
        Runtime.getRuntime().addShutdownHook(Thread {
            gracefullShutdownHook()
        })
    }


    var successor by fingerTableWrapper::successor
    var currFinger = 0 // for fixFingers. from 0 to 10

    //todo Make this nicer
    suspend fun checkPredecessor() {
//        logger.info { "checking predecessor $predecessor" }
        if (predecessor == null)
            return
        val channel = channelPool.getChannel(predecessor!!.host, predecessor!!.port)
        val state = withContext(Dispatchers.IO) {
            channel.getState(true)
        }
        // THere are problems when the channel is not shutdown correctly
        // The channel stays open and the connectivity state is set to idle??
        // Not having state == ...IDLE caused a memory leak somehow or NVM.
        if (state == ConnectivityState.TRANSIENT_FAILURE || state == ConnectivityState.IDLE || state == ConnectivityState.SHUTDOWN) {
            logger.info { "Predecessor has failed." }
            fingerTableWrapper.removeIf { _, e -> e == predecessor }
            predecessor = null
        }


    }

    /**
     * lists all of the keys of the given Peer.
     */
    suspend fun listRequest(entry: Services.tableEntry): List<String> = client.listRequest(Ip(entry.host, entry.port))

    override suspend fun list(request: Services.empty): Services.keys {
        logger.info { "received list request" }
        return Services.keys.newBuilder().addAllKey(dataTable.list()).build()
    }

    suspend fun fixFingers() {
        currFinger = if (currFinger == 0) 1 else currFinger
        val helper = predecessor
            ?: throw Exception("Predecessor not available for fixFingers, Todo: Create a dedicated exception")// Todo: What should we do here?
        var successorRequest: Services.tableEntry? = null
        channelPool.tryOrClose(helper.host, helper.port) {
            successorRequest = withContext(Dispatchers.IO) {
                successorRequest(
                    helper.host,
                    helper.port,
                    fingerTableWrapper.ids[currFinger]
                )
            }
        }
        //Todo: wtf is this 🔽
        if (successorRequest != null && successorRequest?.port != 0) {
            fingerTableWrapper[currFinger] = successorRequest ?: throw Error("Should never happen")

        } else {
            logger.info { "port is 0. At FixFingers" }
        }

        currFinger = (currFinger + 1) % TABLE_SIZE
    }

    suspend fun stabilize() {
        try {
            logger.debug { "checking successor" }
            val predecessor_of_successor = predecessorRequest(successor!!)
            if (predecessor_of_successor != null &&
                inRangeSuccessor(predecessor_of_successor.id)
            ) {
                if (predecessor_of_successor.port != 0)
                    successor = predecessor_of_successor
                else
                    logger.info { "Port is 0. at stabilize." }

            }

            notifyRequest(successor!!)

        } catch (e: StatusRuntimeException) {
            logger.info { "successor has failed." }//Should probably make this debug.
            //Todo: Make general function.
            fingerTableWrapper.removeIf { _, e -> e == successor }
            channelPool["${successor?.host}${successor?.port}"]?.shutdown()
            channelPool.remove("${successor?.host}${successor?.port}")
        } catch (e: NullPointerException) {
            successor = self
        }


    }

    suspend fun notifyRequest(entry: Services.tableEntry) {
        logger.debug { "sending notify request to ${entry.host}:${entry.port}" }
        channelPool.tryOrClose(entry.host, entry.port) {
            val channel = channelPool.getChannel(entry.host, entry.port)
            val stub = NodeGrpc.newBlockingStub(channel)
            withContext(Dispatchers.IO) { stub.notify(self) }
        }

    }

    override suspend fun notify(request: Services.tableEntry): Services.empty {
        logger.debug { "received notify request from ${request.host}:${request.port}" }
        if (predecessor == null) {

            predecessor = request
        } else if (inRangePredecessor(request.id)) {
            predecessor = request
        }
        return Services.empty.getDefaultInstance()
    }


    suspend fun predecessorRequest(entry: Services.tableEntry): Services.tableEntry? = client.predecessorRequest(Ip(entry.host,entry.port))

    override suspend fun predecessor(request: Services.empty): Services.tableEntry {
        logger.debug { "received predecessor request" }
        //Todo: check this
        return predecessor ?: Services.tableEntry.getDefaultInstance()
    }

    override suspend fun join(request: Services.tableEntry): Services.tableEntry {
        fingerTableWrapper.insertIntoTable(request)
        return self
    }

    /**
     * Put a key,value pair into the DHT.
     */
    suspend fun putRequest(name: String, data: String) {
        logger.info { "Making Put request for key: $name, value: $data" }
        val hash = name.hashCode().absoluteValue % CHORD_SIZE
        if (inRangeSuccessor(hash)) {
            dataTable[name] = data
        } else {
            val before = fingerTableWrapper.maxBefore(hash)
            if (before == null) {
                logger.warn { "Cannot insert data" }
                return
            }
            client.putRequest(Ip(before.host, before.port), name, data)
        }
    }

    override suspend fun put(request: Services.dataEntry): Services.empty {
        logger.info("Received put request for key ${request.name}")
        putRequest(request.name, request.data)
        return Services.empty.getDefaultInstance()
    }

    fun joinRequest(host: String, port: Int): Services.tableEntry {
        val channel = channelPool.getChannel(host, port)
        val stub = NodeGrpc.newBlockingStub(channel)
        val join = stub.join(self)
//        channel.shutdown()
        return join

    }

    fun start(seedHost: String, seedPort: Int) {
        buildTable(seedHost, seedPort)
        joinRequest(seedHost, seedPort)
    }

    fun buildTable(seedHost: String, seedPort: Int) {
        for (i in fingerTableWrapper.ids) {
            runBlocking {
                val entry = successorRequest(seedHost, seedPort, i)
                fingerTableWrapper.insertIntoTable(entry)
            }
        }
    }

    suspend fun successorRequest(host: String, port: Int, id: Int): Services.tableEntry =
        client.successorRequest(Ip(host, port), id)

    fun inRangePredecessor(id: Int): Boolean {
        if (self.id > predecessor!!.id && predecessor!!.id < id && id < self.id) {
            return true
        } else if (self.id <= predecessor!!.id &&
            !(predecessor!!.id >= id && self.id <= id)
        ) {
            return true
        }
        return false
    }

    fun inRangeSuccessor(id: Int): Boolean {
        // If successor id is bigger than our id and the given id is between us and our successor
        return if (successor!!.id > self.id && self.id < id && id <= successor!!.id) {
            true
        }
        // If our successor is "behind" us, meaning they wrapper around the chord ring
        else if (successor!!.id < self.id &&
            !(self.id >= id && successor!!.id < id)
        ) {
            true
        }
        // If i am the only one in the network
        else successor == self
    }

    override suspend fun successor(request: Services.id): Services.tableEntry {
        logger.debug { "received successor request of ${request.id}" }

        val maxBefore = fingerTableWrapper.maxBefore(request.id)

        return if (successor != null && inRangeSuccessor(request.id)) {
            successor ?: Services.tableEntry.getDefaultInstance()
        } else {
            when {
                maxBefore == null -> {
                    self
                }
                successor != null && successor!!.id == self.id -> { // only you in the finger table.
                    self
                }
                else -> {
                    return successorRequest(maxBefore.host, maxBefore.port, request.id)
                }
            }
        }
    }


    private fun hash(): Int {
        return (host.hashCode().absoluteValue + port.hashCode()) % CHORD_SIZE//2^8
    }

    fun startService(args: Array<String>): Server {

        val start = ServerBuilder
            .forPort(port)
            .addService(this)
            .build()
            .start()
        server = start
        if (args.size > 2)
            start(args[2], args[3].toInt())
        else {
            for (i in 0 until TABLE_SIZE) {
                fingerTableWrapper[i] = self
            }

        }
        GlobalScope.launch {
            while (true) {
                delay(5000)
                try {
                    checkPredecessor();stabilize();fixFingers()
                } catch (e: java.lang.Exception) {
                    println(e)
                }
            }
        }

        return start
    }

    /**
     * Create a [Services.dataEntry] instance from the key and value
     */
    fun dataEntryfromMapEntry(key: String, value: String): Services.dataEntry {
        return Services.dataEntry
            .newBuilder()
            .setName(key)
            .setData(value)
            .build()
    }

    suspend fun getRequest(name: String): Services.dataEntry {
        val hash = name.hashCode().absoluteValue % CHORD_SIZE
        return if (inRangeSuccessor(hash) && dataTable.contains(name)) {
            dataEntryfromMapEntry(name, dataTable[name]!!)
        } else {
            val before = fingerTableWrapper.maxBefore(hash) ?: throw Exception("before is null, cannot do get")

            client.getRequest(Ip(before.host, before.port), name)
        }
    }

    override suspend fun get(request: Services.name): Services.dataEntry {
        logger.info("Received get request for key:  ${request.name}")
        val hash = request.name.hashCode().absoluteValue % CHORD_SIZE
        val dataEntry: Services.dataEntry?
        return if (inRangeSuccessor(hash)) {
            dataEntryfromMapEntry(request.name, dataTable[request.name]!!)
        } else {
            getRequest(request.name)
        }
//        logger.info("Responding to get request (for ${request.name}) with: ${dataEntry?.data}")
    }

    companion object {
        const val TABLE_SIZE = 5
        val CHORD_SIZE = 2.0.pow(TABLE_SIZE.toDouble()).toInt() // 0 to 2^n -1 = 0 to 255 to 0 again

        @JvmStatic
        fun main(args: Array<String>) {
            create(args).startService(args).awaitTermination()
        }

        fun create(args: Array<String>): ChordNode {
            return ChordNode(args[0], args[1].toInt())
        }
    }
}