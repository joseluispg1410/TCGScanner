package com.example.tcgscanner

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class MainActivity : AppCompatActivity() {

    private lateinit var listContainer: LinearLayout

    // 🔥 Mapas de cartas
    private val cardMap = mutableMapOf<String, Int>()
    private val notFoundCards = mutableMapOf<String, Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scanButton = Button(this).apply {
            text = "📷 Escanear carta"
            setBackgroundColor(Color.BLACK)
            setTextColor(Color.WHITE)
            setOnClickListener { openScanner() }
        }

        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scrollView = ScrollView(this).apply {
            addView(listContainer)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)

            addView(scanButton,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 80 })

            addView(scrollView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                ))
        }

        setContentView(root)
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
                refreshList()
            }
        }
    }

    // =========================
    // 🔥 LISTA AGRUPADA + ORDENADA
    // =========================
    private fun refreshList() {
        listContainer.removeAllViews()

        val grouped = cardMap.entries.groupBy { getBaseCode(it.key) }

        grouped.toSortedMap().forEach { (collection, cards) ->
            // 🟩 CABECERA (Código + Nombre de la Baraja)
            val header = TextView(this).apply {
                text = collection // Valor por defecto mientras busca
                textSize = 20f
                setTextColor(Color.BLACK)
                setPadding(20, 40, 20, 10)
            }

            // 🔥 Aquí es donde ocurre la magia: buscamos el nombre real
            lifecycleScope.launch {
                val deckName = withContext(Dispatchers.IO) {
                    AppDatabase.getDatabase(this@MainActivity).deckDao().getDeckName(collection)
                }
                if (deckName != null) {
                    header.text = "$collection: $deckName"
                }
            }

            listContainer.addView(header)

            // 🔽 ordenar cartas por número
            cards
                .sortedBy { extractNumber(it.key) }
                .forEach { (code, count) ->
                    val row = createCardRow(code, count, true)
                    listContainer.addView(row)
                }
        }

        // 🔥 SECCIÓN: CARTAS NO ENCONTRADAS EN LA BD
        if (notFoundCards.isNotEmpty()) {
            val separator = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 5).apply {
                    setMargins(0, 50, 0, 20)
                }
                setBackgroundColor(Color.LTGRAY)
            }
            listContainer.addView(separator)

            val errorHeader = TextView(this).apply {
                text = "Cartas leídas no encontradas en la BD"
                textSize = 18f
                setTextColor(Color.RED)
                setPadding(20, 20, 20, 10)
                gravity = Gravity.CENTER_HORIZONTAL
            }
            listContainer.addView(errorHeader)

            notFoundCards.forEach { (code, count) ->
                val row = createCardRow(code, count, false)
                listContainer.addView(row)
            }
        }
    }

    private fun createCardRow(code: String, count: Int, isFound: Boolean): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(40, 10, 20, 10)
        }

        val textView = TextView(this).apply {
            text = if (count > 1) "$code x$count" else code
            textSize = 16f
            setTextColor(if (isFound) Color.DKGRAY else Color.RED)

            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        if (isFound) {
            lifecycleScope.launch {
                val cardName = withContext(Dispatchers.IO) {
                    AppDatabase.getDatabase(this@MainActivity).deckDao().getCardName(code)
                }
                if (cardName != null) {
                    textView.text = if (count > 1) "$code: $cardName x$count" else "$code: $cardName"
                }
            }
        }

        val deleteButton = Button(this).apply {
            text = "🗑️"
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener {
                val targetMap = if (isFound) cardMap else notFoundCards
                val current = targetMap[code] ?: return@setOnClickListener

                if (current > 1) {
                    targetMap[code] = current - 1
                } else {
                    targetMap.remove(code)
                }
                refreshList()
            }
        }

        row.addView(textView)
        row.addView(deleteButton)
        return row
    }

    // =========================
    // 🔥 EXTRAER BASE (DUSA-SP)
    // =========================
    private fun getBaseCode(code: String): String {
        val regex = Regex("([A-Z0-9]{3,4}-[A-Z]{1,2})")
        return regex.find(code)?.value ?: code
    }

    // =========================
    // 🔥 EXTRAER NÚMERO (008, 013)
    // =========================
    private fun extractNumber(code: String): Int {
        val regex = Regex("(\\d{3})$")
        return regex.find(code)?.value?.toIntOrNull() ?: Int.MAX_VALUE
    }
}