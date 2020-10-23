package me.rahimklaber.dhtrpc.datatable

interface DataTable<K,V> {
    operator fun set(key: K, data: V)
    operator fun get(key: K): V?

    fun registerSetCallback(callBack: SetCallBack<K,V>)

}