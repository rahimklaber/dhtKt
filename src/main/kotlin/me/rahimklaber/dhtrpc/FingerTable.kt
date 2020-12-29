package me.rahimklaber.dhtrpc

import javafx.collections.FXCollections
import javafx.collections.ObservableMap
import java.util.Comparator
import kotlin.math.pow


class FingerTable(val id: Int, val size: Int) {
    val ringSize = 2.0.pow(size.toDouble()).toInt()
    val ids = IntRange(1, size).map { (id + 2.0.pow(it - 1)).toInt() % ChordNode.CHORD_SIZE }
    // should fix ui and mkae this private?
     val table: ObservableMap<Int, Services.tableEntry> =
        FXCollections.synchronizedObservableMap(FXCollections.observableHashMap())
    var successor: Services.tableEntry?
        get() = this[0]
        set(value) {
            if (value != null) {
                this[0] = value
            } else {
                remove(0)
            }
        }
    // We have this here even though the predecessor is not a part of the fingertable.
    // It allows us to use helper functions that reside in the fingertable class.
    var predecessor: Services.tableEntry? = null
    /**
     * Get table entry by it's "id" within the fingerTable.
     * From 0 to tableSize - 1
     *
     * For example:
     * If the size of finger table is 5 and get(4), then the 5th element in the finger table is returned.
     * The under the hood the elements of the fingertable are mapped differently.
     */
    operator fun get(key: Int): Services.tableEntry? = table[ids[key]]

    /**
     * Set table entry by it's "id" within the fingerTable.
     * From 0 to tableSize - 1
     *
     * For example:
     * If the size of finger table is 5 and Set(4,RANDOM_ENTRY), then the element is set in the 5th position of the finger table.
     * The under the hood the elements of the fingertable are mapped differently.
     */
    operator fun set(key: Int, entry: Services.tableEntry) {
        table[ids[key]] = entry
    }

    /**
     * Remove the element from the fingertable by its table id.
     */
    fun remove(id : Int){
        table.remove(ids[id])
    }

    fun removeIf(predicate: (Int, Services.tableEntry) -> Boolean) {
        val toRemove = table.filter { (id, entry) ->
            predicate(id, entry)
        }.keys
        table.keys.removeAll(toRemove)
    }

    /**
     * Get entries by their `absolute` index.
     */
    fun getByAbsolutePos(key: Int) = table[key]

    /**
     * Set entries by their `absolute` index.
     */
    fun setByAbsolutePos(key: Int, entry: Services.tableEntry) {
        table[key] = entry
    }
    fun putIf(
        key: Int,
        value: Services.tableEntry,
        predicate: (Int) -> Boolean
    ) {
        if (predicate(key)) setByAbsolutePos(key, value)

    }

    /**
     * get the closest preceding node to the id
     * Todo: fix this, it doesnt take the ring into consideration
     * @param id
     * @return
     */
    fun maxBefore(id: Int): Services.tableEntry? {
        return table.filter { (_, e) -> e.id < id }
            .map { (_, e) -> e }
            .maxWithOrNull(Comparator.comparingInt(Services.tableEntry::getId)) //?: predecessor
    }


    /**
     * get the closest node after the id
     * Todo this might return null so fix that
     *
     * @param id
     * @return
     */
    fun minAfter(id: Int): Services.tableEntry? {
        return table.filter { (k, e) -> e.id > id }
            .map { (k, e) -> e }
            .minWithOrNull(Comparator.comparingInt(Services.tableEntry::getId))
    }
    /**
     *  Tries to insert the given entry in the finger table.
     *
     *  It tries to insert the entry in all of the finger table positions.
     *
     * @param e the entry to be inserted
     */
    fun insertIntoTable(e: Services.tableEntry) {
        // This is confusing as fuck
        // dont like that the `insertPredicate` fun uses the `e` param.
        fun insertPredicate(k: Int): Boolean {
            return if (getByAbsolutePos(k) == null) {
                true
            } else {
                val diffCurr = fingerDiff(k, getByAbsolutePos(k)!!.id)
                val diffNew = fingerDiff(k, e.id)
                diffNew <= diffCurr
            }
        }

        if (e.id == id) return
        ids
            .forEach {
                putIf(
                    it,
                    e,
                    ::insertPredicate
                )
            }
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
                (ChordNode.CHORD_SIZE - finger) + id
            }
            id > finger -> {
                id - finger
            }
            else -> {
                0
            }
        }
    }


}