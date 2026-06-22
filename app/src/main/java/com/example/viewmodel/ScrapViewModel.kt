package com.example.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.api.Content
import com.example.api.GenerateContentRequest
import com.example.api.InlineData
import com.example.api.Part
import com.example.api.RetrofitClient
import com.example.util.ScrapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

sealed class UiState<out T> {
    object Idle : UiState<Nothing>()
    object Loading : UiState<Nothing>()
    data class Success<out T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}

class ScrapViewModel : ViewModel() {

    private val apiKey: String = BuildConfig.GEMINI_API_KEY

    // Helper to check if API key is validated
    fun isApiKeyAvailable(): Boolean {
        return apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY"
    }

    // --- 1. Text Paraphraser State ---
    private val _paraphraseState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val paraphraseState: StateFlow<UiState<String>> = _paraphraseState.asStateFlow()

    fun paraphraseText(text: String, style: String) {
        if (text.isBlank()) {
            _paraphraseState.value = UiState.Error("Please enter some text to paraphrase.")
            return
        }
        if (!isApiKeyAvailable()) {
            _paraphraseState.value = UiState.Error("Gemini API Key is missing. Please set GEMINI_API_KEY in the AI Studio Secrets panel.")
            return
        }

        _paraphraseState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val prompt = "Paraphrase this text in a $style style. Keep it natural, readable, and highly polished. Do not add any greeting, signature, or pre-amble. Only return the paraphrased text.\n\nText:\n$text"
                val responseText = callGeminiApi(prompt)
                _paraphraseState.value = UiState.Success(responseText)
            } catch (e: Exception) {
                _paraphraseState.value = UiState.Error(e.localizeError())
            }
        }
    }

    fun clearParaphraser() {
        _paraphraseState.value = UiState.Idle
    }

    // --- 2. Grammar Fixer State ---
    private val _grammarState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val grammarState: StateFlow<UiState<String>> = _grammarState.asStateFlow()

    fun fixGrammar(text: String) {
        if (text.isBlank()) {
            _grammarState.value = UiState.Error("Please enter some text to fix.")
            return
        }
        if (!isApiKeyAvailable()) {
            _grammarState.value = UiState.Error("Gemini API Key is missing. Please set GEMINI_API_KEY in the AI Studio Secrets panel.")
            return
        }

        _grammarState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val prompt = "Correct any grammatical, punctuation, or spelling errors in this text. Improve clarity and natural flow. Avoid changing the speaker's original intent or tone unless it is very awkward. Show the corrected text. Do not add any greeting, signature, or introduction. Only output the corrected version.\n\nText:\n$text"
                val responseText = callGeminiApi(prompt)
                _grammarState.value = UiState.Success(responseText)
            } catch (e: Exception) {
                _grammarState.value = UiState.Error(e.localizeError())
            }
        }
    }

    fun clearGrammar() {
        _grammarState.value = UiState.Idle
    }

    // --- 3. Username Generator State ---
    private val _usernameState = MutableStateFlow<UiState<List<String>>>(UiState.Idle)
    val usernameState: StateFlow<UiState<List<String>>> = _usernameState.asStateFlow()

    fun generateUsernames(name: String, style: String) {
        if (name.isBlank()) {
            _usernameState.value = UiState.Error("Please enter a name or keyword.")
            return
        }
        if (!isApiKeyAvailable()) {
            _usernameState.value = UiState.Error("Gemini API Key is missing. Please set GEMINI_API_KEY in the AI Studio Secrets panel.")
            return
        }

        _usernameState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val prompt = "Generate 10 clean, aesthetic, realistic, and highly creative usernames inspired by the name/interests: '$name' and standard style: '$style'. Avoid nonsense words or random numbers. Place each username on a clean new line. Do not prefix with numbers or dots, just raw usernames. Do not add any greeting or explanation."
                val responseRaw = callGeminiApi(prompt)
                val usernames = responseRaw.split("\n")
                    .map { it.trim().trimStart('-', '*', '•').trim() }
                    .filter { it.isNotBlank() }
                    .take(10)
                _usernameState.value = UiState.Success(usernames)
            } catch (e: Exception) {
                _usernameState.value = UiState.Error(e.localizeError())
            }
        }
    }

    fun clearUsernames() {
        _usernameState.value = UiState.Idle
    }

    // --- 4. PDF Summarizer State ---
    private val _pdfState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val pdfState: StateFlow<UiState<String>> = _pdfState.asStateFlow()

    private val _pdfInfo = MutableStateFlow<String?>(null)
    val pdfInfo: StateFlow<String?> = _pdfInfo.asStateFlow()

    fun summarizePdf(context: Context, uri: Uri) {
        if (!isApiKeyAvailable()) {
            _pdfState.value = UiState.Error("Gemini API Key is missing. Please set GEMINI_API_KEY in the AI Studio Secrets panel.")
            return
        }

        _pdfState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val size = ScrapUtils.getFileSize(context, uri)
                val isTextFile = context.contentResolver.getType(uri)?.contains("text") == true || uri.path?.endsWith(".txt") == true
                
                if (isTextFile) {
                    _pdfInfo.value = "Text file loaded (${ScrapUtils.formatBytes(size)})"
                    val plainText = withContext(Dispatchers.IO) {
                        ScrapUtils.readTextFromUri(context, uri)
                    }
                    if (plainText.isBlank()) {
                        _pdfState.value = UiState.Error("The text file is empty.")
                        return@launch
                    }
                    val prompt = "Please summarize the following document content. Extract the key takeaways, topics, and important details. Return a concise, clean, and meaningful bulleted list summary. Go straight into the summary without intro or outro greetings.\n\nContent:\n$plainText"
                    val response = callGeminiApi(prompt)
                    _pdfState.value = UiState.Success(response)
                } else {
                    // It's a PDF
                    _pdfInfo.value = "PDF file loaded (${ScrapUtils.formatBytes(size)}). Rendering first page..."
                    val bitmap = withContext(Dispatchers.IO) {
                        ScrapUtils.renderPdfFirstPage(context, uri)
                    }
                    if (bitmap == null) {
                        _pdfState.value = UiState.Error("Failed to render PDF page. Make sure it's a valid, unencrypted PDF document.")
                        return@launch
                    }
                    
                    val base64 = withContext(Dispatchers.IO) {
                        ScrapUtils.bitmapToBase64(bitmap)
                    }
                    if (base64 == null) {
                        _pdfState.value = UiState.Error("Error parsing PDF graphics.")
                        return@launch
                    }

                    _pdfInfo.value = "First page rendered successfully. Summarizing via Gemini..."
                    val response = callGeminiMultimodalApi(
                        prompt = "Please summarize the attached first page of the PDF document. What are the key takeaways, topics, and important details? Deliver a concise, clean, and meaningful bulleted list summary. Under no circumstances should you add intro/outro greetings. Go straight into the summary.",
                        base64Data = base64,
                        mimeType = "image/jpeg"
                    )
                    _pdfState.value = UiState.Success(response)
                }
            } catch (e: Exception) {
                _pdfState.value = UiState.Error(e.localizeError())
            }
        }
    }

    fun clearPdf() {
        _pdfState.value = UiState.Idle
        _pdfInfo.value = null
    }

    // --- 5. Image to Text (OCR) State ---
    private val _ocrState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val ocrState: StateFlow<UiState<String>> = _ocrState.asStateFlow()

    fun extractTextFromImage(context: Context, uri: Uri) {
        if (!isApiKeyAvailable()) {
            _ocrState.value = UiState.Error("Gemini API Key is missing. Please set GEMINI_API_KEY in the AI Studio Secrets panel.")
            return
        }

        _ocrState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    ScrapUtils.uriToBitmap(context, uri)
                }
                if (bitmap == null) {
                    _ocrState.value = UiState.Error("Failed to load selected image.")
                    return@launch
                }

                val base64 = withContext(Dispatchers.IO) {
                    ScrapUtils.bitmapToBase64(bitmap, quality = 85)
                }
                if (base64 == null) {
                    _ocrState.value = UiState.Error("Error encoding image.")
                    return@launch
                }

                val prompt = "Extract and transcribe all readable text from this image. Preserve layout structure if possible. Return strictly the extracted text. Do not summarize, explain, or add commentary. If there is no text, reply exactly with: '[No text detected in image]'"
                val response = callGeminiMultimodalApi(prompt, base64, "image/jpeg")
                _ocrState.value = UiState.Success(response)
            } catch (e: Exception) {
                _ocrState.value = UiState.Error(e.localizeError())
            }
        }
    }

    fun clearOcr() {
        _ocrState.value = UiState.Idle
    }

    // --- 6. Word Counter State (Real-time computed in Compose, no ViewModel trigger needed, but we can helper here) ---
    // Exposing helper for real time statistics calculations
    data class WordStats(
        val words: Int,
        val charsWithSpaces: Int,
        val charsNoSpaces: Int,
        val sentences: Int,
        val paragraphs: Int,
        val readTimeMinutes: Double,
        val speakTimeMinutes: Double
    )

    fun calculateStats(text: String): WordStats {
        if (text.isBlank()) return WordStats(0, 0, 0, 0, 0, 0.0, 0.0)
        
        val trimmed = text.trim()
        val wordList = trimmed.split("\\s+".toRegex()).filter { it.isNotBlank() }
        val words = wordList.size
        val charsWithSpaces = text.length
        val charsNoSpaces = text.replace("\\s".toRegex(), "").length
        
        val sentences = trimmed.split("[.!?]+".toRegex()).filter { it.trim().isNotBlank() }.size
        val paragraphs = trimmed.split("\n+".toRegex()).filter { it.trim().isNotBlank() }.size
        
        // Reading speed: ~200 wpm
        val readTime = words.toDouble() / 200.0
        // Speaking speed: ~130 wpm
        val speakTime = words.toDouble() / 130.0

        return WordStats(words, charsWithSpaces, charsNoSpaces, sentences, paragraphs, readTime, speakTime)
    }

    // --- 7. Image Converter State ---
    private val _convertState = MutableStateFlow<UiState<File>>(UiState.Idle)
    val convertState: StateFlow<UiState<File>> = _convertState.asStateFlow()

    fun convertImage(context: Context, uri: Uri, targetFormat: String) {
        _convertState.value = UiState.Loading
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val bitmap = ScrapUtils.uriToBitmap(context, uri)
                        ?: throw Exception("Failed to decode image.")
                    
                    val outputStream = ByteArrayOutputStream()
                    val compressFormat = when (targetFormat.uppercase()) {
                        "PNG" -> Bitmap.CompressFormat.PNG
                        "WEBP" -> Bitmap.CompressFormat.WEBP
                        else -> Bitmap.CompressFormat.JPEG
                    }
                    val ext = targetFormat.lowercase()
                    
                    bitmap.compress(compressFormat, 100, outputStream)
                    val bytes = outputStream.toByteArray()

                    val cacheDir = context.cacheDir
                    val file = File(cacheDir, "scraptool_converted_${System.currentTimeMillis()}.$ext")
                    FileOutputStream(file).use { fos ->
                        fos.write(bytes)
                    }
                    
                    _convertState.value = UiState.Success(file)
                }
            } catch (e: Exception) {
                _convertState.value = UiState.Error(e.message ?: "Conversion failed.")
            }
        }
    }

    fun clearConverter() {
        _convertState.value = UiState.Idle
    }

    // --- 8. Image Compressor State ---
    data class CompressionResult(
        val file: File,
        val originalSize: Long,
        val compressedSize: Long,
        val ratio: Double
    )

    private val _compressState = MutableStateFlow<UiState<CompressionResult>>(UiState.Idle)
    val compressState: StateFlow<UiState<CompressionResult>> = _compressState.asStateFlow()

    fun compressImage(context: Context, uri: Uri, quality: Int) {
        _compressState.value = UiState.Loading
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val originalSize = ScrapUtils.getFileSize(context, uri)
                    val bitmap = ScrapUtils.uriToBitmap(context, uri)
                        ?: throw Exception("Failed to load selected image.")

                    val outputStream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                    val compressedBytes = outputStream.toByteArray()
                    val compressedSize = compressedBytes.size.toLong()

                    val cacheDir = context.cacheDir
                    val file = File(cacheDir, "scraptool_compressed_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(file).use { fos ->
                        fos.write(compressedBytes)
                    }

                    val ratio = if (originalSize > 0) {
                        (1.0 - (compressedSize.toDouble() / originalSize.toDouble())) * 100.0
                    } else {
                        0.0
                    }

                    _compressState.value = UiState.Success(
                        CompressionResult(
                            file = file,
                            originalSize = originalSize,
                            compressedSize = compressedSize,
                            ratio = ratio
                        )
                    )
                }
            } catch (e: Exception) {
                _compressState.value = UiState.Error(e.message ?: "Compression failed.")
            }
        }
    }

    fun clearCompressor() {
        _compressState.value = UiState.Idle
    }

    // --- Common API Handlers ---

    private suspend fun callGeminiApi(prompt: String): String = withContext(Dispatchers.IO) {
        val request = GenerateContentRequest(
            contents = listOf(
                Content(parts = listOf(Part(text = prompt)))
            )
        )
        val response = RetrofitClient.service.generateContent(apiKey, request)
        val textResult = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
        textResult ?: throw Exception("Gemini returned an empty response. Please verify your prompt or API status.")
    }

    private suspend fun callGeminiMultimodalApi(prompt: String, base64Data: String, mimeType: String): String = withContext(Dispatchers.IO) {
        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(text = prompt),
                        Part(inlineData = InlineData(mimeType = mimeType, data = base64Data))
                    )
                )
            )
        )
        val response = RetrofitClient.service.generateContent(apiKey, request)
        val textResult = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
        textResult ?: throw Exception("Gemini returned an empty response. Please verify input size/quality.")
    }

    private fun Throwable.localizeError(): String {
        val msg = this.message ?: ""
        return when {
            msg.contains("403") -> "Authentication Error: Please check that your Gemini API key is correct and has the necessary permissions."
            msg.contains("400") -> "Bad Request: The generated request contains fields that are not supported or are invalid."
            msg.contains("429") -> "Quota Exceeded: Too many requests have been sent. Please wait a moment and try again."
            msg.contains("500") || msg.contains("503") -> "Server Error: The Gemini API is currently experiencing issues. Please try again later."
            msg.contains("Unable to resolve host") -> "Network Connection Error: Please verify your device's internet connection."
            else -> "Error: ${this.localizedMessage ?: "An unexpected error occurred."}"
        }
    }
}
