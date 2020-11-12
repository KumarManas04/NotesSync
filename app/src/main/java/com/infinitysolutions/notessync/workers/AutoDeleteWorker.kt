package com.infinitysolutions.notessync.workers

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.infinitysolutions.notessync.contracts.Contract
import com.infinitysolutions.notessync.contracts.Contract.Companion.IMAGE_DELETED
import com.infinitysolutions.notessync.contracts.Contract.Companion.IMAGE_LIST_TRASH
import com.infinitysolutions.notessync.contracts.Contract.Companion.IMAGE_TRASH
import com.infinitysolutions.notessync.contracts.Contract.Companion.NOTE_DELETED
import com.infinitysolutions.notessync.model.*
import com.infinitysolutions.notessync.util.WorkSchedulerHelper
import java.io.File
import java.util.*

class AutoDeleteWorker(private val context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val notesRoomDatabase = NotesRoomDatabase.getDatabase(applicationContext)
        val notesDao = notesRoomDatabase.notesDao()
        val imagesDao = notesRoomDatabase.imagesDao()

        val cal = Calendar.getInstance()
        cal.set(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH),
            0,
            0,
            0
        )
        val presentTime = cal.timeInMillis
        val differTime: Long = 2592000000
        val trashList = notesDao.getTrashPresent()
        val deletedIds = hashSetOf<String>()
        for (item in trashList) {
            if (item.dateModified + differTime <= presentTime) {
                deleteNote(item, notesDao, imagesDao)
                deletedIds.add(item.nId.toString())
            }
        }
        if(deletedIds.isNotEmpty()){
            val prefs = context.getSharedPreferences(Contract.SHARED_PREFS_NAME, Context.MODE_PRIVATE)
            var set = prefs.getStringSet(Contract.PREF_SYNC_QUEUE, null)
            if(set == null)
                set = deletedIds
            else
                set.addAll(deletedIds)
            val editor = prefs.edit()
            editor.putStringSet(Contract.PREF_SYNC_QUEUE, set)
            editor.commit()
            WorkSchedulerHelper().syncNotes(false, context)
        }
        return Result.success()
    }

    private fun deleteNote(note: Note, notesDao: NotesDao, imagesDao: ImagesDao) {
        if (note.noteType == IMAGE_TRASH || note.noteType == IMAGE_LIST_TRASH) {
            val imageNoteContent = Gson().fromJson(note.noteContent, ImageNoteContent::class.java)
            deleteImagesByIds(imageNoteContent.idList, imagesDao)
            changeNoteType(note, IMAGE_DELETED, notesDao)
        } else if (note.noteType == Contract.NOTE_TRASH || note.noteType == Contract.LIST_TRASH) {
            changeNoteType(note, NOTE_DELETED, notesDao)
        }
    }

    private fun changeNoteType(note: Note, noteType: Int, notesDao: NotesDao) {
        notesDao.simpleInsert(
            Note(
                note.nId,
                note.noteTitle,
                note.noteContent,
                note.dateCreated,
                Calendar.getInstance().timeInMillis,
                note.gDriveId,
                noteType,
                note.synced,
                note.noteColor,
                note.reminderTime
            )
        )
    }

    private fun deleteImagesByIds(idList: ArrayList<Long>, imagesDao: ImagesDao) {
        val images = getImagesByIds(idList, imagesDao)
        for (image in images)
            deleteImage(image.imageId!!, image.imagePath, imagesDao)
    }

    private fun getImagesByIds(idList: ArrayList<Long>, imagesDao: ImagesDao): ArrayList<ImageData> {
        return ArrayList(imagesDao.getImagesByIds(idList))
    }

    private fun deleteImage(id: Long, path: String, imagesDao: ImagesDao) {
        val file = File(path)
        if (file.exists())
            file.delete()
        imagesDao.deleteImageById(id)
    }
}