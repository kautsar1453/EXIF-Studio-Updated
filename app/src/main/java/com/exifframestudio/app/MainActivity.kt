package com.exifframestudio.app

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.DownloadListener
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private var pendingBytes: ByteArray? = null
    private var pendingFileName: String = ""
    private var pendingMimeType: String = "image/jpeg"

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris = if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            when {
                data?.clipData != null -> {
                    val count = data.clipData!!.itemCount
                    Array(count) { i -> data.clipData!!.getItemAt(i).uri }
                }
                data?.data != null -> arrayOf(data.data!!)
                else -> null
            }
        } else {
            null
        }
        filePathCallback?.onReceiveValue(uris)
        filePathCallback = null
    }

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val bytes = pendingBytes
        if (isGranted && bytes != null) {
            writeBytesToMediaStore(bytes, pendingFileName, pendingMimeType)
        } else {
            showToast("Izin penyimpanan ditolak, gagal menyimpan ke Galeri.")
        }
        pendingBytes = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true

            webViewClient = WebViewClient()

            // Mengizinkan <input type="file"> memilih foto dari Galeri
            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView?,
                    callback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    filePathCallback?.onReceiveValue(null)
                    filePathCallback = callback

                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "image/*"
                    }
                    return try {
                        fileChooserLauncher.launch(Intent.createChooser(intent, "Pilih Foto"))
                        true
                    } catch (e: Exception) {
                        filePathCallback = null
                        false
                    }
                }
            }

            // Jembatan JS jika HTML memanggil window.AndroidGallery.saveBase64Image(dataUrl, namaFile)
            addJavascriptInterface(object {
                @JavascriptInterface
                fun saveBase64Image(base64Data: String, fileName: String?) {
                    saveDataUrlToGallery(base64Data, fileName)
                }
            }, "AndroidGallery")

            // Menangkap klik tombol <a download> untuk URL offline (data:image/... maupun blob:...)
            setDownloadListener(DownloadListener { url, _, contentDisposition, mimeType, _ ->
                val guessedName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                when {
                    url.startsWith("data:") -> {
                        saveDataUrlToGallery(url, guessedName)
                    }
                    url.startsWith("blob:") -> {
                        val safeName = guessedName.replace("'", "")
                        val jsConverter = """
                            (function() {
                                fetch('$url')
                                    .then(res => res.blob())
                                    .then(blob => {
                                        const reader = new FileReader();
                                        reader.onloadend = function() {
                                            AndroidGallery.saveBase64Image(reader.result, '$safeName');
                                        };
                                        reader.readAsDataURL(blob);
                                    })
                                    .catch(err => console.error(err));
                            })();
                        """.trimIndent()
                        evaluateJavascript(jsConverter, null)
                    }
                    else -> {
                        showToast("Format unduhan tidak didukung secara offline.")
                    }
                }
            })
        }

        setContentView(webView)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        // Memuat halaman aplikasi offline dari app/src/main/assets/index.html
        webView.loadUrl("file:///android_asset/index.html")
    }

    private fun saveDataUrlToGallery(dataUrl: String, rawFileName: String?) {
        try {
            val mimeType = if (dataUrl.startsWith("data:") && dataUrl.contains(";")) {
                dataUrl.substringAfter("data:").substringBefore(";")
            } else {
                "image/jpeg"
            }

            val extension = if (mimeType.contains("png", ignoreCase = true)) "png" else "jpg"
            val cleanName = if (!rawFileName.isNullOrBlank() && rawFileName != "downloadfile.bin") {
                rawFileName
            } else {
                "EXIF_${System.currentTimeMillis()}.$extension"
            }

            val base64Part = dataUrl.substringAfter(",")
            val imageBytes = Base64.decode(base64Part, Base64.DEFAULT)

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                pendingBytes = imageBytes
                pendingFileName = cleanName
                pendingMimeType = mimeType
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return
            }

            writeBytesToMediaStore(imageBytes, cleanName, mimeType)
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("Gagal memproses gambar: ${e.message}")
        }
    }

    private fun writeBytesToMediaStore(bytes: ByteArray, fileName: String, mimeType: String) {
        try {
            val resolver = contentResolver
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/EXIFFrameStudio"
                    )
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("Gagal membuat entri MediaStore")

                resolver.openOutputStream(uri)?.use { out ->
                    out.write(bytes)
                }

                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES
                )
                val appDir = File(picturesDir, "EXIFFrameStudio")
                if (!appDir.exists()) appDir.mkdirs()

                val targetFile = File(appDir, fileName)
                FileOutputStream(targetFile).use { out ->
                    out.write(bytes)
                }

                MediaScannerConnection.scanFile(
                    this,
                    arrayOf(targetFile.absolutePath),
                    arrayOf(mimeType),
                    null
                )
            }

            showToast("Tersimpan ke Galeri: Pictures/EXIFFrameStudio/$fileName")
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("Gagal menyimpan ke Galeri: ${e.message}")
        }
    }

    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
}
