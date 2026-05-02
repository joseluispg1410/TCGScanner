package com.example.tcgscanner

import androidx.room.*

@androidx.room.Entity(tableName = "decks")
data class Deck(
    @androidx.room.PrimaryKey val id: Int?,
    @androidx.room.ColumnInfo(name = "codigo_deck") val codigoDeck: String?,
    @androidx.room.ColumnInfo(name = "nombre_deck") val nombreDeck: String?
)

@androidx.room.Dao
interface DeckDao {
    @androidx.room.Query("SELECT nombre_deck FROM decks WHERE codigo_deck = :code LIMIT 1")
    fun getDeckName(code: String): String?

    @androidx.room.Query("SELECT nombre_carta FROM card_deck WHERE deck_card_id = :fullCode LIMIT 1")
    fun getCardName(fullCode: String): String?

    @androidx.room.Query("SELECT image_url FROM cards WHERE nombre_es = :name LIMIT 1")
    fun getCardImageUrl(name: String): String?
}

@androidx.room.Entity(tableName = "card_deck")
data class CardDetail(
    @androidx.room.PrimaryKey val id: Int?,
    @androidx.room.ColumnInfo(name = "codigo_deck") val codigoDeck: String?,
    @androidx.room.ColumnInfo(name = "deck_card_id") val deckCardId: String?,
    @androidx.room.ColumnInfo(name = "nombre_carta") val nombreCarta: String?
)

@androidx.room.Entity(tableName = "cards")
data class CardImage(
    @androidx.room.PrimaryKey val id: Int?,
    @androidx.room.ColumnInfo(name = "nombre_es") val nombreEs: String?,
    @androidx.room.ColumnInfo(name = "nombre_en") val nombreEn: String?,
    @androidx.room.ColumnInfo(name = "descripcion") val descripcion: String?,
    @androidx.room.ColumnInfo(name = "image_url") val imageUrl: String?
)