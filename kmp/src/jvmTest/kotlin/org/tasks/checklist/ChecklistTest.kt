package org.tasks.checklist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.tasks.checklist.Checklist.Item

class ChecklistTest {
    @Test
    fun textAndItems() {
        val parsed = Checklist.parse("Shop first.\n\n- [ ] milk\n- [x] eggs\n* [X] bread")
        assertEquals("Shop first.", parsed.text)
        assertEquals(listOf(Item("milk"), Item("eggs", true), Item("bread", true)), parsed.items)
        assertEquals(2, parsed.done)
    }

    @Test
    fun lineInsideTextStaysText() {
        val notes = "- [ ] not an item\nbecause text follows"
        assertEquals(Checklist.Parsed(notes, emptyList()), Checklist.parse(notes))
    }

    @Test
    fun onlyItemsAndEmptyItem() {
        val parsed = Checklist.parse("- [ ] milk\n- [ ] ")
        assertEquals("", parsed.text)
        assertEquals(listOf(Item("milk"), Item("")), parsed.items)
    }

    @Test
    fun nullAndPlainText() {
        assertEquals(Checklist.Parsed("", emptyList()), Checklist.parse(null))
        assertEquals(Checklist.Parsed("just text", emptyList()), Checklist.parse("just text\n"))
    }

    @Test
    fun joinRoundTrips() {
        val items = listOf(Item("milk"), Item("eggs", true), Item(""))
        assertEquals("text\n\n- [ ] milk\n- [x] eggs\n- [ ] ", Checklist.join("text", items))
        assertEquals("- [ ] milk", Checklist.join("", listOf(Item("milk"))))
        assertEquals("text", Checklist.join("text\n\n", emptyList()))
        val parsed = Checklist.parse(Checklist.join("a\n\nb", items))
        assertEquals("a\n\nb", parsed.text)
        assertEquals(items, parsed.items)
    }

    @Test
    fun uncheckAll() {
        assertEquals("t\n\n- [ ] a\n- [ ] b", Checklist.uncheckAll("t\n\n- [x] a\n- [ ] b"))
        val untouched = "t\n- [ ] a"
        assertSame(untouched, Checklist.uncheckAll(untouched))
        assertNull(Checklist.uncheckAll(null))
    }

    @Test
    fun withoutBlankItems() {
        assertEquals("- [x] a", Checklist.withoutBlankItems("- [x] a\n- [ ] \n- [ ]  "))
        assertEquals("t", Checklist.withoutBlankItems("t\n\n- [ ] "))
    }
}
