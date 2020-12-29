package me.rahimklaber.dhtrpc.view

import javafx.collections.FXCollections
import javafx.collections.MapChangeListener
import javafx.scene.control.TableView
import javafx.scene.control.TextArea
import javafx.stage.FileChooser
import kotlinx.coroutines.*
import me.rahimklaber.dhtrpc.ChordNode
import me.rahimklaber.dhtrpc.Services.tableEntry
import tornadofx.*
import java.io.File

data class MyPair<F, S>(var first: F, var second: S)
data class UiTableEntry(val tableEntry: tableEntry, val fingerPos: Int)
data class DataEntry(val name: String, val data: String)
class ChordRing : View("ChordRing") {
    val args = arrayOf("192.168.0.175", "222")

//
//    val args = arrayOf("192.168.0.175", "857", "192.168.0.175", "222")
    var node: ChordNode = ChordNode.create(args)
    val map = node.fingerTableWrapper.table
    val datamap = node.dataTable

    init {
//        System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "TRACE")
        runAsync { node.startService(args).awaitTermination() }
    }


    override val root = borderpane {
        top = hbox {

            label {

                GlobalScope.launch(Dispatchers.Main) {
                    while (true) {
                        delay(1000)
                        this@label.text = "${node.self}\n${node.predecessor}"
                    }
                }

            }
            button {
                onHover {
                    tooltip("bulk insert values into dht")
                }

                text = "bulk Insert file"
                setOnAction {
                    val file = chooseFile(filters = arrayOf(FileChooser.ExtensionFilter("json", "*.json"))).first()
                    GlobalScope.launch(Dispatchers.IO) {
                        bulkInsert(file)
                    }
                }
            }


        }
        right = tableview<MyPair<String, String?>> {
            readonlyColumn("key", MyPair<String, String?>::first)
            readonlyColumn("value", MyPair<String, String?>::second)
            setOnMouseClicked {
                GlobalScope.launch(Dispatchers.Main) {
                    val data = node.getRequest(this@tableview.selectedItem!!.first)
                    this@tableview.selectedItem!!.second = data?.data
                    this@tableview.refresh()
                }
            }
        }
        left = hbox {
            tableview<UiTableEntry> {
                items = FXCollections.observableArrayList(map.map { e -> UiTableEntry(e.value, e.key) })
                map.addListener(MapChangeListener {
                    GlobalScope.launch(Dispatchers.Main) {
                        items = FXCollections.observableArrayList(map.map { e -> UiTableEntry(e.value, e.key) })
                    }
                })
                val poscol = readonlyColumn("fingerpos", UiTableEntry::fingerPos)
                readonlyColumn("entry", UiTableEntry::tableEntry)
                setOnMouseClicked {
                    GlobalScope.launch {
                        val list =
                            withContext(Dispatchers.IO) { node.listRequest(this@tableview.selectedItem!!.tableEntry) }


                        (right as TableView<*>).items =
                            FXCollections.observableArrayList(list.map { MyPair<String, String?>(it, null) })
                    }
                }

                //runAsync { refresh() }
                sortOrder.add(poscol)
            }
            tableview<DataEntry> {
                items = FXCollections.observableArrayList()
                datamap.registerSetCallback { key: String, data: String ->
                    GlobalScope.launch(Dispatchers.Main) {
                        items.add(DataEntry(key, data))
                    }
                }
                readonlyColumn("name", DataEntry::name)
                readonlyColumn("data", DataEntry::data)
            }
        }
//        right =
        bottom =
            hbox {
                val Putbox = vbox {
                    val key = textarea {

                    }
                    val value = textarea {

                    }
                }

                button {
                    text = "Put"
                    setOnAction {
                        val key = Putbox.children[0] as TextArea
                        val data = Putbox.children[1] as TextArea
                        GlobalScope.launch(Dispatchers.IO) {
                            node.putRequest(key.text, data.text)
                        }
                    }
                }

                val Getbox = vbox {
                    val key = textarea {

                    }
                    val value = textarea {
                        isEditable = false
                    }
                }

                button {
                    text = "Get"
                    setOnAction {
                        val key = Getbox.children[0] as TextArea
                        val value = Getbox.children[1] as TextArea
                        GlobalScope.launch(Dispatchers.Main) {

                            value.text = withContext(Dispatchers.IO) {
                                node.getRequest(key.text).toString()
                            }
                        }

                    }
                }
            }
    }

    /**
     * json file :
     *      {key : value}
     */
    suspend fun bulkInsert(file: File) {
        val inputStream = file.inputStream().use { stream ->
            val json = loadJsonObject(stream)
            val jsonMap = json.toMap().mapValues {
                it.value.toString()
            }
            var count = 0
            jsonMap.forEach { (key, value) ->
//                GlobalScope.launch(Dispatchers.IO) {
                    node.putRequest(key, value)
//                }
            }
        }


    }
}


class RingApp : App(ChordRing::class)

fun main(args: Array<String>) {
    launch<RingApp>(args)
}