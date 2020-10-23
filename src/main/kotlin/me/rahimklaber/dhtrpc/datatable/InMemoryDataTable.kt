package me.rahimklaber.dhtrpc.datatable

/**
 * An in-memory implementation of the DataTable interface.
 */
class InMemoryDataTable<K, V> : DataTable<K, V> {
    private val map = HashMap<K, V>()
    private val callBacks = arrayListOf<SetCallBack<K, V>>()


    override fun set(key: K, data: V) {
        map[key] = data
        callBacks.forEach {
            it.invoke(key,data)
        }
    }

    override fun get(key: K): V? = map[key]


    override fun registerSetCallback(callBack: SetCallBack<K,V>) {
        callBacks.add(callBack)
    }

}

