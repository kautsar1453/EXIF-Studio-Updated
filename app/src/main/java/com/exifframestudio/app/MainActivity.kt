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

    private data class SaveItem(val bytes: ByteArray, val fileName: String, val mimeType: String)

    private var pendingQueue: List<SaveItem>? = null
    private var pendingReqId: String = ""

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris = if (result.resultCode == Activity.RESULT_OK) {
            val d = result.data
            when {
                d?.clipData != null -> Array(d.clipData!!.itemCount) { i -> d.clipData!!.getItemAt(i).uri }
                d?.data != null -> arrayOf(d.data!!)
                else -> null
            }
        } else null
        filePathCallback?.onReceiveValue(uris)
        filePathCallback = null
    }

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val items = pendingQueue
        val reqId = pendingReqId
        pendingQueue = null
        pendingReqId = ""
        if (granted && !items.isNullOrEmpty()) executeSaveBatchAsync(items, reqId)
        else {
            notifySaveComplete(reqId, false)
            showToast("Izin penyimpanan ditolak")
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

            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView?,
                    callback: ValueCallback<Array<Uri>>?,
                    params: FileChooserParams?
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

            addJavascriptInterface(object {
                @JavascriptInterface
                fun saveBase64Image(base64Data: String, fileName: String?) =
                    saveBase64ImageWithId(base64Data, fileName, "")

                @JavascriptInterface
                fun saveBase64ImageWithId(base64Data: String, fileName: String?, requestId: String?) {
                    val reqId = requestId ?: ""
                    val item = parseDataUrl(base64Data, fileName, "EXIF_${System.currentTimeMillis()}")
                    if (item == null) notifySaveComplete(reqId, false)
                    else enqueueSaveItems(listOf(item), reqId)
                }

                @JavascriptInterface
                fun saveBatchBase64Images(jsonArrayStr: String, requestId: String?) {
                    val reqId = requestId ?: ""
                    try {
                        val arr = JSONArray(jsonArrayStr)
                        val items = mutableListOf<SaveItem>()
                        val stamp = System.currentTimeMillis()
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            val parsed = parseDataUrl(
                                obj.getString("dataUrl"),
                                obj.optString("fileName", "EXIF_Slide_${i + 1}_$stamp.jpg"),
                                "EXIF_Slide_${i + 1}_$stamp"
                            )
                            if (parsed != null) items.add(parsed)
                        }
                        if (items.isEmpty()) notifySaveComplete(reqId, false)
                        else enqueueSaveItems(items, reqId)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        notifySaveComplete(reqId, false)
                    }
                }
            }, "AndroidGallery")

            addJavascriptInterface(object {
                @JavascriptInterface
                fun shareBase64Image(base64Data: String, fileName: String?) {
                    val item = parseDataUrl(base64Data, fileName, "EXIF_Share_${System.currentTimeMillis()}")
                    if (item == null) showToast("Gagal menyiapkan gambar")
                    else shareItemsNative(listOf(item))
                }

                @JavascriptInterface
                fun shareMultipleBase64Images(jsonArrayStr: String) {
                    try {
                        val arr = JSONArray(jsonArrayStr)
                        val items = mutableListOf<SaveItem>()
                        val stamp = System.currentTimeMillis()
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            val parsed = parseDataUrl(
                                obj.getString("dataUrl"),
                                obj.optString("fileName", "EXIF_Slide_${i + 1}_$stamp.jpg"),
                                "EXIF_Slide_${i + 1}_$stamp"
                            )
                            if (parsed != null) items.add(parsed)
                        }
                        if (items.isEmpty()) showToast("Tidak ada slide untuk dibagikan")
                        else shareItemsNative(items)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        showToast("Gagal membagikan slide")
                    }
                }
            }, "AndroidShare")

            setDownloadListener(DownloadListener { url, _, contentDisposition, mimeType, _ ->
                val guessedName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                when {
                    url.startsWith("data:") -> {
                        val reqId = "dl_${System.currentTimeMillis()}"
                        evaluateJavascript("window.__showLightroomSavingHud&&window.__showLightroomSavingHud('Menyimpan...');", null)
                        val item = parseDataUrl(url, guessedName, "EXIF_${System.currentTimeMillis()}")
                        if (item != null) enqueueSaveItems(listOf(item), reqId)
                        else notifySaveComplete(reqId, false)
                    }
                    url.startsWith("blob:") -> {
                        val safeName = guessedName.replace("'", "")
                        val js = """
                            (function(){
                                if(window.__showLightroomSavingHud)window.__showLightroomSavingHud('Menyimpan...');
                                fetch('$url').then(r=>r.blob()).then(b=>{
                                    const rd=new FileReader();
                                    rd.onloadend=()=>AndroidGallery.saveBase64ImageWithId(rd.result,'$safeName','blob_'+Date.now());
                                    rd.readAsDataURL(b);
                                }).catch(()=>window.__onGallerySaveFinished&&window.__onGallerySaveFinished('',false));
                            })();
                        """.trimIndent()
                        evaluateJavascript(js, null)
                    }
                }
            })
        }

        setContentView(webView)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                webView.evaluateJavascript(
                    "(function(){return(window.__handleAndroidBack&&window.__handleAndroidBack())?'handled':'pass';})()"
                ) { res ->
                    if (res != null && res.contains("handled")) return@evaluateJavascript
                    if (webView.canGoBack()) webView.goBack()
                    else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })

        webView.loadUrl("file:///android_asset/index.html")
    }

    private fun injectStudioAddons(view: WebView?) {
        if (view == null) return
        listOf("addon_core.js", "addon_freestyle.js", "addon_split.js").forEach { name ->
            try {
                val js = assets.open(name).bufferedReader().use { it.readText() }
                view.evaluateJavascript(js, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun parseDataUrl(dataUrl: String, rawName: String?, fallback: String): SaveItem? {
        return try {
            val mime = if (dataUrl.startsWith("data:") && dataUrl.contains(";"))
                dataUrl.substringAfter("data:").substringBefore(";") else "image/jpeg"
            val ext = if (mime.contains("png", ignoreCase = true)) "png" else "jpg"
            val cleanName = if (!rawName.isNullOrBlank() && rawName != "downloadfile.bin") {
                if (rawName.contains(".")) rawName else "$rawName.$ext"
            } else "$fallback.$ext"
            val bytes = Base64.decode(dataUrl.substringAfter(","), Base64.DEFAULT)
            if (bytes.isEmpty()) null else SaveItem(bytes, cleanName, mime)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun enqueueSaveItems(items: List<SaveItem>, reqId: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingQueue = items
            pendingReqId = reqId
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        executeSaveBatchAsync(items, reqId)
    }

    private fun executeSaveBatchAsync(items: List<SaveItem>, reqId: String) {
        ioExecutor.execute {
            try {
                val total = items.size
                items.forEachIndexed { idx, item ->
                    if (total > 1) updateSaveProgress(reqId, idx + 1, total)
                    writeSingleItemToMediaStore(item)
                }
                notifySaveComplete(reqId, true)
            } catch (e: Exception) {
                e.printStackTrace()
                notifySaveComplete(reqId, false)
                showToast("Gagal menyimpan ke Galeri")
            }
        }
    }

    private fun writeSingleItemToMediaStore(item: SaveItem): Uri? {
        val resolver = contentResolver
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, item.fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/EXIFFrameStudio")
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
            uri
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "EXIFFrameStudio")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, item.fileName)
            FileOutputStream(file).use { it.write(item.bytes); it.flush() }
            MediaScannerConnection.scanFile(this, arrayOf(file.absolutePath), arrayOf(item.mimeType), null)
            Uri.fromFile(file)
        }
    }

    private fun shareItemsNative(items: List<SaveItem>) {
        ioExecutor.execute {
            try {
                val shareDir = File(cacheDir, "shared_images").apply { if (!exists()) mkdirs() }
                val cutoff = System.currentTimeMillis() - 3_600_000L
                shareDir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }

                val uris = ArrayList<Uri>()
                var primaryMime = "image/jpeg"
                for (item in items) {
                    val outFile = File(shareDir, item.fileName)
                    FileOutputStream(outFile).use { it.write(item.bytes); it.flush() }
                    val uri = try {
                        FileProvider.getUriForFile(this, "${packageName}.fileprovider", outFile)
                    } catch (_: Exception) {
                        writeSingleItemToMediaStore(item) ?: throw IllegalStateException("Gagal menyiapkan URI")
                    }
                    uris.add(uri)
                    primaryMime = item.mimeType
                }

                runOnUiThread {
                    if (uris.size == 1) {
                        val u = uris[0]
                        val i = Intent(Intent.ACTION_SEND).apply {
                            type = primaryMime
                            putExtra(Intent.EXTRA_STREAM, u)
                            clipData = ClipData.newRawUri("", u)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(i, "Bagikan").apply { addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) })
                    } else {
                        val clip = ClipData.newRawUri("", uris[0])
                        for (k in 1 until uris.size) clip.addItem(ClipData.Item(uris[k]))
                        val i = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                            type = "image/*"
                            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                            clipData = clip
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(i, "Bagikan ${uris.size} Slide").apply { addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) })
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Gagal membuka menu Bagikan")
            }
        }
    }

    private fun updateSaveProgress(reqId: String, current: Int, total: Int) {
        runOnUiThread {
            webView.evaluateJavascript("window.__onGallerySaveProgress&&window.__onGallerySaveProgress('$reqId',$current,$total);", null)
        }
    }

    private fun notifySaveComplete(reqId: String, success: Boolean) {
        runOnUiThread {
            webView.evaluateJavascript("window.__onGallerySaveFinished&&window.__onGallerySaveFinished('$reqId',$success);", null)
        }
    }

    private fun showToast(msg: String) {
        runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
    }
}
