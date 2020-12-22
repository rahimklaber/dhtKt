package me.rahimklaber.dhtrpc

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.graphstream.graph.IdAlreadyInUseException
import org.graphstream.graph.Node
import org.graphstream.graph.implementations.MultiGraph


class Crawler(val seed: Ip) {
    val nodes = listOf<Services.tableEntry>()
    val graph = MultiGraph("Chord")
    val client = Client()
    val style = """
        node{
            z-index: 0;
            shape: box;
	        size: 25px, 25px;
	        fill-mode: plain;   /* Default.          */
	        fill-color: red;    /* Default is black. */
	        stroke-mode: plain; /* Default is none.  */
	        stroke-color: blue; /* Default is black. */
            text-size: 22px;

            }
            
        edge{
        z-index: 0;
        
        }
    """.trimIndent()

    fun addToGraph(node: Services.tableEntry, previousNode: Services.tableEntry? = null) {
        if (previousNode == null) {

            val addNode: Node = graph.addNode(node.id.toString())
            addNode.setAttribute("ui.label", addNode.id)


        } else {
            try {
                val addNode = graph.addNode(node.id.toString())
                addNode.setAttribute("ui.label", addNode.id)

                graph.addEdge("${node.id}${previousNode.id}", previousNode.id.toString(), node.id.toString())
            }catch (e: IdAlreadyInUseException){
                graph.addEdge("${node.id}${previousNode.id}", previousNode.id.toString(), node.id.toString())

            }
        }
    }

    init {
        graph.setAttribute("ui.quality")
        graph.setAttribute("ui.antialias")
        graph.setAttribute("ui.stylesheet", style);

        GlobalScope.launch {
            var previousNode = client.successorRequest(seed, 0)
            addToGraph(previousNode)
            while (true) {
                delay(1000)
                var currentNode = client.successorRequest(seed, previousNode.id + 1)
                addToGraph(currentNode, previousNode)
                previousNode = currentNode
            }
        }
        graph.display()
    }
    companion object{
        @JvmStatic
        fun main(args: Array<String>) {
            System.setProperty("org.graphstream.ui", "swing")
            Crawler(Ip("192.168.0.175", 222))
        }
    }
}