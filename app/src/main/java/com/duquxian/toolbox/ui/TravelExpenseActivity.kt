package com.duquxian.toolbox.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewAssetLoader
import com.duquxian.toolbox.R
import com.duquxian.toolbox.databinding.ActivityTravelExpenseBinding
import com.duquxian.toolbox.debug.DebugLogger
import java.io.ByteArrayInputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TravelExpenseActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTravelExpenseBinding
    private lateinit var webView: WebView
    private val assetLoader by lazy {
        WebViewAssetLoader.Builder()
            .addPathHandler(
                "/assets/",
                WebViewAssetLoader.AssetsPathHandler(this),
            )
            .build()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTravelExpenseBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemUi.configure(window, binding.root)
        DebugLogger.event("TravelExpense", "Tool opened")

        webView = binding.webView
        webView.setBackgroundColor(getColor(R.color.screen_background))
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
            builtInZoomControls = false
            displayZoomControls = false
            textZoom = 100
        }
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?,
            ): WebResourceResponse? {
                val requestUrl = request?.url ?: return blockedResponse()
                if (requestUrl.host != ASSET_HOST) {
                    return blockedResponse()
                }
                return assetLoader.shouldInterceptRequest(requestUrl) ?: blockedResponse()
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean {
                return request?.url?.host != ASSET_HOST
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                DebugLogger.event("TravelExpense", "Page loaded")
            }
        }
        webView.addJavascriptInterface(ToolboxBridge(), "AndroidToolbox")
        webView.loadUrl("https://$ASSET_HOST/assets/travel-expense/index.html")

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    webView.evaluateJavascript(
                        "window.toolboxNavigateBack ? window.toolboxNavigateBack() : false",
                    ) { handled ->
                        if (handled != "true") {
                            finish()
                        }
                    }
                }
            },
        )
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.removeJavascriptInterface("AndroidToolbox")
            webView.loadUrl("about:blank")
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun blockedResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "utf-8",
            403,
            "Forbidden",
            mapOf("Cache-Control" to "no-store"),
            ByteArrayInputStream(ByteArray(0)),
        )
    }

    private fun shareWorkbook(fileName: String, mimeType: String, encodedData: String) {
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) {
                val safeName = fileName
                    .replace(Regex("""[\\/:*?"<>|]"""), "_")
                    .take(160)
                    .ifBlank { "出差报销.xlsx" }
                    .let { if (it.endsWith(".xlsx", ignoreCase = true)) it else "$it.xlsx" }
                val directory = File(cacheDir, "tool-exports").apply { mkdirs() }
                File(directory, safeName).apply {
                    writeBytes(Base64.decode(encodedData, Base64.DEFAULT))
                }
            }
            runCatching {
                val uri = FileProvider.getUriForFile(
                    this@TravelExpenseActivity,
                    "$packageName.fileprovider",
                    file,
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri(file.name, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, getString(R.string.travel_expense_share_title)))
            }.onFailure { error ->
                DebugLogger.error("TravelExpense", "Failed to share workbook", error)
                Toast.makeText(
                    this@TravelExpenseActivity,
                    R.string.travel_expense_file_error,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    private inner class ToolboxBridge {
        @JavascriptInterface
        fun shareFile(fileName: String, mimeType: String, encodedData: String) {
            runOnUiThread {
                shareWorkbook(fileName, mimeType, encodedData)
            }
        }

        @JavascriptInterface
        fun closeTool() {
            runOnUiThread { finish() }
        }
    }

    companion object {
        private const val ASSET_HOST = "appassets.androidplatform.net"
    }
}

