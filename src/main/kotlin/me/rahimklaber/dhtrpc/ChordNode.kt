package me.rahimklaber.dhtrpc

/**
 * TODO: stop using blocking calls; figure out how to send a request, close the connection and then the recipient will send us the response. This seems to be much better than making a request to a node and then waiting for that node to make a request etc etc.
 */

/**
 * Todo: cache channels instead of creating a new one every time.
 * Todo: Make public facing api use grpc but something else for inter-node communication.????
 */
import io.grpc.ConnectivityState
import io.grpc.ManagedChannelBuilder
import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.stub.StreamObserver
import javafx.collections.FXCollections
import javafx.collections.ObservableMap
import mu.KotlinLogging
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue
import kotlin.math.pow

class ChordNode(val host: String, val port: Int) : NodeGrpc.NodeImplBase() {
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

    //fun successor()= fingerTable[fingerTableIds[0]]
    var currFinger = 0 // for fixFingers. from 0 to 10

    //todo make this work
    fun checkPredecessor() {
        logger.debug { "checking predecessor" }
        if (predecessor == null)
            return
        val channel = ManagedChannelBuilder.forAddress(predecessor!!.host, predecessor!!.port)
            .usePlaintext()
            .build()
        try {
            val state = channel.getState(false)
            Thread.sleep(10)
            if (state == ConnectivityState.TRANSIENT_FAILURE) {
                predecessor = null
            }

        } catch (e: Exception) {
            println(e)
        } finally {
            channel.shutdown()
        }

    }

    fun listRequest(entry: Services.tableEntry): List<String> {
        logger.info { "sending list request to ${entry.host}:${entry.port}" }
        val channel = ManagedChannelBuilder.forAddress(entry.host, entry.port)
            .usePlaintext()
            .build()
        val stub = NodeGrpc.newBlockingStub(channel)
        val keys: List<String>
        try {
            keys = stub.list(Services.empty.getDefaultInstance()).keyList
        } finally {
            channel.shutdown()
        }

        return keys
    }

    override fun list(request: Services.empty, responseObserver: StreamObserver<Services.keys>) {
        logger.info { "received list request" }
        responseObserver.onNext(Services.keys.newBuilder().addAllKey(dataTable.keys).build())
        responseObserver.onCompleted()
    }

    fun fixFingers() {
        val helper = fingerTable[fingerTableIds[currFinger]]!!
        val successorRequest = successorRequest(helper.host, helper.port, fingerTableIds[currFinger])
        //Todo: wtf is this 🔽
        if (successorRequest.port != 0) {
            fingerTable[fingerTableIds[currFinger]] = successorRequest

        }

        currFinger = (currFinger + 1) % TABLE_SIZE
    }

    fun stabilize() {
        try {
            logger.debug { "stabilize... entrry: ${fingerTable[fingerTableIds[0]]!!}" }
            if (fingerTable[fingerTableIds[0]]!!.port == 0) {
                // fingerTable[fingerTableIds[0]] =self
            }
            val predecessor_of_successor = predecessorRequest(fingerTable[fingerTableIds[0]]!!)
            if (predecessor_of_successor != null &&
                inRangeSuccessor(predecessor_of_successor.id)
            ) {
                if (predecessor_of_successor.port != 0)
                    fingerTable[fingerTableIds[0]] = predecessor_of_successor

            }
            notifyRequest(fingerTable[fingerTableIds[0]]!!)
        } catch (e: Exception) {
            e.stackTrace.forEach { println(it) }
            println(e)
        }


    }

    fun notifyRequest(entry: Services.tableEntry): Services.empty? {
        logger.debug { "sending notify request to ${entry.host}:${entry.port}" }
        val channel = ManagedChannelBuilder.forAddress(entry.host, entry.port)
            .usePlaintext()
            .build()
        val stub = NodeGrpc.newBlockingStub(channel)
        val notify = stub.notify(self)
        channel.shutdown()
        return notify
    }

    override fun notify(request: Services.tableEntry?, responseObserver: StreamObserver<Services.empty>?) {
        logger.debug { "received notify request from ${request?.host}:${request?.port}" }
        if (predecessor == null) {

            predecessor = request
        } else if (inRangePredecessor(request!!.id)) {
            predecessor = request
        }
        responseObserver?.onNext(Services.empty.getDefaultInstance())
        responseObserver?.onCompleted()
    }

    fun MutableMap<Int, Services.tableEntry>.putIf(key: Int, value: Services.tableEntry, predicate: (Int) -> Boolean) {
        if (predicate(value.id)) put(key, value)

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
        fun insertPredicate(k: Int): Boolean {
            return if (fingerTable[k] == null) {
                true
            } else {
                val diffCurr = fingerDiff(k, fingerTable[k]!!.id)
                val diffNew = fingerDiff(k, e.id)
                diffNew <= diffCurr
            }
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
        val channel = ManagedChannelBuilder.forAddress(entry.host, entry.port)
            .usePlaintext()
            .build()
        val stub = NodeGrpc.newBlockingStub(channel)
        val predecessor: Services.tableEntry?
        try {
            predecessor = stub.predecessor(Services.empty.getDefaultInstance())
        }finally {
            channel.shutdown()
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
            val channel = ManagedChannelBuilder.forAddress(before!!.host, before.port)
                .usePlaintext()
                .build()
            val stub = NodeGrpc.newBlockingStub(channel)
            try {
                val put = stub.put(Services.dataEntry.newBuilder().setName(name).setData(data).build())
            } finally {
                channel.shutdown()
            }


        }
    }

    override fun put(request: Services.dataEntry, responseObserver: StreamObserver<Services.empty>) {
        logger.info("Received put request for key ${request.name}")
        val hash = request.name.hashCode().absoluteValue % CHORD_SIZE
        if (inRangeSuccessor(hash)) {
            dataTable[request.name] = request.data
        } else {
            val before = maxBefore(hash)
            putRequest(request.name, request.data)
        }
        responseObserver.onNext(Services.empty.getDefaultInstance())
        responseObserver.onCompleted()
    }

    fun joinRequest(host: String, port: Int): Services.tableEntry {
        val channel = ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            .build()
        val stub = NodeGrpc.newBlockingStub(channel)
        val join = stub.join(self)
        channel.shutdown()
        return join

    }

    fun start(seedHost: String, seedPort: Int) {
        buildTable(seedHost, seedPort)
        joinRequest(seedHost, seedPort)
        try {
            //fingerTable.values.map { joinRequest(host, port) }
        } catch (e: Exception) {
            println(e)
        }
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
        val channel = ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            .build()
        val stub = NodeGrpc.newBlockingStub(channel)
        val successor: Services.tableEntry
        try {
            successor = stub.successor(serviceId)
        } finally {
            channel.shutdown()
        }
        return successor


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
        return if (fingerTable[fingerTableIds[0]]!!.id > self.id && self.id < id && id <= fingerTable[fingerTableIds[0]]!!.id) {
            true
        }
        // If our successor is "behind" us, meaning they wrapper around the chord ring
        else if (fingerTable[fingerTableIds[0]]!!.id < self.id &&
            !(self.id >= id && fingerTable[fingerTableIds[0]]!!.id < id)
        ) {
            true
        }
        // If i am the only one in the network
        else fingerTable[fingerTableIds[0]]!! == self
    }

    override fun successor(request: Services.id, responseObserver: StreamObserver<Services.tableEntry>) {
        logger.debug { "received successor request of ${request.id}" }
        val firstOrNull = fingerTable[request.id]
        val maxBefore = maxBefore(request.id)
        val minAfter = minAfter(request.id)
        if (inRangeSuccessor(request.id)) {
            responseObserver.onNext(fingerTable[fingerTableIds[0]])
        } else {
            when {
                maxBefore == null -> {
                    responseObserver.onNext(self)
                }
                fingerTable[fingerTableIds[0]]!!.id == self.id -> {
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
     *
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
        if (args.size > 2)
            start(args[2], args[3].toInt())
        else {
            fingerTableIds.forEach {
                fingerTable[it] = self
            }
        }
        scheduledExecutor.scheduleAtFixedRate({
            try {
                checkPredecessor();fixFingers();stabilize()
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

    fun getRequest(name: String): Services.dataEntry {
        val hash = name.hashCode().absoluteValue % CHORD_SIZE
        return if (inRangeSuccessor(hash) && dataTable.containsKey(name)) {
            dataEntryfromMapEntry(name, dataTable[name]!!)
        } else {
            val before = maxBefore(hash)
            val channel = ManagedChannelBuilder.forAddress(before!!.host, before.port)
                .usePlaintext()
                .build()
            val stub = NodeGrpc.newBlockingStub(channel)
            val get: Services.dataEntry
            try {
                get = stub.get(Services.name.newBuilder().setName(name).build())
            } finally {
                channel.shutdown()
            }
            get
        }
    }

    override fun get(request: Services.name, responseObserver: StreamObserver<Services.dataEntry>) {

        val hash = request.name.hashCode().absoluteValue % CHORD_SIZE
        val dataEntry: Services.dataEntry?
        dataEntry = if (inRangeSuccessor(hash)) {
            dataEntryfromMapEntry(request.name, dataTable[request.name]!!)
        } else {
            getRequest(request.name)
        }
        logger.info("Received get request for key:  ${request.name}, value: ${dataEntry.data}")
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