package com.infinitysolutions.notessync.Model

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

//TODO: Update database version
@Database(entities = [Note::class, ImageData::class], version = 1)
abstract class NotesRoomDatabase : RoomDatabase() {
    abstract fun notesDao(): NotesDao
    abstract fun imagesDao(): ImagesDao

    companion object {
        @Volatile
        private var INSTANCE: NotesRoomDatabase? = null

        fun getDatabase(context: Context): NotesRoomDatabase {
            synchronized(this) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                        context.applicationContext,
                        NotesRoomDatabase::class.java,
                        "notes_database"
                    ).build()
                }
                return INSTANCE as NotesRoomDatabase
            }
        }
    }
}