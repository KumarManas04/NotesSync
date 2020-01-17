package com.infinitysolutions.notessync.Model

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Note::class, ImageData::class], version = 2)
abstract class NotesRoomDatabase : RoomDatabase() {
    abstract fun notesDao(): NotesDao
    abstract fun imagesDao(): ImagesDao

    companion object {
        @Volatile
        private var INSTANCE: NotesRoomDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2){
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `images_table` (`image_id` INTEGER PRIMARY KEY AUTOINCREMENT, `image_path` TEXT NOT NULL, `date_created` INTEGER NOT NULL, `date_modified` INTEGER NOT NULL, `g_drive_id` TEXT, `type` INTEGER NOT NULL)")
            }
        }

        fun getDatabase(context: Context): NotesRoomDatabase {
            synchronized(this) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                        context.applicationContext,
                        NotesRoomDatabase::class.java,
                        "notes_database"
                    )
                        .addMigrations(MIGRATION_1_2)
                        .build()
                }
                return INSTANCE as NotesRoomDatabase
            }
        }
    }
}