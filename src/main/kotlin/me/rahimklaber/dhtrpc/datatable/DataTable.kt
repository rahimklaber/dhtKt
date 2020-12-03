package me.rahimklaber.dhtrpc.datatable

interface DataTable<K,V> {
    /**
     * when data is inserted, the callbacks are ran.
     * Therefore the callbacks should be lightweight.
     */
    operator fun set(key: K, data: V)
    operator fun get(key: K): V?
    /**
     * register a callback to be run when data is inserted.
     */
    fun registerSetCallback(callBack: SetCallBack<K,V>)

    /**
     * Return a list of keys in the table.
     */
    fun list() : Set<K>

    /**
     * Check if the table contains the key.
     */
    fun contains(key: K): Boolean

}