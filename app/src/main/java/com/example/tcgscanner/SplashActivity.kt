package com.example.tcgscanner

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#0A192F")) // colorMidnight
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.logo)
            layoutParams = FrameLayout.LayoutParams(900, 900, Gravity.CENTER)
            scaleType = ImageView.ScaleType.FIT_CENTER
            alpha = 0f
            scaleX = 0.8f
            scaleY = 0.8f
        }

        val statusText = TextView(this).apply {
            text = "Buscando actualizaciones..."
            setTextColor(Color.parseColor("#8892B0"))
            textSize = 12f
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            ).apply { bottomMargin = 100 }
        }

        root.addView(logo)
        root.addView(statusText)
        setContentView(root)

        logo.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(800)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        lifecycleScope.launch {
            // 1. Sincronizar catálogo maestro en segundo plano
            try {
                val response = RetrofitClient.instance.getFullCatalog()
                if (response.isSuccessful && response.body() != null) {
                    val catalog = response.body()!!
                    val db = AppDatabase.getDatabase(this@SplashActivity).deckDao()
                    
                    withContext(Dispatchers.IO) {
                        // Limpiamos los códigos de barajas antes de insertar los nuevos
                        // para evitar conflictos con IDs manuales repetidos en Postgres
                        db.clearDecks()

                        db.insertDecks(catalog.decks)
                        db.insertCards(catalog.cards)
                        db.insertCardDetails(catalog.card_decks)
                    }
                    statusText.text = "Catálogo actualizado (${catalog.cards.size} cartas)"
                } else {
                    statusText.text = "Error de servidor: ${response.code()}"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                statusText.text = "Modo offline (Error: ${e.message})"
            }

            // 2. Esperar un poco para que se vea el logo
            delay(1000)

            // 3. Decidir a qué pantalla ir
            val prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE)
            val userId = prefs.getInt("user_id", -1)

            val nextIntent = if (userId != -1) {
                Intent(this@SplashActivity, MainActivity::class.java)
            } else {
                Intent(this@SplashActivity, LoginActivity::class.java)
            }
            
            startActivity(nextIntent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }
    }
}
