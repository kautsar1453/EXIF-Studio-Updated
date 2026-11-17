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
                "window.__onGallerySaveProgress && window.__onGallerySaveProgress('$requestId', $current,$total);",
                null
            )
        }
    }

    private fun notifySaveComplete(requestId: String, success: Boolean) {
        runOnUiThread {
            webView.evaluateJavascript(
                "window.__onGallerySaveFinished && window.__onGallerySaveFinished('$requestId',$success);",
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
(function(){
if(window.__efsInit)return;window.__efsInit=true;
const S={mode:'frame',img:null,exif:'',free:{bg:'#ffffff',blur:false,pad:8,rad:0,wm:'bottom-center',ox:0,oy:0,txt:''},split:{n:3,ar:'original'},pv:{open:false,s:1,x:0,y:0}};

function readExif(file){
  return new Promise(res=>{
    try{
      if(!file||!file.type||!/jpe?g/i.test(file.type))return res('');
      const r=new FileReader();
      r.onload=e=>{
        try{
          const v=new DataView(e.target.result);
          if(v.byteLength<4||v.getUint16(0,false)!==0xFFD8)return res('');
          let o=2;
          while(o+4<v.byteLength){
            const m=v.getUint16(o,false);o+=2;
            if(m===0xFFE1){
              const l=v.getUint16(o,false);
              if(o+8<v.byteLength&&v.getUint32(o+2,false)===0x45786966){
                const ts=o+8,le=v.getUint16(ts,false)===0x4949,t={};
                const asc=(vo,c)=>{let s='',st=c<=4?vo:ts+v.getUint32(vo,le);for(let i=0;i<c-1&&st+i<v.byteLength;i++){const ch=v.getUint8(st+i);if(!ch)break;s+=String.fromCharCode(ch);}return s.trim();};
                const rat=vo=>{const p=ts+v.getUint32(vo,le);if(p+8>v.byteLength)return null;const d=v.getUint32(p+4,le);return d?v.getUint32(p,le)/d:null;};
                const ifd=rel=>{const d=ts+rel;if(d+2>v.byteLength)return;const n=v.getUint16(d,le);for(let i=0;i<n;i++){const en=d+2+i*12;if(en+12>v.byteLength)break;const tg=v.getUint16(en,le),tp=v.getUint16(en+2,le),cnt=v.getUint32(en+4,le),vo=en+8;if(tg===0x010F&&tp===2)t.mk=asc(vo,cnt);else if(tg===0x0110&&tp===2)t.md=asc(vo,cnt);else if(tg===0x8769)ifd(v.getUint32(vo,le));else if(tg===0x829A&&tp===5)t.ex=rat(vo);else if(tg===0x829D&&tp===5)t.fn=rat(vo);else if(tg===0x8827&&tp===3)t.iso=v.getUint16(vo,le);else if(tg===0x920A&&tp===5)t.fl=rat(vo);}};
                ifd(v.getUint32(ts+4,le));
                const p=[];
                if(t.md){let c=t.md;if(t.mk&&!c.toLowerCase().startsWith(t.mk.toLowerCase()))c=t.mk+' '+c;p.push(c);}
                if(t.fl)p.push(Math.round(t.fl)+'mm');
                if(t.fn)p.push('f/'+(Math.round(t.fn*10)/10));
                if(t.ex)p.push(t.ex>=1?(Math.round(t.ex*10)/10)+'s':'1/'+Math.round(1/t.ex)+'s');
                if(t.iso)p.push('ISO '+t.iso);
                return res(p.join(' • '));
              }
              o+=l;
            }else if((m&0xFF00)===0xFF00){if(m===0xFFDA||m===0xFFD9)break;o+=v.getUint16(o,false);}else break;
          }
          res('');
        }catch(_){res('');}
      };
      r.onerror=()=>res('');
      r.readAsArrayBuffer(file.slice(0,131072));
    }catch(_){res('');}
  });
}

document.addEventListener('change',e=>{
  const inp=e.target;
  if(inp&&inp.tagName==='INPUT'&&inp.type==='file'&&inp.files&&inp.files[0]){
    const f=inp.files[0];
    if(!f.type||!f.type.startsWith('image/'))return;
    readExif(f).then(ex=>{if(ex){S.exif=ex;if(!S.free.txt)S.free.txt=ex;}if(S.mode==='freestyle')drawCustom();});
    const fr=new FileReader();
    fr.onload=ev=>{const im=new Image();im.onload=()=>{S.img=im;S.free.ox=0;S.free.oy=0;if(S.mode!=='frame')drawCustom();};im.src=ev.target.result;};
    fr.readAsDataURL(f);
  }
},true);

const css=document.createElement('style');
css.textContent=`
body{padding-top:54px!important}
#efs-bar{position:fixed;top:0;left:0;right:0;height:52px;z-index:2147482000;background:rgba(18,18,21,.96);backdrop-filter:blur(12px);border-bottom:1px solid rgba(255,255,255,.1);display:flex;align-items:center;gap:6px;padding:6px 10px;box-sizing:border-box;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif}
.efs-grp{display:flex;gap:4px;background:rgba(255,255,255,.06);padding:3px;border-radius:11px;flex:1}
.efs-mbtn{flex:1;height:34px;border:none;border-radius:8px;background:transparent;color:#a1a1aa;font-size:12px;font-weight:600;white-space:nowrap;padding:0 6px;cursor:pointer}
.efs-mbtn.on{background:#fafafa;color:#09090b}
#efs-spbtn{height:38px;padding:0 12px;border-radius:11px;border:1px solid rgba(245,158,11,.45);background:rgba(245,158,11,.16);color:#fbbf24;font-size:12px;font-weight:700;white-space:nowrap;cursor:pointer}
#efs-cwrap{display:none;flex-direction:column;align-items:center;width:100%;padding:10px 12px 6px;box-sizing:border-box}
#efs-cwrap.on{display:flex}
#efs-ccanvas{max-width:100%;max-height:52vh;width:auto;height:auto;border-radius:8px;box-shadow:0 12px 32px rgba(0,0,0,.45);touch-action:none}
#efs-fpanel{display:none;width:100%;max-width:540px;margin:8px auto 12px;background:#18181b;border:1px solid rgba(255,255,255,.1);border-radius:16px;padding:12px 14px;box-sizing:border-box;color:#fafafa;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif}
#efs-fpanel.on{display:block}
.efs-row{margin-bottom:12px}.efs-row:last-child{margin-bottom:0}
.efs-hd{display:flex;justify-content:space-between;font-size:12px;font-weight:600;color:#d4d4d8;margin-bottom:6px}
.efs-sws{display:flex;align-items:center;gap:8px;flex-wrap:wrap}
.efs-sw{width:36px;height:36px;border-radius:10px;border:2px solid rgba(255,255,255,.18);cursor:pointer;padding:0;font-size:10px;font-weight:700;color:#fff}
.efs-sw.on{border-color:#f59e0b;transform:scale(1.07)}
.efs-sl{width:100%;accent-color:#f59e0b;height:24px}
.efs-inp{width:100%;height:40px;border-radius:10px;border:1px solid rgba(255,255,255,.14);background:#09090b;color:#fafafa;padding:0 10px;font-size:13px;box-sizing:border-box}
.efs-wmg{display:grid;grid-template-columns:repeat(2,1fr);gap:8px}
.efs-wmb{min-height:50px;padding:10px 12px;border-radius:12px;border:1.5px solid rgba(255,255,255,.14);background:#27272a;color:#e4e4e7;font-size:13px;font-weight:600;display:flex;align-items:center;justify-content:center;gap:8px;cursor:pointer}
.efs-wmb.on{background:rgba(245,158,11,.2);border-color:#f59e0b;color:#fbbf24}
.efs-wmb svg{width:21px;height:21px;flex-shrink:0}
.efs-big-wm{min-height:48px!important;min-width:66px!important;padding:10px 14px!important;font-size:13.5px!important;border-radius:12px!important}
.efs-big-wm svg{width:22px!important;height:22px!important}
#lr-hud{position:fixed;inset:0;z-index:2147483647;background:rgba(9,9,11,.68);backdrop-filter:blur(8px);display:none;align-items:center;justify-content:center;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif}
#lr-hud.on{display:flex}
#lr-box{width:min(240px,75vw);background:rgba(24,24,27,.97);border:1px solid rgba(255,255,255,.14);border-radius:18px;padding:20px 18px 18px;box-shadow:0 24px 48px rgba(0,0,0,.6);color:#fff;text-align:center}
.lr-rw{width:44px;height:44px;margin:0 auto 12px;display:flex;align-items:center;justify-content:center}
#lr-sp{width:40px;height:40px;border-radius:50%;border:3px solid rgba(255,255,255,.14);border-top-color:#38bdf8;animation:lrS .8s linear infinite}
#lr-ok{display:none;width:40px;height:40px;border-radius:50%;background:rgba(16,185,129,.18);border:2px solid #10b981;color:#10b981;align-items:center;justify-content:center;font-size:20px;font-weight:700}
#lr-er{display:none;width:40px;height:40px;border-radius:50%;background:rgba(239,68,68,.18);border:2px solid #ef4444;color:#ef4444;align-items:center;justify-content:center;font-size:19px;font-weight:700}
#lr-tt{font-size:14.5px;font-weight:600;margin-bottom:10px}
.lr-tr{width:100%;height:4px;background:rgba(255,255,255,.12);border-radius:999px;overflow:hidden}
#lr-fl{height:100%;width:32%;background:linear-gradient(90deg,#0284c7,#38bdf8);border-radius:999px;transition:width .2s ease}
@keyframes lrS{to{transform:rotate(360deg)}}
#efs-pv{position:fixed;inset:0;z-index:2147483645;background:#000;display:none;align-items:center;justify-content:center;overflow:hidden;touch-action:none;user-select:none}
#efs-pv.on{display:flex}
#efs-pvs{width:100vw;height:100vh;display:flex;align-items:center;justify-content:center;overflow:hidden;touch-action:none}
#efs-pvi{max-width:100vw;max-height:100vh;width:auto;height:auto;object-fit:contain;transform-origin:center center;transform:translate3d(0,0,0) scale(1);will-change:transform;pointer-events:none}
#ig-mod{position:fixed;inset:0;z-index:2147483640;background:rgba(9,9,11,.84);backdrop-filter:blur(10px);display:none;flex-direction:column;justify-content:flex-end;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif;color:#fafafa}
#ig-mod.on{display:flex}
.ig-sh{background:#141417;border-top:1px solid rgba(255,255,255,.12);border-radius:22px 22px 0 0;padding:16px 18px 24px;max-height:90vh;overflow-y:auto}
.ig-hd{display:flex;align-items:center;justify-content:space-between;margin-bottom:12px}
.ig-xb{width:34px;height:34px;border-radius:50%;border:1px solid rgba(255,255,255,.14);background:rgba(255,255,255,.06);color:#e4e4e7;font-size:16px;cursor:pointer}
.ig-pb{background:#09090b;border:1px solid rgba(255,255,255,.1);border-radius:14px;padding:12px;margin-bottom:14px;display:flex;flex-direction:column;align-items:center}
.ig-ps{position:relative;max-width:100%;max-height:220px;display:inline-block;overflow:hidden;border-radius:6px}
#ig-pc{display:block;max-width:100%;max-height:220px;width:auto;height:auto}
#ig-ov{position:absolute;inset:0;display:flex;pointer-events:none}
.ig-cl{flex:1;height:100%;border-right:1.5px dashed rgba(251,191,36,.9);position:relative;box-sizing:border-box}.ig-cl:last-child{border-right:none}
.ig-bg{position:absolute;top:6px;left:6px;background:rgba(0,0,0,.72);color:#fbbf24;font-size:10px;font-weight:700;padding:2px 6px;border-radius:6px}
#ig-dm{margin-top:8px;font-size:11.5px;color:#a1a1aa}
.ig-sr{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;margin-bottom:12px}
.ig-sb{min-height:44px;padding:10px 8px;border-radius:11px;border:1px solid rgba(255,255,255,.12);background:#1f1f23;color:#d4d4d8;font-size:13px;font-weight:600;cursor:pointer}
.ig-sb.on{background:rgba(245,158,11,.18);border-color:#f59e0b;color:#fbbf24}
.ig-ac{display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-top:4px}
.ig-bp{min-height:46px;border-radius:12px;border:none;background:#f59e0b;color:#09090b;font-size:13.5px;font-weight:700;cursor:pointer}
.ig-bs{min-height:46px;border-radius:12px;border:1px solid rgba(255,255,255,.18);background:#27272a;color:#fff;font-size:13.5px;font-weight:600;cursor:pointer}
`;
(document.head||document.documentElement).appendChild(css);

const hud=document.createElement('div');
hud.id='lr-hud';
hud.innerHTML='<div id="lr-box"><div class="lr-rw"><div id="lr-sp"></div><div id="lr-ok">✓</div><div id="lr-er">!</div></div><div id="lr-tt">Menyimpan...</div><div class="lr-tr"><div id="lr-fl"></div></div></div>';
let hT=null,hI=null,hS=null;

function showHud(txt){
  if(!hud.parentNode&&document.body)document.body.appendChild(hud);
  clearTimeout(hT);clearTimeout(hS);clearInterval(hI);
  const sp=document.getElementById('lr-sp'),ok=document.getElementById('lr-ok'),er=document.getElementById('lr-er'),tt=document.getElementById('lr-tt'),fl=document.getElementById('lr-fl');
  if(sp)sp.style.display='block';if(ok)ok.style.display='none';if(er)er.style.display='none';
  if(tt)tt.textContent=txt||'Menyimpan...';
  if(fl){fl.style.background='linear-gradient(90deg,#0284c7,#38bdf8)';fl.style.width='32%';}
  hud.classList.add('on');
  let p=32;
  hI=setInterval(()=>{if(p<88){p+=(90-p)*0.16;if(fl)fl.style.width=p.toFixed(1)+'%';}},140);
  hS=setTimeout(()=>{clearInterval(hI);hud.classList.remove('on');},2800);
}
window.__showLightroomSavingHud=showHud;
window.__onGallerySaveProgress=(id,c,t)=>{
  clearTimeout(hS);clearInterval(hI);
  const tt=document.getElementById('lr-tt'),fl=document.getElementById('lr-fl');
  if(tt)tt.textContent='Menyimpan '+c+'/'+t+'...';
  if(fl)fl.style.width=Math.min(95,Math.round((c/t)*92))+'%';
};
window.__onGallerySaveFinished=(id,okFlag)=>{
  clearTimeout(hS);clearInterval(hI);clearTimeout(hT);
  if(!hud.parentNode&&document.body)document.body.appendChild(hud);
  hud.classList.add('on');
  const sp=document.getElementById('lr-sp'),ok=document.getElementById('lr-ok'),er=document.getElementById('lr-er'),tt,fl=document.getElementById('lr-fl');
  if(sp)sp.style.display='none';
  if(okFlag){
    if(ok)ok.style.display='flex';if(er)er.style.display='none';if(tt)tt.textContent='Tersimpan';
    if(fl){fl.style.background='#10b981';fl.style.width='100%';}
  }else{
    if(ok)ok.style.display='none';if(er)er.style.display='flex';if(tt)tt.textContent='Gagal Menyimpan';
    if(fl){fl.style.background='#ef4444';fl.style.width='100%';}
  }
  hT=setTimeout(()=>hud.classList.remove('on'),okFlag?1000:1700);
};

if(window.AndroidGallery&&typeof window.AndroidGallery.saveBase64Image==='function'){
  const orig=window.AndroidGallery.saveBase64Image.bind(window.AndroidGallery);
  window.AndroidGallery.saveBase64Image=(u,n)=>{
    const id='s_'+Date.now();showHud('Menyimpan...');
    if(window.AndroidGallery.saveBase64ImageWithId)window.AndroidGallery.saveBase64ImageWithId(u,n||'',id);
    else{orig(u,n||'');setTimeout(()=>window.__onGallerySaveFinished(id,true),650);}
  };
}

function getNativeCanvas(){
  const cs=Array.from(document.querySelectorAll('canvas')).filter(c=>c.id!=='ig-pc'&&c.id!=='efs-ccanvas'&&c.width>=120&&c.height>=120&&c.getBoundingClientRect().width>30);
  if(cs.length){cs.sort((a,b)=>(b.width*b.height)-(a.width*a.height));return cs[0];}
  const ims=Array.from(document.querySelectorAll('img')).filter(i=>i.id!=='efs-pvi'&&i.complete&&i.naturalWidth>=150&&i.getBoundingClientRect().width>30);
  if(ims.length){ims.sort((a,b)=>(b.naturalWidth*b.naturalHeight)-(a.naturalWidth*a.naturalHeight));const o=document.createElement('canvas');o.width=ims[0].naturalWidth;o.height=ims[0].naturalHeight;o.getContext('2d').drawImage(ims[0],0,0);return o;}
  return null;
}
function getOrigImg(){
  if(S.img&&S.img.complete&&S.img.naturalWidth>0)return S.img;
  const ims=Array.from(document.querySelectorAll('img')).filter(i=>i.id!=='efs-pvi'&&i.complete&&i.naturalWidth>=150);
  if(ims.length){ims.sort((a,b)=>(b.naturalWidth*b.naturalHeight)-(a.naturalWidth*a.naturalHeight));S.img=ims[0];return ims[0];}
  return null;
}
function getExifFallback(){
  if(S.exif)return S.exif;
  const els=Array.from(document.querySelectorAll('input,span,div,p')).filter(el=>!el.closest('#efs-bar,#efs-fpanel,#ig-mod'));
  for(const el of els){
    const t=(el.value||el.innerText||'').trim();
    if(t.length>4&&t.length<90&&/\b(iso|f\/|mm|1\/\d+|nikon|canon|sony|fuji|leica|apple|iphone|samsung|xiaomi)\b/i.test(t)){S.exif=t;return t;}
  }
  return '';
}

function drawCustom(){
  const cc=document.getElementById('efs-ccanvas');if(!cc)return null;
  const im=getOrigImg();
  if(!im){const nc=getNativeCanvas();if(nc){cc.width=nc.width;cc.height=nc.height;cc.getContext('2d').drawImage(nc,0,0);}return cc;}
  const iw=im.naturalWidth,ih=im.naturalHeight;
  if(S.mode==='noframe'){cc.width=iw;cc.height=ih;const x=cc.getContext('2d');x.imageSmoothingEnabled=true;x.imageSmoothingQuality='high';x.drawImage(im,0,0,iw,ih);return cc;}
  const f=S.free,bd=Math.max(iw,ih),pad=Math.round(bd*(f.pad/100));
  const bot=(f.wm==='bottom-center'||f.wm==='bottom-left')?Math.max(pad,Math.round(bd*0.065)):pad;
  const cw=iw+pad*2,ch=ih+pad+bot;
  cc.width=cw;cc.height=ch;
  const x=cc.getContext('2d');x.imageSmoothingEnabled=true;x.imageSmoothingQuality='high';
  if(f.blur){
    x.save();x.filter='blur('+Math.max(24,Math.round(bd*0.025))+'px) brightness(0.75)';
    const sc=Math.max(cw/iw,ch/ih)*1.15;x.drawImage(im,(cw-iw*sc)/2,(ch-ih*sc)/2,iw*sc,ih*sc);x.restore();
  }else{x.fillStyle=f.bg;x.fillRect(0,0,cw,ch);}
  const rad=Math.round((f.rad/40)*(Math.min(iw,ih)*0.08));
  x.save();
  if(pad>0){x.shadowColor='rgba(0,0,0,.36)';x.shadowBlur=Math.round(bd*0.025);x.shadowOffsetY=Math.round(bd*0.01);}
  if(rad>0){
    x.beginPath();x.moveTo(pad+rad,pad);x.lineTo(pad+iw-rad,pad);x.quadraticCurveTo(pad+iw,pad,pad+iw,pad+rad);
    x.lineTo(pad+iw,pad+ih-rad);x.quadraticCurveTo(pad+iw,pad+ih,pad+iw-rad,pad+ih);
    x.lineTo(pad+rad,pad+ih);x.quadraticCurveTo(pad,pad+ih,pad,pad+ih-rad);
    x.lineTo(pad,pad+rad);x.quadraticCurveTo(pad,pad,pad+rad,pad);x.closePath();
    x.fillStyle='#000';x.fill();x.shadowColor='transparent';x.clip();
  }
  x.drawImage(im,pad,pad,iw,ih);x.restore();
  const wt=(f.txt||getExifFallback()||'').trim();
  if(f.wm!=='none'&&wt){
    const fs=Math.max(20,Math.round(bd*0.021));
    x.save();x.font='600 '+fs+'px -apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif';x.textBaseline='middle';
    const dark=!f.blur&&(f.bg==='#09090b'||f.bg==='#18181b'||f.bg==='#000000');
    let col=dark?'#f4f4f5':'#18181b';
    if(f.blur||f.wm==='inside-bottom'){col='#fff';x.shadowColor='rgba(0,0,0,.72)';x.shadowBlur=Math.round(fs*0.45);}
    x.fillStyle=col;
    let bx=cw/2,by=pad+ih+bot/2;
    if(f.wm==='bottom-left'){x.textAlign='left';bx=pad+Math.max(12,Math.round(fs*0.5));}
    else if(f.wm==='inside-bottom'){x.textAlign='center';by=pad+ih-Math.round(fs*1.6);}
    else x.textAlign='center';
    x.fillText(wt,bx+f.ox,by+f.oy);x.restore();
  }
  return cc;
}
function getOutCanvas(){return S.mode==='frame'?(getNativeCanvas()||drawCustom()):(drawCustom()||getNativeCanvas());}

const pvMod=document.createElement('div');
pvMod.id='efs-pv';
pvMod.innerHTML='<div id="efs-pvs"><img id="efs-pvi" alt="" /></div>';
function applyPv(){const i=document.getElementById('efs-pvi');if(i)i.style.transform='translate3d('+S.pv.x+'px,'+S.pv.y+'px,0) scale('+S.pv.s+')';}
function resetPv(){S.pv.s=1;S.pv.x=0;S.pv.y=0;applyPv();}
function openPv(){
  const c=getOutCanvas();if(!c)return;
  if(!pvMod.parentNode&&document.body)document.body.appendChild(pvMod);
  const i=document.getElementById('efs-pvi');
  resetPv();i.onload=resetPv;i.src=c.toDataURL('image/jpeg',0.95);
  S.pv.open=true;pvMod.classList.add('on');
}
function closePv(){if(!S.pv.open)return false;S.pv.open=false;pvMod.classList.remove('on');resetPv();return true;}
window.__handleAndroidBack=()=>{
  if(S.pv.open)return closePv();
  const m=document.getElementById('ig-mod');if(m&&m.classList.contains('on')){m.classList.remove('on');return true;}
  return false;
};

function bindPinch(){
  const st=document.getElementById('efs-pvs');if(!st)return;
  let pin=false,pan=false,sd=1,ss=1,spx=0,spy=0,smx=0,smy=0,px0=0,py0=0,lt=0,lx=0,ly=0;
  const dist=(a,b)=>Math.hypot(b.clientX-a.clientX,b.clientY-a.clientY)||1;
  const off=(cx,cy)=>{const r=st.getBoundingClientRect();return{x:cx-(r.left+r.width/2),y:cy-(r.top+r.height/2)};};
  const clamp=()=>{const mx=window.innerWidth*(S.pv.s-1)*0.55,my=window.innerHeight*(S.pv.s-1)*0.55;S.pv.x=Math.max(-mx,Math.min(mx,S.pv.x));S.pv.y=Math.max(-my,Math.min(my,S.pv.y));};
  st.addEventListener('touchstart',e=>{
    if(!S.pv.open)return;
    if(e.touches.length===2){
      pin=true;pan=false;sd=dist(e.touches[0],e.touches[1]);ss=S.pv.s;spx=S.pv.x;spy=S.pv.y;
      const m=off((e.touches[0].clientX+e.touches[1].clientX)/2,(e.touches[0].clientY+e.touches[1].clientY)/2);smx=m.x;smy=m.y;
    }else if(e.touches.length===1){
      const now=Date.now(),tx=e.touches[0].clientX,ty=e.touches[0].clientY;
      if(now-lt<280&&Math.hypot(tx-lx,ty-ly)<28){
        if(S.pv.s>1.05)resetPv();
        else{const p=off(tx,ty);S.pv.s=2.5;S.pv.x=-p.x*1.2;S.pv.y=-p.y*1.2;clamp();applyPv();}
        lt=0;return;
      }
      lt=now;lx=tx;ly=ty;
      if(S.pv.s>1.01){pan=true;px0=tx-S.pv.x;py0=ty-S.pv.y;}
    }
  },{passive:false});
  st.addEventListener('touchmove',e=>{
    if(!S.pv.open)return;e.preventDefault();
    if(pin&&e.touches.length===2){
      const d=dist(e.touches[0],e.touches[1]),ns=Math.max(1,Math.min(5,ss*(d/sd)));
      const cm=off((e.touches[0].clientX+e.touches[1].clientX)/2,(e.touches[0].clientY+e.touches[1].clientY)/2),rt=ns/ss;
      S.pv.s=ns;S.pv.x=cm.x-(smx-spx)*rt;S.pv.y=cm.y-(smy-spy)*rt;
      if(ns<=1.02){S.pv.x=0;S.pv.y=0;}else clamp();
      applyPv();
    }else if(pan&&e.touches.length===1&&S.pv.s>1.01){
      S.pv.x=e.touches[0].clientX-px0;S.pv.y=e.touches[0].clientY-py0;clamp();applyPv();
    }
  },{passive:false});
  st.addEventListener('touchend',e=>{
    if(e.touches.length<2)pin=false;
    if(!e.touches.length){pan=false;if(S.pv.s<=1.03)resetPv();}
  });
}

const toData=b=>new Promise((res,rej)=>{const r=new FileReader();r.onloadend=()=>res(r.result);r.onerror=rej;r.readAsDataURL(b);});
navigator.canShare=()=>true;
navigator.share=async d=>{
  if(!window.AndroidShare)return;
  if(S.mode!=='frame'){const c=getOutCanvas();if(c){window.AndroidShare.shareBase64Image(c.toDataURL('image/jpeg',0.95),'EXIF_'+Date.now()+'.jpg');return;}}
  if(d&&Array.isArray(d.files)&&d.files.length){
    if(d.files.length===1){const u=await toData(d.files[0]);window.AndroidShare.shareBase64Image(u,d.files[0].name||'EXIF_'+Date.now()+'.jpg');}
    else{const a=[];for(let i=0;i<d.files.length;i++)a.push({dataUrl:await toData(d.files[i]),fileName:d.files[i].name||'EXIF_'+(i+1)+'.jpg'});window.AndroidShare.shareMultipleBase64Images(JSON.stringify(a));}
    return;
  }
  if(d&&typeof d.url==='string'&&d.url.startsWith('data:image/')){window.AndroidShare.shareBase64Image(d.url,'EXIF_'+Date.now()+'.jpg');return;}
  const c=getOutCanvas();if(c)window.AndroidShare.shareBase64Image(c.toDataURL('image/jpeg',0.95),'EXIF_'+Date.now()+'.jpg');
};

const btnLbl=b=>((b.innerText||'')+' '+(b.getAttribute('aria-label')||'')+' '+(b.getAttribute('title')||'')+' '+(b.id||'')+' '+(typeof b.className==='string'?b.className:'')).toLowerCase();

['touchstart','pointerdown'].forEach(ev=>{
  document.addEventListener(ev,e=>{
    const b=e.target.closest('button,a,[role="button"]');
    if(!b||b.closest('#efs-bar,#efs-fpanel'))return;
    if(/\b(simpan|save|download|unduh|export)\b/.test(btnLbl(b)))showHud('Menyimpan...');
  },true);
});

document.addEventListener('click',e=>{
  const b=e.target.closest('button,a,[role="button"]');
  if(!b||b.closest('#ig-mod,#efs-bar,#efs-fpanel'))return;
  const l=btnLbl(b);
  if(/\b(simpan|save|download|unduh|export)\b/.test(l)){
    showHud('Menyimpan...');
    if(S.mode!=='frame'){
      e.preventDefault();e.stopPropagation();e.stopImmediatePropagation();
      const c=getOutCanvas(),id='c_'+Date.now();
      if(c&&window.AndroidGallery&&window.AndroidGallery.saveBase64ImageWithId)window.AndroidGallery.saveBase64ImageWithId(c.toDataURL('image/jpeg',0.95),'EXIF_'+Date.now()+'.jpg',id);
      else window.__onGallerySaveFinished(id,false);
    }
    return;
  }
  if(/\b(share|bagikan)\b/.test(l)){
    if(S.mode!=='frame'){
      e.preventDefault();e.stopPropagation();e.stopImmediatePropagation();
      const c=getOutCanvas();if(c&&window.AndroidShare)window.AndroidShare.shareBase64Image(c.toDataURL('image/jpeg',0.95),'EXIF_'+Date.now()+'.jpg');
      return;
    }
    const h=b.getAttribute('href')||'';
    if(h.startsWith('data:image/')){e.preventDefault();e.stopPropagation();window.AndroidShare&&window.AndroidShare.shareBase64Image(h,'EXIF_'+Date.now()+'.jpg');return;}
    let called=false;const os=navigator.share;
    navigator.share=async(...a)=>{called=true;return os.apply(this,a);};
    setTimeout(()=>{navigator.share=os;if(!called&&window.AndroidShare){const c=getOutCanvas();if(c)window.AndroidShare.shareBase64Image(c.toDataURL('image/jpeg',0.95),'EXIF_'+Date.now()+'.jpg');}},140);
  }
},true);

let spSrc=null;
const spMod=document.createElement('div');
spMod.id='ig-mod';
spMod.innerHTML='<div class="ig-sh"><div class="ig-hd"><b style="font-size:16px">Split IG</b><button type="button" class="ig-xb" id="ig-x">✕</button></div><div class="ig-pb"><div class="ig-ps"><canvas id="ig-pc"></canvas><div id="ig-ov"></div></div><div id="ig-dm"></div></div><div class="ig-sr" id="ig-cg"><button type="button" class="ig-sb" data-n="2">2 Slide</button><button type="button" class="ig-sb on" data-n="3">3 Slide</button><button type="button" class="ig-sb" data-n="4">4 Slide</button></div><div class="ig-sr" id="ig-ag"><button type="button" class="ig-sb on" data-a="original">Asli</button><button type="button" class="ig-sb" data-a="4:5">4:5</button><button type="button" class="ig-sb" data-a="1:1">1:1</button></div><div class="ig-ac"><button type="button" class="ig-bs" id="ig-shb">Share</button><button type="button" class="ig-bp" id="ig-svb">Simpan</button></div></div>';

function mkPano(sc,n,ar){
  const sw=sc.width,sh=sc.height;
  if(ar==='original'){const o=document.createElement('canvas');o.width=sw;o.height=sh;o.getContext('2d').drawImage(sc,0,0);return o;}
  const tr=(ar==='4:5'?0.8:1)*n;
  let pw,ph;if(sw/sh>=tr){pw=sw;ph=Math.round(sw/tr);}else{ph=sh;pw=Math.round(sh*tr);}
  const o=document.createElement('canvas');o.width=pw;o.height=ph;const x=o.getContext('2d');
  let bg='#fff';try{const d=sc.getContext('2d').getImageData(2,2,1,1).data;bg='rgb('+d[0]+','+d[1]+','+d[2]+')';}catch(_){}
  x.fillStyle=bg;x.fillRect(0,0,pw,ph);
  const k=Math.min(pw/sw,ph/sh),dw=Math.round(sw*k),dh=Math.round(sh*k);
  x.imageSmoothingEnabled=true;x.imageSmoothingQuality='high';
  x.drawImage(sc,Math.round((pw-dw)/2),Math.round((ph-dh)/2),dw,dh);return o;
}
function drawSplit(){
  if(!spSrc)return;
  const n=S.split.n,p=mkPano(spSrc,n,S.split.ar),pc=document.getElementById('ig-pc');
  pc.width=p.width;pc.height=p.height;pc.getContext('2d').drawImage(p,0,0);
  const ov=document.getElementById('ig-ov');ov.innerHTML='';
  for(let i=1;i<=n;i++){const d=document.createElement('div');d.className='ig-cl';d.innerHTML='<span class="ig-bg">'+i+'/'+n+'</span>';ov.appendChild(d);}
  document.getElementById('ig-dm').textContent=n+' Slide • '+Math.floor(p.width/n)+' × '+p.height+' px';
  document.getElementById('ig-svb').textContent='Simpan ('+n+')';
  document.getElementById('ig-shb').textContent='Share ('+n+')';
}
function sliceUrls(){
  if(!spSrc)return[];
  const n=S.split.n,p=mkPano(spSrc,n,S.split.ar),tw=p.width,th=p.height,bw=Math.floor(tw/n),st=Date.now(),out=[];
  for(let i=0;i<n;i++){
    const sx=i*bw,sw=(i===n-1)?(tw-sx):bw,c=document.createElement('canvas');c.width=sw;c.height=th;
    const x=c.getContext('2d');x.imageSmoothingEnabled=true;x.imageSmoothingQuality='high';x.drawImage(p,sx,0,sw,th,0,0,sw,th);
    out.push({dataUrl:c.toDataURL('image/jpeg',0.95),fileName:'EXIF_IG_'+(i+1)+'of'+n+'_'+st+'.jpg'});
  }
  return out;
}

const bar=document.createElement('div');
bar.id='efs-bar';
bar.innerHTML='<div class="efs-grp"><button type="button" class="efs-mbtn on" data-m="frame">Bingkai</button><button type="button" class="efs-mbtn" data-m="noframe">Tanpa Frame</button><button type="button" class="efs-mbtn" data-m="freestyle">Gaya Bebas</button></div><button type="button" id="efs-spbtn">Split IG</button>';

const cwrap=document.createElement('div');cwrap.id='efs-cwrap';cwrap.innerHTML='<canvas id="efs-ccanvas"></canvas>';
const fpanel=document.createElement('div');fpanel.id='efs-fpanel';
fpanel.innerHTML='<div class="efs-row"><div class="efs-hd"><span>Latar</span></div><div class="efs-sws" id="efs-sws"><button type="button" class="efs-sw on" data-b="#ffffff" style="background:#fff;color:#000"></button><button type="button" class="efs-sw" data-b="#09090b" style="background:#09090b"></button><button type="button" class="efs-sw" data-b="#27272a" style="background:#27272a"></button><button type="button" class="efs-sw" data-b="#f5f5f4" style="background:#f5f5f4"></button><button type="button" class="efs-sw" data-b="blur" style="background:linear-gradient(135deg,#38bdf8,#f59e0b)">Blur</button><input type="color" id="efs-cp" value="#ffffff" style="width:36px;height:36px;border:none;background:none;padding:0"/></div></div><div class="efs-row"><div class="efs-hd"><span>Jarak Tepi</span><span id="efs-pv2">8%</span></div><input type="range" class="efs-sl" id="efs-ps" min="0" max="24" value="8"/></div><div class="efs-row"><div class="efs-hd"><span>Sudut Foto</span><span id="efs-rv">0</span></div><input type="range" class="efs-sl" id="efs-rs" min="0" max="40" value="0"/></div><div class="efs-row"><div class="efs-hd"><span>Tata Letak Watermark</span></div><div class="efs-wmg" id="efs-wmg"><button type="button" class="efs-wmb on" data-w="bottom-center"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2"/><line x1="8" y1="18" x2="16" y2="18"/></svg><span>Bawah Tengah</span></button><button type="button" class="efs-wmb" data-w="bottom-left"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2"/><line x1="6" y1="18" x2="13" y2="18"/></svg><span>Bawah Kiri</span></button><button type="button" class="efs-wmb" data-w="inside-bottom"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2"/><line x1="8" y1="15" x2="16" y2="15"/></svg><span>Dalam Foto</span></button><button type="button" class="efs-wmb" data-w="none"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2"/><line x1="5" y1="5" x2="19" y2="19"/></svg><span>Sembunyi</span></button></div></div><div class="efs-row"><input type="text" class="efs-inp" id="efs-wt" placeholder="Teks EXIF (geser di foto)"/></div>';

function setMode(m){
  S.mode=m;
  bar.querySelectorAll('.efs-mbtn').forEach(b=>b.classList.toggle('on',b.getAttribute('data-m')===m));
  const ncs=Array.from(document.querySelectorAll('canvas')).filter(c=>c.id!=='ig-pc'&&c.id!=='efs-ccanvas');
  if(m==='frame'){cwrap.classList.remove('on');fpanel.classList.remove('on');ncs.forEach(c=>c.style.display='');}
  else{
    ncs.forEach(c=>c.style.display='none');cwrap.classList.add('on');fpanel.classList.toggle('on',m==='freestyle');
    const ti=document.getElementById('efs-wt');if(ti&&!ti.value){ti.value=S.free.txt||getExifFallback()||'';S.free.txt=ti.value;}
    drawCustom();
  }
}

function init(){
  if(!document.body)return;
  if(!bar.parentNode)document.body.appendChild(bar);
  if(!hud.parentNode)document.body.appendChild(hud);
  if(!pvMod.parentNode)document.body.appendChild(pvMod);
  if(!spMod.parentNode)document.body.appendChild(spMod);
  const nc=Array.from(document.querySelectorAll('canvas')).find(c=>c.id!=='ig-pc'&&c.id!=='efs-ccanvas');
  if(nc&&nc.parentElement&&!cwrap.parentNode){nc.parentElement.insertBefore(cwrap,nc);nc.parentElement.insertBefore(fpanel,nc.nextSibling);}
  else if(!cwrap.parentNode){document.body.insertBefore(cwrap,document.body.firstChild);document.body.insertBefore(fpanel,cwrap.nextSibling);}

  bindPinch();
  document.querySelectorAll('button,[role="button"]').forEach(b=>{
    if(b.closest('#efs-bar,#efs-fpanel,#ig-mod'))return;
    const pt=(b.parentElement&&b.parentElement.parentElement)?(b.parentElement.parentElement.innerText||'').toLowerCase():'';
    const bt=(b.innerText||b.getAttribute('aria-label')||'').toLowerCase();
    if(/\b(watermark|tata letak|layout|posisi|align|logo|teks)\b/.test(pt)||/\b(kiri|tengah|kanan|bawah|atas|minimal|klasik|modern|leica|fuji|sony)\b/.test(bt))b.classList.add('efs-big-wm');
  });

  bar.querySelectorAll('.efs-mbtn').forEach(b=>b.addEventListener('click',()=>setMode(b.getAttribute('data-m'))));
  document.getElementById('efs-spbtn').addEventListener('click',()=>{const c=getOutCanvas();if(!c)return;spSrc=c;drawSplit();spMod.classList.add('on');});

  let sx=0,sy=0,st=0,dr=false,dx0=0,dy0=0;
  document.addEventListener('pointerdown',e=>{
    const m=e.target.closest('canvas,img');if(!m||m.id==='ig-pc'||m.id==='efs-pvi')return;
    sx=e.clientX;sy=e.clientY;st=Date.now();
    if(m.id==='efs-ccanvas'&&S.mode==='freestyle'&&S.free.wm!=='none'){dr=true;dx0=e.clientX;dy0=e.clientY;}
  },true);
  document.addEventListener('pointermove',e=>{
    if(!dr)return;const cc=document.getElementById('efs-ccanvas');if(!cc)return;
    const r=cc.getBoundingClientRect();if(r.width<=0)return;const k=cc.width/r.width;
    S.free.ox+=(e.clientX-dx0)*k;S.free.oy+=(e.clientY-dy0)*k;dx0=e.clientX;dy0=e.clientY;drawCustom();
  },true);
  document.addEventListener('pointerup',e=>{
    dr=false;const m=e.target.closest('canvas,img');if(!m||m.id==='ig-pc'||m.id==='efs-pvi')return;
    if(Math.hypot(e.clientX-sx,e.clientY-sy)<10&&Date.now()-st<360)openPv();
  },true);

  document.querySelectorAll('#efs-sws .efs-sw').forEach(s=>s.addEventListener('click',()=>{
    document.querySelectorAll('#efs-sws .efs-sw').forEach(x=>x.classList.remove('on'));s.classList.add('on');
    const v=s.getAttribute('data-b');if(v==='blur')S.free.blur=true;else{S.free.blur=false;S.free.bg=v;}drawCustom();
  }));
  document.getElementById('efs-cp').addEventListener('input',e=>{document.querySelectorAll('#efs-sws .efs-sw').forEach(x=>x.classList.remove('on'));S.free.blur=false;S.free.bg=e.target.value;drawCustom();});
  document.getElementById('efs-ps').addEventListener('input',e=>{S.free.pad=parseInt(e.target.value,10)||0;document.getElementById('efs-pv2').textContent=S.free.pad+'%';drawCustom();});
  document.getElementById('efs-rs').addEventListener('input',e=>{S.free.rad=parseInt(e.target.value,10)||0;document.getElementById('efs-rv').textContent=S.free.rad;drawCustom();});
  document.querySelectorAll('#efs-wmg .efs-wmb').forEach(b=>b.addEventListener('click',()=>{
    document.querySelectorAll('#efs-wmg .efs-wmb').forEach(x=>x.classList.remove('on'));b.classList.add('on');
    S.free.wm=b.getAttribute('data-w');S.free.ox=0;S.free.oy=0;drawCustom();
  }));
  document.getElementById('efs-wt').addEventListener('input',e=>{S.free.txt=e.target.value;drawCustom();});

  document.getElementById('ig-x').addEventListener('click',()=>spMod.classList.remove('on'));
  spMod.addEventListener('click',e=>{if(e.target===spMod)spMod.classList.remove('on');});
  document.querySelectorAll('#ig-cg .ig-sb').forEach(b=>b.addEventListener('click',()=>{document.querySelectorAll('#ig-cg .ig-sb').forEach(x=>x.classList.remove('on'));b.classList.add('on');S.split.n=parseInt(b.getAttribute('data-n'),10)||3;drawSplit();}));
  document.querySelectorAll('#ig-ag .ig-sb').forEach(b=>b.addEventListener('click',()=>{document.querySelectorAll('#ig-ag .ig-sb').forEach(x=>x.classList.remove('on'));b.classList.add('on');S.split.ar=b.getAttribute('data-a')||'original';drawSplit();}));
  ['touchstart','pointerdown'].forEach(ev=>document.getElementById('ig-svb').addEventListener(ev,()=>showHud('Menyimpan...')));
  document.getElementById('ig-svb').addEventListener('click',()=>{
    spMod.classList.remove('on');showHud('Menyimpan...');
    const sl=sliceUrls(),id='sp_'+Date.now();
    if(sl.length&&window.AndroidGallery&&window.AndroidGallery.saveBatchBase64Images)window.AndroidGallery.saveBatchBase64Images(JSON.stringify(sl),id);
    else window.__onGallerySaveFinished(id,false);
  });
  document.getElementById('ig-shb').addEventListener('click',()=>{
    const sl=sliceUrls();if(!sl.length)return;spMod.classList.remove('on');
    if(window.AndroidShare&&window.AndroidShare.shareMultipleBase64Images)window.AndroidShare.shareMultipleBase64Images(JSON.stringify(sl));
  });
}

if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init);else init();
})();
"""
    }
}
