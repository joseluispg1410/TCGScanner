package com.example.tcgscanner

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

data class AuthRequest(
    val email: String,
    val password: String
)

data class AuthResponse(
    val user_id: Int? = null,
    val message: String?,
    val detail: String? = null
)

data class SyncRequest(
    val user_id: Int,
    val cards: Map<String, Int>
)

data class CatalogResponse(
    val decks: List<Deck>,
    val cards: List<CardImage>,
    val card_decks: List<CardDetail>
)

interface ApiService {
    @POST("auth/register")
    suspend fun register(@Body request: AuthRequest): Response<AuthResponse>

    @POST("auth/login")
    suspend fun login(@Body request: AuthRequest): Response<AuthResponse>

    @POST("sync")
    suspend fun syncCollection(@Body request: SyncRequest): Response<AuthResponse>

    @GET("collection/{user_id}")
    suspend fun getCollection(@Path("user_id") userId: Int): Response<Map<String, Int>>

    @GET("catalog/full")
    suspend fun getFullCatalog(): Response<CatalogResponse>
}
