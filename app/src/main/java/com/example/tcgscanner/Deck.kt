package com.example.tcgscanner

import androidx.room.*
import com.google.gson.annotations.SerializedName

@androidx.room.Entity(tableName = "decks_catalog")
data class Deck(
    @androidx.room.PrimaryKey val id: Int?,
    @SerializedName("codigo_deck")
    @androidx.room.ColumnInfo(name = "codigo_deck") val codigoDeck: String?,
    @SerializedName("nombre_deck")
    @androidx.room.ColumnInfo(name = "nombre_deck") val nombreDeck: String?
)

@androidx.room.Dao
interface DeckDao {
    @androidx.room.Query("SELECT nombre_deck FROM decks_catalog WHERE codigo_deck = :code LIMIT 1")
    fun getDeckName(code: String): String?

    @androidx.room.Query("SELECT nombre_carta FROM card_deck_catalog WHERE deck_card_id = :fullCode LIMIT 1")
    fun getCardName(fullCode: String): String?

    @androidx.room.Query("SELECT image_url FROM cards_catalog WHERE nombre_es = :name LIMIT 1")
    fun getCardImageUrl(name: String): String?

    @androidx.room.Query("SELECT nombre_en FROM cards_catalog WHERE nombre_es = :nameEs LIMIT 1")
    fun getCardNameEn(nameEs: String): String?

    @androidx.room.Query("SELECT * FROM card_deck_catalog WHERE codigo_deck = :deckCode")
    fun getAllCardsInDeck(deckCode: String): List<CardDetail>

    @androidx.room.Query("SELECT DISTINCT codigo_deck FROM decks_catalog")
    suspend fun getAllDeckCodes(): List<String>

    @androidx.room.Query("SELECT * FROM decks_catalog ORDER BY nombre_deck ASC")
    suspend fun getAllDecks(): List<Deck>

    @androidx.room.Query("SELECT deck_card_id FROM card_deck_catalog")
    suspend fun getAllCardIds(): List<String>

    // --- MÉTODOS PARA SINCRONIZACIÓN DEL CATÁLOGO ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDecks(decks: List<Deck>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCards(cards: List<CardImage>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCardDetails(details: List<CardDetail>)

    @androidx.room.Query("DELETE FROM decks_catalog")
    suspend fun clearDecks()
}

@androidx.room.Entity(tableName = "card_deck_catalog")
data class CardDetail(
    @androidx.room.PrimaryKey val id: Int?,
    @SerializedName("codigo_deck")
    @androidx.room.ColumnInfo(name = "codigo_deck") val codigoDeck: String?,
    @SerializedName("deck_card_id")
    @androidx.room.ColumnInfo(name = "deck_card_id") val deckCardId: String?,
    @SerializedName("nombre_carta")
    @androidx.room.ColumnInfo(name = "nombre_carta") val nombreCarta: String?
)

@androidx.room.Entity(tableName = "cards_catalog")
data class CardImage(
    @androidx.room.PrimaryKey val id: Int?,
    @SerializedName("nombre_es")
    @androidx.room.ColumnInfo(name = "nombre_es") val nombreEs: String?,
    @SerializedName("nombre_en")
    @androidx.room.ColumnInfo(name = "nombre_en") val nombreEn: String?,
    @SerializedName("descripcion")
    @androidx.room.ColumnInfo(name = "descripcion") val descripcion: String?,
    @SerializedName("image_url")
    @androidx.room.ColumnInfo(name = "image_url") val imageUrl: String?
)