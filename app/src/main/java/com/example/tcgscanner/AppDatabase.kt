package com.example.tcgscanner

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Deck::class, CardDetail::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deckDao(): DeckDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "yugioh_cards.db"
                )
                    .createFromAsset("yugioh_cards.db") // 🔥 Esto vincula tu archivo SQLite
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}