package com.example.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.viewmodel.ScrapViewModel

class UploadMessageHolder {
    var value: ValueCallback<Array<Uri>>? = null
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ScrapApp(viewModel: ScrapViewModel) {
    val context = LocalContext.current
    val holder = remember { UploadMessageHolder() }

    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val results = result.data?.let { data ->
                val clipData = data.clipData
                if (clipData != null) {
                    val count = clipData.itemCount
                    Array(count) { i -> clipData.getItemAt(i).uri }
                } else {
                    data.data?.let { uri -> arrayOf(uri) }
                }
            }
            holder.value?.onReceiveValue(results)
        } else {
            holder.value?.onReceiveValue(null)
        }
        holder.value = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090A10))
            .statusBarsPadding()
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewClient = WebViewClient()
                    webChromeClient = object : WebChromeClient() {
                        override fun onShowFileChooser(
                            webView: WebView?,
                            filePathCallback: ValueCallback<Array<Uri>>?,
                            fileChooserParams: FileChooserParams?
                        ): Boolean {
                            holder.value?.onReceiveValue(null)
                            holder.value = filePathCallback

                            val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                                type = "*/*"
                                addCategory(Intent.CATEGORY_OPENABLE)
                            }
                            try {
                                fileChooserLauncher.launch(intent)
                            } catch (e: Exception) {
                                holder.value?.onReceiveValue(null)
                                holder.value = null
                                return false
                            }
                            return true
                        }
                    }

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = true
                        allowContentAccess = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }

                    loadUrl("file:///android_asset/index.html")
                }
            }
        )
    }
}
