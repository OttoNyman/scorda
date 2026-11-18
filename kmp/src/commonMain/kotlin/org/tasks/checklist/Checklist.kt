package org.tasks.checklist

/**
 * A checklist rides at the end of a task's notes as Markdown task lines (`- [ ] milk`,
 * `- [x] eggs`), so Google Tasks, backups and search carry it as plain text. Only the unbroken
 * block of such lines at the very end counts; the same line inside the text stays text.
 */
object Checklist {
    data class Item(val text: String, val checked: Boolean = false)

    data class Parsed(val text: String, val items: List<Item>) {
        val done: Int get() = items.count { it.checked }
    }

    private val LINE = Regex("""^[-*] \[([ xX])\] ?(.*)$""")

    fun parse(notes: String?): Parsed {
        val lines = notes.orEmpty().trimEnd().lines()
        var start = lines.size
        while (start > 0 && LINE.matches(lines[start - 1])) start--
        val items = lines.drop(start).map { line ->
            val match = LINE.matchEntire(line)!!
            Item(text = match.groupValues[2], checked = match.groupValues[1] != " ")
        }
        return Parsed(lines.take(start).joinToString("\n").trimEnd(), items)
    }

    fun join(text: String?, items: List<Item>): String {
        val body = text.orEmpty().trimEnd()
        val list = items.joinToString("\n") { "- [${if (it.checked) "x" else " "}] ${it.text}" }
        return when {
            items.isEmpty() -> body
            body.isEmpty() -> list
            else -> "$body\n\n$list"
        }
    }

    /** Notes as they are when nothing changes, so an untouched description is not rewritten. */
    private fun rewrite(notes: String?, change: (List<Item>) -> List<Item>): String? {
        val parsed = parse(notes)
        val items = change(parsed.items)
        return if (items == parsed.items) notes else join(parsed.text, items)
    }

    fun withoutBlankItems(notes: String?): String? = rewrite(notes) { items -> items.filter { it.text.isNotBlank() } }

    fun uncheckAll(notes: String?): String? = rewrite(notes) { items -> items.map { it.copy(checked = false) } }
}
