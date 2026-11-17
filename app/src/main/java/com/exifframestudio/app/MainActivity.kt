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
                    view?.evaluateJavascript(STUDIO_FINAL_JS, null)
                }
            }

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

            // Bridge Simpan ke Galeri (Single & Batch Slide)
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
                        notifySaveComplete(reqId, false)
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
                            notifySaveComplete(reqId, false)
                            return
                        }
                        enqueueSaveItems(items, reqId)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        notifySaveComplete(reqId, false)
                    }
                }
            }, "AndroidGallery")

            // Bridge Share Native Android
            addJavascriptInterface(object {
                @JavascriptInterface
                fun shareBase64Image(base64Data: String, fileName: String?) {
                    val item = parseDataUrl(base64Data, fileName, "EXIF_Share_${System.currentTimeMillis()}")
                    if (item == null) {
                        showToast("Gagal menyiapkan gambar")
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
                            showToast("Tidak ada slide untuk dibagikan")
                            return
                        }
                        shareItemsNative(items)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        showToast("Gagal membagikan slide")
                    }
                }
            }, "AndroidShare")

            // Menangkap unduhan dari <a download> (data:image/... maupun blob:...)
            setDownloadListener(DownloadListener { url, _, contentDisposition, mimeType, _ ->
                val guessedName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                when {
                    url.startsWith("data:") -> {
                        val reqId = "dl_${System.currentTimeMillis()}"
                        evaluateJavascript(
                            "window.__showLightroomSavingHud && window.__showLightroomSavingHud('Menyimpan...');",
                            null
                        )
                        val item = parseDataUrl(url, guessedName, "EXIF_${System.currentTimeMillis()}")
                        if (item != null) {
                            enqueueSaveItems(listOf(item), reqId)
                        } else {
                            notifySaveComplete(reqId, false)
                        }
                    }
                    url.startsWith("blob:") -> {
                        val safeName = guessedName.replace("'", "")
                        val jsConverter = """
                            (function() {
                                if (window.__showLightroomSavingHud) {
                                    window.__showLightroomSavingHud('Menyimpan...');
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
                                    .catch(() => {
                                        if (window.__onGallerySaveFinished) window.__onGallerySaveFinished('', false);
                                    });
                            })();
                        """.trimIndent()
                        evaluateJavascript(jsConverter, null)
                    }
                }
            })
        }

        setContentView(webView)

        // Tombol Back HP: menutup Preview / Modal terlebih dahulu sebelum keluar aplikasi
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                webView.evaluateJavascript(
                    "(function(){ return (window.__handleAndroidBack && window.__handleAndroidBack()) ? 'handled' : 'pass'; })()"
                ) { result ->
                    if (result != null && result.contains("handled")) {
                        return@evaluateJavascript
                    }
                    if (webView.canGoBack()) {
                        webView.goBack()
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })

        webView.loadUrl("file:///android_asset/index.html")
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
                items.forEachIndexed { index, item ->
                    if (total > 1) {
                        updateSaveProgress(requestId, index + 1, total)
                    }
                    writeSingleItemToMediaStore(item)
                }
                notifySaveComplete(requestId, true)
            } catch (e: Exception) {
                e.printStackTrace()
                notifySaveComplete(requestId, false)
                showToast("Gagal menyimpan ke Galeri")
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
                        writeSingleItemToMediaStore(item)
                            ?: throw IllegalStateException("Gagal menyiapkan URI gambar")
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
                        val chooser = Intent.createChooser(sendIntent, "Bagikan").apply {
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
                            "Bagikan ${uris.size} Slide"
                        ).apply {
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(chooser)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Gagal membuka menu Bagikan")
            }
        }
    }

    private fun updateSaveProgress(requestId: String, current: Int, total: Int) {
        runOnUiThread {
            webView.evaluateJavascript(
                "window.__onGallerySaveProgress && window.__onGallerySaveProgress('$requestId', $current, $total);",
                null
            )
        }
    }

    private fun notifySaveComplete(requestId: String, success: Boolean) {
        runOnUiThread {
            webView.evaluateJavascript(
                "window.__onGallerySaveFinished && window.__onGallerySaveFinished('$requestId', $success);",
                null
            )
        }
    }

    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val STUDIO_FINAL_JS = """
(function () {
  if (window.__studioV3Initialized) return;
  window.__studioV3Initialized = true;
  window.__studioAddonsInitialized = true;

  const state = {
    mode: 'frame', // 'frame' | 'noframe' | 'freestyle'
    originalImg: null,
    exifText: '',
    free: {
      bg: '#ffffff',
      blurBg: false,
      paddingPct: 8,
      radiusPx: 0,
      shadow: true,
      wmLayout: 'bottom-center',
      wmOffsetX: 0,
      wmOffsetY: 0,
      customText: ''
    },
    split: {
      count: 3,
      aspect: 'original'
    },
    preview: {
      open: false,
      scale: 1,
      posX: 0,
      posY: 0
    }
  };

  function extractExifSummaryFromFile(file) {
    return new Promise((resolve) => {
      try {
        if (!file || !file.type || (!file.type.includes('jpeg') && !file.type.includes('jpg'))) {
          resolve('');
          return;
        }
        const reader = new FileReader();
        reader.onload = function (e) {
          try {
            const view = new DataView(e.target.result);
            if (view.byteLength < 4 || view.getUint16(0, false) !== 0xFFD8) {
              resolve('');
              return;
            }
            let offset = 2;
            while (offset + 4 < view.byteLength) {
              const marker = view.getUint16(offset, false);
              offset += 2;
              if (marker === 0xFFE1) {
                const len = view.getUint16(offset, false);
                if (offset + 8 < view.byteLength && view.getUint32(offset + 2, false) === 0x45786966) {
                  const tiffStart = offset + 8;
                  const little = view.getUint16(tiffStart, false) === 0x4949;
                  const ifd0Offset = view.getUint32(tiffStart + 4, little);
                  const tags = {};

                  function readAscii(valOff, count) {
                    let s = '';
                    const start = count <= 4 ? valOff : tiffStart + view.getUint32(valOff, little);
                    for (let i = 0; i < count - 1 && start + i < view.byteLength; i++) {
                      const ch = view.getUint8(start + i);
                      if (ch === 0) break;
                      s += String.fromCharCode(ch);
                    }
                    return s.trim();
                  }

                  function readRational(valOff) {
                    const ptr = tiffStart + view.getUint32(valOff, little);
                    if (ptr + 8 > view.byteLength) return null;
                    const num = view.getUint32(ptr, little);
                    const den = view.getUint32(ptr + 4, little);
                    return den ? num / den : null;
                  }

                  function parseIfd(ifdRel) {
                    const dir = tiffStart + ifdRel;
                    if (dir + 2 > view.byteLength) return;
                    const entries = view.getUint16(dir, little);
                    for (let i = 0; i < entries; i++) {
                      const ent = dir + 2 + i * 12;
                      if (ent + 12 > view.byteLength) break;
                      const tag = view.getUint16(ent, little);
                      const type = view.getUint16(ent + 2, little);
                      const count = view.getUint32(ent + 4, little);
                      const valOff = ent + 8;

                      if (tag === 0x010F && type === 2) tags.make = readAscii(valOff, count);
                      else if (tag === 0x0110 && type === 2) tags.model = readAscii(valOff, count);
                      else if (tag === 0x8769) parseIfd(view.getUint32(valOff, little));
                      else if (tag === 0x829A && type === 5) tags.exposure = readRational(valOff);
                      else if (tag === 0x829D && type === 5) tags.fnumber = readRational(valOff);
                      else if (tag === 0x8827 && type === 3) tags.iso = view.getUint16(valOff, little);
                      else if (tag === 0x920A && type === 5) tags.focal = readRational(valOff);
                    }
                  }

                  parseIfd(ifd0Offset);
                  const parts = [];
                  if (tags.model) {
                    let cam = tags.model;
                    if (tags.make && !cam.toLowerCase().startsWith(tags.make.toLowerCase())) {
                      cam = tags.make + ' ' + cam;
                    }
                    parts.push(cam);
                  }
                  if (tags.focal) parts.push(Math.round(tags.focal) + 'mm');
                  if (tags.fnumber) parts.push('f/' + (Math.round(tags.fnumber * 10) / 10));
                  if (tags.exposure) {
                    const expStr = tags.exposure >= 1
                      ? (Math.round(tags.exposure * 10) / 10) + 's'
                      : '1/' + Math.round(1 / tags.exposure) + 's';
                    parts.push(expStr);
                  }
                  if (tags.iso) parts.push('ISO ' + tags.iso);
                  resolve(parts.join('  •  '));
                  return;
                }
                offset += len;
              } else if ((marker & 0xFF00) === 0xFF00) {
                if (marker === 0xFFDA || marker === 0xFFD9) break;
                const segLen = view.getUint16(offset, false);
                offset += segLen;
              } else {
                break;
              }
            }
            resolve('');
          } catch (_) {
            resolve('');
          }
        };
        reader.onerror = () => resolve('');
        reader.readAsArrayBuffer(file.slice(0, 131072));
      } catch (_) {
        resolve('');
      }
    });
  }

  function handleSelectedImageFile(file) {
    if (!file || !file.type || !file.type.startsWith('image/')) return;
    extractExifSummaryFromFile(file).then((exif) => {
      if (exif) {
        state.exifText = exif;
        if (!state.free.customText) state.free.customText = exif;
      }
      if (state.mode === 'freestyle') renderCustomCanvas();
    });

    const reader = new FileReader();
    reader.onload = function (ev) {
      const img = new Image();
      img.onload = function () {
        state.originalImg = img;
        state.free.wmOffsetX = 0;
        state.free.wmOffsetY = 0;
        if (state.mode !== 'frame') {
          renderCustomCanvas();
        }
      };
      img.src = ev.target.result;
    };
    reader.readAsDataURL(file);
  }

  document.addEventListener('change', function (e) {
    const input = e.target;
    if (input && input.tagName === 'INPUT' && input.type === 'file' && input.files && input.files[0]) {
      handleSelectedImageFile(input.files[0]);
    }
  }, true);

  const style = document.createElement('style');
  style.textContent = `
    body {
      padding-top: 54px !important;
    }

    #efs-top-bar {
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      height: 52px;
      z-index: 2147482000;
      background: rgba(18, 18, 21, 0.96);
      backdrop-filter: blur(12px);
      -webkit-backdrop-filter: blur(12px);
      border-bottom: 1px solid rgba(255, 255, 255, 0.1);
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 6px;
      padding: 6px 10px;
      box-sizing: border-box;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
    }
    .efs-mode-group {
      display: flex;
      align-items: center;
      gap: 4px;
      background: rgba(255, 255, 255, 0.06);
      padding: 3px;
      border-radius: 11px;
      flex: 1;
      min-width: 0;
    }
    .efs-top-btn {
      flex: 1;
      height: 34px;
      border: none;
      border-radius: 8px;
      background: transparent;
      color: #a1a1aa;
      font-size: 12px;
      font-weight: 600;
      white-space: nowrap;
      padding: 0 6px;
      cursor: pointer;
      -webkit-tap-highlight-color: transparent;
    }
    .efs-top-btn.active {
      background: #fafafa;
      color: #09090b;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.35);
    }
    #efs-split-top-btn {
      height: 38px;
      padding: 0 12px;
      border-radius: 11px;
      border: 1px solid rgba(245, 158, 11, 0.45);
      background: rgba(245, 158, 11, 0.16);
      color: #fbbf24;
      font-size: 12px;
      font-weight: 700;
      white-space: nowrap;
      display: inline-flex;
      align-items: center;
      cursor: pointer;
      flex-shrink: 0;
      -webkit-tap-highlight-color: transparent;
    }
    #efs-split-top-btn:active {
      transform: scale(0.96);
    }

    #efs-custom-stage-wrap {
      display: none;
      flex-direction: column;
      align-items: center;
      width: 100%;
      box-sizing: border-box;
      padding: 10px 12px 6px;
    }
    #efs-custom-stage-wrap.active {
      display: flex;
    }
    #efs-custom-canvas {
      max-width: 100%;
      max-height: 52vh;
      width: auto;
      height: auto;
      border-radius: 8px;
      box-shadow: 0 12px 32px rgba(0, 0, 0, 0.45);
      cursor: zoom-in;
      touch-action: none;
    }

    #efs-freestyle-panel {
      display: none;
      width: 100%;
      max-width: 540px;
      margin: 8px auto 12px;
      background: #18181b;
      border: 1px solid rgba(255, 255, 255, 0.1);
      border-radius: 16px;
      padding: 12px 14px;
      box-sizing: border-box;
      color: #fafafa;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
    }
    #efs-freestyle-panel.active {
      display: block;
    }
    .efs-fs-row {
      margin-bottom: 12px;
    }
    .efs-fs-row:last-child {
      margin-bottom: 0;
    }
    .efs-fs-head {
      display: flex;
      justify-content: space-between;
      align-items: center;
      font-size: 12px;
      font-weight: 600;
      color: #d4d4d8;
      margin-bottom: 6px;
    }
    .efs-color-strip {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-wrap: wrap;
    }
    .efs-swatch {
      width: 36px;
      height: 36px;
      border-radius: 10px;
      border: 2px solid rgba(255, 255, 255, 0.18);
      cursor: pointer;
      padding: 0;
      font-size: 10px;
      font-weight: 700;
      color: #fff;
      display: inline-flex;
      align-items: center;
      justify-content: center;
    }
    .efs-swatch.active {
      border-color: #f59e0b;
      transform: scale(1.07);
    }
    .efs-slider {
      width: 100%;
      accent-color: #f59e0b;
      height: 24px;
    }
    .efs-text-input {
      width: 100%;
      height: 40px;
      border-radius: 10px;
      border: 1px solid rgba(255, 255, 255, 0.14);
      background: #09090b;
      color: #fafafa;
      padding: 0 10px;
      font-size: 13px;
      box-sizing: border-box;
    }

    /* Tombol tata letak watermark lebih besar & nyaman disentuh */
    .efs-wm-grid {
      display: grid;
      grid-template-columns: repeat(2, 1fr);
      gap: 8px;
    }
    .efs-wm-btn {
      min-height: 50px;
      padding: 10px 12px;
      border-radius: 12px;
      border: 1.5px solid rgba(255, 255, 255, 0.14);
      background: #27272a;
      color: #e4e4e7;
      font-size: 13px;
      font-weight: 600;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      cursor: pointer;
      -webkit-tap-highlight-color: transparent;
    }
    .efs-wm-btn.active {
      background: rgba(245, 158, 11, 0.2);
      border-color: #f59e0b;
      color: #fbbf24;
    }
    .efs-wm-btn svg {
      width: 21px;
      height: 21px;
      flex-shrink: 0;
    }
    .efs-enlarged-native-wm {
      min-height: 48px !important;
      min-width: 66px !important;
      padding: 10px 14px !important;
      font-size: 13.5px !important;
      border-radius: 12px !important;
    }
    .efs-enlarged-native-wm svg {
      width: 22px !important;
      height: 22px !important;
    }

    /* Loading Lightroom instan tanpa jeda saat klik Simpan */
    #lr-save-backdrop {
      position: fixed;
      inset: 0;
      z-index: 2147483647;
      background: rgba(9, 9, 11, 0.68);
      backdrop-filter: blur(8px);
      -webkit-backdrop-filter: blur(8px);
      display: none;
      align-items: center;
      justify-content: center;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
    }
    #lr-save-backdrop.visible {
      display: flex;
    }
    #lr-save-card {
      width: min(250px, 76vw);
      background: rgba(24, 24, 27, 0.97);
      border: 1px solid rgba(255, 255, 255, 0.14);
      border-radius: 18px;
      padding: 20px 18px 18px;
      box-shadow: 0 24px 48px rgba(0, 0, 0, 0.6);
      color: #fafafa;
      text-align: center;
    }
    .lr-ring-wrap {
      width: 44px;
      height: 44px;
      margin: 0 auto 12px;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .lr-spinner {
      width: 40px;
      height: 40px;
      border-radius: 50%;
      border: 3px solid rgba(255, 255, 255, 0.14);
      border-top-color: #38bdf8;
      animation: lrSpin 0.8s linear infinite;
    }
    .lr-check {
      display: none;
      width: 40px;
      height: 40px;
      border-radius: 50%;
      background: rgba(16, 185, 129, 0.18);
      border: 2px solid #10b981;
      color: #10b981;
      align-items: center;
      justify-content: center;
      font-size: 20px;
      font-weight: 700;
    }
    .lr-error-icon {
      display: none;
      width: 40px;
      height: 40px;
      border-radius: 50%;
      background: rgba(239, 68, 68, 0.18);
      border: 2px solid #ef4444;
      color: #ef4444;
      align-items: center;
      justify-content: center;
      font-size: 19px;
      font-weight: 700;
    }
    #lr-save-title {
      font-size: 14.5px;
      font-weight: 600;
      margin-bottom: 10px;
      color: #ffffff;
    }
    .lr-bar-track {
      width: 100%;
      height: 4px;
      background: rgba(255, 255, 255, 0.12);
      border-radius: 999px;
      overflow: hidden;
    }
    #lr-bar-fill {
      height: 100%;
      width: 32%;
      background: linear-gradient(90deg, #0284c7, #38bdf8);
      border-radius: 999px;
      transition: width 0.22s ease, background 0.22s ease;
    }
    @keyframes lrSpin {
      to { transform: rotate(360deg); }
    }

    /* Halaman Preview Fullscreen: tidak zoom saat dibuka, zoom cubit, keluar dengan Back HP */
    #efs-preview-modal {
      position: fixed;
      inset: 0;
      z-index: 2147483645;
      background: #000000;
      display: none;
      align-items: center;
      justify-content: center;
      overflow: hidden;
      touch-action: none;
      user-select: none;
      -webkit-user-select: none;
    }
    #efs-preview-modal.open {
      display: flex;
    }
    #efs-preview-stage {
      width: 100vw;
      height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      overflow: hidden;
      touch-action: none;
    }
    #efs-preview-img {
      max-width: 100vw;
      max-height: 100vh;
      width: auto;
      height: auto;
      object-fit: contain;
      transform-origin: center center;
      transform: translate3d(0px, 0px, 0px) scale(1);
      will-change: transform;
      pointer-events: none;
    }

    /* Split IG Sheet */
    #ig-split-modal {
      position: fixed;
      inset: 0;
      z-index: 2147483640;
      background: rgba(9, 9, 11, 0.84);
      backdrop-filter: blur(10px);
      -webkit-backdrop-filter: blur(10px);
      display: none;
      flex-direction: column;
      justify-content: flex-end;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      color: #fafafa;
    }
    #ig-split-modal.open {
      display: flex;
    }
    .ig-sheet {
      background: #141417;
      border-top: 1px solid rgba(255, 255, 255, 0.12);
      border-radius: 22px 22px 0 0;
      padding: 16px 18px 24px;
      max-height: 90vh;
      overflow-y: auto;
    }
    .ig-sheet-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 12px;
    }
    .ig-sheet-title {
      font-size: 16px;
      font-weight: 700;
    }
    .ig-close-btn {
      width: 34px;
      height: 34px;
      border-radius: 50%;
      border: 1px solid rgba(255, 255, 255, 0.14);
      background: rgba(255, 255, 255, 0.06);
      color: #e4e4e7;
      font-size: 16px;
      cursor: pointer;
    }
    .ig-preview-box {
      background: #09090b;
      border: 1px solid rgba(255, 255, 255, 0.1);
      border-radius: 14px;
      padding: 12px;
      margin-bottom: 14px;
      display: flex;
      flex-direction: column;
      align-items: center;
    }
    .ig-preview-stage {
      position: relative;
      max-width: 100%;
      max-height: 220px;
      display: inline-block;
      overflow: hidden;
      border-radius: 6px;
    }
    #ig-preview-canvas {
      display: block;
      max-width: 100%;
      max-height: 220px;
      width: auto;
      height: auto;
    }
    #ig-grid-overlay {
      position: absolute;
      inset: 0;
      display: flex;
      pointer-events: none;
    }
    .ig-slice-cell {
      flex: 1;
      height: 100%;
      border-right: 1.5px dashed rgba(251, 191, 36, 0.9);
      position: relative;
      box-sizing: border-box;
    }
    .ig-slice-cell:last-child {
      border-right: none;
    }
    .ig-slice-badge {
      position: absolute;
      top: 6px;
      left: 6px;
      background: rgba(0, 0, 0, 0.72);
      color: #fbbf24;
      font-size: 10px;
      font-weight: 700;
      padding: 2px 6px;
      border-radius: 6px;
    }
    #ig-dim-info {
      margin-top: 8px;
      font-size: 11.5px;
      color: #a1a1aa;
    }
    .ig-seg-row {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 8px;
      margin-bottom: 12px;
    }
    .ig-seg-btn {
      min-height: 44px;
      padding: 10px 8px;
      border-radius: 11px;
      border: 1px solid rgba(255, 255, 255, 0.12);
      background: #1f1f23;
      color: #d4d4d8;
      font-size: 13px;
      font-weight: 600;
      cursor: pointer;
    }
    .ig-seg-btn.active {
      background: rgba(245, 158, 11, 0.18);
      border-color: #f59e0b;
      color: #fbbf24;
    }
    .ig-actions {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 10px;
      margin-top: 4px;
    }
    .ig-btn-primary {
      min-height: 46px;
      border-radius: 12px;
      border: none;
      background: #f59e0b;
      color: #09090b;
      font-size: 13.5px;
      font-weight: 700;
      cursor: pointer;
    }
    .ig-btn-secondary {
      min-height: 46px;
      border-radius: 12px;
      border: 1px solid rgba(255, 255, 255, 0.18);
      background: #27272a;
      color: #ffffff;
      font-size: 13.5px;
      font-weight: 600;
      cursor: pointer;
    }
  `;
  (document.head || document.documentElement).appendChild(style);

  const hudBackdrop = document.createElement('div');
  hudBackdrop.id = 'lr-save-backdrop';
  hudBackdrop.innerHTML =
    '<div id="lr-save-card">' +
      '<div class="lr-ring-wrap">' +
        '<div class="lr-spinner" id="lr-spinner"></div>' +
        '<div class="lr-check" id="lr-check">✓</div>' +
        '<div class="lr-error-icon" id="lr-error-icon">!</div>' +
      '</div>' +
      '<div id="lr-save-title">Menyimpan...</div>' +
      '<div class="lr-bar-track"><div id="lr-bar-fill"></div></div>' +
    '</div>';

  let hudTimer = null;
  let hudProgressInterval = null;
  let hudSafetyTimer = null;

  function showSavingHudInstant(title) {
    if (!hudBackdrop.parentNode && document.body) document.body.appendChild(hudBackdrop);
    clearTimeout(hudTimer);
    clearTimeout(hudSafetyTimer);
    clearInterval(hudProgressInterval);

    const spinner = document.getElementById('lr-spinner');
    const check = document.getElementById('lr-check');
    const errIcon = document.getElementById('lr-error-icon');
    const titleEl = document.getElementById('lr-save-title');
    const fillEl = document.getElementById('lr-bar-fill');

    if (spinner) spinner.style.display = 'block';
    if (check) check.style.display = 'none';
    if (errIcon) errIcon.style.display = 'none';
    if (titleEl) titleEl.textContent = title || 'Menyimpan...';
    if (fillEl) {
      fillEl.style.background = 'linear-gradient(90deg, #0284c7, #38bdf8)';
      fillEl.style.width = '30%';
    }

    hudBackdrop.classList.add('visible');

    let pct = 30;
    hudProgressInterval = setInterval(() => {
      if (pct < 88) {
        pct += (90 - pct) * 0.16;
        if (fillEl) fillEl.style.width = pct.toFixed(1) + '%';
      }
    }, 150);

    hudSafetyTimer = setTimeout(() => {
      clearInterval(hudProgressInterval);
      hudBackdrop.classList.remove('visible');
    }, 2800);
  }

  window.__showLightroomSavingHud = showSavingHudInstant;

  window.__onGallerySaveProgress = function (requestId, current, total) {
    clearTimeout(hudSafetyTimer);
    clearInterval(hudProgressInterval);
    const titleEl = document.getElementById('lr-save-title');
    const fillEl = document.getElementById('lr-bar-fill');
    if (titleEl) titleEl.textContent = 'Menyimpan ' + current + '/' + total + '...';
    if (fillEl) {
      const pct = Math.min(95, Math.round((current / total) * 92));
      fillEl.style.width = pct + '%';
    }
  };

  window.__onGallerySaveFinished = function (requestId, success) {
    clearTimeout(hudSafetyTimer);
    clearInterval(hudProgressInterval);
    clearTimeout(hudTimer);

    const spinner = document.getElementById('lr-spinner');
    const check = document.getElementById('lr-check');
    const errIcon = document.getElementById('lr-error-icon');
    const titleEl = document.getElementById('lr-save-title');
    const fillEl = document.getElementById('lr-bar-fill');

    if (!hudBackdrop.parentNode && document.body) document.body.appendChild(hudBackdrop);
    hudBackdrop.classList.add('visible');

    if (spinner) spinner.style.display = 'none';
    if (success) {
      if (check) check.style.display = 'flex';
      if (errIcon) errIcon.style.display = 'none';
      if (titleEl) titleEl.textContent = 'Tersimpan';
      if (fillEl) {
        fillEl.style.background = '#10b981';
        fillEl.style.width = '100%';
      }
    } else {
      if (check) check.style.display = 'none';
      if (errIcon) errIcon.style.display = 'flex';
      if (titleEl) titleEl.textContent = 'Gagal Menyimpan';
      if (fillEl) {
        fillEl.style.background = '#ef4444';
        fillEl.style.width = '100%';
      }
    }

    hudTimer = setTimeout(() => {
      hudBackdrop.classList.remove('visible');
    }, success ? 1050 : 1800);
  };

  if (window.AndroidGallery && typeof window.AndroidGallery.saveBase64Image === 'function') {
    const origSave = window.AndroidGallery.saveBase64Image.bind(window.AndroidGallery);
    window.AndroidGallery.saveBase64Image = function (dataUrl, fileName) {
      const reqId = 'save_' + Date.now();
      showSavingHudInstant('Menyimpan...');
      if (typeof window.AndroidGallery.saveBase64ImageWithId === 'function') {
        window.AndroidGallery.saveBase64ImageWithId(dataUrl, fileName || '', reqId);
      } else {
        origSave(dataUrl, fileName || '');
        setTimeout(() => window.__onGallerySaveFinished(reqId, true), 650);
      }
    };
  }

  function getNativeAppCanvas() {
    const canvases = Array.from(document.querySelectorAll('canvas')).filter((c) => {
      if (c.id === 'ig-preview-canvas' || c.id === 'efs-custom-canvas') return false;
      const rect = c.getBoundingClientRect();
      return c.width >= 120 && c.height >= 120 && rect.width > 30 && rect.height > 30;
    });
    if (canvases.length > 0) {
      canvases.sort((a, b) => (b.width * b.height) - (a.width * a.height));
      return canvases[0];
    }
    const imgs = Array.from(document.querySelectorAll('img')).filter((img) => {
      if (img.id === 'efs-preview-img') return false;
      const rect = img.getBoundingClientRect();
      return img.complete && img.naturalWidth >= 150 && img.naturalHeight >= 150 && rect.width > 30;
    });
    if (imgs.length > 0) {
      imgs.sort((a, b) => (b.naturalWidth * b.naturalHeight) - (a.naturalWidth * a.naturalHeight));
      const best = imgs[0];
      const off = document.createElement('canvas');
      off.width = best.naturalWidth;
      off.height = best.naturalHeight;
      off.getContext('2d').drawImage(best, 0, 0);
      return off;
    }
    return null;
  }

  function ensureOriginalImageFallback() {
    if (state.originalImg && state.originalImg.complete && state.originalImg.naturalWidth > 0) {
      return state.originalImg;
    }
    const imgs = Array.from(document.querySelectorAll('img')).filter((img) => {
      if (img.id === 'efs-preview-img') return false;
      return img.complete && img.naturalWidth >= 150 && img.naturalHeight >= 150;
    });
    if (imgs.length > 0) {
      imgs.sort((a, b) => (b.naturalWidth * b.naturalHeight) - (a.naturalWidth * a.naturalHeight));
      state.originalImg = imgs[0];
      return imgs[0];
    }
    return null;
  }

  function scrapeExifTextFallback() {
    if (state.exifText) return state.exifText;
    const candidates = Array.from(document.querySelectorAll('input, span, div, p')).filter((el) => {
      if (el.closest('#efs-top-bar') || el.closest('#efs-freestyle-panel') || el.closest('#ig-split-modal')) return false;
      const t = (el.value || el.innerText || '').trim();
      return t.length > 4 && t.length < 90 && /\b(iso|f\/|mm|1\/\d+|nikon|canon|sony|fuji|leica|apple|iphone|samsung|xiaomi)\b/i.test(t);
    });
    if (candidates.length > 0) {
      const txt = (candidates[0].value || candidates[0].innerText || '').trim();
      state.exifText = txt;
      return txt;
    }
    return '';
  }

  function renderCustomCanvas() {
    const customCanvas = document.getElementById('efs-custom-canvas');
    if (!customCanvas) return null;

    const img = ensureOriginalImageFallback();
    if (!img) {
      const nativeC = getNativeAppCanvas();
      if (nativeC) {
        customCanvas.width = nativeC.width;
        customCanvas.height = nativeC.height;
        customCanvas.getContext('2d').drawImage(nativeC, 0, 0);
      }
      return customCanvas;
    }

    const iw = img.naturalWidth;
    const ih = img.naturalHeight;

    if (state.mode === 'noframe') {
      customCanvas.width = iw;
      customCanvas.height = ih;
      const ctx = customCanvas.getContext('2d');
      ctx.imageSmoothingEnabled = true;
      ctx.imageSmoothingQuality = 'high';
      ctx.drawImage(img, 0, 0, iw, ih);
      return customCanvas;
    }

    const f = state.free;
    const padRatio = (f.paddingPct || 0) / 100;
    const baseDim = Math.max(iw, ih);
    const padPx = Math.round(baseDim * padRatio);
    const hasBottomSpace = (f.wmLayout === 'bottom-center' || f.wmLayout === 'bottom-left');
    const extraBottom = hasBottomSpace ? Math.max(padPx, Math.round(baseDim * 0.065)) : padPx;

    const cw = iw + padPx * 2;
    const ch = ih + padPx + extraBottom;
    customCanvas.width = cw;
    customCanvas.height = ch;

    const ctx = customCanvas.getContext('2d');
    ctx.imageSmoothingEnabled = true;
    ctx.imageSmoothingQuality = 'high';

    if (f.blurBg) {
      ctx.save();
      ctx.filter = 'blur(' + Math.max(24, Math.round(baseDim * 0.025)) + 'px) brightness(0.75)';
      const coverScale = Math.max(cw / iw, ch / ih) * 1.15;
      const bw = iw * coverScale;
      const bh = ih * coverScale;
      ctx.drawImage(img, (cw - bw) / 2, (ch - bh) / 2, bw, bh);
      ctx.restore();
    } else {
      ctx.fillStyle = f.bg || '#ffffff';
      ctx.fillRect(0, 0, cw, ch);
    }

    const px = padPx;
    const py = padPx;
    const rad = Math.round((f.radiusPx / 40) * (Math.min(iw, ih) * 0.08));

    ctx.save();
    if (f.shadow && padPx > 0) {
      ctx.shadowColor = 'rgba(0, 0, 0, 0.36)';
      ctx.shadowBlur = Math.round(baseDim * 0.025);
      ctx.shadowOffsetY = Math.round(baseDim * 0.01);
    }

    if (rad > 0) {
      ctx.beginPath();
      ctx.moveTo(px + rad, py);
      ctx.lineTo(px + iw - rad, py);
      ctx.quadraticCurveTo(px + iw, py, px + iw, py + rad);
      ctx.lineTo(px + iw, py + ih - rad);
      ctx.quadraticCurveTo(px + iw, py + ih, px + iw - rad, py + ih);
      ctx.lineTo(px + rad, py + ih);
      ctx.quadraticCurveTo(px, py + ih, px, py + ih - rad);
      ctx.lineTo(px, py + rad);
      ctx.quadraticCurveTo(px, py, px + rad, py);
      ctx.closePath();
      ctx.fillStyle = '#000000';
      ctx.fill();
      ctx.shadowColor = 'transparent';
      ctx.clip();
    }

    ctx.drawImage(img, px, py, iw, ih);
    ctx.restore();

    const wmText = (f.customText || scrapeExifTextFallback() || '').trim();
    if (f.wmLayout !== 'none' && wmText) {
      const fontSize = Math.max(20, Math.round(baseDim * 0.021));
      ctx.save();
      ctx.font = '600 ' + fontSize + 'px -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif';
      ctx.textBaseline = 'middle';

      const isDarkBg = !f.blurBg && (f.bg === '#09090b' || f.bg === '#18181b' || f.bg === '#000000');
      let textColor = isDarkBg ? '#f4f4f5' : '#18181b';
      if (f.blurBg || f.wmLayout === 'inside-bottom') {
        textColor = '#ffffff';
        ctx.shadowColor = 'rgba(0, 0, 0, 0.72)';
        ctx.shadowBlur = Math.round(fontSize * 0.45);
      }
      ctx.fillStyle = textColor;

      let baseX = cw / 2;
      let baseY = py + ih + extraBottom / 2;
      if (f.wmLayout === 'bottom-left') {
        ctx.textAlign = 'left';
        baseX = px + Math.max(12, Math.round(fontSize * 0.5));
      } else if (f.wmLayout === 'inside-bottom') {
        ctx.textAlign = 'center';
        baseX = cw / 2;
        baseY = py + ih - Math.round(fontSize * 1.6);
      } else {
        ctx.textAlign = 'center';
      }

      ctx.fillText(wmText, baseX + f.wmOffsetX, baseY + f.wmOffsetY);
      ctx.restore();
    }

    return customCanvas;
  }

  function getActiveOutputCanvas() {
    if (state.mode === 'frame') {
      const nativeC = getNativeAppCanvas();
      if (nativeC) return nativeC;
    }
    return renderCustomCanvas() || getNativeAppCanvas();
  }

  const previewModal = document.createElement('div');
  previewModal.id = 'efs-preview-modal';
  previewModal.innerHTML =
    '<div id="efs-preview-stage">' +
      '<img id="efs-preview-img" alt="Preview" />' +
    '</div>';

  function applyPreviewTransform() {
    const imgEl = document.getElementById('efs-preview-img');
    if (!imgEl) return;
    imgEl.style.transform =
      'translate3d(' + state.preview.posX + 'px, ' + state.preview.posY + 'px, 0px) scale(' + state.preview.scale + ')';
  }

  function resetPreviewZoom() {
    state.preview.scale = 1;
    state.preview.posX = 0;
    state.preview.posY = 0;
    applyPreviewTransform();
  }

  function openPhotoPreview() {
    const srcCanvas = getActiveOutputCanvas();
    if (!srcCanvas) return;

    if (!previewModal.parentNode && document.body) document.body.appendChild(previewModal);
    const imgEl = document.getElementById('efs-preview-img');
    resetPreviewZoom();
    imgEl.onload = resetPreviewZoom;
    imgEl.src = srcCanvas.toDataURL('image/jpeg', 0.95);
    state.preview.open = true;
    previewModal.classList.add('open');
  }

  function closePhotoPreview() {
    if (!state.preview.open) return false;
    state.preview.open = false;
    previewModal.classList.remove('open');
    resetPreviewZoom();
    return true;
  }

  window.__handleAndroidBack = function () {
    if (state.preview.open) {
      closePhotoPreview();
      return true;
    }
    const splitM = document.getElementById('ig-split-modal');
    if (splitM && splitM.classList.contains('open')) {
      splitM.classList.remove('open');
      return true;
    }
    return false;
  };

  function setupPinchZoomGestures() {
    const stage = document.getElementById('efs-preview-stage');
    if (!stage) return;

    let isPinching = false;
    let isPanning = false;
    let startDist = 1;
    let startScale = 1;
    let startPosX = 0;
    let startPosY = 0;
    let startMidX = 0;
    let startMidY = 0;
    let panStartX = 0;
    let panStartY = 0;
    let lastTapTime = 0;
    let lastTapX = 0;
    let lastTapY = 0;

    function getTouchDist(t1, t2) {
      const dx = t2.clientX - t1.clientX;
      const dy = t2.clientY - t1.clientY;
      return Math.sqrt(dx * dx + dy * dy) || 1;
    }

    function getStageCenterOffset(clientX, clientY) {
      const rect = stage.getBoundingClientRect();
      return {
        x: clientX - (rect.left + rect.width / 2),
        y: clientY - (rect.top + rect.height / 2)
      };
    }

    function clampPan() {
      const maxX = (window.innerWidth * (state.preview.scale - 1)) * 0.55;
      const maxY = (window.innerHeight * (state.preview.scale - 1)) * 0.55;
      state.preview.posX = Math.max(-maxX, Math.min(maxX, state.preview.posX));
      state.preview.posY = Math.max(-maxY, Math.min(maxY, state.preview.posY));
    }

    stage.addEventListener('touchstart', function (e) {
      if (!state.preview.open) return;
      if (e.touches.length === 2) {
        isPinching = true;
        isPanning = false;
        startDist = getTouchDist(e.touches[0], e.touches[1]);
        startScale = state.preview.scale;
        startPosX = state.preview.posX;
        startPosY = state.preview.posY;
        const midClientX = (e.touches[0].clientX + e.touches[1].clientX) / 2;
        const midClientY = (e.touches[0].clientY + e.touches[1].clientY) / 2;
        const mid = getStageCenterOffset(midClientX, midClientY);
        startMidX = mid.x;
        startMidY = mid.y;
      } else if (e.touches.length === 1) {
        const now = Date.now();
        const tx = e.touches[0].clientX;
        const ty = e.touches[0].clientY;
        if (now - lastTapTime < 280 && Math.hypot(tx - lastTapX, ty - lastTapY) < 28) {
          if (state.preview.scale > 1.05) {
            resetPreviewZoom();
          } else {
            const pt = getStageCenterOffset(tx, ty);
            state.preview.scale = 2.5;
            state.preview.posX = -pt.x * 1.2;
            state.preview.posY = -pt.y * 1.2;
            clampPan();
            applyPreviewTransform();
          }
          lastTapTime = 0;
          return;
        }
        lastTapTime = now;
        lastTapX = tx;
        lastTapY = ty;

        if (state.preview.scale > 1.01) {
          isPanning = true;
          panStartX = tx - state.preview.posX;
          panStartY = ty - state.preview.posY;
        }
      }
    }, { passive: false });

    stage.addEventListener('touchmove', function (e) {
      if (!state.preview.open) return;
      e.preventDefault();
      if (isPinching && e.touches.length === 2) {
        const dist = getTouchDist(e.touches[0], e.touches[1]);
        const newScale = Math.max(1, Math.min(5, startScale * (dist / startDist)));
        const midClientX = (e.touches[0].clientX + e.touches[1].clientX) / 2;
        const midClientY = (e.touches[0].clientY + e.touches[1].clientY) / 2;
        const curMid = getStageCenterOffset(midClientX, midClientY);
        const ratio = newScale / startScale;

        state.preview.scale = newScale;
        state.preview.posX = curMid.x - (startMidX - startPosX) * ratio;
        state.preview.posY = curMid.y - (startMidY - startPosY) * ratio;
        if (newScale <= 1.02) {
          state.preview.posX = 0;
          state.preview.posY = 0;
        } else {
          clampPan();
        }
        applyPreviewTransform();
      } else if (isPanning && e.touches.length === 1 && state.preview.scale > 1.01) {
        state.preview.posX = e.touches[0].clientX - panStartX;
        state.preview.posY = e.touches[0].clientY - panStartY;
        clampPan();
        applyPreviewTransform();
      }
    }, { passive: false });

    stage.addEventListener('touchend', function (e) {
      if (e.touches.length < 2) isPinching = false;
      if (e.touches.length === 0) {
        isPanning = false;
        if (state.preview.scale <= 1.03) {
          resetPreviewZoom();
        }
      }
    });
  }

  function blobOrFileToDataUrl(blob) {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onloadend = () => resolve(reader.result);
      reader.onerror = reject;
      reader.readAsDataURL(blob);
    });
  }

  navigator.canShare = function () {
    return true;
  };

  navigator.share = async function (shareData) {
    if (!window.AndroidShare) throw new Error('AndroidShare unavailable');

    if (state.mode !== 'frame') {
      const activeC = getActiveOutputCanvas();
      if (activeC) {
        window.AndroidShare.shareBase64Image(
          activeC.toDataURL('image/jpeg', 0.95),
          'EXIF_' + Date.now() + '.jpg'
        );
        return;
      }
    }

    if (shareData && Array.isArray(shareData.files) && shareData.files.length > 0) {
      if (shareData.files.length === 1) {
        const f = shareData.files[0];
        const dataUrl = await blobOrFileToDataUrl(f);
        window.AndroidShare.shareBase64Image(dataUrl, f.name || ('EXIF_' + Date.now() + '.jpg'));
      } else {
        const payload = [];
        for (let i = 0; i < shareData.files.length; i++) {
          const f = shareData.files[i];
          const dataUrl = await blobOrFileToDataUrl(f);
          payload.push({
            dataUrl: dataUrl,
            fileName: f.name || ('EXIF_Slide_' + (i + 1) + '_' + Date.now() + '.jpg')
          });
        }
        window.AndroidShare.shareMultipleBase64Images(JSON.stringify(payload));
      }
      return;
    }

    if (shareData && typeof shareData.url === 'string' && shareData.url.startsWith('data:image/')) {
      window.AndroidShare.shareBase64Image(shareData.url, 'EXIF_' + Date.now() + '.jpg');
      return;
    }

    const canvas = getActiveOutputCanvas();
    if (canvas) {
      window.AndroidShare.shareBase64Image(canvas.toDataURL('image/jpeg', 0.95), 'EXIF_' + Date.now() + '.jpg');
    }
  };

  function isSaveTriggerElement(el) {
    if (!el) return false;
    const btn = el.closest('button, a, [role="button"]');
    if (!btn) return false;
    if (btn.closest('#efs-top-bar') || btn.closest('#efs-freestyle-panel')) return false;
    const label = (
      (btn.innerText || '') + ' ' +
      (btn.getAttribute('aria-label') || '') + ' ' +
      (btn.getAttribute('title') || '') + ' ' +
      (btn.id || '') + ' ' +
      (typeof btn.className === 'string' ? btn.className : '')
    ).toLowerCase();
    return /\b(simpan|save|download|unduh|export)\b/.test(label);
  }

  document.addEventListener('pointerdown', function (e) {
    if (isSaveTriggerElement(e.target)) {
      showSavingHudInstant('Menyimpan...');
    }
  }, true);

  document.addEventListener('click', function (e) {
    const btn = e.target.closest('button, a, [role="button"]');
    if (!btn) return;
    if (btn.closest('#ig-split-modal') || btn.closest('#efs-top-bar') || btn.closest('#efs-freestyle-panel')) return;

    const label = (
      (btn.innerText || '') + ' ' +
      (btn.getAttribute('aria-label') || '') + ' ' +
      (btn.getAttribute('title') || '') + ' ' +
      (btn.id || '') + ' ' +
      (typeof btn.className === 'string' ? btn.className : '')
    ).toLowerCase();

    if (/\b(simpan|save|download|unduh|export)\b/.test(label)) {
      showSavingHudInstant('Menyimpan...');
      if (state.mode !== 'frame') {
        e.preventDefault();
        e.stopPropagation();
        e.stopImmediatePropagation();
        setTimeout(() => {
          const activeC = getActiveOutputCanvas();
          const reqId = 'custom_' + Date.now();
          if (activeC && window.AndroidGallery && typeof window.AndroidGallery.saveBase64ImageWithId === 'function') {
            window.AndroidGallery.saveBase64ImageWithId(
              activeC.toDataURL('image/jpeg', 0.95),
              'EXIF_' + Date.now() + '.jpg',
              reqId
            );
          } else {
            window.__onGallerySaveFinished(reqId, false);
          }
        }, 20);
      }
      return;
    }

    if (/\b(share|bagikan)\b/.test(label)) {
      if (state.mode !== 'frame') {
        e.preventDefault();
        e.stopPropagation();
        e.stopImmediatePropagation();
        const activeC = getActiveOutputCanvas();
        if (activeC && window.AndroidShare) {
          window.AndroidShare.shareBase64Image(
            activeC.toDataURL('image/jpeg', 0.95),
            'EXIF_' + Date.now() + '.jpg'
          );
        }
        return;
      }

      const href = btn.getAttribute('href') || '';
      if (href.startsWith('data:image/')) {
        e.preventDefault();
        e.stopPropagation();
        window.AndroidShare && window.AndroidShare.shareBase64Image(href, 'EXIF_' + Date.now() + '.jpg');
        return;
      }

      let nativeShareCalled = false;
      const origShare = navigator.share;
      navigator.share = async function (...args) {
        nativeShareCalled = true;
        return origShare.apply(this, args);
      };

      setTimeout(() => {
        navigator.share = origShare;
        if (!nativeShareCalled && window.AndroidShare) {
          const canvas = getActiveOutputCanvas();
          if (canvas) {
            window.AndroidShare.shareBase64Image(canvas.toDataURL('image/jpeg', 0.95), 'EXIF_' + Date.now() + '.jpg');
          }
        }
      }, 140);
    }
  }, true);

  let splitSourceCanvas = null;

  const splitModal = document.createElement('div');
  splitModal.id = 'ig-split-modal';
  splitModal.innerHTML =
    '<div class="ig-sheet">' +
      '<div class="ig-sheet-header">' +
        '<div class="ig-sheet-title">Split IG</div>' +
        '<button type="button" class="ig-close-btn" id="ig-close-btn">✕</button>' +
      '</div>' +
      '<div class="ig-preview-box">' +
        '<div class="ig-preview-stage">' +
          '<canvas id="ig-preview-canvas"></canvas>' +
          '<div id="ig-grid-overlay"></div>' +
        '</div>' +
        '<div id="ig-dim-info"></div>' +
      '</div>' +
      '<div class="ig-seg-row" id="ig-count-group">' +
        '<button type="button" class="ig-seg-btn" data-count="2">2 Slide</button>' +
        '<button type="button" class="ig-seg-btn active" data-count="3">3 Slide</button>' +
        '<button type="button" class="ig-seg-btn" data-count="4">4 Slide</button>' +
      '</div>' +
      '<div class="ig-seg-row" id="ig-aspect-group">' +
        '<button type="button" class="ig-seg-btn active" data-aspect="original">Asli</button>' +
        '<button type="button" class="ig-seg-btn" data-aspect="4:5">4:5</button>' +
        '<button type="button" class="ig-seg-btn" data-aspect="1:1">1:1</button>' +
      '</div>' +
      '<div class="ig-actions">' +
        '<button type="button" class="ig-btn-secondary" id="ig-share-slides-btn">Share</button>' +
        '<button type="button" class="ig-btn-primary" id="ig-save-slides-btn">Simpan</button>' +
      '</div>' +
    '</div>';

  function buildPanoramaCanvas(srcCanvas, slides, aspectMode) {
    const sw = srcCanvas.width;
    const sh = srcCanvas.height;

    if (aspectMode === 'original') {
      const out = document.createElement('canvas');
      out.width = sw;
      out.height = sh;
      out.getContext('2d').drawImage(srcCanvas, 0, 0);
      return out;
    }

    const slideRatio = aspectMode === '4:5' ? (4 / 5) : 1.0;
    const totalRatio = slideRatio * slides;

    let panoW, panoH;
    if (sw / sh >= totalRatio) {
      panoW = sw;
      panoH = Math.round(sw / totalRatio);
    } else {
      panoH = sh;
      panoW = Math.round(sh * totalRatio);
    }

    const out = document.createElement('canvas');
    out.width = panoW;
    out.height = panoH;
    const ctx = out.getContext('2d');

    let bgColor = '#ffffff';
    try {
      const px = srcCanvas.getContext('2d').getImageData(2, 2, 1, 1).data;
      bgColor = 'rgb(' + px[0] + ',' + px[1] + ',' + px[2] + ')';
    } catch (_) {}

    ctx.fillStyle = bgColor;
    ctx.fillRect(0, 0, panoW, panoH);

    const scale = Math.min(panoW / sw, panoH / sh);
    const drawW = Math.round(sw * scale);
    const drawH = Math.round(sh * scale);
    const offsetX = Math.round((panoW - drawW) / 2);
    const offsetY = Math.round((panoH - drawH) / 2);

    ctx.imageSmoothingEnabled = true;
    ctx.imageSmoothingQuality = 'high';
    ctx.drawImage(srcCanvas, offsetX, offsetY, drawW, drawH);
    return out;
  }

  function renderSplitPreview() {
    if (!splitSourceCanvas) return;
    const slides = state.split.count;
    const pano = buildPanoramaCanvas(splitSourceCanvas, slides, state.split.aspect);
    const prevCanvas = document.getElementById('ig-preview-canvas');
    prevCanvas.width = pano.width;
    prevCanvas.height = pano.height;
    prevCanvas.getContext('2d').drawImage(pano, 0, 0);

    const overlay = document.getElementById('ig-grid-overlay');
    overlay.innerHTML = '';
    for (let i = 1; i <= slides; i++) {
      const cell = document.createElement('div');
      cell.className = 'ig-slice-cell';
      cell.innerHTML = '<span class="ig-slice-badge">' + i + '/' + slides + '</span>';
      overlay.appendChild(cell);
    }

    const perW = Math.floor(pano.width / slides);
    document.getElementById('ig-dim-info').textContent = slides + ' Slide • ' + perW + ' × ' + pano.height + ' px';
    document.getElementById('ig-save-slides-btn').textContent = 'Simpan (' + slides + ')';
    document.getElementById('ig-share-slides-btn').textContent = 'Share (' + slides + ')';
  }

  function generateSliceDataUrls() {
    if (!splitSourceCanvas) return [];
    const slides = state.split.count;
    const pano = buildPanoramaCanvas(splitSourceCanvas, slides, state.split.aspect);
    const totalW = pano.width;
    const totalH = pano.height;
    const baseSliceW = Math.floor(totalW / slides);
    const stamp = Date.now();
    const results = [];

    for (let i = 0; i < slides; i++) {
      const sx = i * baseSliceW;
      const sliceW = (i === slides - 1) ? (totalW - sx) : baseSliceW;

      const sliceCanvas = document.createElement('canvas');
      sliceCanvas.width = sliceW;
      sliceCanvas.height = totalH;
      const sctx = sliceCanvas.getContext('2d');
      sctx.imageSmoothingEnabled = true;
      sctx.imageSmoothingQuality = 'high';
      sctx.drawImage(pano, sx, 0, sliceW, totalH, 0, 0, sliceW, totalH);

      results.push({
        dataUrl: sliceCanvas.toDataURL('image/jpeg', 0.95),
        fileName: 'EXIF_IG_' + (i + 1) + 'of' + slides + '_' + stamp + '.jpg'
      });
    }
    return results;
  }

  const topBar = document.createElement('div');
  topBar.id = 'efs-top-bar';
  topBar.innerHTML =
    '<div class="efs-mode-group">' +
      '<button type="button" class="efs-top-btn active" data-mode="frame">Bingkai</button>' +
      '<button type="button" class="efs-top-btn" data-mode="noframe">Tanpa Frame</button>' +
      '<button type="button" class="efs-top-btn" data-mode="freestyle">Gaya Bebas</button>' +
    '</div>' +
    '<button type="button" id="efs-split-top-btn">Split IG</button>';

  const customStageWrap = document.createElement('div');
  customStageWrap.id = 'efs-custom-stage-wrap';
  customStageWrap.innerHTML = '<canvas id="efs-custom-canvas"></canvas>';

  const freePanel = document.createElement('div');
  freePanel.id = 'efs-freestyle-panel';
  freePanel.innerHTML =
    '<div class="efs-fs-row">' +
      '<div class="efs-fs-head"><span>Latar</span></div>' +
      '<div class="efs-color-strip" id="efs-bg-strip">' +
        '<button type="button" class="efs-swatch active" data-bg="#ffffff" style="background:#ffffff;color:#000"></button>' +
        '<button type="button" class="efs-swatch" data-bg="#09090b" style="background:#09090b"></button>' +
        '<button type="button" class="efs-swatch" data-bg="#27272a" style="background:#27272a"></button>' +
        '<button type="button" class="efs-swatch" data-bg="#f5f5f4" style="background:#f5f5f4"></button>' +
        '<button type="button" class="efs-swatch" data-bg="blur" style="background:linear-gradient(135deg,#38bdf8,#f59e0b)">Blur</button>' +
        '<input type="color" id="efs-bg-picker" value="#ffffff" style="width:36px;height:36px;border:none;background:none;padding:0;cursor:pointer" />' +
      '</div>' +
    '</div>' +
    '<div class="efs-fs-row">' +
      '<div class="efs-fs-head"><span>Jarak Tepi</span><span id="efs-pad-val">8%</span></div>' +
      '<input type="range" class="efs-slider" id="efs-pad-slider" min="0" max="24" value="8" />' +
    '</div>' +
    '<div class="efs-fs-row">' +
      '<div class="efs-fs-head"><span>Sudut Foto</span><span id="efs-rad-val">0</span></div>' +
      '<input type="range" class="efs-slider" id="efs-rad-slider" min="0" max="40" value="0" />' +
    '</div>' +
    '<div class="efs-fs-row">' +
      '<div class="efs-fs-head"><span>Tata Letak Watermark</span></div>' +
      '<div class="efs-wm-grid" id="efs-wm-layout-grid">' +
        '<button type="button" class="efs-wm-btn active" data-wm="bottom-center">' +
          '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2"/><line x1="8" y1="18" x2="16" y2="18"/></svg>' +
          '<span>Bawah Tengah</span>' +
        '</button>' +
        '<button type="button" class="efs-wm-btn" data-wm="bottom-left">' +
          '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2"/><line x1="6" y1="18" x2="13" y2="18"/></svg>' +
          '<span>Bawah Kiri</span>' +
        '</button>' +
        '<button type="button" class="efs-wm-btn" data-wm="inside-bottom">' +
          '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2"/><line x1="8" y1="15" x2="16" y2="15"/></svg>' +
          '<span>Dalam Foto</span>' +
        '</button>' +
        '<button type="button" class="efs-wm-btn" data-wm="none">' +
          '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2"/><line x1="5" y1="5" x2="19" y2="19"/></svg>' +
          '<span>Sembunyi</span>' +
        '</button>' +
      '</div>' +
    '</div>' +
    '<div class="efs-fs-row">' +
      '<input type="text" class="efs-text-input" id="efs-wm-text-input" placeholder="Teks EXIF (geser teks di foto)" />' +
    '</div>';

  function switchFrameMode(newMode) {
    state.mode = newMode;
    document.querySelectorAll('#efs-top-bar .efs-top-btn').forEach((b) => {
      b.classList.toggle('active', b.getAttribute('data-mode') === newMode);
    });

    const nativeCanvases = Array.from(document.querySelectorAll('canvas')).filter(
      (c) => c.id !== 'ig-preview-canvas' && c.id !== 'efs-custom-canvas'
    );
    if (newMode === 'frame') {
      customStageWrap.classList.remove('active');
      freePanel.classList.remove('active');
      nativeCanvases.forEach((c) => { c.style.display = ''; });
    } else {
      nativeCanvases.forEach((c) => { c.style.display = 'none'; });
      customStageWrap.classList.add('active');
      freePanel.classList.toggle('active', newMode === 'freestyle');
      const txtInput = document.getElementById('efs-wm-text-input');
      if (txtInput && !txtInput.value) {
        txtInput.value = state.free.customText || scrapeExifTextFallback() || '';
        state.free.customText = txtInput.value;
      }
      renderCustomCanvas();
    }
  }

  function enhanceExistingWatermarkButtons() {
    document.querySelectorAll('button, [role="button"]').forEach((btn) => {
      if (btn.closest('#efs-top-bar') || btn.closest('#efs-freestyle-panel') || btn.closest('#ig-split-modal')) return;
      const parentText = (btn.parentElement && btn.parentElement.parentElement)
        ? (btn.parentElement.parentElement.innerText || '').toLowerCase()
        : '';
      const btnText = (btn.innerText || btn.getAttribute('aria-label') || '').toLowerCase();
      if (
        /\b(watermark|tata letak|layout|posisi|align|logo|teks)\b/.test(parentText) ||
        /\b(kiri|tengah|kanan|bawah|atas|minimal|klasik|modern|leica|fuji|sony)\b/.test(btnText)
      ) {
        btn.classList.add('efs-enlarged-native-wm');
      }
    });
  }

  function mountAll() {
    if (!document.body) return;
    if (!topBar.parentNode) document.body.appendChild(topBar);
    if (!hudBackdrop.parentNode) document.body.appendChild(hudBackdrop);
    if (!previewModal.parentNode) document.body.appendChild(previewModal);
    if (!splitModal.parentNode) document.body.appendChild(splitModal);

    const nativeC = Array.from(document.querySelectorAll('canvas')).find(
      (c) => c.id !== 'ig-preview-canvas' && c.id !== 'efs-custom-canvas'
    );
    if (nativeC && nativeC.parentElement && !customStageWrap.parentNode) {
      nativeC.parentElement.insertBefore(customStageWrap, nativeC);
      nativeC.parentElement.insertBefore(freePanel, nativeC.nextSibling);
    } else if (!customStageWrap.parentNode) {
      document.body.insertBefore(customStageWrap, document.body.firstChild);
      document.body.insertBefore(freePanel, customStageWrap.nextSibling);
    }

    setupPinchZoomGestures();
    enhanceExistingWatermarkButtons();

    topBar.querySelectorAll('.efs-top-btn').forEach((btn) => {
      btn.addEventListener('click', () => {
        switchFrameMode(btn.getAttribute('data-mode'));
      });
    });

    document.getElementById('efs-split-top-btn').addEventListener('click', () => {
      const captured = getActiveOutputCanvas();
      if (!captured) return;
      splitSourceCanvas = captured;
      renderSplitPreview();
      splitModal.classList.add('open');
    });

    let tapStartX = 0;
    let tapStartY = 0;
    let tapStartTime = 0;
    let isDraggingWm = false;
    let wmDragStartX = 0;
    let wmDragStartY = 0;

    document.addEventListener('pointerdown', (e) => {
      const targetMedia = e.target.closest('canvas, img');
      if (!targetMedia || targetMedia.id === 'ig-preview-canvas' || targetMedia.id === 'efs-preview-img') return;
      tapStartX = e.clientX;
      tapStartY = e.clientY;
      tapStartTime = Date.now();

      if (targetMedia.id === 'efs-custom-canvas' && state.mode === 'freestyle' && state.free.wmLayout !== 'none') {
        isDraggingWm = true;
        wmDragStartX = e.clientX;
        wmDragStartY = e.clientY;
      }
    }, true);

    document.addEventListener('pointermove', (e) => {
      if (!isDraggingWm) return;
      const customC = document.getElementById('efs-custom-canvas');
      if (!customC) return;
      const rect = customC.getBoundingClientRect();
      if (rect.width <= 0) return;
      const ratio = customC.width / rect.width;
      const dx = (e.clientX - wmDragStartX) * ratio;
      const dy = (e.clientY - wmDragStartY) * ratio;
      wmDragStartX = e.clientX;
      wmDragStartY = e.clientY;
      state.free.wmOffsetX += dx;
      state.free.wmOffsetY += dy;
      renderCustomCanvas();
    }, true);

    document.addEventListener('pointerup', (e) => {
      isDraggingWm = false;
      const targetMedia = e.target.closest('canvas, img');
      if (!targetMedia || targetMedia.id === 'ig-preview-canvas' || targetMedia.id === 'efs-preview-img') return;
      const moved = Math.hypot(e.clientX - tapStartX, e.clientY - tapStartY);
      const elapsed = Date.now() - tapStartTime;
      if (moved < 10 && elapsed < 360) {
        openPhotoPreview();
      }
    }, true);

    document.querySelectorAll('#efs-bg-strip .efs-swatch').forEach((sw) => {
      sw.addEventListener('click', () => {
        document.querySelectorAll('#efs-bg-strip .efs-swatch').forEach((s) => s.classList.remove('active'));
        sw.classList.add('active');
        const val = sw.getAttribute('data-bg');
        if (val === 'blur') {
          state.free.blurBg = true;
        } else {
          state.free.blurBg = false;
          state.free.bg = val;
        }
        renderCustomCanvas();
      });
    });

    document.getElementById('efs-bg-picker').addEventListener('input', (e) => {
      document.querySelectorAll('#efs-bg-strip .efs-swatch').forEach((s) => s.classList.remove('active'));
      state.free.blurBg = false;
      state.free.bg = e.target.value;
      renderCustomCanvas();
    });

    document.getElementById('efs-pad-slider').addEventListener('input', (e) => {
      state.free.paddingPct = parseInt(e.target.value, 10) || 0;
      document.getElementById('efs-pad-val').textContent = state.free.paddingPct + '%';
      renderCustomCanvas();
    });

    document.getElementById('efs-rad-slider').addEventListener('input', (e) => {
      state.free.radiusPx = parseInt(e.target.value, 10) || 0;
      document.getElementById('efs-rad-val').textContent = state.free.radiusPx;
      renderCustomCanvas();
    });

    document.querySelectorAll('#efs-wm-layout-grid .efs-wm-btn').forEach((btn) => {
      btn.addEventListener('click', () => {
        document.querySelectorAll('#efs-wm-layout-grid .efs-wm-btn').forEach((b) => b.classList.remove('active'));
        btn.classList.add('active');
        state.free.wmLayout = btn.getAttribute('data-wm');
        state.free.wmOffsetX = 0;
        state.free.wmOffsetY = 0;
        renderCustomCanvas();
      });
    });

    document.getElementById('efs-wm-text-input').addEventListener('input', (e) => {
      state.free.customText = e.target.value;
      renderCustomCanvas();
    });

    document.getElementById('ig-close-btn').addEventListener('click', () => {
      splitModal.classList.remove('open');
    });
    splitModal.addEventListener('click', (e) => {
      if (e.target === splitModal) splitModal.classList.remove('open');
    });

    document.querySelectorAll('#ig-count-group .ig-seg-btn').forEach((btn) => {
      btn.addEventListener('click', () => {
        document.querySelectorAll('#ig-count-group .ig-seg-btn').forEach((b) => b.classList.remove('active'));
        btn.classList.add('active');
        state.split.count = parseInt(btn.getAttribute('data-count'), 10) || 3;
        renderSplitPreview();
      });
    });

    document.querySelectorAll('#ig-aspect-group .ig-seg-btn').forEach((btn) => {
      btn.addEventListener('click', () => {
        document.querySelectorAll('#ig-aspect-group .ig-seg-btn').forEach((b) => b.classList.remove('active'));
        btn.classList.add('active');
        state.split.aspect = btn.getAttribute('data-aspect') || 'original';
        renderSplitPreview();
      });
    });

    document.getElementById('ig-save-slides-btn').addEventListener('pointerdown', () => {
      showSavingHudInstant('Menyimpan...');
    });

    document.getElementById('ig-save-slides-btn').addEventListener('click', () => {
      splitModal.classList.remove('open');
      showSavingHudInstant('Menyimpan...');
      setTimeout(() => {
        const slices = generateSliceDataUrls();
        const reqId = 'split_' + Date.now();
        if (slices.length && window.AndroidGallery && typeof window.AndroidGallery.saveBatchBase64Images === 'function') {
          window.AndroidGallery.saveBatchBase64Images(JSON.stringify(slices), reqId);
        } else {
          window.__onGallerySaveFinished(reqId, false);
        }
      }, 25);
    });

    document.getElementById('ig-share-slides-btn').addEventListener('click', () => {
      const slices = generateSliceDataUrls();
