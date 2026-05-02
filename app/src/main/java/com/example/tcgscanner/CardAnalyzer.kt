package com.example.tcgscanner

import android.content.Context
import android.graphics.*
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.ByteArrayOutputStream

class CardAnalyzer(
    private val context: Context,
    private val onCodeDetected: (String) -> Unit,
    private val getScanRect: () -> RectF?
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val regex = Regex("([A-Z0-9]{3,6})\\s*-\\s*([A-Z0-9]{3,5})")

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        val scanRect = getScanRect()
        
        if (mediaImage != null && scanRect != null) {
            val bitmap = processImageProxy(imageProxy, scanRect)
            
            if (bitmap != null) {
                // 1. Aplicar mejoras visuales y ESCALADO (Digital Zoom)
                val enhancedBitmap = enhanceAndUpscaleBitmap(bitmap)
                val image = InputImage.fromBitmap(enhancedBitmap, 0)

                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        for (block in visionText.textBlocks) {
                            for (line in block.lines) {
                                val text = line.text.uppercase().replace(" ", "")
                                val match = regex.find(text)

                                if (match != null) {
                                    val rawPart1 = match.groupValues[1]
                                    val rawPart2 = match.groupValues[2]
                                    
                                    // 2. Curación de texto (Healing)
                                    val healedCode = healCode(rawPart1, rawPart2)
                                    
                                    Log.i("OCR_RESULT", "¡CÓDIGO DETECTADO!: $healedCode (Original: ${match.value})")
                                    onCodeDetected(healedCode)
                                    return@addOnSuccessListener 
                                }
                            }
                        }
                    }
                    .addOnFailureListener {
                        Log.e("OCR_ERROR", "Error al procesar texto", it)
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        } else {
            imageProxy.close()
        }
    }

    /**
     * Corrige errores comunes de OCR en códigos de Yu-Gi-Oh
     */
    private fun healCode(part1: String, part2: String): String {
        val healedPart2 = StringBuilder()
        part2.forEachIndexed { index, char ->
            if (index == 0 && char.isLetter()) {
                healedPart2.append(char)
            } else {
                val healedChar = when (char) {
                    'O', 'Q', 'D', 'U' -> '0'
                    'I', 'L', '!', '|', 'T', 'J' -> '1'
                    'Z' -> '2'
                    'E' -> '3'
                    'A' -> '4'
                    'S' -> '5'
                    'G' -> '6'
                    'B' -> '8'
                    'P' -> '9'
                    else -> char
                }
                healedPart2.append(healedChar)
            }
        }
        val healedPart1 = part1.replace('0', 'O')
        return "$healedPart1-$healedPart2"
    }

    /**
     * Mejora el contraste, escala de grises y agranda la imagen para mejor resolución de caracteres.
     */
    private fun enhanceAndUpscaleBitmap(src: Bitmap): Bitmap {
        val scale = 2.5f
        val width = (src.width * scale).toInt()
        val height = (src.height * scale).toInt()
        val scaled = Bitmap.createScaledBitmap(src, width, height, true)

        val dest = Bitmap.createBitmap(width, height, scaled.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)
        val paint = Paint()

        val cm = ColorMatrix()
        cm.setSaturation(0f) 
        
        val contrast = 1.8f 
        val brightness = -70f 
        val matrix = floatArrayOf(
            contrast, 0f, 0f, 0f, brightness,
            0f, contrast, 0f, 0f, brightness,
            0f, 0f, contrast, 0f, brightness,
            0f, 0f, 0f, 1f, 0f
        )
        cm.postConcat(ColorMatrix(matrix))

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(scaled, 0f, 0f, paint)
        return dest
    }

    private fun processImageProxy(imageProxy: ImageProxy, rect: RectF): Bitmap? {
        val yBuffer = imageProxy.planes[0].buffer
        val uBuffer = imageProxy.planes[1].buffer
        val vBuffer = imageProxy.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 100, out)
        val imageBytes = out.toByteArray()
        val fullBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: return null

        val matrix = Matrix()
        matrix.postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
        val rotatedBitmap = Bitmap.createBitmap(fullBitmap, 0, 0, fullBitmap.width, fullBitmap.height, matrix, true)

        return cropToView(rotatedBitmap, rect)
    }

    private fun cropToView(bitmap: Bitmap, rect: RectF): Bitmap? {
        val viewWidth = context.resources.displayMetrics.widthPixels.toFloat()
        val viewHeight = context.resources.displayMetrics.heightPixels.toFloat()
        val bitmapWidth = bitmap.width.toFloat()
        val bitmapHeight = bitmap.height.toFloat()

        val scale: Float
        var dx = 0f
        var dy = 0f

        if (bitmapWidth * viewHeight > viewWidth * bitmapHeight) {
            scale = viewHeight / bitmapHeight
            dx = (bitmapWidth * scale - viewWidth) * 0.5f
        } else {
            scale = viewWidth / bitmapWidth
            dy = (bitmapHeight * scale - viewHeight) * 0.5f
        }

        val left = (rect.left + dx) / scale
        val top = (rect.top + dy) / scale
        val width = rect.width() / scale
        val height = rect.height() / scale

        val fLeft = left.toInt().coerceIn(0, bitmap.width - 1)
        val fTop = top.toInt().coerceIn(0, bitmap.height - 1)
        val fWidth = width.toInt().coerceAtMost(bitmap.width - fLeft)
        val fHeight = height.toInt().coerceAtMost(bitmap.height - fTop)

        if (fWidth <= 0 || fHeight <= 0) return null
        return Bitmap.createBitmap(bitmap, fLeft, fTop, fWidth, fHeight)
    }
}
