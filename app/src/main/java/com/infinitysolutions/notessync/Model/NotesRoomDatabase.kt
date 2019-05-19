package com.infinitysolutions.notessync.Model

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Note::class], version = 1)
abstract class NotesRoomDatabase : RoomDatabase() {
    abstract fun notesDao(): NotesDao

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