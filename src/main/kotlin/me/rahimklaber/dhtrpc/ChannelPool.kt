package me.rahimklaber.dhtrpc

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.StatusRuntimeException

/**
 * Class that handles all of the grpc-Channels we use.
 *
 * Instead of creating new channels every time we need to do a request, we cache the channels and re-use them.
 *
 */
class ChannelPool {
    private val pool : HashMap<String,ManagedChannel> = HashMap()
    fun getChannel(host: String, port: Int): ManagedChannel {
        // `:` separator ?
        var channel = pool["$host$port"]
        return if (channel == null) {
            // Todo: find out if creating a channel creates a socket immediately, if so use a coroutine for this?
            channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build()
            pool["$host$port"] = channel
            channel
        } else {
            channel
        }
    }

    /**
     * Get a specific channel from the pool.
     *
     * key should be the ip and port concatenated to each other.
     */
    operator fun get(key:String) = pool[key]

    /**
     * Remove a specific channel from the pool.
     * Most likely used in instances where the channel has failed.
     *
     * key should be the ip and port concatenated to each other.
     */
    fun remove(key:String) = pool.remove(key)

    /**
     * Shuts down all channels in the pool
     */
    fun shutdown() {
        pool.forEach{it.value.shutdownNow()}
    }


    /**
     * Try to send a message.
     * If it fails due to a closed connection, the channel is removed.
     */
    inline fun tryOrClose(host: String, port: Int, `fun`: () -> Unit) {
        try {
            `fun`()
        } catch (e: StatusRuntimeException) {
            this["$host$port"]?.shutdown()
            remove("$host$port")
//            fingerTableWrapper.removeIf { _: Int, e: Services.tableEntry -> (e.host == host) and (e.port == port) }
        }
    }

}