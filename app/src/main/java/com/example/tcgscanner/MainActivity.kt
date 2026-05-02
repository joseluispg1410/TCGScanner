package com.example.tcgscanner

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class MainActivity : AppCompatActivity() {

    private lateinit var listContainer: LinearLayout
    private lateinit var mainRoot: FrameLayout

    // Colores del tema Midnight Blue
    private val colorMidnight = Color.parseColor("#0A192F") // Azul Marino Profundo
    private val colorHeaderBg = Color.parseColor("#172A45") // Azul Marino Intermedio
    private val colorTextGold = Color.parseColor("#E6B800") // Dorado Elegante
    private val colorTextLight = Color.parseColor("#CCD6F6") // Gris Azulado Claro
    private val colorTextDim = Color.parseColor("#8892B0")   // Gris Azulado Atenuado

    // 🔥 Mapas de cartas
    private var cardMap = mutableMapOf<String, Int>()
    private var notFoundCards = mutableMapOf<String, Int>()
    
    // Estado de visibilidad de las colecciones
    private val collectionVisibility = mutableMapOf<String, Boolean>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mainRoot = FrameLayout(this).apply {
            setBackgroundColor(colorMidnight)
        }

        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(0, 0, 0, 300) // Espacio para el FAB
        }

        val scrollView = ScrollView(this).apply {
            addView(listContainer)
            setBackgroundColor(Color.TRANSPARENT)
        }

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(0, 40, 0, 0)

            addView(scrollView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                ))
        }

        // 🔘 BOTÓN FLOTANTE (FAB) REDONDO EN EL MEDIO ABAJO
        val fabScanner = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_camera) 
            
            val size = 180
            layoutParams = FrameLayout.LayoutParams(size, size, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                bottomMargin = 80
            }
            
            val shape = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(colorTextGold) // Dorado para el botón principal
            }
            background = shape
            setColorFilter(colorMidnight) // Icono en azul marino
            setPadding(40, 40, 40, 40)
            elevation = 20f

            setOnClickListener { openScanner() }
        }

        mainRoot.addView(contentLayout)
        mainRoot.addView(fabScanner)
        setContentView(mainRoot)

        // 🔥 Cargar datos al iniciar
        loadPersistedData()
        refreshList()
    }

    private fun openScanner() {
        startActivityForResult(Intent(this, ScannerActivity::class.java), 100)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 100 && resultCode == Activity.RESULT_OK) {
            val list = data?.getStringArrayListExtra("CARDS") ?: return

            lifecycleScope.launch {
                list.forEach { code ->
                    val cardName = withContext(Dispatchers.IO) {
                        AppDatabase.getDatabase(this@MainActivity).deckDao().getCardName(code)
                    }

                    if (cardName != null) {
                        cardMap[code] = (cardMap[code] ?: 0) + 1
                    } else {
                        notFoundCards[code] = (notFoundCards[code] ?: 0) + 1
                    }
                }
                saveDataToDisk()
                refreshList()
            }
        }
    }

    private fun refreshList() {
        listContainer.removeAllViews()

        val grouped = cardMap.entries.groupBy { getBaseCode(it.key) }

        grouped.toSortedMap().forEach { (collection, cards) ->
            val isVisible = collectionVisibility[collection] ?: true
            
            // 🟩 CABECERA REDISEÑADA (Midnight Style)
            val headerContainer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(30, 25, 30, 25)
                
                val shape = GradientDrawable().apply {
                    setColor(colorHeaderBg)
                    cornerRadius = 20f
                    setStroke(2, colorTextGold) // Borde dorado fino
                }
                background = shape
                
                val params = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(20, 40, 20, 10)
                layoutParams = params
            }

            val headerText = TextView(this).apply {
                text = collection
                textSize = 16f
                setTextColor(colorTextLight)
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val toggleBtn = Button(this).apply {
                text = if (isVisible) "OCULTAR" else "MOSTRAR"
                setTextColor(colorTextGold)
                setBackgroundColor(Color.TRANSPARENT)
                textSize = 11f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setOnClickListener {
                    collectionVisibility[collection] = !isVisible
                    refreshList()
                }
            }

            headerContainer.addView(headerText)
            headerContainer.addView(toggleBtn)
            listContainer.addView(headerContainer)

            lifecycleScope.launch {
                val deckName = withContext(Dispatchers.IO) {
                    AppDatabase.getDatabase(this@MainActivity).deckDao().getDeckName(collection)
                }
                if (deckName != null) {
                    headerText.text = "$collection: $deckName"
                    headerText.setTextColor(Color.WHITE)
                }
            }

            if (isVisible) {
                val gridLayout = GridLayout(this).apply {
                    columnCount = 3
                    setPadding(20, 15, 20, 15)
                }

                cards
                    .sortedBy { extractNumber(it.key) }
                    .forEach { (code, count) ->
                        val cardView = createCardItem(code, count)
                        gridLayout.addView(cardView)
                    }
                
                listContainer.addView(gridLayout)
            }
        }

        if (notFoundCards.isNotEmpty()) {
            val separator = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 3).apply {
                    setMargins(40, 60, 40, 20)
                }
                setBackgroundColor(Color.parseColor("#F44336")) // Rojo para errores
            }
            listContainer.addView(separator)

            val errorHeader = TextView(this).apply {
                text = "CÓDIGOS NO RECONOCIDOS"
                textSize = 16f
                setTextColor(Color.parseColor("#F44336"))
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(20, 20, 20, 10)
                gravity = Gravity.CENTER_HORIZONTAL
            }
            listContainer.addView(errorHeader)

            notFoundCards.forEach { (code, count) ->
                val row = createNotFoundRow(code, count)
                listContainer.addView(row)
            }
        }
    }

    private fun createCardItem(code: String, count: Int): LinearLayout {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(10, 10, 10, 10)
            layoutParams = GridLayout.LayoutParams().apply {
                width = resources.displayMetrics.widthPixels / 3 - 40
            }
        }

        val imgContainer = FrameLayout(this)

        val imageView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                300
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(colorHeaderBg) // Fondo de la carta a juego
        }

        val deleteBtn = Button(this).apply {
            text = "X"
            textSize = 12f
            setTextColor(Color.WHITE)
            val shape = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#CCF44336")) // Rojo semi
            }
            background = shape
            layoutParams = FrameLayout.LayoutParams(60, 60, Gravity.TOP or Gravity.END)
            
            setOnClickListener {
                val current = cardMap[code] ?: return@setOnClickListener
                if (current > 1) cardMap[code] = current - 1 else cardMap.remove(code)
                saveDataToDisk()
                refreshList()
            }
        }

        imgContainer.addView(imageView)
        imgContainer.addView(deleteBtn)

        val textCode = TextView(this).apply {
            text = if (count > 1) "$code x$count" else code
            textSize = 11f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(colorTextLight)
        }

        val textName = TextView(this).apply {
            textSize = 9f
            gravity = Gravity.CENTER
            setTextColor(colorTextDim)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        item.addView(imgContainer)
        item.addView(textCode)
        item.addView(textName)

        lifecycleScope.launch {
            val cardName = withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(this@MainActivity).deckDao().getCardName(code)
            }
            if (cardName != null) {
                textName.text = cardName
                
                val imageUrl = withContext(Dispatchers.IO) {
                    AppDatabase.getDatabase(this@MainActivity).deckDao().getCardImageUrl(cardName)
                }
                
                if (!imageUrl.isNullOrEmpty()) {
                    Glide.with(this@MainActivity)
                        .load(imageUrl)
                        .into(imageView)
                    
                    imageView.setOnClickListener {
                        showImageOverlay(imageUrl)
                    }
                }
            }
        }

        return item
    }

    private fun createNotFoundRow(code: String, count: Int): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(40, 10, 20, 10)
            gravity = Gravity.CENTER_VERTICAL
        }

        val textView = TextView(this).apply {
            text = if (count > 1) "$code x$count" else code
            textSize = 14f
            setTextColor(colorTextDim)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val deleteButton = Button(this).apply {
            text = "X"
            textSize = 14f
            setTextColor(Color.parseColor("#F44336"))
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener {
                val current = notFoundCards[code] ?: return@setOnClickListener
                if (current > 1) notFoundCards[code] = current - 1 else notFoundCards.remove(code)
                saveDataToDisk()
                refreshList()
            }
        }

        row.addView(textView)
        row.addView(deleteButton)
        return row
    }

    private fun showImageOverlay(url: String) {
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#DD000000")) // Fondo más oscuro para enfoque
            setOnClickListener { mainRoot.removeView(this) }
        }

        val fullImage = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply { 
                setMargins(60, 60, 60, 60)
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        Glide.with(this).load(url).into(fullImage)
        overlay.addView(fullImage)
        mainRoot.addView(overlay)
    }

    // 🔥 PERSISTENCIA
    private fun saveDataToDisk() {
        val prefs = getSharedPreferences("CardData", Context.MODE_PRIVATE)
        val gson = Gson()
        prefs.edit {
            putString("cardMap", gson.toJson(cardMap))
            putString("notFoundCards", gson.toJson(notFoundCards))
        }
    }

    private fun loadPersistedData() {
        val prefs = getSharedPreferences("CardData", Context.MODE_PRIVATE)
        val gson = Gson()
        
        val type = object : TypeToken<MutableMap<String, Int>>() {}.type
        
        val jsonFound = prefs.getString("cardMap", null)
        if (jsonFound != null) {
            cardMap = gson.fromJson(jsonFound, type)
        }
        
        val jsonNotFound = prefs.getString("notFoundCards", null)
        if (jsonNotFound != null) {
            notFoundCards = gson.fromJson(jsonNotFound, type)
        }
    }

    private fun getBaseCode(code: String): String {
        val regex = Regex("([A-Z0-9]{3,4}-[A-Z]{1,2})")
        return regex.find(code)?.value ?: code
    }

    private fun extractNumber(code: String): Int {
        val regex = Regex("(\\d{3})$")
        return regex.find(code)?.value?.toIntOrNull() ?: Int.MAX_VALUE
    }
}
