package com.example.tcgscanner

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.HapticFeedbackConstants
import android.os.Vibrator
import android.os.VibrationEffect
import android.os.Build
import android.widget.*
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.InstallStatusListener
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.google.android.material.slider.Slider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScannerActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlayView: OverlayView
    private lateinit var btnZoom: Button
    private lateinit var btnFlash: Button
    private lateinit var btnExit: Button
    private lateinit var btnToggleControls: Button
    private lateinit var controlsPanel: LinearLayout
    private lateinit var txtLastCode: TextView

    private lateinit var sliderX: Slider
    private lateinit var sliderY: Slider
    private lateinit var sliderSize: Slider

    private lateinit var loadingOverlay: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var loadingText: TextView

    private var camera: Camera? = null
    private lateinit var cameraExecutor: ExecutorService

    private val zoomLevels = listOf(1f, 1.5f, 2f, 2.5f, 3f)
    private var zoomIndex = 2 // Inicia en x2
    private var isFlashOn = false

    private val scannedCards = ArrayList<String>()
    private var lastSavedCard: String? = null
    private val resetLastCardRunnable = Runnable { lastSavedCard = null }

    // Colores del tema Midnight
    private val colorMidnight = Color.parseColor("#0A192F")
    private val colorHeaderBg = Color.parseColor("#CC172A45") // Semitransparente
    private val colorGold = Color.parseColor("#E6B800")
    private val colorTextLight = Color.parseColor("#CCD6F6")

    // Mapas para guardar los ajustes por cada nivel de zoom
    private val offsetMapX = mutableMapOf<Int, Float>()
    private val offsetMapY = mutableMapOf<Int, Float>()
    private val sizeFactorMap = mutableMapOf<Int, Float>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        previewView = PreviewView(this)
        overlayView = OverlayView(this, null)

        loadAllSettings()

        // RESET DE MEMORIA: Al abrir el scanner, olvidamos la última carta de la sesión anterior
        lastSavedCard = null 

        txtLastCode = TextView(this).apply {
            text = "Esperando código..."
            setTextColor(colorGold)
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            val shape = GradientDrawable().apply {
                setColor(colorHeaderBg)
                cornerRadius = 20f
            }
            background = shape
            setPadding(40, 20, 40, 20)
        }

        btnZoom = createModernButton("Zoom x${zoomLevels[zoomIndex]}")
        btnFlash = createModernButton("Flash OFF")
        btnToggleControls = createModernButton("Ajustes")
        btnExit = createModernButton("Salir").apply {
            setTextColor(Color.WHITE)
            val shape = GradientDrawable().apply {
                setColor(Color.parseColor("#CCF44336")) // Rojo elegante para salir
                cornerRadius = 15f
            }
            background = shape
        }

        btnFlash.setOnClickListener {
            if (camera == null) return@setOnClickListener
            isFlashOn = !isFlashOn
            camera?.cameraControl?.enableTorch(isFlashOn)
            btnFlash.text = if (isFlashOn) "Flash ON" else "Flash OFF"
            btnFlash.setTextColor(if (isFlashOn) Color.YELLOW else colorGold)
        }

        btnExit.setOnClickListener {
            saveAllSettings()
            val resultIntent = Intent().apply {
                putStringArrayListExtra("CARDS", scannedCards)
            }
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }

        btnToggleControls.setOnClickListener {
            controlsPanel.visibility = if (controlsPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        controlsPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val shape = GradientDrawable().apply {
                setColor(colorHeaderBg)
                cornerRadius = 30f
                setStroke(2, colorGold)
            }
            background = shape
            setPadding(50, 40, 50, 40)
            visibility = View.GONE // Oculto por defecto
            
            addView(createLabel("Ajustes del Overlay (Zoom x${zoomLevels[zoomIndex]})").apply { 
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            
            addView(createLabel("Posición Horizontal (X)"))
            sliderX = createSlider(-0.5f, 0.5f, offsetMapX[zoomIndex] ?: 0f) { value ->
                offsetMapX[zoomIndex] = value
                updateOverlay()
            }
            addView(sliderX)

            addView(createLabel("Posición Vertical (Y)"))
            sliderY = createSlider(-0.5f, 0.5f, offsetMapY[zoomIndex] ?: 0f) { value ->
                offsetMapY[zoomIndex] = value
                updateOverlay()
            }
            addView(sliderY)

            addView(createLabel("Escala del cuadro"))
            sliderSize = createSlider(0.5f, 2.0f, sizeFactorMap[zoomIndex] ?: 1f) { value ->
                sizeFactorMap[zoomIndex] = value
                updateOverlay()
            }
            addView(sliderSize)
        }

        val topButtonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(btnZoom)
            addView(btnFlash)
            addView(btnToggleControls)
            addView(btnExit)
        }

        val mainContainer = FrameLayout(this)
        setContentView(mainContainer)

        mainContainer.addView(previewView)
        mainContainer.addView(overlayView)

        // Info de código detectado
        mainContainer.addView(
            txtLastCode,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL or Gravity.TOP
            ).apply { topMargin = 280 }
        )

        // Panel de controles (abajo pero con margen)
        mainContainer.addView(
            controlsPanel,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            ).apply { 
                setMargins(40, 0, 40, 100)
            }
        )

        // Botones superiores
        mainContainer.addView(
            topButtonsRow,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            ).apply { topMargin = 120 }
        )

        // --- ⏳ PANTALLA DE CARGA ML KIT ---
        loadingOverlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#EE0A192F")) // Fondo Midnight casi opaco
            visibility = View.GONE
            
            progressBar = ProgressBar(this@ScannerActivity).apply {
                indeterminateDrawable.setColorFilter(colorGold, PorterDuff.Mode.SRC_IN)
            }
            
            loadingText = TextView(this@ScannerActivity).apply {
                text = "Iniciando motor de IA..."
                setTextColor(Color.WHITE)
                textSize = 16f
                setPadding(0, 40, 0, 0)
                gravity = Gravity.CENTER
            }
            
            addView(progressBar)
            addView(loadingText)
        }
        
        mainContainer.addView(loadingOverlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (allPermissionsGranted()) {
            checkMLKitResources()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun checkMLKitResources() {
        val moduleInstallClient = ModuleInstall.getClient(this)
        val optionalModuleApi = com.google.mlkit.vision.text.TextRecognition.getClient(com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS)

        moduleInstallClient.areModulesAvailable(optionalModuleApi)
            .addOnSuccessListener { response ->
                if (response.areModulesAvailable()) {
                    startCamera()
                } else {
                    loadingOverlay.visibility = View.VISIBLE
                    loadingText.text = "Descargando componentes de IA...\n(Esto solo ocurre la primera vez)"

                    val listener = InstallStatusListener { update ->
                        val progress = update.progressInfo
                        if (progress != null) {
                            val percent = (progress.bytesDownloaded * 100 / progress.totalBytesToDownload).toInt()
                            runOnUiThread {
                                loadingText.text = "Descargando componentes de IA: $percent%\n(Esto solo ocurre la primera vez)"
                            }
                        }
                        if (update.installState == ModuleInstallStatusUpdate.InstallState.STATE_COMPLETED) {
                            runOnUiThread {
                                loadingOverlay.visibility = View.GONE
                                startCamera()
                            }
                        }
                    }

                    val request = ModuleInstallRequest.newBuilder()
                        .addApi(optionalModuleApi)
                        .setListener(listener)
                        .build()

                    moduleInstallClient.installModules(request)
                        .addOnSuccessListener { installResponse ->
                            if (installResponse.areModulesAlreadyInstalled()) {
                                loadingOverlay.visibility = View.GONE
                                startCamera()
                            }
                        }
                        .addOnFailureListener {
                            loadingText.text = "Error al iniciar descarga.\nVerifica tu conexión."
                            progressBar.visibility = View.GONE
                        }
                }
            }
            .addOnFailureListener {
                startCamera()
            }
    }

    private fun createModernButton(title: String): Button {
        return Button(this).apply {
            text = title
            setTextColor(colorGold)
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            val shape = GradientDrawable().apply {
                setColor(colorHeaderBg)
                cornerRadius = 15f
            }
            background = shape
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(10, 0, 10, 0)
            }
        }
    }

    private fun updateUIForZoom() {
        sliderX.value = offsetMapX[zoomIndex] ?: 0f
        sliderY.value = offsetMapY[zoomIndex] ?: 0f
        sliderSize.value = sizeFactorMap[zoomIndex] ?: 1f
        (controlsPanel.getChildAt(0) as? TextView)?.text = "Ajustes del Overlay (Zoom x${zoomLevels[zoomIndex]})"
        updateOverlay()
    }

    private fun createSlider(min: Float, max: Float, initial: Float, onValueChange: (Float) -> Unit): Slider {
        return Slider(this).apply {
            valueFrom = min
            valueTo = max
            value = initial.coerceIn(min, max)
            trackActiveTintList = android.content.res.ColorStateList.valueOf(colorGold)
            thumbTintList = android.content.res.ColorStateList.valueOf(colorGold)
            addOnChangeListener { _, value, _ -> onValueChange(value) }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 10 }
        }
    }

    private fun createLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(colorTextLight)
            textSize = 13f
            setPadding(0, 15, 0, 0)
        }
    }

    private fun updateOverlay() {
        overlayView.setOffsets(offsetMapX[zoomIndex] ?: 0f, offsetMapY[zoomIndex] ?: 0f)
        overlayView.setSizeFactor(sizeFactorMap[zoomIndex] ?: 1f)
        overlayView.setZoomFactor(zoomLevels[zoomIndex])
    }

    private fun vibrateDevice() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(70)
            }
        }
        // Forzar también haptic feedback por si la vibración está silenciada
        previewView.isHapticFeedbackEnabled = true
        previewView.performHapticFeedback(
            HapticFeedbackConstants.VIRTUAL_KEY,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
        )
    }

    private fun saveAllSettings() {
        val prefs = getSharedPreferences("OverlayPrefs", Context.MODE_PRIVATE)
        prefs.edit {
            putInt("zoomIndex", zoomIndex)
            putString("lastSavedCard", lastSavedCard)
            zoomLevels.indices.forEach { i ->
                putFloat("offsetX_$i", offsetMapX[i] ?: 0f)
                putFloat("offsetY_$i", offsetMapY[i] ?: 0f)
                putFloat("sizeFactor_$i", sizeFactorMap[i] ?: 1f)
            }
        }
    }

    private fun loadAllSettings() {
        val prefs = getSharedPreferences("OverlayPrefs", Context.MODE_PRIVATE)
        zoomIndex = prefs.getInt("zoomIndex", 2) // Por defecto x2
        zoomLevels.indices.forEach { i ->
            offsetMapX[i] = prefs.getFloat("offsetX_$i", 0f)
            offsetMapY[i] = prefs.getFloat("offsetY_$i", 0f)
            sizeFactorMap[i] = prefs.getFloat("sizeFactor_$i", 1f)
        }
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .setTargetResolution(android.util.Size(1920, 1080))
                .build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(android.util.Size(1280, 720)) // Resolución optimizada para velocidad
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analyzer ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        // Asegurarnos de tener los códigos de la caché global (ahora cargados desde Postgres en MainActivity)
                        val codes = MainActivity.allCardIdsCache
                        
                        withContext(Dispatchers.Main) {
                            analyzer.setAnalyzer(cameraExecutor, CardAnalyzer(
                                context = this@ScannerActivity,
                                validCardCodes = codes ?: emptySet(),
                                onCodeDetected = { code ->
                                    // Evitar registrar la misma carta varias veces seguidas mientras se mantiene el enfoque
                                    if (code == lastSavedCard) return@CardAnalyzer
                                    
                                    runOnUiThread {
                                        scannedCards.add(code)
                                        lastSavedCard = code
                                        txtLastCode.text = "¡Detectado: $code!"
                                        
                                        txtLastCode.removeCallbacks(resetLastCardRunnable)
                                        txtLastCode.postDelayed(resetLastCardRunnable, 3000)
                                        
                                        vibrateDevice()
                                    }
                                },
                                getScanRect = { overlayView.getScanRect() }
                            ))
                        }
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )
                
                camera?.cameraControl?.setZoomRatio(zoomLevels[zoomIndex])
                
                runOnUiThread {
                    updateUIForZoom()
                }

                btnZoom.isEnabled = true
                btnFlash.isEnabled = true
            } catch (e: Exception) {
                Log.e("CAMERA", "Error al iniciar cámara", e)
            }
        }, ContextCompat.getMainExecutor(this))
        
        btnZoom.setOnClickListener {
            val cam = camera ?: return@setOnClickListener
            zoomIndex = (zoomIndex + 1) % zoomLevels.size
            updateUIForZoom()
            val newZoom = zoomLevels[zoomIndex]
            btnZoom.text = "Zoom x$newZoom"
            cam.cameraControl.setZoomRatio(newZoom)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                checkMLKitResources()
            } else {
                Toast.makeText(this, "Permisos denegados.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
