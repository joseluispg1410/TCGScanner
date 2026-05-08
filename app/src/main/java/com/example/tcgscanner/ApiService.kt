package com.example.tcgscanner

import retrofit2.Response
import retrofit2.http.*

data class AuthRequest(val email: String, val password: String)
data class AuthResponse(val user_id: Int?, val message: String?, val detail: String?)

data class SyncRequest(val user_id: Int, val cards: Map<String, Int>)

data class CatalogResponse(
    val decks: List<Deck>,
    val cards: List<CardImage>,
    val card_decks: List<CardDetail>
)

data class UpdateDeckCodeRequest(val old_code: String, val new_code: String)

data class ImportStatusResponse(val status: String, val progress: Int, val message: String?)

data class DeckSettingResponse(val custom_cover_path: String?)

data class SaveDeckSettingRequest(
    val user_id: Int,
    val deck_code: String,
    val custom_cover_path: String?
)

data class SaveCustomImageRequest(
    val user_id: Int,
    val deck_code: String,
    val image_path: String
)

interface ApiService {
    @POST("/auth/register")
    suspend fun register(@Body req: AuthRequest): Response<AuthResponse>

    @POST("/auth/login")
    suspend fun login(@Body req: AuthRequest): Response<AuthResponse>

    @POST("/sync")
    suspend fun syncCollection(@Body request: SyncRequest): Response<AuthResponse>

    @GET("/collection/{user_id}")
    suspend fun getCollection(@Path("user_id") userId: Int): Response<Map<String, Int>>

    @GET("/tcgs")
    suspend fun getTcgs(): Response<List<TCG>>

    @GET("/catalog/full/{tcg_id}")
    suspend fun getFullCatalog(@Path("tcg_id") tcgId: Int): Response<CatalogResponse>

    @POST("/catalog/import/{deck_code}")
    suspend fun importDeckFromWiki(@Path("deck_code") deckCode: String): Response<AuthResponse>

    @GET("/catalog/import/status/{deck_code}")
    suspend fun getImportStatus(@Path("deck_code") deckCode: String): Response<ImportStatusResponse>

    @POST("/catalog/deck/update-code")
    suspend fun updateDeckCode(@Body req: UpdateDeckCodeRequest): Response<AuthResponse>

    // --- Endpoints de Personalización ---
    @GET("/settings/deck/{user_id}/{deck_code}")
    suspend fun getDeckSetting(
        @Path("user_id") userId: Int,
        @Path("deck_code") deckCode: String
    ): Response<DeckSettingResponse>

    @POST("/settings/deck/save")
    suspend fun saveDeckSetting(@Body req: SaveDeckSettingRequest): Response<AuthResponse>

    @GET("/settings/custom-images/{user_id}/{deck_code}")
    suspend fun getCustomImages(
        @Path("user_id") userId: Int,
        @Path("deck_code") deckCode: String
    ): Response<List<String>>

    @POST("/settings/custom-images/save")
    suspend fun saveCustomImage(@Body req: SaveCustomImageRequest): Response<AuthResponse>
}
