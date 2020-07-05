package me.rahimklaber.dhtrpc.view

import javafx.animation.Animation
import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.collections.FXCollections
import javafx.collections.MapChangeListener
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.scene.control.TableView
import javafx.scene.control.TextArea
import javafx.util.Duration
import me.rahimklaber.dhtrpc.ChordNode
import me.rahimklaber.dhtrpc.Services.tableEntry
import tornadofx.*

data class MyPair<F, S>(var first: F, var second: S)
data class UiTableEntry(val tableEntry: tableEntry, val fingerPos: Int)
data class DataEntry(val name: String, val data: String)
class ChordRing : View("ChordRing") {
    //    val args = arrayOf("192.168.0.175", "222")
//
    val args = arrayOf("192.168.0.175", "222", "192.168.0.187", "554")
    var node: ChordNode = ChordNode.create(args)
    val map = node.fingerTable
    val datamap = node.dataTable

    init {
        System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "TRACE")
        runAsync { node.startService(args).awaitTermination() }
    }


    override val root = borderpane {
        top = label {

            val timeline = Timeline(
                KeyFrame(
                    Duration.millis(1000.0),
                    EventHandler { e: ActionEvent ->
                        this@label.text = "${node.self}\n${node.predecessor}"
                    }
                )
            )
            timeline.cycleCount = Animation.INDEFINITE // loop forever

            timeline.play()

        }
        right = tableview<MyPair<String, String?>> {
            readonlyColumn("key", MyPair<String, String?>::first)
            readonlyColumn("value", MyPair<String, String?>::second)
            setOnMouseClicked {
                runAsyncWithOverlay {
                    val data = node.getRequest(this.selectedItem!!.first)
                    this.selectedItem!!.second = data.data
                    this.refresh()
                }
            }
        }
        left = hbox {
            tableview<UiTableEntry> {
                items = FXCollections.observableArrayList(map.map { e -> UiTableEntry(e.value, e.key) })
                map.addListener(MapChangeListener {
                    runLater {
                        items = FXCollections.observableArrayList(map.map { e -> UiTableEntry(e.value, e.key) })
                    }
                })
                val poscol = readonlyColumn("fingerpos", UiTableEntry::fingerPos)
                readonlyColumn("entry", UiTableEntry::tableEntry)
                setOnMouseClicked {
                    runAsyncWithOverlay {
                        val list = node.listRequest(this@tableview.selectedItem!!.tableEntry)

                        (right as TableView<MyPair<String, String?>>).items =
                            FXCollections.observableArrayList(list.map { MyPair<String, String?>(it, null) })
                    }
                }

                //runAsync { refresh() }
                sortOrder.add(poscol)
            }
            tableview<DataEntry> {
                items = FXCollections.observableArrayList()
                datamap.addListener(MapChangeListener {
                    runLater {
                        items = FXCollections.observableArrayList(datamap.map { e -> DataEntry(e.key, e.value) })
                    }
                })
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
                        runAsync {
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
                        runAsync {
                            value.text = node.getRequest(key.text).toString()
                        }

                    }
                }
            }
    }
}

class RingApp : App(ChordRing::class)

fun main(args: Array<String>) {
    launch<RingApp>(args)
}