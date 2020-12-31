package me.rahimklaber.dhtrpc

import mu.KotlinLogging
import java.util.*

class CommandLineInterface(
    val ip : Ip,
    join: Boolean
) {
    val node : ChordNode = ChordNode(ip.host,ip.port)

    init {
        node.startService(Array(0) { "" })
    }

    suspend fun handleCmd(line: String) {
        return handleCmd(line.split(" "))
    }

    suspend fun handleCmd(cmds: List<String>) {
        when (cmds[0]){
            "put" -> {
                assert(cmds.size == 3)
                node.putRequest(cmds[1],cmds[2])
            }
            "get" -> {
                assert(cmds.size == 2)
                println(node.getRequest(cmds[1]).data)
            }
            "fingers" -> {
                assert(cmds.size == 1)
                println(node.fingerTableWrapper.table.entries.forEach(::println))
            }
            "list" -> {
                // list of any nodes
                println(node.dataTable)
            }
            "successor" -> {
                assert(cmds.size == 2)
                //Todo: Fix the successor request to not require a ip
                println(node.successorRequest(ip.host,ip.port,cmds[1].toInt()))
            }
            "predecessor" -> {
                //Todo: make predecessor request and successor request uniform
                TODO("make predecessor request and successor request uniform")
            }
            else -> {
                println("dab on th aterz")
            }
        }
    }
}

suspend fun main(args: Array<String>) {
    System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "ERROR")

    println("Godking Array Initiated...")
    val cmdInterface = CommandLineInterface(Ip("localhost",277),false)
    while (true) {
        val line = readLine() ?: ""
        cmdInterface.handleCmd(line)
    }
}