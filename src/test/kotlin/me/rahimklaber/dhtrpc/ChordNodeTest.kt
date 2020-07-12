package me.rahimklaber.dhtrpc

import org.junit.jupiter.api.BeforeEach

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ch.tutteli.atrium.api.fluent.en_GB.*
import ch.tutteli.atrium.api.verbs.expect

/**
 * Todo: make CHORD_SIZE and TABLE_SIZE dynamic
 * NOTE: these tests assume that TABLE_SIZE is 5 and CHORD_SIZE 32
 */
internal class ChordNodeTest {
    lateinit var node: ChordNode
    @BeforeEach
    fun setUp() {
        node = ChordNode("xxx",111)
    }

    @Test
    fun `Test insert into table when table is empty`(){
        val entry = Services.tableEntry.newBuilder()
            .setId(10)
            .build()
        node.insertIntoTable(entry)
        expect(node.fingerTable){
            isNotEmpty()
        }

        for ((_,v) in node.fingerTable) {
            assertEquals(entry,v)
        }
    }
    @Test
    fun `Test fingerDiff same id`(){
        expect(node.fingerDiff(20,20)).toBe(0)
    }

    @Test
    fun `Test fingerDiff finger smaller than id`(){
        expect(node.fingerDiff(19,20)).toBe(1)
    }

    @Test
    fun `Test fingerDiff finger bigger than id`(){
        expect(node.fingerDiff(20,19)).toBe(31)
    }
}