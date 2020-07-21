package me.rahimklaber.dhtrpc

/**
 * TODO: stop using blocking calls; figure out how to send a request, close the connection and then the recipient will send us the response. This seems to be much better than making a request to a node and then waiting for that node to make a request etc etc.
 */

/**
 * Todo: cache channels instead of creating a new one every time.
 * Todo: Make public facing api use grpc but something else for inter-node communication.????
 */
import io.grpc.*
import io.grpc.stub.StreamObserver
import javafx.collections.FXCollections
import javafx.collections.ObservableMap
import mu.KotlinLogging
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.collections.HashMap
import kotlin.math.absoluteValue
import kotlin.math.pow

class ChordNode(val host: String, val port: Int) : NodeGrpc.NodeImplBase() {
    //    init {
//        System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "DEBUG")
//    }
    private val logger = KotlinLogging.logger {}
    val self: Services.tableEntry = Services.tableEntry.newBuilder()
        .setHost(host)
        .setPort(port)
        .setId(hash())
        .build()
    var predecessor: Services.tableEntry? = null
    val fingerTable: ObservableMap<Int, Services.tableEntry> =
        FXCollections.synchronizedObservableMap(FXCollections.observableHashMap())
    val dataTable: ObservableMap<String, String> =
        FXCollections.synchronizedObservableMap(FXCollections.observableHashMap())
    val fingerTableIds = IntRange(1, TABLE_SIZE).map { (self.id + 2.0.pow(it - 1)).toInt() % CHORD_SIZE }
    val scheduledExecutor = Executors.newSingleThreadScheduledExecutor()
    val channelPool = HashMap<String, ManagedChannel>()
    var server: Server? = null

    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            gracefullShutdownHook()
        })
    }

    fun gracefullShutdownHook() {
        scheduledExecutor.shutdownNow()
        server?.shutdownNow()
        channelPool.forEach { it.value.shutdownNow() }
    }

    fun getChannel(host: String, port: Int): ManagedChannel {
        // `:` separator ?
        var channel = channelPool["$host$port"]
        return if (channel == null) {
            channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build()
            channelPool["$host$port"] = channel
            channel
        } else {
            channel
        }
    }

    /**
     * Try to send a message.
     * If it fails due to a closed connection, the channel is removed and the node is removed from the finger table.
     */
    fun tryOrClose(host: String, port: Int, `fun`: () -> Unit) {
        try {
            `fun`()
        } catch (e: StatusRuntimeException) {
            channelPool["$host$port"]?.shutdown()
            channelPool.remove("$host$port")
            val toRemove = fingerTable.filter { (_, e) -> (e.host == host) and (e.port == port) }.keys
            fingerTable.keys.removeAll(toRemove)
            //close
        }
    }

    var successor: Services.tableEntry?
        get() = fingerTable[fingerTableIds[0]]
        set(value) {
            fingerTable[fingerTableIds[0]] = value
        }
    var currFinger = 0 // for fixFingers. from 0 to 10

    //todo Make this nicer
    fun checkPredecessor() {
//        logger.info { "checking predecessor $predecessor" }
        if (predecessor == null)
            return
        val channel = getChannel(predecessor!!.host, predecessor!!.port)
        try {
            val state = channel.getState(true)

            // THere are problems when the channel is not shutdown correctly
            // The channel stays open and the connectivity state is set to idle??
            // Not having state == ...IDLE caused a memory leak somehow.
            if (state == ConnectivityState.TRANSIENT_FAILURE || state == ConnectivityState.IDLE) {
                logger.info { "Predecessor has failed." }
                val toRemove = fingerTable.filter { (k, e) -> e == predecessor }.keys
                fingerTable.keys.removeAll(toRemove)
                predecessor = null
            }

        } catch (e: Exception) {
            println(e)
            channel.shutdown()
        } finally {
//            channel.shutdown()
        }

    }

    fun listRequest(entry: Services.tableEntry): List<String> {
        logger.info { "sending list request to ${entry.host}:${entry.port}" }

        var keys: List<String>? = null
        tryOrClose(entry.host, entry.port) {
            val channel = getChannel(entry.host, entry.port)
            val stub = NodeGrpc.newBlockingStub(channel)
            keys = stub.list(Services.empty.getDefaultInstance()).keyList
        }


        return keys ?: emptyList()
    }

    override fun list(request: Services.empty, responseObserver: StreamObserver<Services.keys>) {
        logger.info { "received list request" }
        responseObserver.onNext(Services.keys.newBuilder().addAllKey(dataTable.keys).build())
        responseObserver.onCompleted()
    }

    fun fixFingers() {
        currFinger = if (currFinger == 0) 1 else currFinger
        val helper = predecessor!! // Todo: What should we do here?
        var successorRequest: Services.tableEntry? = null
        tryOrClose(helper.host, helper.port) {
            successorRequest = successorRequest(helper.host, helper.port, fingerTableIds[currFinger])
        }
        //Todo: wtf is this 🔽
        if (successorRequest != null && successorRequest?.port != 0) {
            fingerTable[fingerTableIds[currFinger]] = successorRequest

        } else {
            logger.info { "port is 0. At FixFingers" }
        }

        currFinger = (currFinger + 1) % TABLE_SIZE
    }

    fun stabilize() {
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
            val toRemove = fingerTable.filter { (k, e) -> e == successor }.keys
            fingerTable.keys.removeAll(toRemove)
            channelPool["${successor?.host}${successor?.port}"]?.shutdown()
            channelPool.remove("${successor?.host}${successor?.port}")
        } catch (e: NullPointerException) {
            successor = self
        }


    }

    fun notifyRequest(entry: Services.tableEntry) {
        logger.debug { "sending notify request to ${entry.host}:${entry.port}" }
        tryOrClose(entry.host, entry.port) {
            val channel = getChannel(entry.host, entry.port)
            val stub = NodeGrpc.newBlockingStub(channel)
            val notify = stub.notify(self)
        }

    }

    override fun notify(request: Services.tableEntry, responseObserver: StreamObserver<Services.empty>) {
        logger.debug { "received notify request from ${request.host}:${request.port}" }
        if (predecessor == null) {

            predecessor = request
        } else if (inRangePredecessor(request.id)) {
            predecessor = request
        }
        responseObserver.onNext(Services.empty.getDefaultInstance())
        responseObserver.onCompleted()
    }


    /**
     * diff between fingertable pos and an id
     * takes into account that the fingertable is a ring
     *
     * calculates how much needs to be added to `finger` to be `id`
     */
    fun fingerDiff(finger: Int, id: Int): Int {
        return when {
            id < finger -> {
                (CHORD_SIZE - finger) + id
            }
            id > finger -> {
                id - finger
            }
            else -> {
                0
            }
        }
    }

    /**
     * TODO this seems to always return true ?
     *
     * @param e
     */
    fun insertIntoTable(e: Services.tableEntry) {
        // This is confusing as fuck
        // dont like that the `insertPredicate` fun uses the `e` param.
        fun insertPredicate(k: Int): Boolean {
            return if (fingerTable[k] == null) {
                true
            } else {
                val diffCurr = fingerDiff(k, fingerTable[k]!!.id)
                val diffNew = fingerDiff(k, e.id)
                diffNew <= diffCurr
            }
        }

        fun MutableMap<Int, Services.tableEntry>.putIf(
            key: Int,
            value: Services.tableEntry,
            predicate: (Int) -> Boolean
        ) {
            if (predicate(key)) put(key, value)

        }
        if (e.id == self.id) return
        fingerTableIds
            .forEach {
                fingerTable.putIf(
                    it,
                    e,
                    ::insertPredicate
                )
            }
    }


    /**
     * get the key where the node with the given id should be inserted.
     *
     */
    fun keyOf(id: Int): Int {
        for (i in 1..TABLE_SIZE) {
            val key = (self.id + 2.0.pow(i - 1)).toInt() % CHORD_SIZE
            if (id >= key) return key
        }
        return 0
    }

    fun predecessorRequest(entry: Services.tableEntry): Services.tableEntry? {
        logger.debug { "sending predecessor request to ${entry.host} : ${entry.port}" }
        var predecessor: Services.tableEntry? = null
        tryOrClose(entry.host, entry.port) {
            val channel = getChannel(entry.host, entry.port)
            val stub = NodeGrpc.newBlockingStub(channel)
            predecessor = stub.predecessor(Services.empty.getDefaultInstance())
        }


        return predecessor
    }

    override fun predecessor(request: Services.empty, responseObserver: StreamObserver<Services.tableEntry>) {
        logger.debug { "received predecessor request" }
        responseObserver.onNext(predecessor)
        responseObserver.onCompleted()
    }

    override fun join(request: Services.tableEntry, responseObserver: StreamObserver<Services.tableEntry>) {
        responseObserver.onNext(self)
        insertIntoTable(request)

        responseObserver.onCompleted()

    }

    /**
     * Put a key,value pair into the DHT.
     */
    fun putRequest(name: String, data: String) {
        logger.info { "Making Put request for key: $name, value: $data" }
        val hash = name.hashCode().absoluteValue % CHORD_SIZE
        if (inRangeSuccessor(hash)) {
            dataTable[name] = data
        } else {
            val before = maxBefore(hash)
            if (before == null) {
                logger.warn { "Cannot insert data" }
                return
            }
            tryOrClose(before.host, before.port) {
                val channel = getChannel(before.host, before.port)
                val stub = NodeGrpc.newBlockingStub(channel)
                val put = stub.put(Services.dataEntry.newBuilder().setName(name).setData(data).build())
            }
        }
    }

    override fun put(request: Services.dataEntry, responseObserver: StreamObserver<Services.empty>) {
        logger.info("Received put request for key ${request.name}")
        val hash = request.name.hashCode().absoluteValue % CHORD_SIZE
        if (inRangeSuccessor(hash)) {
            dataTable[request.name] = request.data
        } else {
            putRequest(request.name, request.data)
        }
        responseObserver.onNext(Services.empty.getDefaultInstance())
        responseObserver.onCompleted()
    }

    fun joinRequest(host: String, port: Int): Services.tableEntry {
        val channel = getChannel(host, port)
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
        for (i in fingerTableIds) {
            val entry = successorRequest(seedHost, seedPort, i)
            insertIntoTable(entry)
        }
    }

    fun successorRequest(host: String, port: Int, id: Int): Services.tableEntry {
        logger.debug { "sending successor request to $host : $port of id $id" }
        val serviceId = Services.id.newBuilder().setId(id).build()
        val channel = getChannel(host, port)
        val stub = NodeGrpc.newBlockingStub(channel)
        var successor: Services.tableEntry? = null

        tryOrClose(host, port) {
            successor = stub.successor(serviceId)
        }


        return successor ?: self // return this if null


    }

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

    override fun successor(request: Services.id, responseObserver: StreamObserver<Services.tableEntry>) {
        logger.debug { "received successor request of ${request.id}" }

        val maxBefore = maxBefore(request.id)

        if (successor != null && inRangeSuccessor(request.id)) {
            responseObserver.onNext(successor)
        } else {
            when {
                maxBefore == null -> {
                    responseObserver.onNext(self)
                }
                successor != null && successor!!.id == self.id -> { // only you in the finger table.
                    responseObserver.onNext(self)
                }
                else -> {
                    responseObserver.onNext(successorRequest(maxBefore.host, maxBefore.port, request.id))
                }
            }
        }
        responseObserver.onCompleted()
    }

    /**
     * get the closest preceding node to the id
     * Todo: fix this, it doesnt take the ring into consideration
     * @param id
     * @return
     */
    fun maxBefore(id: Int): Services.tableEntry? {
        return fingerTable.filter { (k, e) -> e.id < id }
            .map { (k, e) -> e }
            .maxWith(Comparator.comparingInt(Services.tableEntry::getId)) ?: predecessor
    }

    /**
     * get the closest node after the id
     * Todo this might return null so fix that
     *
     * @param id
     * @return
     */
    fun minAfter(id: Int): Services.tableEntry? {
        return fingerTable.filter { (k, e) -> e.id > id }
            .map { (k, e) -> e }
            .minWith(Comparator.comparingInt(Services.tableEntry::getId))
    }

    private fun hash(): Int {
        return (host.hashCode().absoluteValue + port.hashCode()) % CHORD_SIZE//2^8
    }

    fun startService(args: Array<String>): Server {

        val start = ServerBuilder
            .forPort(args[1].toInt())
            .addService(this)
            .build()
            .start()
        server = start
        if (args.size > 2)
            start(args[2], args[3].toInt())
        else {
            fingerTableIds.forEach {
                fingerTable[it] = self
            }
        }
        scheduledExecutor.scheduleAtFixedRate({
            try {
                checkPredecessor();stabilize();fixFingers()
            } catch (e: java.lang.Exception) {
                println(e)
            }
        }, 5000, 5000, TimeUnit.MILLISECONDS)
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

    fun getRequest(name: String): Services.dataEntry? {
        logger.info { "Making get request for : $name" }
        val hash = name.hashCode().absoluteValue % CHORD_SIZE
        return if (inRangeSuccessor(hash) && dataTable.containsKey(name)) {
            dataEntryfromMapEntry(name, dataTable[name]!!)
        } else {
            val before = maxBefore(hash)

            var get: Services.dataEntry? = null
            tryOrClose(before!!.host, before.port) {
                val channel = getChannel(before.host, before.port)
                val stub = NodeGrpc.newBlockingStub(channel)
                get = stub.get(Services.name.newBuilder().setName(name).build())
            }

            get
        }
    }

    override fun get(request: Services.name, responseObserver: StreamObserver<Services.dataEntry>) {
        logger.info("Received get request for key:  ${request.name}")
        val hash = request.name.hashCode().absoluteValue % CHORD_SIZE
        val dataEntry: Services.dataEntry?
        dataEntry = if (inRangeSuccessor(hash)) {
            dataEntryfromMapEntry(request.name, dataTable[request.name]!!)
        } else {
            getRequest(request.name)
        }
        logger.info("Responding to get request (for ${request.name}) with: ${dataEntry?.data}")
        responseObserver.onNext(dataEntry)
        responseObserver.onCompleted()
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