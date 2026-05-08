package com.example.tcgscanner

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch

class TCGSelectionActivity : AppCompatActivity() {

    private lateinit var listContainer: LinearLayout
    private val colorMidnight = Color.parseColor("#0A192F")
    private val colorHeaderBg = Color.parseColor("#172A45")
    private val colorTextGold = Color.parseColor("#E6B800")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this).apply {
            setBackgroundColor(colorMidnight)
        }

        val scroll = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(40, 100, 40, 100)
        }

        val title = TextView(this).apply {
            text = "SELECCIONA UN JUEGO"
            textSize = 22f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 80)
        }

        listContainer.addView(title)
        scroll.addView(listContainer)
        root.addView(scroll)
        setContentView(root)

        loadTCGs()
    }

    private fun loadTCGs() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getTcgs()
                if (response.isSuccessful) {
                    val tcgs = response.body() ?: emptyList()
                    renderTCGList(tcgs)
                } else {
                    Toast.makeText(this@TCGSelectionActivity, "Error al cargar juegos", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@TCGSelectionActivity, "Error de red: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun renderTCGList(tcgs: List<TCG>) {
        listContainer.removeAllViews()
        
        // Re-añadir título
        val title = TextView(this).apply {
            text = "SELECCIONA UN JUEGO"
            textSize = 22f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 80)
        }
        listContainer.addView(title)

        tcgs.forEach { tcg ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 0)
                val shape = GradientDrawable().apply {
                    setColor(colorHeaderBg)
                    cornerRadius = 40f
                    setStroke(3, colorTextGold)
                }
                background = shape
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 500).apply {
                    setMargins(0, 0, 0, 60)
                }
                setOnClickListener {
                    val intent = Intent(this@TCGSelectionActivity, MainActivity::class.java)
                    intent.putExtra("TCG_ID", tcg.id)
                    intent.putExtra("TCG_NAME", tcg.name)
                    intent.putExtra("TCG_CODE", tcg.code)
                    startActivity(intent)
                }
            }

            val image = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
                scaleType = ImageView.ScaleType.CENTER_CROP
                if (!tcg.imageUrl.isNullOrEmpty()) {
                    Glide.with(this@TCGSelectionActivity).load(tcg.imageUrl).into(this)
                } else {
                    setImageResource(android.R.drawable.ic_menu_gallery)
                }
            }

            val name = TextView(this).apply {
                text = tcg.name
                textSize = 18f
                setTextColor(Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, 20, 0, 20)
            }

            card.addView(image)
            card.addView(name)
            listContainer.addView(card)
        }
    }
}
