package me.rahimklaber.dhtrpc

import ch.tutteli.atrium.api.fluent.en_GB.isNotEmpty
import ch.tutteli.atrium.api.fluent.en_GB.toBe
import ch.tutteli.atrium.api.verbs.expect
import javafx.collections.FXCollections
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Todo: make CHORD_SIZE and TABLE_SIZE dynamic
 * NOTE: these tests assume that TABLE_SIZE is 5 and CHORD_SIZE 32
 */
internal class ChordNodeTest {
    lateinit var node: ChordNode

    @BeforeEach
    fun setUp() {
        node = ChordNode("xxx", 111)
    }

    @Test
    fun `Test insert into table when table is empty`() {
        val entry = Services.tableEntry.newBuilder()
            .setId(10)
            .build()
        node.insertIntoTable(entry)
        expect(node.fingerTable) {
            isNotEmpty()
        }

        for ((_, v) in node.fingerTable) {
            assertEquals(entry, v)
        }
    }


    @Test
    fun `Test insert into table two times`() {
        val firstEntry = Services.tableEntry.newBuilder()
            .setId(10)
            .build()
        val secondEntry = Services.tableEntry.newBuilder()
            .setId(20)
            .build()
        /**
         *  These are thee finger table ids of the current node's finger table.
         *  key: 23
         *  key: 8
         *  key: 9
         *  key: 11
         *  key: 15
         */
        node.insertIntoTable(firstEntry)
        node.insertIntoTable(secondEntry)
        val expectedMap =
            FXCollections.synchronizedObservableMap<Int, Services.tableEntry>(FXCollections.observableHashMap()).also {
                it[23] = firstEntry
                it[8] = firstEntry
                it[9] = firstEntry
                it[11] = secondEntry
                it[15] = secondEntry
            }

        expect(node.fingerTable.entries).toBe(expectedMap.entries)
    }

    @Test
    fun `Test fingerDiff same id`() {
        expect(node.fingerDiff(20, 20)).toBe(0)
    }

    @Test
    fun `Test fingerDiff finger smaller than id`() {
        expect(node.fingerDiff(19, 20)).toBe(1)
    }

    @Test
    fun `Test fingerDiff finger bigger than id`() {
        expect(node.fingerDiff(20, 19)).toBe(31)
    }
}