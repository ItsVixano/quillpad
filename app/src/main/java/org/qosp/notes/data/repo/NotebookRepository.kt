package org.qosp.notes.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.qosp.notes.data.dao.NotebookDao
import org.qosp.notes.data.model.Notebook

import java.time.Instant

class NotebookRepository(
    private val notebookDao: NotebookDao,
    private val noteRepository: NoteRepository,
) {

    suspend fun insert(notebook: Notebook): Long {
        return notebookDao.insert(notebook)
    }

    suspend fun delete(vararg notebooks: Notebook) {
        val affectedNotes = notebooks
            .map { noteRepository.getByNotebook(it.id).first() }
            .flatten()
            .filterNot { it.isLocalOnly }

        notebookDao.delete(*notebooks)
        if (affectedNotes.isNotEmpty()) {
            val updatedNotes = affectedNotes.map { it.copy(notebookId = null, modifiedDate = Instant.now().epochSecond) }.toTypedArray()
            noteRepository.updateNotes(*updatedNotes)
        }
    }

    suspend fun update(vararg notebooks: Notebook, shouldSync: Boolean = true) {
        notebookDao.update(*notebooks)
        if (shouldSync) {
            notebooks.forEach { notebook ->
                val notes = noteRepository.getByNotebook(notebook.id).first().filterNot { it.isLocalOnly }
                if (notes.isNotEmpty()) {
                    val updatedNotes = notes.map { it.copy(modifiedDate = Instant.now().epochSecond) }.toTypedArray()
                    noteRepository.updateNotes(*updatedNotes)
                }
            }
        }
    }

    fun getById(notebookId: Long): Flow<Notebook?> {
        return notebookDao.getById(notebookId)
    }

    fun getAll(): Flow<List<Notebook>> {
        return notebookDao.getAll()
    }

    fun getByName(name: String): Flow<Notebook?> {
        return notebookDao.getByName(name)
    }
}
