package org.qosp.notes.ui.editor.markdown

import android.widget.TextView
import io.noties.markwon.Markwon
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Code
import org.commonmark.node.CustomBlock
import org.commonmark.node.CustomNode
import org.commonmark.node.Node

class MarkdownOptions {
    var maximumTableColumns: Int = 100
    var tableReplacement: () -> Node = { Code("...") }
}

object MarkdownPreviewCache {
    private val cache = androidx.collection.LruCache<String, android.text.Spanned>(300)

    fun get(key: String): android.text.Spanned? = cache.get(key)
    fun put(key: String, value: android.text.Spanned) {
        cache.put(key, value)
    }
    fun clear() {
        cache.evictAll()
    }
}

fun Markwon.renderMarkdown(content: String, withOptions: MarkdownOptions.() -> Unit = {}): android.text.Spanned {
    val options = MarkdownOptions()
    options.withOptions()

    val node = parse(content)
    val visitor = OptionsVisitor(options)
    node.accept(visitor)

    return render(node)
}

inline fun Markwon.applyTo(textView: TextView, content: String, withOptions: MarkdownOptions.() -> Unit = {}) {
    val options = MarkdownOptions()
    withOptions(options)

    val node = parse(content)
    val visitor = OptionsVisitor(options)
    node.accept(visitor)

    setParsedMarkdown(textView, render(node))
}

class OptionsVisitor(private val options: MarkdownOptions) : AbstractVisitor() {
    override fun visit(customBlock: CustomBlock?) {
        if (customBlock is TableBlock) {
            val visitor = TableRowVisitor()
            customBlock.firstChild?.firstChild?.accept(visitor)

            if (visitor.cellCount > options.maximumTableColumns) {
                val replacement = options.tableReplacement()
                customBlock.insertAfter(replacement)
                customBlock.unlink()
            }
        } else {
            visitChildren(customBlock)
        }
    }

    private class TableRowVisitor : AbstractVisitor() {
        var cellCount = 0

        override fun visit(customNode: CustomNode?) {
            when (customNode) {
                is TableRow -> visitChildren(customNode)
                is TableCell -> cellCount++
            }
        }
    }
}
