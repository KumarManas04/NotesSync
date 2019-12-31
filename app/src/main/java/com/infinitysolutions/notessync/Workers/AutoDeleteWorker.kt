package com.infinitysolutions.notessync.Workers

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.infinitysolutions.notessync.Contracts.Contract.Companion.NOTE_DELETED
import com.infinitysolutions.notessync.Model.Note
import com.infinitysolutions.notessync.Model.NotesRoomDatabase
import java.util.*

class AutoDeleteWorker(context: Context, params: WorkerParameters) : Worker(context, params){

    override fun doWork(): Result {
        val notesRoomDatabase = NotesRoomDatabase.getDatabase(applicationContext)
        val notesDao = notesRoomDatabase.notesDao()

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
        for (item in trashList){
            if (item.dateModified + differTime <= presentTime){
                //TODO: Handle image based notes
                notesDao.simpleInsert(
                    Note(
                        item.nId,
                        item.noteTitle,
                        item.noteContent,
                        item.dateCreated,
                        presentTime,
                        item.gDriveId,
                        NOTE_DELETED,
                        item.synced,
                        item.noteColor,
                        item.reminderTime
                    )
                )
            }
        }
        return Result.success()
    }

}