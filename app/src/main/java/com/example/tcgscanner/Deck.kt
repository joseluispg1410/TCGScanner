package com.example.tcgscanner

import com.google.gson.annotations.SerializedName

// Data models for the API (No more Room annotations)

data class TCG(
    val id: Int,
    val name: String,
    val code: String,
    @SerializedName("image_url") val imageUrl: String?
)

data class Deck(
    val id: Int?,
    @SerializedName("codigo_deck_sp")
    val codigoDeckSp: String?,
    @SerializedName("codigo_deck_en")
    val codigoDeckEn: String?,
    @SerializedName("nombre_deck")
    val nombreDeck: String?
)

data class CardDetail(
    val id: Int?,
    @SerializedName("codigo_deck_sp")
    val codigoDeckSp: String?,
    @SerializedName("deck_card_id")
    val deckCardId: String?,
    @SerializedName("nombre_carta")
    val nombreCarta: String?
)

data class CardImage(
    val id: Int?,
    @SerializedName("nombre_es")
    val nombreEs: String?,
    @SerializedName("nombre_en")
    val nombreEn: String?,
    @SerializedName("descripcion")
    val descripcion: String?,
    @SerializedName("image_url")
    val imageUrl: String?
)

data class UserDeckSetting(
    val userId: Int,
    val deckCode: String,
    val customCoverPath: String? = null
)

data class CustomDeckImage(
    val userId: Int,
    val deckCode: String,
    val imagePath: String
)

data class DeckTotalCount(
    val deckCode: String,
    val total: Int
)
