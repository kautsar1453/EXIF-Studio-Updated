package com.exifframestudio.app

import android.Manifest
import android.app.Activity
import android.content.ClipData
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
import androidx.core.content.FileProvider
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val ioExecutor = Executors.newSingleThreadExecutor()

    private data class PendingSaveItem(
        val bytes: ByteArray,
        val fileName: String,
        val mimeType: String
    )

    private var pendingSaveQueue: List<PendingSaveItem>? = null
    private var pendingRequestId: String = ""

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
        val items = pendingSaveQueue
        val reqId = pendingRequestId
        pendingSaveQueue = null
        pendingRequestId = ""

        if (isGranted && !items.isNullOrEmpty()) {
            executeSaveBatchAsync(items, reqId)
        } else {
            notifySaveComplete(reqId, false, "Izin penyimpanan ditolak")
            showToast("Izin penyimpanan ditolak, gagal menyimpan ke Galeri.")
        }
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

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    injectStudioAddons(view)
                }
            }

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

            // Bridge Penyimpanan Galeri (Single & Batch dengan progress + callback selesai)
            addJavascriptInterface(object {
                @JavascriptInterface
                fun saveBase64Image(base64Data: String, fileName: String?) {
                    saveBase64ImageWithId(base64Data, fileName, "")
                }

                @JavascriptInterface
                fun saveBase64ImageWithId(base64Data: String, fileName: String?, requestId: String?) {
                    val reqId = requestId ?: ""
                    val item = parseDataUrl(base64Data, fileName, "EXIF_${System.currentTimeMillis()}")
                    if (item == null) {
                        notifySaveComplete(reqId, false, "Format gambar tidak valid")
                        return
                    }
                    enqueueSaveItems(listOf(item), reqId)
                }

                @JavascriptInterface
                fun saveBatchBase64Images(jsonArrayStr: String, requestId: String?) {
                    val reqId = requestId ?: ""
                    try {
                        val arr = JSONArray(jsonArrayStr)
                        val items = mutableListOf<PendingSaveItem>()
                        val stamp = System.currentTimeMillis()
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            val dataUrl = obj.getString("dataUrl")
                            val name = obj.optString("fileName", "EXIF_Slide_${i + 1}_$stamp.jpg")
                            val parsed = parseDataUrl(dataUrl, name, "EXIF_Slide_${i + 1}_$stamp")
                            if (parsed != null) items.add(parsed)
                        }
                        if (items.isEmpty()) {
                            notifySaveComplete(reqId, false, "Tidak ada gambar untuk disimpan")
                            return
                        }
                        enqueueSaveItems(items, reqId)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        notifySaveComplete(reqId, false, e.message ?: "Gagal memproses batch")
                    }
                }
            }, "AndroidGallery")

            // Bridge Share Native Android (Langsung memunculkan pilihan aplikasi tujuan share)
            addJavascriptInterface(object {
                @JavascriptInterface
                fun shareBase64Image(base64Data: String, fileName: String?) {
                    val item = parseDataUrl(base64Data, fileName, "EXIF_Share_${System.currentTimeMillis()}")
                    if (item == null) {
                        showToast("Gagal menyiapkan gambar untuk dibagikan.")
                        return
                    }
                    shareItemsNative(listOf(item))
                }

                @JavascriptInterface
                fun shareMultipleBase64Images(jsonArrayStr: String) {
                    try {
                        val arr = JSONArray(jsonArrayStr)
                        val items = mutableListOf<PendingSaveItem>()
                        val stamp = System.currentTimeMillis()
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            val dataUrl = obj.getString("dataUrl")
                            val name = obj.optString("fileName", "EXIF_Slide_${i + 1}_$stamp.jpg")
                            val parsed = parseDataUrl(dataUrl, name, "EXIF_Slide_${i + 1}_$stamp")
                            if (parsed != null) items.add(parsed)
                        }
                        if (items.isEmpty()) {
                            showToast("Tidak ada slide untuk dibagikan.")
                            return
                        }
                        shareItemsNative(items)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        showToast("Gagal membagikan slide: ${e.message}")
                    }
                }
            }, "AndroidShare")

            // Menangkap tombol <a download> dari HTML (data:image/... maupun blob:...)
            setDownloadListener(DownloadListener { url, _, contentDisposition, mimeType, _ ->
                val guessedName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                when {
                    url.startsWith("data:") -> {
                        val reqId = "dl_${System.currentTimeMillis()}"
                        evaluateJavascript(
                            "window.__showLightroomSavingHud && window.__showLightroomSavingHud('Menyimpan ke Galeri...', ${quoteJs(guessedName)});",
                            null
                        )
                        val item = parseDataUrl(url, guessedName, "EXIF_${System.currentTimeMillis()}")
                        if (item != null) {
                            enqueueSaveItems(listOf(item), reqId)
                        } else {
                            notifySaveComplete(reqId, false, "Format gambar tidak didukung")
                        }
                    }
                    url.startsWith("blob:") -> {
                        val safeName = guessedName.replace("'", "")
                        val jsConverter = """
                            (function() {
                                if (window.__showLightroomSavingHud) {
                                    window.__showLightroomSavingHud('Menyimpan ke Galeri...', 'Memproses foto resolusi penuh...');
                                }
                                fetch('$url')
                                    .then(res => res.blob())
                                    .then(blob => {
                                        const reader = new FileReader();
                                        reader.onloadend = function() {
                                            const reqId = 'blob_' + Date.now();
                                            AndroidGallery.saveBase64ImageWithId(reader.result, '$safeName', reqId);
                                        };
                                        reader.readAsDataURL(blob);
                                    })
                                    .catch(err => {
                                        if (window.__onGallerySaveFinished) window.__onGallerySaveFinished('', false, String(err));
                                    });
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

    private fun injectStudioAddons(view: WebView?) {
        try {
            val scriptBytes = assets.open("studio-addons.js").use { it.readBytes() }
            val encoded = Base64.encodeToString(scriptBytes, Base64.NO_WRAP)
            val loaderJs = """
                (function() {
                    if (window.__exifStudioAddonsLoaded) return;
                    window.__exifStudioAddonsLoaded = true;
                    try {
                        const bin = atob('$encoded');
                        const bytes = new Uint8Array(bin.length);
                        for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
                        const code = new TextDecoder('utf-8').decode(bytes);
                        const s = document.createElement('script');
                        s.textContent = code;
                        (document.head || document.documentElement).appendChild(s);
                    } catch (e) {
                        console.error('Failed to load studio-addons:', e);
                    }
                })();
            """.trimIndent()
            view?.evaluateJavascript(loaderJs, null)
        } catch (_: Exception) {
            // Jika studio-addons.js sudah dimuat langsung lewat <script> di index.html
        }
    }

    private fun parseDataUrl(dataUrl: String, rawFileName: String?, fallbackBase: String): PendingSaveItem? {
        return try {
            val mimeType = if (dataUrl.startsWith("data:") && dataUrl.contains(";")) {
                dataUrl.substringAfter("data:").substringBefore(";")
            } else {
                "image/jpeg"
            }
            val extension = if (mimeType.contains("png", ignoreCase = true)) "png" else "jpg"
            val cleanName = if (!rawFileName.isNullOrBlank() && rawFileName != "downloadfile.bin") {
                if (rawFileName.contains(".")) rawFileName else "$rawFileName.$extension"
            } else {
                "$fallbackBase.$extension"
            }
            val base64Part = dataUrl.substringAfter(",")
            val bytes = Base64.decode(base64Part, Base64.DEFAULT)
            if (bytes.isEmpty()) null else PendingSaveItem(bytes, cleanName, mimeType)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun enqueueSaveItems(items: List<PendingSaveItem>, requestId: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingSaveQueue = items
            pendingRequestId = requestId
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        executeSaveBatchAsync(items, requestId)
    }

    private fun executeSaveBatchAsync(items: List<PendingSaveItem>, requestId: String) {
        ioExecutor.execute {
            try {
                val total = items.size
                var lastSavedName = ""
                items.forEachIndexed { index, item ->
                    if (total > 1) {
                        updateSaveProgress(requestId, index + 1, total, item.fileName)
                    }
                    writeSingleItemToMediaStore(item)
                    lastSavedName = item.fileName
                }
                val summary = if (total > 1) {
                    "$total slide tersimpan di Pictures/EXIFFrameStudio"
                } else {
                    "Pictures/EXIFFrameStudio/$lastSavedName"
                }
                notifySaveComplete(requestId, true, summary)
            } catch (e: Exception) {
                e.printStackTrace()
                notifySaveComplete(requestId, false, e.message ?: "Gagal menyimpan ke Galeri")
                showToast("Gagal menyimpan ke Galeri: ${e.message}")
            }
        }
    }

    private fun writeSingleItemToMediaStore(item: PendingSaveItem): Uri? {
        val resolver = contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, item.fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType)
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/EXIFFrameStudio"
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Gagal membuat entri MediaStore")

            resolver.openOutputStream(uri)?.use { out ->
                out.write(item.bytes)
                out.flush()
            } ?: throw IllegalStateException("Gagal membuka stream penyimpanan")

            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } else {
            val picturesDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES
            )
            val appDir = File(picturesDir, "EXIFFrameStudio")
            if (!appDir.exists()) appDir.mkdirs()

            val targetFile = File(appDir, item.fileName)
            FileOutputStream(targetFile).use { out ->
                out.write(item.bytes)
                out.flush()
            }

            MediaScannerConnection.scanFile(
                this,
                arrayOf(targetFile.absolutePath),
                arrayOf(item.mimeType),
                null
            )
            return Uri.fromFile(targetFile)
        }
    }

    private fun shareItemsNative(items: List<PendingSaveItem>) {
        ioExecutor.execute {
            try {
                val shareDir = File(cacheDir, "shared_images").apply {
                    if (!exists()) mkdirs()
                }
                val cutoff = System.currentTimeMillis() - 3_600_000L
                shareDir.listFiles()?.forEach { old ->
                    if (old.lastModified() < cutoff) old.delete()
                }

                val uris = ArrayList<Uri>()
                var primaryMime = "image/jpeg"

                for (item in items) {
                    val outFile = File(shareDir, item.fileName)
                    FileOutputStream(outFile).use { fos ->
                        fos.write(item.bytes)
                        fos.flush()
                    }
                    val uri = try {
                        FileProvider.getUriForFile(
                            this,
                            "${packageName}.fileprovider",
                            outFile
                        )
                    } catch (_: IllegalArgumentException) {
                        // Fallback otomatis ke MediaStore jika <provider> belum ditambahkan di AndroidManifest.xml
                        writeSingleItemToMediaStore(item)
                            ?: throw IllegalStateException("Gagal menyiapkan URI gambar untuk dibagikan")
                    }
                    uris.add(uri)
                    primaryMime = item.mimeType
                }

                runOnUiThread {
                    if (uris.size == 1) {
                        val singleUri = uris[0]
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = primaryMime
                            putExtra(Intent.EXTRA_STREAM, singleUri)
                            clipData = ClipData.newRawUri("", singleUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        val chooser = Intent.createChooser(sendIntent, "Bagikan Foto").apply {
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(chooser)
                    } else {
                        val clip = ClipData.newRawUri("", uris[0])
                        for (i in 1 until uris.size) {
                            clip.addItem(ClipData.Item(uris[i]))
                        }
                        val multiIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                            type = "image/*"
                            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                            clipData = clip
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        val chooser = Intent.createChooser(
                            multiIntent,
                            "Bagikan ${uris.size} Slide Instagram"
                        ).apply {
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(chooser)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Gagal membuka menu Bagikan: ${e.message}")
            }
        }
    }

    private fun updateSaveProgress(requestId: String, current: Int, total: Int, fileName: String) {
        runOnUiThread {
            webView.evaluateJavascript(
                "window.__onGallerySaveProgress && window.__onGallerySaveProgress(${quoteJs(requestId)}, $current, $total, ${quoteJs(fileName)});",
                null
            )
        }
    }

    private fun notifySaveComplete(requestId: String, success: Boolean, info: String) {
        runOnUiThread {
            webView.evaluateJavascript(
                "window.__onGallerySaveFinished && window.__onGallerySaveFinished(${quoteJs(requestId)}, $success, ${quoteJs(info)});",
                null
            )
        }
    }

    private fun quoteJs(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "")
        return "'$escaped'"
    }

    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
}
