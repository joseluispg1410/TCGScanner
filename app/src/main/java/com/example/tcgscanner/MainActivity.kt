package com.example.tcgscanner

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
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
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    private lateinit var scrollView: ScrollView
    private var isStatsOpen = false

    // 📈 PAGINACIÓN
    private var decksLoadedCount = 0
    private val PAGE_SIZE = 15

    // 💰 PRECIOS
    private val priceCache = mutableMapOf<String, String>()

    // Colores del tema Midnight Blue
    private val colorMidnight = Color.parseColor("#0A192F")
    private val colorLightMidnight = Color.parseColor("#112240")
    private val colorBorder = Color.parseColor("#233554")
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
    private var showOnlyOwnedDecks = true
    private var showProgress = true

    // 🔍 ESTADO DE BÚSQUEDA Y CACHÉ
    private var isSearchMode = false
    private var searchQuery = ""
    private var tcgId: Int = 1
    private var tcgName: String = "TCG SCANNER"
    private var tcgCode: String = "YGO"
    
    private val deckNamesCache = mutableMapOf<String, String>()
    private val cardNamesCache = mutableMapOf<String, String>()
    private val deckTotalsCache = mutableMapOf<String, Int>()
    
    // --- NUEVAS CACHÉS PARA MODO SIN SQLITE ---
    private var allDecksCache: List<Deck>? = null
    private var allCardDetailsCache: List<CardDetail>? = null
    private var allCardImagesCache: List<CardImage>? = null
    
    private val customCoversCache = mutableMapOf<String, List<String>>()
    private val globalCoversCache = mutableMapOf<String, List<String>>()
    private val resIdCache = mutableMapOf<String, Int>()
    private var pendingDeckCode: String? = null

    // 🔄 ESTADO DE IMPORTACIÓN WIKI
    private val activeImports = mutableMapOf<String, String>() // deckCode -> Status
    private var importJob: kotlinx.coroutines.Job? = null

    // 🎯 MODO SELECCIÓN
    private var isSelectionMode = false
    private val selectedItems = mutableSetOf<String>()
    private lateinit var toolbarContent: FrameLayout
    private lateinit var searchLayout: LinearLayout
    private lateinit var selectionLayout: LinearLayout
    private lateinit var selectionCountText: TextView

    companion object {
        var allCardIdsCache: Set<String>? = null
    }

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

        // 🛠️ TOOLBAR MINIMALISTA
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(30, 20, 30, 20)
            setBackgroundColor(colorMidnight)
            elevation = 0f
            
            // Línea inferior sutil
            val bottomBorder = View(this@MainActivity).apply {
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2).apply {
                    gravity = Gravity.BOTTOM
                }
                setBackgroundColor(colorBorder)
            }
            
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        toolbarContent = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        // --- 🔍 LAYOUT BÚSQUEDA / TÍTULO ---
        searchLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            
            searchBtn = ImageButton(this@MainActivity).apply {
                setImageResource(R.drawable.ic_search_modern)
                setBackgroundColor(Color.TRANSPARENT)
                setPadding(25, 25, 25, 25)
                setColorFilter(colorTextLight)
                setOnClickListener {
                    if (!isSearchMode) enterSearchMode() else exitSearchMode()
                }
            }

            tcgId = intent.getIntExtra("TCG_ID", 1)
            tcgName = intent.getStringExtra("TCG_NAME") ?: "TCG SCANNER"
            tcgCode = intent.getStringExtra("TCG_CODE") ?: "YGO"

            toolbarTitle = TextView(this@MainActivity).apply {
                text = tcgName.uppercase()
                textSize = 15f
                setTextColor(colorTextLight)
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                letterSpacing = 0.1f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            searchInput = EditText(this@MainActivity).apply {
                hint = "Buscar..."
                textSize = 15f
                setTextColor(Color.WHITE)
                setHintTextColor(colorTextDim)
                background = null
                visibility = View.GONE
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        searchQuery = s.toString(); refreshList()
                    }
                    override fun afterTextChanged(s: Editable?) {}
                })
            }

            val menuBtn = ImageButton(this@MainActivity).apply {
                setImageResource(R.drawable.ic_menu_modern)
                setBackgroundColor(Color.TRANSPARENT)
                setPadding(25, 25, 25, 25)
                setColorFilter(colorTextLight)
                setOnClickListener { view -> showPopupMenu(view) }
            }

            addView(searchBtn)
            addView(toolbarTitle)
            addView(searchInput)
            addView(menuBtn)
        }

        // --- 🎯 LAYOUT SELECCIÓN ---
        selectionLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE

            val cancelBtn = ImageButton(this@MainActivity).apply {
                setImageResource(R.drawable.ic_close_modern)
                setBackgroundColor(Color.TRANSPARENT)
                setPadding(25, 25, 25, 25)
                setColorFilter(colorTextLight)
                setOnClickListener { exitSelectionMode() }
            }

            selectionCountText = TextView(this@MainActivity).apply {
                text = "0 seleccionados"
                textSize = 15f
                setTextColor(colorTextGold)
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val deleteBatchBtn = ImageButton(this@MainActivity).apply {
                setImageResource(R.drawable.ic_delete_modern)
                setBackgroundColor(Color.TRANSPARENT)
                setPadding(25, 25, 25, 25)
                setColorFilter(Color.RED)
                setOnClickListener { deleteSelectedItems() }
            }

            addView(cancelBtn)
            addView(selectionCountText)
            addView(deleteBatchBtn)
        }

        toolbarContent.addView(searchLayout)
        toolbarContent.addView(selectionLayout)
        toolbar.addView(toolbarContent)

        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(0, 0, 0, 300)
        }

        scrollView = ScrollView(this).apply {
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

        // 📊 PANEL DE ESTADÍSTICAS MINIMALISTA
        statsPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 60, 60, 60)
            visibility = View.GONE
            
            val shape = GradientDrawable().apply {
                setColor(colorLightMidnight)
                cornerRadius = 30f
                setStroke(1, colorBorder)
            }
            background = shape
            
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            ).apply { 
                topMargin = 200 
                leftMargin = 50
                rightMargin = 50
            }
            elevation = 20f
        }

        // 🔘 BOTÓN FLOTANTE (FAB) MINIMALISTA
        fabScanner = ImageButton(this).apply {
            setImageResource(R.drawable.ic_camera_modern) 
            val size = 150
            layoutParams = FrameLayout.LayoutParams(size, size, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                bottomMargin = 60
            }
            val shape = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(colorTextGold)
            }
            background = shape
            setColorFilter(colorMidnight)
            setPadding(35, 35, 35, 35)
            elevation = 10f
            setOnClickListener { openScanner() }
        }

        mainRoot.addView(contentLayout)
        mainRoot.addView(statsPanel)
        mainRoot.addView(fabScanner)
        setContentView(mainRoot)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isSelectionMode) {
                    exitSelectionMode()
                } else if (isSearchMode) {
                    exitSearchMode()
                } else if (isStatsOpen) {
                    toggleStats()
                } else if (selectedCollection != null) {
                    selectedCollection = null
                    refreshList()
                } else if (isDeckViewMode && collectionVisibility.values.any { it }) {
                    collectionVisibility.clear()
                    refreshList()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        loadPersistedData()
        updateFullCatalog(showToast = false) // Sincronización automática al arrancar

        // 🚀 PRE-CARGA PRO: Cargar IDs y calentar el motor de IA antes de que se abra el scanner
        lifecycleScope.launch(Dispatchers.IO) {
            // El catálogo se carga en memoria dentro de updateFullCatalog
            // Esperamos un poco a que el catálogo esté listo antes de generar los IDs para el scanner
            while (allCardDetailsCache == null) {
                delay(500)
            }
            
            val details = allCardDetailsCache ?: emptyList()
            val mappings = allDecksCache ?: emptyList()
            
            val translatedIds = mutableSetOf<String>()
            
            details.forEach { detail ->
                val id = detail.deckCardId ?: return@forEach
                translatedIds.add(id)
                val currentBase = getBaseCode(id)
                val deck = mappings.find { it.codigoDeckSp == currentBase || it.codigoDeckEn == currentBase }
                
                // Si la carta es EN, generar el equivalente SP para el scanner
                if (deck?.codigoDeckEn == currentBase && deck.codigoDeckSp != null) {
                    translatedIds.add(id.replace(currentBase, deck.codigoDeckSp!!))
                }
                // Si la carta es SP, generar el equivalente EN para el scanner
                if (deck?.codigoDeckSp == currentBase && deck.codigoDeckEn != null) {
                    translatedIds.add(id.replace(currentBase, deck.codigoDeckEn!!))
                }
            }
            
            allCardIdsCache = translatedIds
            CardAnalyzer.warmUp() // 🔥 Calentamiento Pro
            Log.d("CACHE", "Scanner listo con ${translatedIds.size} códigos (reales + traducidos)")
        }
    }

    private fun updateCachesAndRefresh() {
        lifecycleScope.launch {
            val userPrefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
            val userId = userPrefs.getInt("user_id", -1)

            // 1. CARGAR CONFIGURACIONES DESDE EL SERVIDOR (Sustituye a las tablas de personalización locales)
            if (userId != -1) {
                try {
                    // Aquí podrías añadir endpoints para traer todas las carátulas de golpe si quieres optimizar,
                    // por ahora las limpiaremos para forzar la carga individual desde la API al mostrar cada deck.
                    customCoversCache.clear()
                    globalCoversCache.clear()
                } catch (e: Exception) {
                    Log.e("SYNC", "Error cargando personalizaciones: ${e.message}")
                }
            }

            // 2. ACTUALIZAR TOTALES POR DECK (Basado en el catálogo en memoria)
            val allDecks = allDecksCache ?: emptyList()
            val allDetails = allCardDetailsCache ?: emptyList()
            
            allDecks.forEach { deck ->
                val code = deck.codigoDeckSp ?: ""
                val count = allDetails.count { it.codigoDeckSp == code }
                deckTotalsCache[code] = count
            }

            refreshList()
        }
    }

    private fun showPopupMenu(view: View) {
        val popup = PopupMenu(this, view)
        popup.menu.add("Estadísticas")
        popup.menu.add(if (isDeckViewMode) "Vista Detallada" else "Vista de Carátulas")
        
        val progressItem = popup.menu.add("Mostrar Progreso")
        progressItem.isCheckable = true
        progressItem.isChecked = showProgress
        
        popup.menu.add("Descargar de la nube")
        popup.menu.add("Subir a la nube")
        popup.menu.add("Exportar colección (CSV)")
        popup.menu.add("Cerrar sesión")
        
        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "Estadísticas" -> toggleStats()
                "Vista Detallada", "Vista de Carátulas" -> {
                    isDeckViewMode = !isDeckViewMode
                    saveDataToDisk() // Guardar preferencia de vista
                    refreshList()
                }
                "Mostrar Progreso" -> {
                    showProgress = !showProgress
                    saveDataToDisk()
                    refreshList()
                }
                "Descargar de la nube" -> pullFromCloud(showToast = true)
                "Subir a la nube" -> pushToCloud(showToast = true)
                "Exportar colección (CSV)" -> exportCollectionToCSV()
                "Cerrar sesión" -> logout()
            }
            true
        }
        popup.show()
    }

    private fun logout() {
        getSharedPreferences("UserPrefs", MODE_PRIVATE).edit {
            clear()
        }
        cardMap.clear()
        notFoundCards.clear()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
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

    // Eliminamos onBackPressed override ya que usamos OnBackPressedDispatcher en onCreate

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
                    val cardName = cardNamesCache[code]
                    
                    if (cardName != null) {
                        cardMap[code] = (cardMap[code] ?: 0) + 1
                    } else {
                        // Si no encontramos la carta específica, verificamos si al menos el SET existe
                        val setPart = getBaseCode(code)
                        val isSetValid = deckNamesCache.containsKey(setPart)
                        
                        if (isSetValid) {
                            // Es una carta de un set conocido, aunque no tengamos su nombre aún
                            cardMap[code] = (cardMap[code] ?: 0) + 1
                        } else {
                            notFoundCards[code] = (notFoundCards[code] ?: 0) + 1
                        }
                    }
                }
                saveDataToDisk()
                updateCachesAndRefresh()
            }
        } else if (requestCode == 200 && resultCode == Activity.RESULT_OK) {
            val deckCode = pendingDeckCode ?: return
            val uris = mutableListOf<android.net.Uri>()
            
            data?.clipData?.let { clipData ->
                for (i in 0 until clipData.itemCount) {
                    uris.add(clipData.getItemAt(i).uri)
                }
            } ?: data?.data?.let { uris.add(it) }

            if (uris.isNotEmpty()) {
                saveCustomCovers(deckCode, uris)
            }
        }
    }

    private fun pickCustomCover(deckCode: String) {
        pendingDeckCode = deckCode
        val intent = Intent(Intent.ACTION_PICK).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(intent, 200)
    }

    private fun importDeckWiki(deckCode: String) {
        lifecycleScope.launch {
            try {
                Toast.makeText(this@MainActivity, "Iniciando importación...", Toast.LENGTH_SHORT).show()
                val response = RetrofitClient.instance.importDeckFromWiki(deckCode)
                if (response.isSuccessful) {
                    activeImports[deckCode] = "PROCESSING"
                    startPollingImportStatus(deckCode)
                    refreshList() // Para que aparezca la barra en los detalles si estamos ahí
                } else {
                    Toast.makeText(this@MainActivity, "Error al solicitar importación", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error de red: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showEditDeckCodeDialog(oldCode: String) {
        val input = EditText(this).apply {
            setText(oldCode)
            setPadding(50, 40, 50, 40)
        }
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Editar código de baraja")
            .setMessage("Cambiar el código '$oldCode' por:")
            .setView(input)
            .setPositiveButton("ACTUALIZAR") { _, _ ->
                val newCode = input.text.toString().trim()
                if (newCode.isNotEmpty() && newCode != oldCode) {
                    updateDeckCodeOnServer(oldCode, newCode)
                }
            }
            .setNegativeButton("CANCELAR", null)
            .show()
    }

    private fun updateDeckCodeOnServer(oldCode: String, newCode: String) {
        lifecycleScope.launch {
            try {
                Toast.makeText(this@MainActivity, "Actualizando en el servidor...", Toast.LENGTH_SHORT).show()
                val response = RetrofitClient.instance.updateDeckCode(UpdateDeckCodeRequest(oldCode, newCode))
                
                if (response.isSuccessful) {
                    // Actualizar también nuestra colección local si tiene cartas de este deck
                    val newCardMap = mutableMapOf<String, Int>()
                    cardMap.forEach { (code, count) ->
                        if (getBaseCode(code) == oldCode) {
                            val newFullCode = code.replace(oldCode, newCode)
                            newCardMap[newFullCode] = count
                        } else {
                            newCardMap[code] = count
                        }
                    }
                    cardMap = newCardMap
                    
                    selectedCollection = newCode
                    allDecksCache = null // Forzar recarga
                    saveDataToDisk()
                    updateFullCatalog(showToast = true)
                    Toast.makeText(this@MainActivity, "Código actualizado correctamente", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "Error en el servidor: ${response.message()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error de red: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startPollingImportStatus(deckCode: String) {
        importJob?.cancel()
        importJob = lifecycleScope.launch {
            while (activeImports[deckCode] == "PROCESSING") {
                kotlinx.coroutines.delay(3000) // Poll cada 3 seg
                try {
                    val response = RetrofitClient.instance.getImportStatus(deckCode)
                    if (response.isSuccessful) {
                        val status = response.body()?.status ?: "IDLE"
                        activeImports[deckCode] = status
                        if (status == "COMPLETED") {
                            activeImports.remove(deckCode)
                            updateFullCatalog(showToast = false) // Sincronizar automáticamente tras importación
                            break
                        } else if (status == "ERROR") {
                            withContext(Dispatchers.Main) { refreshList() }
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.e("WIKI", "Error polling status: ${e.message}")
                }
            }
        }
    }

    private fun saveCustomCovers(deckCode: String, uris: List<android.net.Uri>) {
        val userPrefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val userId = userPrefs.getInt("user_id", -1)
        val userEmail = userPrefs.getString("user_email", "")
        if (userId == -1) return

        val isAdmin = userEmail == "admin"
        val targetId = if (isAdmin) 0 else userId

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Ahora guardamos las imágenes en el servidor Postgres
                val newPaths = mutableListOf<String>()

                uris.forEachIndexed { index, uri ->
                    val inputStream = contentResolver.openInputStream(uri)
                    val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                    
                    val fileName = "cover_${if (isAdmin) "admin" else userId}_${deckCode.replace("-", "_")}_$index.jpg"
                    val file = java.io.File(filesDir, fileName)
                    val out = java.io.FileOutputStream(file)
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                    out.close()
                    
                    // Guardar ruta en el servidor
                    RetrofitClient.instance.saveCustomImage(SaveCustomImageRequest(
                        user_id = targetId,
                        deck_code = deckCode,
                        image_path = file.absolutePath
                    ))

                    newPaths.add(file.absolutePath)
                }

                withContext(Dispatchers.Main) {
                    if (isAdmin) {
                        globalCoversCache[deckCode] = newPaths
                    } else {
                        customCoversCache[deckCode] = newPaths
                    }
                    refreshList()
                    Toast.makeText(this@MainActivity, if (isAdmin) "Carátulas Globales actualizadas" else "Carátulas actualizadas", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error al guardar imágenes", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun refreshList() {
        listContainer.removeAllViews()
        decksLoadedCount = 0
        scrollView.setOnScrollChangeListener(null) // Limpiar listener previo
        val grouped = cardMap.entries.groupBy { getBaseCode(it.key) }

        if (isDeckViewMode && selectedCollection == null) {
            fabScanner.visibility = View.GONE
            renderFilterToolbar()
            renderDeckGallery(grouped.keys)
        } else {
            fabScanner.visibility = View.VISIBLE
            if (isDeckViewMode && selectedCollection != null) {
                renderSingleDeckDetails(selectedCollection!!, grouped[selectedCollection] ?: emptyList())
            } else {
                renderDetailedFullList(grouped)
            }
        }
    }

    private fun renderFilterToolbar() {
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(40, 40, 40, 40) 
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        // --- BOTÓN ESCANEAR ---
        val scanBtn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(40, 20, 40, 20)
            val shape = GradientDrawable().apply {
                setColor(colorTextGold)
                cornerRadius = 15f
            }
            background = shape
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                rightMargin = 20
            }
            setOnClickListener { openScanner() }
        }
        val scanIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_camera_modern)
            layoutParams = LinearLayout.LayoutParams(35, 35)
            setColorFilter(colorMidnight)
        }
        val scanText = TextView(this).apply {
            text = " ESCANEAR"
            textSize = 12f
            setTextColor(colorMidnight)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        scanBtn.addView(scanIcon)
        scanBtn.addView(scanText)

        // --- BOTÓN FILTROS ---
        val filterBtn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(40, 20, 40, 20)
            val shape = GradientDrawable().apply {
                setColor(colorLightMidnight)
                cornerRadius = 15f
                setStroke(1, colorBorder)
            }
            background = shape
            setOnClickListener { view -> showFilterMenu(view) }
        }
        val filterIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_search_modern)
            layoutParams = LinearLayout.LayoutParams(35, 35)
            setColorFilter(colorTextLight)
        }
        val filterText = TextView(this).apply {
            text = " FILTROS"
            textSize = 12f
            setTextColor(colorTextLight)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        filterBtn.addView(filterIcon)
        filterBtn.addView(filterText)

        toolbar.addView(scanBtn)
        toolbar.addView(filterBtn)
        listContainer.addView(toolbar)
    }

    private fun showFilterMenu(view: View) {
        val popup = PopupMenu(this, view)
        val itemAll = popup.menu.add("Mostrar todas las barajas")
        val itemOwned = popup.menu.add("Mostrar solo donde tengo cartas")
        
        itemAll.isCheckable = true
        itemOwned.isCheckable = true
        
        itemAll.isChecked = !showOnlyOwnedDecks
        itemOwned.isChecked = showOnlyOwnedDecks

        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "Mostrar todas las barajas" -> {
                    showOnlyOwnedDecks = false
                    refreshList()
                }
                "Mostrar solo donde tengo cartas" -> {
                    showOnlyOwnedDecks = true
                    refreshList()
                }
            }
            true
        }
        popup.show()
    }

    private fun renderDeckGallery(ownedDeckCodes: Set<String>) {
        lifecycleScope.launch {
            // 1. Obtener datos (Instantáneo por caché)
            val allDecks = allDecksCache ?: emptyList()

            // APLICAR FILTRO DE PROPIEDAD
            var filteredDecks = if (showOnlyOwnedDecks) {
                allDecks.filter { ownedDeckCodes.contains(it.codigoDeckSp) || ownedDeckCodes.contains(it.codigoDeckEn) }
            } else {
                allDecks
            }

            val normalizedQuery = searchQuery.normalize()
            if (searchQuery.isNotEmpty()) {
                filteredDecks = filteredDecks.filter { deck -> 
                    val code = (deck.codigoDeckSp ?: deck.codigoDeckEn ?: "").normalize()
                    val name = (deck.nombreDeck ?: "").normalize()
                    code.contains(normalizedQuery) || name.contains(normalizedQuery)
                }
            }

            // 2. Preparar contenedor
            val gridLayout = GridLayout(this@MainActivity).apply {
                columnCount = 1
                setPadding(40, 20, 40, 20)
                alignmentMode = GridLayout.ALIGN_BOUNDS
            }
            listContainer.addView(gridLayout)
            
            val itemWidth = resources.displayMetrics.widthPixels - 80
            val pkgName = packageName
            val res = resources

            fun loadNextBatch() {
                if (decksLoadedCount >= filteredDecks.size) return
                
                val nextBatch = filteredDecks.drop(decksLoadedCount).take(PAGE_SIZE)
                nextBatch.forEach { deck ->
                    val collection = deck.codigoDeckSp ?: deck.codigoDeckEn ?: return@forEach
                    val name = deck.nombreDeck ?: "Colección"

                        val item = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER
                        setPadding(30, 40, 30, 40)
                        val shape = GradientDrawable().apply {
                            setColor(colorLightMidnight)
                            cornerRadius = 30f
                            setStroke(1, colorBorder)
                        }
                        background = shape
                        layoutParams = GridLayout.LayoutParams().apply {
                            width = itemWidth
                            setMargins(0, 20, 0, 20)
                        }
                        setOnClickListener {
                            selectedCollection = collection
                            refreshList()
                        }
                    }

                    val coverImage = ImageView(this@MainActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 800)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        
                        val userImages = customCoversCache[collection]
                        val globalImages = globalCoversCache[collection]
                        
                        val imagesToCycle = when {
                            !userImages.isNullOrEmpty() -> userImages
                            !globalImages.isNullOrEmpty() -> globalImages
                            else -> null
                        }

                        if (imagesToCycle != null) {
                            if (imagesToCycle.size == 1) {
                                Glide.with(this@MainActivity).load(imagesToCycle[0]).into(this)
                            } else {
                                var currentIndex = 0
                                val runCycle = object : Runnable {
                                    override fun run() {
                                        if (ViewCompat.isAttachedToWindow(this@apply)) {
                                            Glide.with(this@MainActivity)
                                                .load(imagesToCycle[currentIndex])
                                                .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade())
                                                .into(this@apply)
                                            
                                            currentIndex = (currentIndex + 1) % imagesToCycle.size
                                            postDelayed(this, 10000)
                                        }
                                    }
                                }
                                post(runCycle)
                            }
                        } else {
                            val cacheKey = collection.lowercase().replace("-", "_")
                            val resId = resIdCache.getOrPut(cacheKey) {
                                res.getIdentifier(cacheKey, "drawable", pkgName)
                            }
                            if (resId != 0) setImageResource(resId) else setImageResource(android.R.drawable.ic_menu_gallery)
                        }
                    }
                    
                    val deckNameText = TextView(this@MainActivity).apply {
                        text = name
                        textSize = 18f
                        setTextColor(Color.WHITE)
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        gravity = Gravity.CENTER
                        setPadding(20, 25, 20, 5)
                        maxLines = 2
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    }

                    val deckCodeText = TextView(this@MainActivity).apply {
                        text = collection
                        textSize = 14f
                        setTextColor(colorTextGold)
                        gravity = Gravity.CENTER
                        setPadding(0, 0, 0, 10)
                    }

                    if (showProgress) {
                        val totalOwned = cardMap.entries.filter { getBaseCode(it.key) == collection }.sumOf { it.value }
                        val totalInDeck = deckTotalsCache[collection] ?: 0
                        
                        if (totalInDeck > 0) {
                            val progressText = TextView(this@MainActivity).apply {
                                text = "$totalOwned / $totalInDeck"
                                textSize = 12f
                                setTextColor(colorTextLight)
                                setTypeface(null, android.graphics.Typeface.BOLD)
                                gravity = Gravity.CENTER
                                setPadding(0, 5, 0, 10)
                            }
                            item.addView(progressText)
                        }
                    }

                    item.addView(coverImage)
                    item.addView(deckNameText)
                    item.addView(deckCodeText)
                    gridLayout.addView(item)
                }
                decksLoadedCount += nextBatch.size
            }

            // Carga inicial
            loadNextBatch()

            // Listener de scroll para carga infinita
            scrollView.setOnScrollChangeListener { v, _, scrollY, _, _ ->
                val view = v as ScrollView
                val contentHeight = view.getChildAt(0).height
                if (scrollY + view.height > contentHeight - 1000) {
                    loadNextBatch()
                }
            }
        }
    }

    private fun renderSingleDeckDetails(collection: String, scannedCards: List<Map.Entry<String, Int>>) {
        // Cabecera superior moderna y balanceada
        val headerBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(20, 30, 20, 30)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val buttonSize = 120 // Tamaño fijo para balancear el centrado

        // Botón Volver (Flecha estándar Android)
        val backBtn = ImageButton(this).apply {
            setImageResource(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(colorTextGold)
            layoutParams = LinearLayout.LayoutParams(buttonSize, buttonSize)
            setPadding(20, 20, 20, 20)
            setOnClickListener {
                selectedCollection = null
                refreshList()
            }
        }

        // Contenedor del Título (Nombre arriba, Código abajo)
        val titleContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val nameText = TextView(this).apply {
            text = "Cargando..."
            textSize = 17f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        }

        val codeText = TextView(this).apply {
            text = collection
            textSize = 13f
            setTextColor(colorTextGold)
            gravity = Gravity.CENTER
        }

        titleContainer.addView(nameText)
        titleContainer.addView(codeText)

        // Botón Menú (3 puntos verticales estándar)
        val menuBtn = ImageButton(this).apply {
            setImageResource(androidx.appcompat.R.drawable.abc_ic_menu_overflow_material)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(colorTextGold)
            layoutParams = LinearLayout.LayoutParams(buttonSize, buttonSize)
            setPadding(20, 20, 20, 20)
            setOnClickListener { view -> 
                val userPrefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
                val isAdmin = userPrefs.getString("user_email", "") == "admin"

                val popup = PopupMenu(this@MainActivity, view)
                popup.menu.add("Compartir esta lista")
                popup.menu.add("Marcar todo como obtenido")
                popup.menu.add("Cambiar carátula de baraja")
                
                if (isAdmin) {
                    popup.menu.add("Importar nombres desde Wiki")
                    popup.menu.add("Editar código de baraja")
                }
                
                popup.setOnMenuItemClickListener { item ->
                    when (item.title) {
                        "Cambiar carátula de baraja" -> pickCustomCover(collection)
                        "Importar nombres desde Wiki" -> importDeckWiki(collection)
                        "Editar código de baraja" -> showEditDeckCodeDialog(collection)
                        else -> Toast.makeText(this@MainActivity, "Opción: ${item.title}", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                popup.show()
            }
        }

        headerBar.addView(backBtn)
        headerBar.addView(titleContainer)
        headerBar.addView(menuBtn)
        listContainer.addView(headerBar)

        // --- 🔄 PANEL DE PROGRESO DE IMPORTACIÓN ---
        val currentStatus = activeImports[collection]
        if (currentStatus != null && currentStatus != "IDLE") {
            val progressLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(40, 0, 40, 20)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }

            if (currentStatus == "PROCESSING") {
                val loadingTxt = TextView(this).apply {
                    text = "Buscando cartas en la Wiki..."
                    textSize = 12f
                    setTextColor(colorTextGold)
                    setPadding(0, 0, 0, 10)
                }
                val pBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                    isIndeterminate = true
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 15)
                }
                progressLayout.addView(loadingTxt)
                progressLayout.addView(pBar)
            } else if (currentStatus == "ERROR") {
                val errorTxt = TextView(this).apply {
                    text = "No se encontraron datos en la Wiki o hubo un error."
                    textSize = 12f
                    setTextColor(Color.parseColor("#F44336")) // Rojo error
                    setPadding(0, 0, 0, 10)
                    gravity = Gravity.CENTER
                }
                val retryBtn = Button(this).apply {
                    text = "REINTENTAR"
                    setBackgroundColor(Color.parseColor("#22F44336"))
                    setTextColor(Color.parseColor("#F44336"))
                    setOnClickListener {
                        activeImports.remove(collection)
                        importDeckWiki(collection)
                    }
                }
                progressLayout.addView(errorTxt)
                progressLayout.addView(retryBtn)
            }
            listContainer.addView(progressLayout)
        }

        lifecycleScope.launch {
            // 1. Cargar nombre de la baraja
            val currentDeck = allDecksCache?.find { it.codigoDeckSp == collection || it.codigoDeckEn == collection }
            nameText.text = currentDeck?.nombreDeck ?: "Colección"
            codeText.text = collection

            // 2. Cargar TODAS las cartas que pertenecen a esta baraja desde la caché en memoria
            val allCardsInDeck = allCardDetailsCache?.filter { it.codigoDeckSp == collection } ?: emptyList()
            
            // 2.5 Asegurar que los nombres estén en la caché
            allCardsInDeck.forEach { card ->
                val code = card.deckCardId ?: ""
                val name = card.nombreCarta ?: ""
                if (code.isNotEmpty()) cardNamesCache[code] = name
            }
            
            val gridLayout = GridLayout(this@MainActivity).apply {
                columnCount = 2
                setPadding(20, 20, 20, 20)
            }
            listContainer.addView(gridLayout)

            // 3. Preparar lista combinada (lo que tengo vs lo que hay)
            val combinedList = allCardsInDeck.map { dbCard ->
                val code = dbCard.deckCardId ?: ""
                val count = cardMap[code] ?: 0
                Pair(code, count)
            }.sortedBy { extractNumber(it.first) }

            // 4. Filtrar por búsqueda
            val normalizedQuery = searchQuery.normalize()
            val filteredList = if (searchQuery.isEmpty()) combinedList
            else combinedList.filter { (code, _) ->
                val cardName = cardNamesCache[code] ?: ""
                code.normalize().contains(normalizedQuery) || cardName.normalize().contains(normalizedQuery)
            }

            val allCodes = filteredList.map { it.first }
            filteredList.forEachIndexed { index, pair ->
                gridLayout.addView(createCardItem(pair.first, pair.second, 2, allCodes, index))
            }
        }
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
                setPadding(30, 20, 30, 20)
                val shape = GradientDrawable().apply {
                    setColor(colorLightMidnight)
                    cornerRadius = 15f
                    setStroke(1, colorBorder)
                }
                background = shape
                val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                params.setMargins(20, 30, 20, 10)
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
                val deckName = deckNamesCache[collection] ?: allDecksCache?.find { it.codigoDeckSp == collection || it.codigoDeckEn == collection }?.nombreDeck
                if (deckName != null) {
                    headerText.text = "$collection: $deckName"
                    headerText.setTextColor(Color.WHITE)
                    deckNamesCache[collection] = deckName
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
        val isSelected = selectedItems.contains(code)
        val isOwned = count > 0
        
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(10, 10, 10, 10)
            layoutParams = GridLayout.LayoutParams().apply { width = resources.displayMetrics.widthPixels / columns - 40 }
            
            // Selección visual: Fondo sutil si está seleccionada
            val shape = GradientDrawable().apply {
                setColor(if (isSelected) Color.parseColor("#1A64FFDA") else Color.TRANSPARENT)
                cornerRadius = 15f
                if (isSelected) setStroke(2, Color.parseColor("#64FFDA"))
            }
            background = shape
        }

        val imgContainer = FrameLayout(this)
        val imageView = ImageView(this).apply {
            val imgHeight = if (columns == 2) 450 else 300
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, imgHeight)
            scaleType = ImageView.ScaleType.FIT_CENTER; setBackgroundColor(colorHeaderBg)
            
            // Efecto GRIS si no la tenemos
            if (!isOwned) {
                val matrix = ColorMatrix().apply { setSaturation(0f) }
                colorFilter = ColorMatrixColorFilter(matrix)
                alpha = 0.5f
            } else {
                clearColorFilter()
                alpha = 1.0f
            }

            // Si está seleccionada, le damos un tinte
            if (isSelected) setColorFilter(Color.parseColor("#66000000")) 
        }
        
        imgContainer.addView(imageView)

        val textCode = TextView(this).apply {
            text = if (count > 1) "$code x$count" else if (count == 1) code else "$code (Falta)"
            textSize = 11f; setTypeface(null, android.graphics.Typeface.BOLD); gravity = Gravity.CENTER
            setTextColor(if (isOwned) colorTextLight else colorTextDim)
        }
        val textName = TextView(this).apply {
            textSize = 9f; gravity = Gravity.CENTER; setTextColor(colorTextDim); maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
        }
        
        item.addView(imgContainer); item.addView(textCode); item.addView(textName)

        val action = {
            if (isSelectionMode) {
                if (selectedItems.contains(code)) selectedItems.remove(code) else selectedItems.add(code)
                updateSelectionCount()
                refreshList()
            } else {
                if (allCodes.isNotEmpty()) showImageOverlay(allCodes, initialIndex)
                else showImageOverlay(listOf(code), 0)
            }
        }

        item.setOnClickListener { action() }
        item.setOnLongClickListener {
            if (!isSelectionMode) {
                enterSelectionMode()
                selectedItems.add(code)
                updateSelectionCount()
                refreshList()
            }
            true
        }

        lifecycleScope.launch {
            val cardName = cardNamesCache[code] ?: allCardDetailsCache?.find { it.deckCardId == code }?.nombreCarta
            if (cardName != null) {
                textName.text = cardName
                val imageUrl = allCardImagesCache?.find { it.nombreEs == cardName }?.imageUrl
                if (!imageUrl.isNullOrEmpty()) {
                    Glide.with(this@MainActivity).load(imageUrl).into(imageView)
                }
            }
        }
        return item
    }

    private fun enterSelectionMode() {
        isSelectionMode = true
        searchLayout.visibility = View.GONE
        selectionLayout.visibility = View.VISIBLE
        selectedItems.clear()
        updateSelectionCount()
        refreshList()
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        searchLayout.visibility = View.VISIBLE
        selectionLayout.visibility = View.GONE
        selectedItems.clear()
        refreshList()
    }

    private fun updateSelectionCount() {
        selectionCountText.text = "${selectedItems.size} seleccionados"
    }

    private fun deleteSelectedItems() {
        selectedItems.forEach { code ->
            val count = cardMap[code] ?: return@forEach
            if (count > 1) {
                cardMap[code] = count - 1
            } else {
                cardMap.remove(code)
            }
        }
        saveDataToDisk()
        exitSelectionMode()
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

        val adminApiLink = TextView(this).apply {
            val userPrefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
            val isAdmin = userPrefs.getString("user_email", "") == "admin"
            visibility = if (isAdmin) View.VISIBLE else View.GONE
            
            text = "🔗 VER EN API YGOPRO"
            textSize = 12f
            setTextColor(Color.parseColor("#4FC3F7")) // Azul claro link
            setPadding(0, 0, 0, 30)
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
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
        sheetContainer.addView(adminApiLink)
        sheetContainer.addView(matchesTitle)
        sheetContainer.addView(horizontalScroll)

        fun updateCollectionsForCard(cardCode: String) {
            collectionsList.removeAllViews()
            lifecycleScope.launch {
                val nameEs = cardNamesCache[cardCode] ?: allCardDetailsCache?.find { it.deckCardId == cardCode }?.nombreCarta ?: return@launch
                val nameEn = allCardImagesCache?.find { it.nombreEs == nameEs }?.nombreEn
                
                // 🛠️ Actualizar Link Admin
                if (nameEn != null) {
                    adminApiLink.setOnClickListener {
                        val url = "https://db.ygoprodeck.com/api/v7/cardinfo.php?name=${URLEncoder.encode(nameEn, "UTF-8")}"
                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                        startActivity(intent)
                    }
                }

                // 💰 Actualizar Precio en el Sheet
                val price = if (nameEn != null) fetchCardPrice(cardCode, nameEn) else "N/A"
                priceInfoText.text = if (price.contains(cardCode)) price else "Precio ($cardCode): $price"

                // 🔍 Buscar en qué otras colecciones del usuario aparece esta misma carta (por nombre)
                val matches = mutableSetOf<String>()
                cardMap.keys.forEach { code ->
                    val name = cardNamesCache[code] ?: allCardDetailsCache?.find { it.deckCardId == code }?.nombreCarta
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
                    val nameEs = allCardDetailsCache?.find { it.deckCardId == code }?.nombreCarta
                    val url = allCardImagesCache?.find { it.nombreEs == nameEs }?.imageUrl
                    
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
                    if (kotlin.math.abs(deltaX) > 100) {
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
        searchBtn.setImageResource(R.drawable.ic_close_modern)
    }

    private fun exitSearchMode() {
        isSearchMode = false
        toolbarTitle.visibility = View.VISIBLE
        searchInput.visibility = View.GONE
        searchInput.text.clear()
        searchQuery = ""
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchInput.windowToken, 0)
        searchBtn.setImageResource(R.drawable.ic_search_modern)
        refreshList()
    }

    private fun saveDataToDisk(syncRemote: Boolean = true) {
        val userPrefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val userId = userPrefs.getInt("user_id", -1)
        if (userId == -1) return

        val prefs = getSharedPreferences("CardData_$userId", Context.MODE_PRIVATE)
        val gson = Gson()
        prefs.edit {
            putString("cardMap", gson.toJson(cardMap))
            putString("notFoundCards", gson.toJson(notFoundCards))
            putBoolean("isDeckViewMode", isDeckViewMode) // Guardar preferencia
        }
        if (syncRemote) pushToCloud(showToast = false)
    }

    private fun loadPersistedData() {
        val userPrefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val userId = userPrefs.getInt("user_id", -1)
        if (userId == -1) return

        val prefs = getSharedPreferences("CardData_$userId", Context.MODE_PRIVATE)
        val gson = Gson()
        val type = object : TypeToken<MutableMap<String, Int>>() {}.type
        val jsonFound = prefs.getString("cardMap", null)
        if (jsonFound != null) cardMap = gson.fromJson(jsonFound, type)
        val jsonNotFound = prefs.getString("notFoundCards", null)
        if (jsonNotFound != null) notFoundCards = gson.fromJson(jsonNotFound, type)
        isDeckViewMode = prefs.getBoolean("isDeckViewMode", false)
        
        pullFromCloud(showToast = false) // Descargar al iniciar
    }

    private fun pullFromCloud(showToast: Boolean) {
        val userPrefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val userId = userPrefs.getInt("user_id", -1)
        if (userId == -1) return

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getCollection(userId)
                if (response.isSuccessful) {
                    val remoteMap = response.body() ?: emptyMap()
                    
                    // Mezclamos: nos quedamos con el máximo entre local y remoto para no perder nada
                    remoteMap.forEach { (code, count) ->
                        val localCount = cardMap[code] ?: 0
                        if (count > localCount) cardMap[code] = count
                    }
                    
                    saveDataToDisk(syncRemote = false)
                    withContext(Dispatchers.Main) { 
                        refreshList()
                        if (showToast) Toast.makeText(this@MainActivity, "Colección descargada", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                if (showToast) withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error al descargar", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun pushToCloud(showToast: Boolean) {
        val userPrefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val userId = userPrefs.getInt("user_id", -1)
        if (userId == -1) return

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.syncCollection(SyncRequest(userId, cardMap))
                if (response.isSuccessful && showToast) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Subido a la nube", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                if (showToast) withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error al subir", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateFullCatalog(showToast: Boolean = true) {
        lifecycleScope.launch {
            try {
                // 1. CARGA DESDE CACHÉ LOCAL (ARCHIVO) - ¡INSTANTÁNEO!
                if (allDecksCache == null) {
                    val cachedCatalog = withContext(Dispatchers.IO) { loadCatalogFromDisk() }
                    if (cachedCatalog != null) {
                        Log.i("CACHE", "Cargando catálogo TCG $tcgId desde archivo local...")
                        applyCatalogToMemory(cachedCatalog)
                        updateCachesAndRefresh()
                    }
                }

                if (showToast) Toast.makeText(this@MainActivity, "Sincronizando...", Toast.LENGTH_SHORT).show()
                
                val response = RetrofitClient.instance.getFullCatalog(tcgId)
                if (response.isSuccessful) {
                    val catalog = response.body()
                    if (catalog != null) {
                        // 2. Guardamos en memoria
                        applyCatalogToMemory(catalog)
                        
                        // 3. Guardamos en DISCO para la próxima vez
                        withContext(Dispatchers.IO) { saveCatalogToDisk(catalog) }
                        
                        // 4. Refrescamos la UI con los datos frescos del servidor
                        updateCachesAndRefresh()
                        
                        if (showToast) Toast.makeText(this@MainActivity, "Catálogo actualizado", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("SYNC", "Error en sincronización: ${e.message}")
            }
        }
    }

    private fun applyCatalogToMemory(catalog: CatalogResponse) {
        allDecksCache = catalog.decks
        allCardDetailsCache = catalog.card_decks
        allCardImagesCache = catalog.cards
        
        // Repoblar cachés rápidas
        allDecksCache?.forEach { deckNamesCache[it.codigoDeckSp ?: ""] = it.nombreDeck ?: "" }
        allCardDetailsCache?.forEach { cardNamesCache[it.deckCardId ?: ""] = it.nombreCarta ?: "" }
        allCardIdsCache = allCardDetailsCache?.mapNotNull { it.deckCardId }?.toSet()
        deckTotalsCache.clear()
    }

    private fun saveCatalogToDisk(catalog: CatalogResponse) {
        try {
            val json = Gson().toJson(catalog)
            val file = java.io.File(filesDir, "catalog_cache_$tcgId.json")
            file.writeText(json)
            Log.i("CACHE", "Catálogo TCG $tcgId guardado en disco")
        } catch (e: Exception) {
            Log.e("CACHE", "Error al guardar: ${e.message}")
        }
    }

    private fun loadCatalogFromDisk(): CatalogResponse? {
        return try {
            val file = java.io.File(filesDir, "catalog_cache_$tcgId.json")
            if (file.exists()) {
                val json = file.readText()
                Gson().fromJson(json, CatalogResponse::class.java)
            } else null
        } catch (e: Exception) {
            Log.e("CACHE", "Error al cargar: ${e.message}")
            null
        }
    }

    private fun getBaseCode(code: String): String {
        // 1. Intentar match con códigos Reales de la Base de Datos (Prioridad Máxima)
        // Buscamos tanto en códigos SP como EN
        val allDecks = allDecksCache ?: emptyList()
        
        // Probamos match con código español
        val bestMatchSp = allDecks.mapNotNull { it.codigoDeckSp }.filter { code.startsWith(it) }.maxByOrNull { it.length }
        if (bestMatchSp != null) return bestMatchSp

        // Probamos match con código inglés
        val bestMatchEn = allDecks.mapNotNull { it.codigoDeckEn }.filter { code.startsWith(it) }.maxByOrNull { it.length }
        if (bestMatchEn != null) return bestMatchEn

        // 2. Fallback: Lógica por guion (si la baraja aún no está en el catálogo)
        if (code.contains("-")) {
            val parts = code.split("-")
            if (parts.size < 2) return parts[0]
            
            val secondPart = parts[1]
            val lettersInSecondPart = secondPart.takeWhile { it.isLetter() }
            
            return if (lettersInSecondPart.isNotEmpty()) {
                "${parts[0]}-$lettersInSecondPart"
            } else {
                parts[0]
            }
        }
        
        return code
    }

    private fun extractNumber(code: String): Int {
        val regex = Regex("(\\d{3,4})$")
        return regex.find(code)?.value?.toIntOrNull() ?: Int.MAX_VALUE
    }

    private fun cleanCodeForPrice(code: String): String {
        // La API de YGOPRODeck ignora guiones y sufijos de idioma.
        // Pero hay que tener cuidado de no quitar letras que son parte del SET (como la J en SDJ).
        
        // 1. Quitar guion y pasar a mayúsculas
        var clean = code.uppercase().replace("-", "")
        
        // 2. Quitar indicadores de idioma específicos si están al final del bloque de letras
        // Ej: BIJS007 -> BIJ007, LOBEN038 -> LOB038
        // Buscamos Letras + (Idioma) + Números
        val languagePatterns = listOf("SP", "EN", "IT", "DE", "FR", "PT", "JP", "KR", "E", "S")
        
        for (lang in languagePatterns) {
            val pattern = Regex("^([A-Z]+)($lang)([0-9]+)$")
            val match = pattern.find(clean)
            if (match != null) {
                clean = match.groupValues[1] + match.groupValues[3]
                break
            }
        }
        
        Log.i("PRICE_CHECK", "cleanCodeForPrice: $code -> $clean")
        return clean
    }

    private suspend fun fetchCardPrice(cardCode: String, cardNameEn: String): String {
        val cacheKey = "${cardNameEn}_$cardCode"
        if (priceCache.containsKey(cacheKey)) return priceCache[cacheKey]!!
        
        Log.i("PRICE_CHECK", "--- Iniciando búsqueda para: $cardNameEn ($cardCode) ---")
        
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://db.ygoprodeck.com/api/v7/cardinfo.php?name=${URLEncoder.encode(cardNameEn, "UTF-8")}")
                val connection = url.openConnection() as java.net.HttpURLConnection
                val response = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                val cardData = json.getJSONArray("data").getJSONObject(0)
                
                // 1. DETERMINAR EL CÓDIGO OBJETIVO EN INGLÉS
                // Buscamos el deck que mejor coincida con el prefijo del código de la carta
                Log.i("PRICE_CHECK", "Buscando coincidencia en allDecksCache (${allDecksCache?.size ?: 0} decks)")
                
                var matchingDeck = allDecksCache?.filter { 
                    val sp = it.codigoDeckSp
                    val en = it.codigoDeckEn
                    (sp != null && cardCode.startsWith(sp)) || (en != null && cardCode.startsWith(en))
                }?.maxByOrNull { 
                    val sp = it.codigoDeckSp
                    val en = it.codigoDeckEn
                    if (sp != null && cardCode.startsWith(sp)) sp.length 
                    else en?.length ?: 0
                }

                // EMERGENCY MAPPING: Si no viene en el catálogo o viene sin EN
                if (matchingDeck == null || matchingDeck.codigoDeckEn == null) {
                    if (cardCode.startsWith("BIJ-S")) {
                        Log.i("PRICE_CHECK", "Emergency Mapping detectado: BIJ-S -> SDJ-")
                        matchingDeck = Deck(null, "BIJ-S", "SDJ-", "Starter Deck Joey")
                    }
                }

                val targetCode = if (matchingDeck != null) {
                    val sp = matchingDeck.codigoDeckSp
                    val en = matchingDeck.codigoDeckEn
                    Log.i("PRICE_CHECK", "Deck encontrado: SP=$sp, EN=$en")
                    if (sp != null && cardCode.startsWith(sp) && en != null) {
                        val replaced = cardCode.replace(sp, en)
                        Log.i("PRICE_CHECK", "Reemplazo realizado: $cardCode -> $replaced")
                        replaced
                    } else {
                        Log.i("PRICE_CHECK", "No se requiere reemplazo (ya es inglés o falta código EN)")
                        cardCode
                    }
                } else {
                    Log.i("PRICE_CHECK", "No se encontró deck coincidente para el prefijo")
                    cardCode
                }

                val cleanUserCode = cleanCodeForPrice(cardCode)
                val cleanEnglishCode = cleanCodeForPrice(targetCode)
                
                Log.i("PRICE_CHECK", "Código usuario: $cardCode -> Limpio: $cleanUserCode")
                Log.i("PRICE_CHECK", "Código objetivo: $targetCode -> Limpio: $cleanEnglishCode")

                // 2. INTENTO BUSCAR PRECIO ESPECÍFICO DEL SET
                val cardSets = cardData.optJSONArray("card_sets")
                var versionPrice: String? = null
                
                if (cardSets != null) {
                    Log.i("PRICE_CHECK", "Analizando ${cardSets.length()} sets de la API")
                    for (i in 0 until cardSets.length()) {
                        val set = cardSets.getJSONObject(i)
                        val apiSetCode = set.getString("set_code")
                        val cleanApiCode = cleanCodeForPrice(apiSetCode)
                        val price = set.optString("set_price", "0.00")
                        
                        // Buscamos match con el código limpio español O el inglés
                        val isMatch = cleanApiCode == cleanUserCode || cleanApiCode == cleanEnglishCode
                        Log.i("PRICE_CHECK", "API Set: $apiSetCode (Limpio: $cleanApiCode) | Price: $price | Match: $isMatch")
                        
                        if (isMatch) {
                            if (price != "N/A" && price != "0.00") {
                                Log.i("PRICE_CHECK", "¡MATCH ENCONTRADO! Precio: $price €")
                                versionPrice = "$price €"
                                break
                            } else {
                                Log.i("PRICE_CHECK", "Match encontrado pero sin precio (0.00)")
                            }
                        }
                    }
                } else {
                    Log.i("PRICE_CHECK", "La API no devolvió 'card_sets'")
                }
                
                // 3. FALLBACK A PRECIO GENERAL SI NO HAY DEL SET
                val finalPrice = if (versionPrice != null) {
                    versionPrice
                } else {
                    val prices = cardData.getJSONArray("card_prices").getJSONObject(0)
                    val cardmarketPrice = prices.optString("cardmarket_price", "N/A")
                    Log.i("PRICE_CHECK", "Usando precio fallback: $cardmarketPrice")
                    if (cardmarketPrice != "N/A" && cardmarketPrice != "0.00") "$cardmarketPrice € (General)" else "N/A"
                }

                priceCache[cacheKey] = finalPrice
                finalPrice
            } catch (e: Exception) {
                Log.e("PRICE_CHECK", "Error en fetchCardPrice: ${e.message}")
                "N/A"
            }
        }
    }

    private fun exportCollectionToCSV() {
        if (cardMap.isEmpty()) {
            Toast.makeText(this, "La colección está vacía", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            Toast.makeText(this@MainActivity, "Generando CSV con precios... esto puede tardar", Toast.LENGTH_LONG).show()
            
            val csvBuilder = StringBuilder()
            // 🛡️ Añadir BOM (Byte Order Mark) para que Excel reconozca UTF-8 (acentos y ñ)
            csvBuilder.append('\uFEFF')
            // 📊 Usar punto y coma (;) como separador, que es el estándar en Excel para España/Europa
            csvBuilder.append("Codigo;Nombre;Cantidad;Precio Individual;Subtotal\n")

            withContext(Dispatchers.IO) {
                cardMap.toSortedMap().forEach { (code, count) ->
                    val nameEs = cardNamesCache[code] ?: allCardDetailsCache?.find { it.deckCardId == code }?.nombreCarta ?: "Desconocida"
                    val nameEn = allCardImagesCache?.find { it.nombreEs == nameEs }?.nombreEn
                    
                    val priceStr = if (nameEn != null) fetchCardPrice(code, nameEn) else "N/A"
                    val priceClean = priceStr.replace(" €", "")
                        .replace(" (General)", "")
                        .replace(",", ".")
                        .toDoubleOrNull() ?: 0.0
                    val subtotal = priceClean * count
                    
                    // Escapar punto y coma en nombres si existen
                    val safeName = nameEs.replace(";", ",")
                    
                    csvBuilder.append("$code;$safeName;$count;$priceStr;${String.format("%.2f", subtotal)} €\n")
                }
            }

            try {
                val file = java.io.File(cacheDir, "MiColeccion_TCG.csv")
                file.writeText(csvBuilder.toString())
                
                val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", file)
                
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_SUBJECT, "Mi Colección TCG")
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                startActivity(Intent.createChooser(intent, "Compartir Colección"))
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error al crear el archivo: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun String.normalize(): String {
        return Normalizer.normalize(this, Normalizer.Form.NFD)
            .replace(Regex("[\\u0300-\\u036f]"), "")
            .lowercase()
    }
}
