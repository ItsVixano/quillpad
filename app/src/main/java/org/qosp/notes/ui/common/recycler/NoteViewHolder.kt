package org.qosp.notes.ui.common.recycler

import android.annotation.SuppressLint
import android.content.Context
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.noties.markwon.Markwon
import org.commonmark.node.Code
import org.qosp.notes.R
import org.qosp.notes.data.model.Attachment
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.model.NoteColor
import org.qosp.notes.data.model.Tag
import org.qosp.notes.databinding.LayoutNoteBinding
import org.qosp.notes.ui.attachments.recycler.AttachmentViewHolder
import org.qosp.notes.ui.attachments.recycler.AttachmentsAdapter
import org.qosp.notes.ui.attachments.recycler.AttachmentsPreviewGridManager
import org.qosp.notes.ui.editor.markdown.MarkdownPreviewCache
import org.qosp.notes.ui.editor.markdown.renderMarkdown
import org.qosp.notes.ui.tasks.TasksAdapter
import org.qosp.notes.ui.utils.dp
import org.qosp.notes.ui.utils.resId

class NoteViewHolder(
    private val binding: LayoutNoteBinding,
    listener: NoteRecyclerListener?,
    private val context: Context,
    private val searchMode: Boolean,
    private val markwon: Markwon,
    tasksViewPool: RecyclerView.RecycledViewPool,
    attachmentsViewPool: RecyclerView.RecycledViewPool,
) : RecyclerView.ViewHolder(binding.root), SelectableViewHolder {

    private val tasksAdapter = TasksAdapter(true, null, markwon)
    private val attachmentsAdapter = AttachmentsAdapter(null, true)

    private val defaultStrokeWidth = 1.dp(context)
    private val selectedStrokeWidth = 2.dp(context)

    init {
        binding.recyclerAttachments.apply {
            layoutManager = AttachmentsPreviewGridManager(context, 2)
            adapter = attachmentsAdapter
            setRecycledViewPool(attachmentsViewPool)
        }

        binding.recyclerTasks.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            adapter = tasksAdapter
            setRecycledViewPool(tasksViewPool)
        }

        if (listener != null) {
            itemView.setOnClickListener { listener.onItemClick(bindingAdapterPosition, binding) }
            itemView.setOnLongClickListener { listener.onLongClick(bindingAdapterPosition, binding) }
        }
    }

    private fun updateBackgroundColor(color: NoteColor) {
        color.resId(context)?.let { resId ->
            binding.root.setCardBackgroundColor(resId)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateTags(tags: List<Tag>) = with(binding) {
        if (tags.isEmpty()) {
            containerTags.isVisible = false
            return@with
        }

        containerTags.isVisible = true
        tagPrimary.text = "# ${tags[0].name}"

        if (tags.size > 1) {
            tagCount.isVisible = true
            tagCount.text = "+${tags.size - 1}"
        } else {
            tagCount.isVisible = false
        }
    }

    private fun updateIndicatorIcons(note: Note, hasReminders: Boolean) = with(binding) {
        indicatorNoteHidden.isVisible = note.isHidden && !searchMode
        indicatorPinned.isVisible = note.isPinned && !searchMode
        indicatorHasReminder.isVisible = hasReminders
        indicatorDeleted.isVisible = note.isDeleted && searchMode
        indicatorArchived.isVisible = note.isArchived && searchMode
    }

    private fun setTitle(note: Note) {
        if (note.title.isEmpty()) {
            binding.textViewTitle.isVisible = false
        } else {
            binding.textViewTitle.isVisible = true
            binding.textViewTitle.text = note.title
        }
    }

    private fun setTextContent(note: Note) = with(binding) {
        val showContent = !note.isList && note.content.isNotEmpty() && !note.isCompactPreview
        textViewContent.isVisible = showContent

        if (showContent) {
            if (note.isMarkdownEnabled && note.content.isNotBlank()) {
                val cacheKey = "${note.id}_${note.modifiedDate}"
                val cached = MarkdownPreviewCache.get(cacheKey)
                if (cached != null) {
                    markwon.setParsedMarkdown(textViewContent, cached)
                } else {
                    try {
                        val previewContent = if (note.content.length > 2000) {
                            note.content.substring(0, 2000)
                        } else {
                            note.content
                        }
                        val rendered = markwon.renderMarkdown(previewContent) {
                            maximumTableColumns = 4
                            tableReplacement = { Code(context.getString(R.string.message_cannot_preview_table)) }
                        }
                        MarkdownPreviewCache.put(cacheKey, rendered)
                        markwon.setParsedMarkdown(textViewContent, rendered)
                    } catch (e: Throwable) {
                        textViewContent.text = ""
                    }
                }
            } else {
                textViewContent.text = note.content
            }
        }
    }

    private fun setTasks(note: Note, useDiff: Boolean = true) = with(binding) {
        val showTasks = note.isList && note.taskList.isNotEmpty() && !note.isCompactPreview
        recyclerTasks.isVisible = showTasks
        indicatorMoreTasks.isVisible = false

        if (showTasks) {
            val taskList = note.taskList.takeIf { it.size <= 8 } ?: note.taskList.subList(0, 8).also {
                val moreItems = note.taskList.size - 8
                val showMoreIndicator = moreItems > 0
                indicatorMoreTasks.isVisible = showMoreIndicator
                if (showMoreIndicator) {
                    indicatorMoreTasks.text =
                        context.resources.getQuantityString(R.plurals.more_items, moreItems, moreItems)
                }
            }
            if (tasksAdapter.tasks != taskList) {
                tasksAdapter.submitList(taskList, useDiff)
            }
        }
    }

    private fun setupAttachments(note: Note) = with(binding) {
        val showAttachments = note.attachments.isNotEmpty() && !note.isCompactPreview
        recyclerAttachments.isVisible = showAttachments
        if (!showAttachments) return@with

        val layoutManager = recyclerAttachments.layoutManager as AttachmentsPreviewGridManager

        val list = note.attachments.take(note.attachments.size.coerceAtMost(4))
        val remaining = note.attachments.size - list.size
        layoutManager.allocateSpans(list.size)
        attachmentsAdapter.submitList(list)

        if (remaining > 0) {
            recyclerAttachments.doOnPreDraw {
                (recyclerAttachments.findViewHolderForAdapterPosition(3) as? AttachmentViewHolder)
                    ?.showMoreAttachmentsIndicator(remaining)
            }
        }
    }

    fun runPayloads(note: Note, payloads: Set<NoteRecyclerAdapter.Payload>) {
        if (NoteRecyclerAdapter.Payload.TitleChanged in payloads) setTitle(note)
        if (NoteRecyclerAdapter.Payload.ContentChanged in payloads ||
            NoteRecyclerAdapter.Payload.MarkdownChanged in payloads
        ) setTextContent(note)
        if (NoteRecyclerAdapter.Payload.TasksChanged in payloads) setTasks(note, useDiff = true)
        if (NoteRecyclerAdapter.Payload.ColorChanged in payloads) updateBackgroundColor(note.color)
        if (NoteRecyclerAdapter.Payload.TagsChanged in payloads) updateTags(note.tags)
        if (NoteRecyclerAdapter.Payload.AttachmentsChanged in payloads) setupAttachments(note)

        if (NoteRecyclerAdapter.Payload.PinChanged in payloads ||
            NoteRecyclerAdapter.Payload.HiddenChanged in payloads ||
            NoteRecyclerAdapter.Payload.ArchivedChanged in payloads ||
            NoteRecyclerAdapter.Payload.DeletedChanged in payloads ||
            NoteRecyclerAdapter.Payload.RemindersChanged in payloads
        ) {
            updateIndicatorIcons(note, note.reminders.isNotEmpty())
        }
    }

    fun bind(note: Note) {
        setTextContent(note)
        setTasks(note, useDiff = false)
        setTitle(note)
        updateBackgroundColor(note.color)
        updateIndicatorIcons(note, note.reminders.isNotEmpty())
        updateTags(note.tags)
        setupAttachments(note)

        ViewCompat.setTransitionName(binding.root, "editor_${note.id}")
    }

    override fun onSelectedStatusChanged(isSelected: Boolean) {
        binding.root.isChecked = isSelected
        binding.root.strokeWidth = if (isSelected) selectedStrokeWidth else defaultStrokeWidth
    }
}
