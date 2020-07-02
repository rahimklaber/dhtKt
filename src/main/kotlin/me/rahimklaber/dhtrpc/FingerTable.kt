package me.rahimklaber.dhtrpc

import javafx.collections.FXCollections
import javafx.collections.ObservableMap
import kotlin.math.pow

class FingerTable(val id: Int, val size: Int) {
    val ringSize = 2.0.pow(size.toDouble()).toInt()
    val ids = IntRange(1, size).map { (id + 2.0.pow(it - 1)).toInt() % ChordNode.CHORD_SIZE }
    private val table: ObservableMap<Int, Services.tableEntry> =
        FXCollections.synchronizedObservableMap(FXCollections.observableHashMap())

    operator fun get(key: Int): Services.tableEntry? = table[key]
    operator fun set(key: Int, entry: Services.tableEntry) {
        table[key] = entry
    }

    /**
     * Get table entry by it's "id" within the fingerTable.
     * From 0 to tableSize - 1
     */
    fun getByTablePos(key : Int) = this[ids[key]]
    /**
     * Set table entry by it's "id" within the fingerTable.
     * From 0 to tableSize - 1
     */
    fun setByTablePos(key : Int , entry : Services.tableEntry) = this[ids[key]]
}