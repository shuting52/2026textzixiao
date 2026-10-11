package com.textzixiao.studio

import android.annotation.SuppressLint
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.io.FileOutputStream

/**
 * 字体设计工作室 · Android 壳
 * WebView 加载本地工作室页面；接管文件选择（字体/素材导入）与 blob 下载（PNG/PSD/工程文件导出）。
 */
class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null

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

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
        }
        webView.addJavascriptInterface(DownloadBridge(), "AndroidBridge")
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                view?.evaluateJavascript(BLOB_HOOK_JS, null)
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
