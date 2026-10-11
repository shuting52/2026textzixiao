package com.textzixiao.studio

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

/**
 * 字体设计工作室 · Android 壳
 * WebView 加载本地工作室页面；接管文件选择（字体/素材导入）与 blob 下载（PNG/PSD/工程文件导出）。
 */
class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var pendingApkDownloadId: Long = -1L

    private val apkCompleteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: return
            if (id == pendingApkDownloadId) {
                pendingApkDownloadId = -1L
                installDownloadedApk()
            }
        }
    }

    private val pickFile =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            fileChooserCallback?.onReceiveValue(uri?.let { arrayOf(it) })
            fileChooserCallback = null
        }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        setContentView(webView)
        val downloadFilter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(apkCompleteReceiver, downloadFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(apkCompleteReceiver, downloadFilter)
        }

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
        }
        val updateBridge = UpdateBridge()
        webView.addJavascriptInterface(DownloadBridge(), "AndroidBridge")
        webView.addJavascriptInterface(updateBridge, "AndroidUpdate")
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                view?.evaluateJavascript(BLOB_HOOK_JS, null)
                // 启动后静默检查一次更新，有新版才弹可爱风弹窗
                view?.postDelayed({ updateBridge.checkUpdate(true) }, 2000)
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = filePathCallback
                return try {
                    pickFile.launch("*/*")
                    true
                } catch (e: Exception) {
                    fileChooserCallback = null
                    false
                }
            }
        }

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl("file:///android_asset/studio/index.html")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::webView.isInitialized) webView.saveState(outState)
    }

    @Deprecated("use onBackPressedDispatcher")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(apkCompleteReceiver)
        } catch (e: Exception) {
            // ignore
        }
        super.onDestroy()
    }

    /** JS 桥：页面内更新弹窗调用，下载 GitHub Releases 的 APK 并引导安装；
     *  版本检查也在原生层做（静态页不允许运行时拉取外部数据），结果通过 JS 回调弹可爱风弹窗 */
    inner class UpdateBridge {
        private val versionUrl =
            "https://raw.githubusercontent.com/shuting52/2026textzixiao/main/version.json"

        /** silent=true 时（启动自检）无更新则不打扰用户 */
        @JavascriptInterface
        fun checkUpdate() = checkUpdate(false)

        @JavascriptInterface
        fun checkUpdate(silent: Boolean) {
            Thread {
                try {
                    val conn = URL(versionUrl).openConnection() as HttpURLConnection
                    conn.connectTimeout = 8000
                    conn.readTimeout = 8000
                    conn.connect()
                    val body = conn.inputStream.bufferedReader().readText()
                    conn.disconnect()
                    val obj = JSONObject(body)
                    val newVersion = obj.optString("version", "")
                    val pinfo =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            packageManager.getPackageInfo(
                                packageName, PackageManager.PackageInfoFlags.of(0)
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            packageManager.getPackageInfo(packageName, 0)
                        }
                    val current = pinfo.versionName ?: ""
                    if (newVersion.isNotEmpty() && compareVersion(newVersion, current) > 0) {
                        val info = JSONObject().apply {
                            put("title", obj.optString("title", "发现新版本"))
                            put("notes", obj.optJSONArray("notes") ?: JSONArray())
                            put("currentVersion", current)
                            put("newVersion", newVersion)
                            put("apkUrl", obj.optString("apkUrl", ""))
                            put(
                                "releasesUrl",
                                obj.optString(
                                    "releasesUrl",
                                    "https://github.com/shuting52/2026textzixiao/releases"
                                )
                            )
                        }
                        webView.post {
                            webView.evaluateJavascript(
                                "window.showUpdatePopup && window.showUpdatePopup($info)",
                                null
                            )
                        }
                    } else if (!silent) {
                        webView.post {
                            webView.evaluateJavascript(
                                "window.showUpToDatePopup && window.showUpToDatePopup()",
                                null
                            )
                        }
                    }
                } catch (e: Exception) {
                    if (!silent) {
                        runOnUiThread {
                            Toast.makeText(
                                this@MainActivity,
                                "检查更新失败：${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }.start()
        }

        private fun compareVersion(a: String, b: String): Int {
            val pa = a.split(".")
            val pb = b.split(".")
            for (i in 0 until maxOf(pa.size, pb.size)) {
                val x = pa.getOrNull(i)?.toIntOrNull() ?: 0
                val y = pb.getOrNull(i)?.toIntOrNull() ?: 0
                if (x != y) return x.compareTo(y)
            }
            return 0
        }

        @JavascriptInterface
        fun downloadApk(url: String) {
            runOnUiThread {
                try {
                    val req = DownloadManager.Request(Uri.parse(url)).apply {
                        setTitle("字体设计工作室更新")
                        setDescription("正在下载新版本…")
                        setNotificationVisibility(
                            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                        )
                        setDestinationInExternalFilesDir(
                            this@MainActivity,
                            Environment.DIRECTORY_DOWNLOADS,
                            "textzixiao-update.apk"
                        )
                        setMimeType("application/vnd.android.package-archive")
                    }
                    val dm =
                        getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    pendingApkDownloadId = dm.enqueue(req)
                    Toast.makeText(
                        this@MainActivity, "开始下载更新包…", Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(
                        this@MainActivity, "下载失败：${e.message}", Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun installDownloadedApk() {
        try {
            val apkFile = File(
                getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                "textzixiao-update.apk"
            )
            if (!apkFile.exists()) {
                Toast.makeText(this, "更新包未找到", Toast.LENGTH_SHORT).show()
                return
            }
            // Android 8+ 需要“允许安装未知应用”授权
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !packageManager.canRequestPackageInstalls()
            ) {
                Toast.makeText(this, "请允许安装未知应用后重试", Toast.LENGTH_LONG).show()
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:$packageName")
                    )
                )
                return
            }
            val uri = FileProvider.getUriForFile(
                this, "$packageName.fileprovider", apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "安装失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /** JS 桥：把页面内的 blob 下载转为真实文件存到“下载”目录 */
    inner class DownloadBridge {
        @JavascriptInterface
        fun saveFile(base64Data: String, filename: String, mimeType: String) {
            runOnUiThread {
                try {
                    val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                    val name = filename.ifBlank { "studio_${System.currentTimeMillis()}" }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val values = ContentValues().apply {
                            put(MediaStore.Downloads.DISPLAY_NAME, name)
                            put(
                                MediaStore.Downloads.MIME_TYPE,
                                mimeType.ifBlank { "application/octet-stream" }
                            )
                            put(
                                MediaStore.Downloads.RELATIVE_PATH,
                                Environment.DIRECTORY_DOWNLOADS
                            )
                        }
                        val uri = contentResolver.insert(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                        )
                        uri?.let {
                            contentResolver.openOutputStream(it)?.use { os -> os.write(bytes) }
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        val dir = Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS
                        )
                        FileOutputStream(File(dir, name)).use { it.write(bytes) }
                    }
                    Toast.makeText(this@MainActivity, "已保存到下载：$name", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "保存失败：${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    companion object {
        /** 拦截页面内 <a download> 的 blob 链接，转交原生保存 */
        private const val BLOB_HOOK_JS = """(function() {
  if (window.__tzHooked) return; window.__tzHooked = true;
  document.addEventListener('click', function(e) {
    var a = e.target.closest ? e.target.closest('a[download]') : null;
    if (!a) return;
    var href = a.getAttribute('href') || '';
    if (href.indexOf('blob:') === 0 && window.AndroidBridge) {
      e.preventDefault(); e.stopPropagation();
      var name = a.getAttribute('download') || 'download';
      fetch(href).then(function(r){ return r.blob(); }).then(function(b){
        var fr = new FileReader();
        fr.onload = function(){
          AndroidBridge.saveFile(fr.result.split(',')[1], name, b.type || '');
        };
        fr.readAsDataURL(b);
      });
    }
  }, true);
})();"""
    }
}
