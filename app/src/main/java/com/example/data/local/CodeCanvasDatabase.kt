package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProjectEntity::class,
        NodeEntity::class,
        LibraryItemEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class CodeCanvasDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun nodeDao(): NodeDao
    abstract fun libraryDao(): LibraryDao

    companion object {
        @Volatile
        private var INSTANCE: CodeCanvasDatabase? = null

        fun getInstance(context: Context): CodeCanvasDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CodeCanvasDatabase::class.java,
                    "codecanvas.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
