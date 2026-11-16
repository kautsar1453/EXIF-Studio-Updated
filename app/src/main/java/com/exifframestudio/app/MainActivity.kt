package com.exifframestudio.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var pendingBase64: String? = null
    private var pendingFileName: String? = null

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val results: Array<Uri>? = if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val data = result.data!!
            val clipData = data.clipData
            if (clipData != null && clipData.itemCount > 0) {
                Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
            } else if (data.data != null) {
                arrayOf(data.data!!)
            } else {
                null
            }
        } else {
            null
        }
        filePathCallback?.onReceiveValue(results)
        filePathCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        webView.addJavascriptInterface(AndroidBridge(), "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {}

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback

                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
                return try {
                    fileChooserLauncher.launch(Intent.createChooser(intent, "Pilih Foto dari Galeri"))
                    true
                } catch (e: Exception) {
                    this@MainActivity.filePathCallback = null
                    false
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                webView.evaluateJavascript(
                    "(function(){ return window.handleAndroidBackPress ? window.handleAndroidBackPress() : false; })()"
                ) { result ->
                    if (result != "true") {
                        if (webView.canGoBack()) {
                            webView.goBack()
                        } else {
                            isEnabled = false
                            onBackPressedDispatcher.onBackPressed()
                        }
                    }
                }
            }
        })

        webView.loadUrl("file:///android_asset/index.html")
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            val b64 = pendingBase64
            val fname = pendingFileName
            if (b64 != null && fname != null) {
                pendingBase64 = null
                pendingFileName = null
                saveImageToGalleryInternal(b64, fname)
            }
        }
    }

    private fun saveImageToGalleryInternal(base64Data: String, fileName: String) {
        try {
            val cleanBase64 = if (base64Data.contains(",")) {
                base64Data.substringAfter(",")
            } else {
                base64Data
            }
            val imageBytes = Base64.decode(cleanBase64, Base64.DEFAULT)

            val isPng = fileName.endsWith(".png", ignoreCase = true) ||
                    base64Data.startsWith("data:image/png", ignoreCase = true)
            val mimeType = if (isPng) "image/png" else "image/jpeg"
            val ext = if (isPng) "png" else "jpg"
            val baseName = fileName.substringBeforeLast(".").ifBlank { "EXIF_${System.currentTimeMillis()}" }
            val finalFileName = "${baseName}_${System.currentTimeMillis() % 10000}.$ext"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, finalFileName)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/EXIF Frame Studio")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("Gagal membuat entri MediaStore")

                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(imageBytes)
                    outputStream.flush()
                } ?: throw IllegalStateException("Gagal membuka stream penyimpanan")

                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)

                runOnUiThread {
                    Toast.makeText(this, "Tersimpan ke Galeri: $finalFileName", Toast.LENGTH_LONG).show()
                }
            } else {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    pendingBase64 = base64Data
                    pendingFileName = fileName
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                        1001
                    )
                    return
                }

                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val appDir = File(picturesDir, "EXIF Frame Studio")
                if (!appDir.exists()) appDir.mkdirs()

                val outFile = File(appDir, finalFileName)
                FileOutputStream(outFile).use { fos ->
                    fos.write(imageBytes)
                    fos.flush()
                }

                MediaScannerConnection.scanFile(
                    this,
                    arrayOf(outFile.absolutePath),
                    arrayOf(mimeType),
                    null
                )

                runOnUiThread {
                    Toast.makeText(this, "Tersimpan ke Galeri: $finalFileName", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            runOnUiThread {
                Toast.makeText(this, "Gagal menyimpan: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    inner class AndroidBridge {

        @JavascriptInterface
        fun saveImage(base64Data: String, fileName: String) {
            saveImageToGalleryInternal(base64Data, fileName)
        }

        @JavascriptInterface
        fun shareImage(base64Data: String, fileName: String) {
            try {
                val cleanBase64 = if (base64Data.contains(",")) {
                    base64Data.substringAfter(",")
                } else {
                    base64Data
                }
                val imageBytes = Base64.decode(cleanBase64, Base64.DEFAULT)

                val isPng = fileName.endsWith(".png", ignoreCase = true) ||
                        base64Data.startsWith("data:image/png", ignoreCase = true)
                val mimeType = if (isPng) "image/png" else "image/jpeg"
                val ext = if (isPng) "png" else "jpg"
                val safeName = "exif_share_${System.currentTimeMillis()}.$ext"

                val cachePath = File(cacheDir, "shared_images")
                if (!cachePath.exists()) cachePath.mkdirs()

                // Bersihkan file cache lama
                cachePath.listFiles()?.forEach { it.delete() }

                val imageFile = File(cachePath, safeName)
                FileOutputStream(imageFile).use { fos ->
                    fos.write(imageBytes)
                    fos.flush()
                }

                val contentUri = FileProvider.getUriForFile(
                    this@MainActivity,
                    "${applicationContext.packageName}.fileprovider",
                    imageFile
                )

                runOnUiThread {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = mimeType
                        putExtra(Intent.EXTRA_STREAM, contentUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(shareIntent, "Bagikan Foto EXIF"))
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Gagal membagikan: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
