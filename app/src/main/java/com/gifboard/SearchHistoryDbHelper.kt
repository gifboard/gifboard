package com.gifboard

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * SQLite database helper for storing search history.
 */
class SearchHistoryDbHelper(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    companion object {
        private const val DATABASE_NAME = "gifboard.db"
        private const val DATABASE_VERSION = 1
        
        private const val TABLE_HISTORY = "search_history"
        private const val COLUMN_ID = "id"
        private const val COLUMN_QUERY = "query"
        private const val COLUMN_TIMESTAMP = "timestamp"
        
        private const val MAX_HISTORY_SIZE = 50
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE_HISTORY (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_QUERY TEXT UNIQUE NOT NULL,
                $COLUMN_TIMESTAMP INTEGER NOT NULL
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_HISTORY")
        onCreate(db)
    }

    /**
     * Add a search query to history. If it exists, updates timestamp.
     * Trims history to MAX_HISTORY_SIZE.
     */
    fun addSearch(query: String) {
        if (query.isBlank()) return
        
        val trimmedQuery = query.trim()
        val db = writableDatabase
        
        val values = ContentValues().apply {
            put(COLUMN_QUERY, trimmedQuery)
            put(COLUMN_TIMESTAMP, System.currentTimeMillis())
        }
        
        // Insert or update if exists
        db.insertWithOnConflict(TABLE_HISTORY, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        
        // Trim to max size
        db.execSQL("""
            DELETE FROM $TABLE_HISTORY 
            WHERE $COLUMN_ID NOT IN (
                SELECT $COLUMN_ID FROM $TABLE_HISTORY 
                ORDER BY $COLUMN_TIMESTAMP DESC 
                LIMIT $MAX_HISTORY_SIZE
            )
        """.trimIndent())
    }

    /**
     * Remove a specific query from history.
     */
    fun removeSearch(query: String) {
        val db = writableDatabase
        db.delete(TABLE_HISTORY, "$COLUMN_QUERY = ?", arrayOf(query))
    }

    /**
     * Clear all search history.
     */
    fun clearAll() {
        val db = writableDatabase
        db.delete(TABLE_HISTORY, null, null)
    }

    /**
     * Get all history items, most recent first.
     */
    fun getHistory(): List<String> {
        val history = mutableListOf<String>()
        val db = readableDatabase
        
        val cursor = db.query(
            TABLE_HISTORY,
            arrayOf(COLUMN_QUERY),
            null, null, null, null,
            "$COLUMN_TIMESTAMP DESC"
        )
        
        cursor.use {
            while (it.moveToNext()) {
                history.add(it.getString(0))
            }
        }
        
        return history
    }
}
