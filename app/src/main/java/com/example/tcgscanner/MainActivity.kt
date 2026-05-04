package com.example.tcgscanner

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer


class MainActivity : AppCompatActivity() {

    private lateinit var listContainer: LinearLayout
    private lateinit var mainRoot: FrameLayout
    private lateinit var statsPanel: LinearLayout
    private lateinit var fabScanner: ImageButton
    private lateinit var toolbarTitle: TextView
    private lateinit var searchInput: EditText
    private lateinit var searchBtn: ImageButton
    private var isStatsOpen = false

    // 💰 PRECIOS
    private val priceCache = mutableMapOf<String, String>()

    // Colores del tema Midnight Blue
    private val colorMidnight = Color.parseColor("#0A192F")
    private val colorHeaderBg = Color.parseColor("#172A45")
    private val colorTextGold = Color.parseColor("#E6B800")
    private val colorTextLight = Color.parseColor("#CCD6F6")
    private val colorTextDim = Color.parseColor("#8892B0")

    // 🔥 Mapas de cartas
    private var cardMap = mutableMapOf<String, Int>()
    private var notFoundCards = mutableMapOf<String, Int>()
    private var lastScanCount = 0
    
    // Estado de visibilidad de las colecciones
    private val collectionVisibility = mutableMapOf<String, Boolean>()
    
    // 🚩 MODO DE VISTA: true = Ver caratulas, false = Ver todas las cartas
    private var isDeckViewMode = false
    private var selectedCollection: String? = null

    // 🔍 ESTADO DE BÚSQUEDA Y CACHÉ
    private var isSearchMode = false
    private var searchQuery = ""
    private val deckNamesCache = mutableMapOf<String, String>()
    private val cardNamesCache = mutableMapOf<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mainRoot = FrameLayout(this).apply {
            setBackgroundColor(colorMidnight)
        }

        // Manejar insets del sistema para evitar solapamiento con la status bar
        ViewCompat.setOnApplyWindowInsetsListener(mainRoot) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = systemBars.top)
            insets
        }

        // 🛠️ TOOLBAR MODERNA (Cápsula blanca, Lupa izquierda, Hamburger derecha)
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(30, 15, 30, 15)
            background = resources.getDrawable(R.drawable.toolbar_bg, null)
            elevation = 15f

            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(40, 30, 40, 30)
            }

            toolbarTitle = TextView(this@MainActivity).apply {
                text = "TCG SCANNER"
                textSize = 18f
                setTextColor(Color.parseColor("#333333"))
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            searchInput = EditText(this@MainActivity).apply {
                hint = "Buscar..."
                textSize = 16f
                setTextColor(Color.BLACK)
                setHintTextColor(Color.GRAY)
                background = null // Quitar línea inferior
                visibility = View.GONE
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        searchQuery = s.toString()
                        refreshList()
                    }
                    override fun afterTextChanged(s: Editable?) {}
                })
            }

            searchBtn = ImageButton(this@MainActivity).apply {
                setImageResource(R.drawable.ic_search)
                setBackgroundColor(Color.TRANSPARENT)
                setPadding(20, 20, 20, 20)
                setOnClickListener {
                    if (!isSearchMode) {
                        enterSearchMode()
                    } else {
                        exitSearchMode()
                    }
                }
            }

            val menuBtn = ImageButton(this@MainActivity).apply {
                setImageResource(R.drawable.ic_menu)
                setBackgroundColor(Color.TRANSPARENT)
                setPadding(20, 20, 20, 20)
                setOnClickListener { view -> showPopupMenu(view) }
            }

            addView(searchBtn)
            addView(toolbarTitle)
            addView(searchInput)
            addView(menuBtn)
        }

        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(0, 0, 0, 300)
        }

        val scrollView = ScrollView(this).apply {
            addView(listContainer)
            setBackgroundColor(Color.TRANSPARENT)
        }

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
            
            addView(toolbar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

            addView(scrollView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                ))
        }

        // 📊 PANEL DE ESTADÍSTICAS
        statsPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 60, 60, 60)
            visibility = View.GONE
            
            val shape = GradientDrawable().apply {
                setColor(colorHeaderBg)
                cornerRadius = 40f
                setStroke(3, colorTextGold)
            }
            background = shape
            
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            ).apply { 
                topMargin = 250 
                leftMargin = 40
                rightMargin = 40
            }
            elevation = 30f
        }

        // 🔘 BOTÓN FLOTANTE (FAB)
        fabScanner = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_camera) 
            val size = 180
            layoutParams = FrameLayout.LayoutParams(size, size, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                bottomMargin = 80
            }
            val shape = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(colorTextGold)
            }
            background = shape
            setColorFilter(colorMidnight)
            setPadding(40, 40, 40, 40)
            elevation = 20f
            setOnClickListener { openScanner() }
        }

        mainRoot.addView(contentLayout)
        mainRoot.addView(statsPanel)
        mainRoot.addView(fabScanner)
        setContentView(mainRoot)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isSearchMode) {
                    exitSearchMode()
                } else if (isStatsOpen) {
                    toggleStats()
                } else if (selectedCollection != null) {
                    selectedCollection = null
                    refreshList()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        loadPersistedData()
        updateCachesAndRefresh()
    }

    private fun updateCachesAndRefresh() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@MainActivity).deckDao()
            withContext(Dispatchers.IO) {
                // Caché de barajas
                cardMap.keys.map { getBaseCode(it) }.distinct().forEach { code ->
                    if (!deckNamesCache.containsKey(code)) {
                        deckNamesCache[code] = db.getDeckName(code) ?: "Colección"
                    }
                }
                // Caché de cartas
                cardMap.keys.forEach { code ->
                    if (!cardNamesCache.containsKey(code)) {
                        cardNamesCache[code] = db.getCardName(code) ?: code
                    }
                }
            }
            refreshList()
        }
    }

    private fun showPopupMenu(view: View) {
        val popup = PopupMenu(this, view)
        popup.menu.add("Estadísticas")
        popup.menu.add(if (isDeckViewMode) "Vista Detallada" else "Vista de Carátulas")
        
        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "Estadísticas" -> toggleStats()
                "Vista Detallada", "Vista de Carátulas" -> {
                    isDeckViewMode = !isDeckViewMode
                    saveDataToDisk() // Guardar preferencia de vista
                    refreshList()
                }
            }
            true
        }
        popup.show()
    }

    private fun toggleStats() {
        isStatsOpen = !isStatsOpen
        if (isStatsOpen) {
            updateStatsUI()
            statsPanel.visibility = View.VISIBLE
            statsPanel.alpha = 0f
            statsPanel.animate().alpha(1f).setDuration(300).start()
        } else {
            statsPanel.animate().alpha(0f).setDuration(200).withEndAction {
                statsPanel.visibility = View.GONE
            }.start()
        }
    }

    private fun updateStatsUI() {
        statsPanel.removeAllViews()
        val totalCards = cardMap.values.sum()
        val uniqueCards = cardMap.size
        val notFoundCount = notFoundCards.values.sum()

        statsPanel.addView(createStatRow("Total escaneadas", totalCards.toString()))
        statsPanel.addView(createStatRow("Modelos únicos", uniqueCards.toString()))
        statsPanel.addView(createStatRow("Último escaneo", "+$lastScanCount"))
        statsPanel.addView(createStatRow("No encontradas", notFoundCount.toString(), Color.RED))
        
        val closeBtn = Button(this).apply {
            text = "CERRAR"
            setTextColor(colorMidnight)
            setBackgroundColor(colorTextGold)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 40
            }
            setOnClickListener { toggleStats() }
        }
        statsPanel.addView(closeBtn)
    }

    override fun onBackPressed() {
        if (isStatsOpen) {
            toggleStats()
        } else if (isDeckViewMode && collectionVisibility.values.any { it }) {
            // Si estamos en modo Deck y hay alguna colección expandida, la cerramos todas (volver atrás)
            collectionVisibility.clear()
            refreshList()
        } else {
            super.onBackPressed()
        }
    }

    private fun createStatRow(label: String, value: String, valueColor: Int = colorTextGold): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 15, 0, 15)
            val lbl = TextView(this@MainActivity).apply {
                text = label
                setTextColor(colorTextLight)
                textSize = 16f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val valTxt = TextView(this@MainActivity).apply {
                text = value
                setTextColor(valueColor)
                textSize = 18f
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            addView(lbl)
            addView(valTxt)
        }
    }

    private fun openScanner() {
        if (isStatsOpen) toggleStats()
        startActivityForResult(Intent(this, ScannerActivity::class.java), 100)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == Activity.RESULT_OK) {
            val list = data?.getStringArrayListExtra("CARDS") ?: return
            lastScanCount = list.size
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
                updateCachesAndRefresh()
            }
        }
    }

    private fun refreshList() {
        listContainer.removeAllViews()
        val grouped = cardMap.entries.groupBy { getBaseCode(it.key) }

        // El icono de escanear SIEMPRE se muestra en la lista de cartas de una baraja (Modo Carátulas)
        // Solo se oculta cuando el visor de imagen (overlay) está abierto (gestionado en showImageOverlay)
        fabScanner.visibility = View.VISIBLE

        if (isDeckViewMode && selectedCollection == null) {
            renderDeckGallery(grouped)
        } else if (isDeckViewMode && selectedCollection != null) {
            renderSingleDeckDetails(selectedCollection!!, grouped[selectedCollection] ?: emptyList())
        } else {
            renderDetailedFullList(grouped)
        }
    }

    private fun renderDeckGallery(grouped: Map<String, List<Map.Entry<String, Int>>>) {
        val gridLayout = GridLayout(this).apply {
            columnCount = 1
            setPadding(40, 20, 40, 20)
            alignmentMode = GridLayout.ALIGN_BOUNDS
        }
        
        val normalizedQuery = searchQuery.normalize()
        val filteredGrouped = if (searchQuery.isEmpty()) grouped 
        else grouped.filter { (collection, _) -> 
            val name = deckNamesCache[collection] ?: ""
            collection.normalize().contains(normalizedQuery) || name.normalize().contains(normalizedQuery)
        }

        filteredGrouped.toSortedMap().forEach { (collection, _) ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(30, 40, 30, 40)
                val shape = GradientDrawable().apply {
                    setColor(colorHeaderBg)
                    cornerRadius = 40f
                    setStroke(2, colorTextGold)
                }
                background = shape
                val params = GridLayout.LayoutParams().apply {
                    width = resources.displayMetrics.widthPixels - 80
                    setMargins(0, 30, 0, 30)
                }
                layoutParams = params
                setOnClickListener {
                    selectedCollection = collection
                    refreshList()
                }
            }

            val coverImage = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 800)
                scaleType = ImageView.ScaleType.FIT_CENTER
                
                val resName = collection.lowercase().replace("-", "_")
                val resId = resources.getIdentifier(resName, "drawable", packageName)
                if (resId != 0) setImageResource(resId) else setImageResource(android.R.drawable.ic_menu_gallery)
            }

            val deckNameText = TextView(this).apply {
                text = "Cargando..."
                textSize = 18f
                setTextColor(Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(20, 25, 20, 5)
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            }

            val deckCodeText = TextView(this).apply {
                text = collection
                textSize = 14f
                setTextColor(colorTextGold)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 10)
            }

            item.addView(coverImage)
            item.addView(deckNameText)
            item.addView(deckCodeText)
            gridLayout.addView(item)

            lifecycleScope.launch {
                val name = withContext(Dispatchers.IO) {
                    AppDatabase.getDatabase(this@MainActivity).deckDao().getDeckName(collection)
                }
                deckNameText.text = name ?: "Colección"
            }
        }
        listContainer.addView(gridLayout)
    }

    private fun renderSingleDeckDetails(collection: String, cards: List<Map.Entry<String, Int>>) {
        // Botón Volver
        val backBtn = Button(this).apply {
            text = "← VOLVER A GALERÍA"
            setTextColor(colorTextGold)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener {
                selectedCollection = null
                refreshList()
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(20, 20, 0, 0)
            }
        }
        listContainer.addView(backBtn)

        // Título de la baraja: Centrado y formato "Código: Título"
        val headerText = TextView(this).apply {
            text = collection // Valor por defecto mientras carga
            textSize = 22f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(40, 20, 40, 30)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        listContainer.addView(headerText)

        lifecycleScope.launch {
            val name = withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(this@MainActivity).deckDao().getDeckName(collection)
            }
            if (name != null) {
                headerText.text = "$collection: $name"
            } else {
                headerText.text = collection
            }
        }

        val gridLayout = GridLayout(this).apply {
            columnCount = 2
            setPadding(20, 20, 20, 20)
        }

        val normalizedQuery = searchQuery.normalize()
        val filteredCards = if (searchQuery.isEmpty()) cards 
        else cards.filter { 
            val name = cardNamesCache[it.key] ?: ""
            it.key.normalize().contains(normalizedQuery) || name.normalize().contains(normalizedQuery)
        }

        val sortedCards = filteredCards.sortedBy { extractNumber(it.key) }
        val allCodes = sortedCards.map { it.key }
        
        sortedCards.forEachIndexed { index, entry ->
            gridLayout.addView(createCardItem(entry.key, entry.value, 2, allCodes, index))
        }
        listContainer.addView(gridLayout)
    }

    private fun renderDetailedFullList(grouped: Map<String, List<Map.Entry<String, Int>>>) {
        val normalizedQuery = searchQuery.normalize()
        val filteredGrouped = if (searchQuery.isEmpty()) grouped
        else {
            grouped.mapValues { (_, cards) ->
                cards.filter { 
                    val name = cardNamesCache[it.key] ?: ""
                    it.key.normalize().contains(normalizedQuery) || name.normalize().contains(normalizedQuery)
                }
            }.filterValues { it.isNotEmpty() }
        }

        filteredGrouped.toSortedMap().forEach { (collection, cards) ->
            val isExpanded = collectionVisibility[collection] ?: true
            
            val headerContainer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(30, 25, 30, 25)
                val shape = GradientDrawable().apply {
                    setColor(colorHeaderBg)
                    cornerRadius = 20f
                    setStroke(2, colorTextGold)
                }
                background = shape
                val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                params.setMargins(20, 40, 20, 10)
                layoutParams = params
                setOnClickListener {
                    collectionVisibility[collection] = !isExpanded
                    refreshList()
                }
            }

            val headerText = TextView(this).apply {
                text = collection
                textSize = 16f
                setTextColor(colorTextLight)
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val toggleBtn = Button(this).apply {
                text = if (isExpanded) "OCULTAR" else "MOSTRAR"
                setTextColor(colorTextGold)
                setBackgroundColor(Color.TRANSPARENT)
                textSize = 11f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setOnClickListener {
                    collectionVisibility[collection] = !isExpanded
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

            if (isExpanded) {
                val gridLayout = GridLayout(this).apply {
                    columnCount = 2
                    setPadding(20, 15, 20, 15)
                }
                val sortedCards = cards.sortedBy { extractNumber(it.key) }
                val allCodes = sortedCards.map { it.key }
                
                sortedCards.forEachIndexed { index, entry ->
                    gridLayout.addView(createCardItem(entry.key, entry.value, 2, allCodes, index))
                }
                listContainer.addView(gridLayout)
            }
        }
        
        // SECCIÓN NO ENCONTRADAS
        if (notFoundCards.isNotEmpty()) {
            val sep = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 3).apply { setMargins(40, 60, 40, 20) }
                setBackgroundColor(Color.parseColor("#F44336"))
            }
            listContainer.addView(sep)
            val errorHeader = TextView(this).apply {
                text = "CÓDIGOS NO RECONOCIDOS"
                textSize = 16f; setTextColor(Color.parseColor("#F44336")); setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(20, 20, 20, 10); gravity = Gravity.CENTER_HORIZONTAL
            }
            listContainer.addView(errorHeader)
            notFoundCards.forEach { (code, count) -> listContainer.addView(createNotFoundRow(code, count)) }
        }
    }

    private fun createCardItem(code: String, count: Int, columns: Int = 3, allCodes: List<String> = emptyList(), initialIndex: Int = 0): LinearLayout {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(10, 10, 10, 10)
            layoutParams = GridLayout.LayoutParams().apply { width = resources.displayMetrics.widthPixels / columns - 40 }
        }
        val imgContainer = FrameLayout(this)
        val imageView = ImageView(this).apply {
            val imgHeight = if (columns == 2) 450 else 300
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, imgHeight)
            scaleType = ImageView.ScaleType.FIT_CENTER; setBackgroundColor(colorHeaderBg)
        }
        val deleteBtn = Button(this).apply {
            text = "X"; textSize = 12f; setTextColor(Color.WHITE)
            val shape = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#CCF44336")) }
            background = shape; layoutParams = FrameLayout.LayoutParams(60, 60, Gravity.TOP or Gravity.END)
            setOnClickListener {
                val current = cardMap[code] ?: return@setOnClickListener
                if (current > 1) cardMap[code] = current - 1 else cardMap.remove(code)
                saveDataToDisk(); refreshList()
            }
        }
        imgContainer.addView(imageView); imgContainer.addView(deleteBtn)
        val textCode = TextView(this).apply {
            text = if (count > 1) "$code x$count" else code
            textSize = 11f; setTypeface(null, android.graphics.Typeface.BOLD); gravity = Gravity.CENTER; setTextColor(colorTextLight)
        }
        val textName = TextView(this).apply {
            textSize = 9f; gravity = Gravity.CENTER; setTextColor(colorTextDim); maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
        }
        item.addView(imgContainer); item.addView(textCode); item.addView(textName)
        lifecycleScope.launch {
            val cardName = withContext(Dispatchers.IO) { AppDatabase.getDatabase(this@MainActivity).deckDao().getCardName(code) }
            if (cardName != null) {
                textName.text = cardName
                val imageUrl = withContext(Dispatchers.IO) { AppDatabase.getDatabase(this@MainActivity).deckDao().getCardImageUrl(cardName) }
                if (!imageUrl.isNullOrEmpty()) {
                    Glide.with(this@MainActivity).load(imageUrl).into(imageView)
                    imageView.setOnClickListener { 
                        if (allCodes.isNotEmpty()) showImageOverlay(allCodes, initialIndex)
                        else showImageOverlay(listOf(code), 0)
                    }
                }
            }
        }
        return item
    }

    private fun createNotFoundRow(code: String, count: Int): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(40, 10, 20, 10); gravity = Gravity.CENTER_VERTICAL }
        val textView = TextView(this).apply {
            text = if (count > 1) "$code x$count" else code
            textSize = 14f; setTextColor(colorTextDim); layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val deleteButton = Button(this).apply {
            text = "X"; textSize = 14f; setTextColor(Color.parseColor("#F44336")); setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener {
                val current = notFoundCards[code] ?: return@setOnClickListener
                if (current > 1) notFoundCards[code] = current - 1 else notFoundCards.remove(code)
                saveDataToDisk(); refreshList()
            }
        }
        row.addView(textView); row.addView(deleteButton)
        return row
    }

    private fun showImageOverlay(codes: List<String>, initialIndex: Int) {
        var currentIndex = initialIndex
        
        // OCULTAR el icono de cámara al abrir el visor
        fabScanner.visibility = View.GONE

        // 1. CAPA BASE: Oscurece el fondo y captura el click para CERRAR
        val overlay = FrameLayout(this).apply { 
            setBackgroundColor(Color.parseColor("#F2000000")) 
            isClickable = true
            isFocusable = true
            setOnClickListener { 
                mainRoot.removeView(this)
                refreshList() // Restaurar visibilidad del FAB según el estado de la lista
            }
        }

        // 2. CONTENEDOR DE LA CARTA: Ocupa el centro y NO deja pasar el click al fondo
        val cardBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ).apply { 
                setMargins(80, 0, 80, 100) // Un poco más de margen inferior para el sheet
            }
            setOnClickListener { /* No hacer nada */ }
        }

        val infoText = TextView(this).apply {
            textSize = 20f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(20, 0, 20, 40)
        }

        val imageView = ImageView(this).apply {
            val displayHeight = resources.displayMetrics.heightPixels
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                (displayHeight * 0.55).toInt() // Bajamos un poco el tamaño para dejar sitio
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
            isClickable = true
        }

        // --- 🔽 BOTTOM SHEET DE COLECCIONES ---
        val sheetContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val shape = GradientDrawable().apply {
                setColor(colorHeaderBg)
                cornerRadii = floatArrayOf(60f, 60f, 60f, 60f, 0f, 0f, 0f, 0f) // Solo arriba
                setStroke(3, colorTextGold)
            }
            background = shape
            setPadding(40, 20, 40, 150) // Aumentamos padding inferior para cubrir hasta el borde
            elevation = 50f
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
            
            // Forzar que el sheet asome un poco (el "peek")
            post {
                val peekHeight = 300f 
                translationY = height - peekHeight
            }

            var isExpanded = false
            setOnClickListener { 
                isExpanded = !isExpanded
                val targetY = if (isExpanded) 0f else (height - 300f)
                animate().translationY(targetY).setDuration(400).start()
            }
        }

        val handle = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(120, 10).apply { 
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = 30
            }
            background = GradientDrawable().apply { 
                setColor(Color.parseColor("#44CCD6F6"))
                cornerRadius = 10f 
            }
        }

        val sheetTitle = TextView(this).apply {
            text = "MÁS INFORMACIÓN SOBRE LA CARTA"
            textSize = 12f
            setTextColor(colorTextGold)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 10)
            gravity = Gravity.CENTER
        }

        val priceInfoText = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 30)
            gravity = Gravity.CENTER
        }

        val matchesTitle = TextView(this).apply {
            text = "ENCUENTRA ESTA CARTA TAMBIÉN EN:"
            textSize = 11f
            setTextColor(colorTextDim)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 20, 0, 20)
            gravity = Gravity.CENTER
            visibility = View.GONE
        }

        val horizontalScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            visibility = View.GONE
        }
        val collectionsList = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        horizontalScroll.addView(collectionsList)

        sheetContainer.addView(handle)
        sheetContainer.addView(sheetTitle)
        sheetContainer.addView(priceInfoText)
        sheetContainer.addView(matchesTitle)
        sheetContainer.addView(horizontalScroll)

        fun updateCollectionsForCard(cardCode: String) {
            collectionsList.removeAllViews()
            lifecycleScope.launch {
                val db = AppDatabase.getDatabase(this@MainActivity).deckDao()
                val nameEs = withContext(Dispatchers.IO) { db.getCardName(cardCode) } ?: return@launch
                val nameEn = withContext(Dispatchers.IO) { db.getCardNameEn(nameEs) }
                
                // 💰 Actualizar Precio en el Sheet
                val price = if (nameEn != null) fetchCardPrice(cardCode, nameEn) else "N/A"
                priceInfoText.text = if (price.contains(cardCode)) price else "Precio ($cardCode): $price"

                // 🔍 Buscar en qué otras colecciones del usuario aparece esta misma carta (por nombre)
                val matches = mutableSetOf<String>()
                cardMap.keys.forEach { code ->
                    val name = withContext(Dispatchers.IO) { db.getCardName(code) }
                    if (name == nameEs) {
                        matches.add(getBaseCode(code))
                    }
                }
                
                // Quitar la colección actual
                matches.remove(getBaseCode(cardCode))

                if (matches.isEmpty()) {
                    matchesTitle.visibility = View.GONE
                    horizontalScroll.visibility = View.GONE
                } else {
                    matchesTitle.visibility = View.VISIBLE
                    horizontalScroll.visibility = View.VISIBLE
                    matches.forEach { col ->
                        val item = LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            gravity = Gravity.CENTER
                            setPadding(20, 0, 20, 0)
                        }
                        val img = ImageView(this@MainActivity).apply {
                            layoutParams = LinearLayout.LayoutParams(120, 160)
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            val resName = col.lowercase().replace("-", "_")
                            val resId = resources.getIdentifier(resName, "drawable", packageName)
                            if (resId != 0) setImageResource(resId) else setImageResource(android.R.drawable.ic_menu_gallery)
                        }
                        val txt = TextView(this@MainActivity).apply {
                            text = col
                            textSize = 10f
                            setTextColor(Color.WHITE)
                            gravity = Gravity.CENTER
                        }
                        item.addView(img); item.addView(txt)
                        collectionsList.addView(item)
                    }
                }
            }
        }
        // --- 🔼 FIN BOTTOM SHEET ---

        fun updateUI() {
            val code = codes[currentIndex]
            updateCollectionsForCard(code) // Actualizar el sheet (ahora incluye precio)
            
            imageView.animate().alpha(0f).setDuration(100).withEndAction {
                lifecycleScope.launch {
                    val db = AppDatabase.getDatabase(this@MainActivity).deckDao()
                    val nameEs = withContext(Dispatchers.IO) { db.getCardName(code) }
                    
                    val url = withContext(Dispatchers.IO) { if (nameEs != null) db.getCardImageUrl(nameEs) else null }
                    
                    infoText.text = if (nameEs != null) "$code: $nameEs" else code
                    
                    if (!url.isNullOrEmpty()) {
                        Glide.with(this@MainActivity).load(url).into(imageView)
                        imageView.animate().alpha(1f).setDuration(200).start()
                    } else {
                        imageView.setImageResource(android.R.drawable.ic_menu_gallery)
                        imageView.animate().alpha(1f).setDuration(200).start()
                    }
                }
            }.start()
        }

        // Swipe handling
        var startX = 0f
        imageView.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    val endX = event.x
                    val deltaX = startX - endX
                    if (Math.abs(deltaX) > 100) {
                        if (deltaX > 0 && currentIndex < codes.size - 1) {
                            currentIndex++; updateUI()
                        } else if (deltaX < 0 && currentIndex > 0) {
                            currentIndex--; updateUI()
                        }
                    } else {
                        v.performClick()
                    }
                    true
                }
                else -> false
            }
        }

        // 3. BOTONES DE NAVEGACIÓN (Fuera del cardBox para no estorbar el swipe)
        val prevBtn = ImageButton(this).apply {
            setImageResource(R.drawable.ic_chevron_left_custom); setBackgroundColor(Color.TRANSPARENT); setColorFilter(Color.WHITE)
            layoutParams = FrameLayout.LayoutParams(180, 300, Gravity.CENTER_VERTICAL or Gravity.START)
            setOnClickListener { if (currentIndex > 0) { currentIndex--; updateUI() } }
        }

        val nextBtn = ImageButton(this).apply {
            setImageResource(R.drawable.ic_chevron_right_custom); setBackgroundColor(Color.TRANSPARENT); setColorFilter(Color.WHITE)
            layoutParams = FrameLayout.LayoutParams(180, 300, Gravity.CENTER_VERTICAL or Gravity.END)
            setOnClickListener { if (currentIndex < codes.size - 1) { currentIndex++; updateUI() } }
        }

        // Montamos la jerarquía final
        cardBox.addView(infoText)
        cardBox.addView(imageView)
        
        overlay.addView(cardBox)
        overlay.addView(sheetContainer) // Añadimos el sheet
        overlay.addView(prevBtn)
        overlay.addView(nextBtn)
        
        updateUI()
        mainRoot.addView(overlay)
    }
    private fun enterSearchMode() {
        isSearchMode = true
        toolbarTitle.visibility = View.GONE
        searchInput.visibility = View.VISIBLE
        searchInput.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
        searchBtn.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
    }

    private fun exitSearchMode() {
        isSearchMode = false
        toolbarTitle.visibility = View.VISIBLE
        searchInput.visibility = View.GONE
        searchInput.text.clear()
        searchQuery = ""
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchInput.windowToken, 0)
        searchBtn.setImageResource(R.drawable.ic_search)
        refreshList()
    }

    private fun saveDataToDisk() {
        val prefs = getSharedPreferences("CardData", Context.MODE_PRIVATE)
        val gson = Gson()
        prefs.edit {
            putString("cardMap", gson.toJson(cardMap))
            putString("notFoundCards", gson.toJson(notFoundCards))
            putBoolean("isDeckViewMode", isDeckViewMode) // Guardar preferencia
        }
    }

    private fun loadPersistedData() {
        val prefs = getSharedPreferences("CardData", Context.MODE_PRIVATE)
        val gson = Gson()
        val type = object : TypeToken<MutableMap<String, Int>>() {}.type
        val jsonFound = prefs.getString("cardMap", null)
        if (jsonFound != null) cardMap = gson.fromJson(jsonFound, type)
        val jsonNotFound = prefs.getString("notFoundCards", null)
        if (jsonNotFound != null) notFoundCards = gson.fromJson(jsonNotFound, type)
        isDeckViewMode = prefs.getBoolean("isDeckViewMode", false)
    }

    private fun getBaseCode(code: String): String {
        val regex = Regex("([A-Z0-9]{3,4}-[A-Z]{1,2})")
        return regex.find(code)?.value ?: code
    }

    private fun extractNumber(code: String): Int {
        val regex = Regex("(\\d{3})$")
        return regex.find(code)?.value?.toIntOrNull() ?: Int.MAX_VALUE
    }

    private suspend fun fetchCardPrice(cardCode: String, cardNameEn: String): String {
        val cacheKey = "${cardNameEn}_$cardCode"
        if (priceCache.containsKey(cacheKey)) return priceCache[cacheKey]!!
        
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://db.ygoprodeck.com/api/v7/cardinfo.php?name=${URLEncoder.encode(cardNameEn, "UTF-8")}")
                val connection = url.openConnection() as java.net.HttpURLConnection
                val response = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                val cardData = json.getJSONArray("data").getJSONObject(0)
                
                // Buscamos específicamente el set que coincida con nuestro código
                val cardSets = cardData.optJSONArray("card_sets")
                var versionPrice = "N/A"
                
                if (cardSets != null) {
                    for (i in 0 until cardSets.length()) {
                        val set = cardSets.getJSONObject(i)
                        val setCode = set.getString("set_code")
                        if (setCode.equals(cardCode, ignoreCase = true)) {
                            // En YGOProDECK, a veces el precio de cardmarket está dentro del set info
                            // Si no, recurrimos al precio general del set
                            versionPrice = set.optString("set_price", "N/A")
                            if (versionPrice != "N/A") versionPrice += " €"
                            break
                        }
                    }
                }
                
                // Si no encontramos el precio específico del set, usamos el de cardmarket general como fallback
                if (versionPrice == "N/A") {
                    val prices = cardData.getJSONArray("card_prices").getJSONObject(0)
                    versionPrice = prices.getString("cardmarket_price") + " €"
                }

                priceCache[cacheKey] = versionPrice
                versionPrice
            } catch (e: Exception) {
                "N/A"
            }
        }
    }

    private fun String.normalize(): String {
        return Normalizer.normalize(this, Normalizer.Form.NFD)
            .replace(Regex("[\\u0300-\\u036f]"), "")
            .lowercase()
    }
}
