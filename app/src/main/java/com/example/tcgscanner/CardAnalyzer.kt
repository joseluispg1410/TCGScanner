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
    private val validCardCodes: Set<String>,
    private val onCodeDetected: (String) -> Unit,
    private val getScanRect: () -> RectF?
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val regex = Regex("([A-Z0-9]{3,7})\\s*-\\s*([A-Z0-9]{3,6})")

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val scanRect = getScanRect() ?: return imageProxy.close()

        val bitmap = processImageProxy(imageProxy, scanRect) ?: return imageProxy.close()
        val enhancedBitmap = enhanceAndUpscaleBitmap(bitmap)
        val image = InputImage.fromBitmap(enhancedBitmap, 0)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                for (block in visionText.textBlocks) {
                    for (line in block.lines) {
                        val text = line.text.uppercase().replace(" ", "")
                        val match = regex.find(text)

                        if (match != null) {
                            val part1 = match.groupValues[1]
                            val part2 = match.groupValues[2]
                            
                            val healedCode = healCode(part1, part2)
                            
                            // VALIDACIÓN TOTAL: ¿Existe esta carta exacta en la base de datos?
                            // Probamos el código tal cual y también normalizando O por 0 (error común OCR)
                            val alternativeCode = healedCode.replace('O', '0')
                            
                            val finalCode = when {
                                validCardCodes.contains(healedCode) -> healedCode
                                validCardCodes.contains(alternativeCode) -> alternativeCode
                                else -> null
                            }

                            if (finalCode != null) {
                                Log.i("SCAN_VALID", "¡MATCH REAL!: $finalCode")
                                onCodeDetected(finalCode)
                                return@addOnSuccessListener
                            }
                        }
                    }
                }
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    private fun healCode(part1: String, part2: String): String {
        // En la parte 2 (número) sí es seguro curar caracteres
        val healedPart2 = StringBuilder()
        var i = 0
        while (i < part2.length && part2[i].isLetter() && i < 2) {
            healedPart2.append(part2[i])
            i++
        }
        for (j in i until part2.length) {
            val char = part2[j]
            val healedChar = when (char) {
                'O', 'Q', 'D', 'U' -> '0'
                'I', 'L', '!', '|', 'T', 'J' -> '1'
                'Z' -> '2'
                'S' -> '5'
                'B' -> '8'
                'G' -> '6'
                'P' -> '9'
                else -> char
            }
            healedPart2.append(healedChar)
        }
        return "$part1-$healedPart2"
    }

    private fun enhanceAndUpscaleBitmap(src: Bitmap): Bitmap {
        val scale = 2.0f
        val width = (src.width * scale).toInt()
        val height = (src.height * scale).toInt()
        val scaled = Bitmap.createScaledBitmap(src, width, height, true)
        val dest = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)
        val paint = Paint()
        val cm = ColorMatrix()
        cm.setSaturation(0f)
        val contrast = 1.4f
        val brightness = -30f
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
        val scale = if (bitmapWidth * viewHeight > viewWidth * bitmapHeight) viewHeight / bitmapHeight else viewWidth / bitmapWidth
        val dx = (bitmapWidth * scale - viewWidth) * 0.5f
        val dy = (bitmapHeight * scale - viewHeight) * 0.5f
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
