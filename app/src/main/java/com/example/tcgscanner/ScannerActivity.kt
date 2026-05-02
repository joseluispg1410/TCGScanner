package com.example.tcgscanner

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.google.android.material.slider.Slider
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
    private lateinit var sliderFlashIntensity: Slider

    private var camera: Camera? = null
    private lateinit var cameraExecutor: ExecutorService

    private val zoomLevels = listOf(1f, 1.5f, 2f, 2.5f, 3f)
    private var zoomIndex = 2 // Inicia en x2 (tercer elemento de la lista)
    private var isFlashOn = false
    private var flashIntensity = 1

    private val scannedCards = ArrayList<String>()
    private var lastSavedCard: String? = null

    // Mapas para guardar los ajustes por cada nivel de zoom
    private val offsetMapX = mutableMapOf<Int, Float>()
    private val offsetMapY = mutableMapOf<Int, Float>()
    private val sizeFactorMap = mutableMapOf<Int, Float>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        previewView = PreviewView(this)
        overlayView = OverlayView(this, null)

        loadAllSettings()

        // Cargar la última carta guardada para ignorarla al inicio
        val prefs = getSharedPreferences("OverlayPrefs", Context.MODE_PRIVATE)
        lastSavedCard = prefs.getString("lastSavedCard", null)

        txtLastCode = TextView(this).apply {
            text = "Esperando código..."
            setTextColor(Color.GREEN)
            textSize = 18f
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#88000000"))
            setPadding(20, 10, 20, 10)
        }

        btnZoom = Button(this).apply {
            text = "Zoom x${zoomLevels[zoomIndex]}"
            isEnabled = false
            setOnClickListener {
                val cam = camera ?: return@setOnClickListener

                zoomIndex = (zoomIndex + 1) % zoomLevels.size
                updateUIForZoom()

                val newZoom = zoomLevels[zoomIndex]
                text = "Zoom x$newZoom"

                cam.cameraControl.setZoomRatio(newZoom)
            }
        }

        btnFlash = Button(this).apply {
            text = "Flash OFF"
            isEnabled = false
            setOnClickListener {
                if (camera == null) return@setOnClickListener
                isFlashOn = !isFlashOn
                if (isFlashOn) {
                    // Intentamos usar el nivel de intensidad si está disponible (Android 13+)
                    try {
                        camera?.cameraControl?.enableTorch(true)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                             camera?.cameraControl?.setTorchStrengthLevel(flashIntensity)
                        }
                    } catch (e: Exception) {
                        camera?.cameraControl?.enableTorch(true)
                    }
                } else {
                    camera?.cameraControl?.enableTorch(false)
                }
                text = if (isFlashOn) "Flash ON" else "Flash OFF"
            }
        }

        btnExit = Button(this).apply {
            text = "Salir"
            setOnClickListener {
                saveAllSettings()
                val resultIntent = Intent().apply {
                    putStringArrayListExtra("CARDS", scannedCards)
                }
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }
        }

        btnToggleControls = Button(this).apply {
            text = "Ajustes"
            setOnClickListener {
                controlsPanel.visibility = if (controlsPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }

        controlsPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#CC000000"))
            setPadding(40, 20, 40, 20)
            visibility = View.VISIBLE
            
            addView(createLabel("Ajustes del Overlay (Zoom x${zoomLevels[zoomIndex]})"))
            
            addView(createLabel("Posición X"))
            sliderX = createSlider(-0.5f, 0.5f, offsetMapX[zoomIndex] ?: 0f) { value ->
                offsetMapX[zoomIndex] = value
                updateOverlay()
            }
            addView(sliderX)

            addView(createLabel("Posición Y"))
            sliderY = createSlider(-0.5f, 0.5f, offsetMapY[zoomIndex] ?: 0f) { value ->
                offsetMapY[zoomIndex] = value
                updateOverlay()
            }
            addView(sliderY)

            addView(createLabel("Escala"))
            sliderSize = createSlider(0.5f, 2.0f, sizeFactorMap[zoomIndex] ?: 1f) { value ->
                sizeFactorMap[zoomIndex] = value
                updateOverlay()
            }
            addView(sliderSize)

            addView(createLabel("Intensidad Flash")).apply { 
                id = View.generateViewId() 
                tag = "labelFlash"
            }
            sliderFlashIntensity = createSlider(1f, 10f, flashIntensity.toFloat()) { value ->
                flashIntensity = value.toInt()
                if (isFlashOn) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        camera?.cameraControl?.setTorchStrengthLevel(flashIntensity)
                    }
                }
            }
            addView(sliderFlashIntensity)
        }

        val topButtonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
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
            ).apply { topMargin = 200 }
        )

        // Panel de controles
        mainContainer.addView(
            controlsPanel,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )

        // Botones superiores
        mainContainer.addView(
            topButtonsRow,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            ).apply { topMargin = 50 }
        )

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
        startCamera()
        // updateUIForZoom() // Se movió dentro de startCamera tras configurar los sliders
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
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(0, 10, 0, 0)
        }
    }

    private fun updateOverlay() {
        overlayView.setOffsets(offsetMapX[zoomIndex] ?: 0f, offsetMapY[zoomIndex] ?: 0f)
        overlayView.setSizeFactor(sizeFactorMap[zoomIndex] ?: 1f)
        overlayView.setZoomFactor(zoomLevels[zoomIndex])
    }

    private fun saveAllSettings() {
        val prefs = getSharedPreferences("OverlayPrefs", Context.MODE_PRIVATE)
        prefs.edit {
            putInt("zoomIndex", zoomIndex)
            putInt("flashIntensity", flashIntensity)
            putString("lastSavedCard", lastSavedCard) // Guardar la última carta
            zoomLevels.indices.forEach { i ->
                putFloat("offsetX_$i", offsetMapX[i] ?: 0f)
                putFloat("offsetY_$i", offsetMapY[i] ?: 0f)
                putFloat("sizeFactor_$i", sizeFactorMap[i] ?: 1f)
            }
        }
    }

    private fun loadAllSettings() {
        val prefs = getSharedPreferences("OverlayPrefs", Context.MODE_PRIVATE)
        zoomIndex = prefs.getInt("zoomIndex", 0)
        flashIntensity = prefs.getInt("flashIntensity", 1)
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
                .setTargetResolution(android.util.Size(1920, 1080))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, CardAnalyzer(
                        context = this,
                        onCodeDetected = { code ->
                            if (code == lastSavedCard) return@CardAnalyzer
                            
                            runOnUiThread {
                                if (!scannedCards.contains(code)) {
                                    scannedCards.add(code)
                                    lastSavedCard = code // Actualizar la última detectada
                                    txtLastCode.text = "¡Detectado: $code!"
                                    Log.d("SCAN", "Lista de cartas: $scannedCards")
                                }
                            }
                        },
                        getScanRect = { overlayView.getScanRect() }
                    ))
                }

            // Seleccionar la mejor cámara trasera (que suele incluir zoom óptico)
            val cameraSelector = try {
                val bestCameraId = cameraProvider.availableCameraInfos
                    .map { androidx.camera.camera2.interop.Camera2CameraInfo.from(it) }
                    .sortedByDescending { it.getCameraCharacteristic(android.hardware.camera2.CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL) }
                    .firstOrNull()?.cameraId
                
                if (bestCameraId != null) {
                    CameraSelector.Builder().addCameraFilter { cameraInfos ->
                        cameraInfos.filter { androidx.camera.camera2.interop.Camera2CameraInfo.from(it).cameraId == bestCameraId }
                    }.build()
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
            } catch (e: Exception) {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )
                
                // Aplicar el zoom inicial guardado o por defecto (x2)
                camera?.cameraControl?.setZoomRatio(zoomLevels[zoomIndex])
                
                // Actualizar UI ahora que los sliders existen y el zoom está aplicado
                runOnUiThread {
                    updateUIForZoom()
                }
                
                // Configurar el slider de flash basado en las capacidades del hardware
                val labelFlash = controlsPanel.findViewWithTag<View>("labelFlash")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    // En CameraX 1.6.0 el método es accesible pero puede que el hardware no lo soporte
                    // Usamos un valor por defecto si no podemos obtenerlo
                    val maxLevel = 10 // Valor por defecto si es Android 13+
                    
                    sliderFlashIntensity.valueTo = maxLevel.toFloat()
                    sliderFlashIntensity.value = flashIntensity.coerceIn(1, maxLevel).toFloat()
                    sliderFlashIntensity.visibility = View.VISIBLE
                    labelFlash?.visibility = View.VISIBLE
                } else {
                    sliderFlashIntensity.visibility = View.GONE
                    labelFlash?.visibility = View.GONE
                }

                btnZoom.isEnabled = true
                btnFlash.isEnabled = true
            } catch (e: Exception) {
                Log.e("CAMERA", "Error al iniciar cámara", e)
            }
        }, ContextCompat.getMainExecutor(this))
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
                startCamera()
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
