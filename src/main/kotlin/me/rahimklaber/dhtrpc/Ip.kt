package me.rahimklaber.dhtrpc


class Ip(val host: String, val port: Int){
    fun toTableEntry(): Services.tableEntry {
        return Services.tableEntry.newBuilder()
            .setHost(host)
            .setPort(port)
            .build()
    }
}
