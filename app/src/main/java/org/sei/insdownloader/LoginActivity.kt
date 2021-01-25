package org.sei.insdownloader

import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import org.sei.insdownloader.databinding.ActivityLoginBinding
import java.util.regex.Pattern

class LoginActivity : AppCompatActivity() {

    private val viewBinding by lazy { ActivityLoginBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)
        setSupportActionBar(viewBinding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        initWebView()

    }

    private fun initWebView() {
        viewBinding.webView.settings.apply {
            javaScriptEnabled = true
        }
        viewBinding.webView.apply {
            webViewClient = LoginWebViewClient(this@LoginActivity)
            addJavascriptInterface(WebJSInterface(this@LoginActivity), "webJSInterface")

            loadUrl("https://www.instagram.com")
        }

    }

    private class WebJSInterface(private val context: Context) {
        @JavascriptInterface
        fun getLoginStatus() {

        }
    }

    private class LoginWebViewClient(private val context: Context) : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?
        ): Boolean {
            return false
        }

        override fun shouldInterceptRequest(
            view: WebView?,
            request: WebResourceRequest?
        ): WebResourceResponse? {
            // https://www.instagram.com/graphql/query/
            //println(request?.url?.path) // /graphql/query/
            //query_hash=003056d32c2554def87228bc3fd9668a&variables={"id":"5782093649","first":12,"after":"QVFCbzd2UFhnX0ZGUE5RcUE5ZE9DNjBWY2ZEOGxaeWlmNDJKejBSV1liZGRPZkpMNzZ6NXlzalZBR1JLZUtpMmRDcDBRdEE3TnQtbEFQblhBanBIOFNXOA=="}
            request?.url?.let {
                if (it.path == "/graphql/query/" && !TextUtils.isEmpty(it.query)) {
                    val queryHashPatt = Pattern.compile("query_hash=(.{28,36})&vari.{30,50}\"after\":")
                    val matcher = queryHashPatt.matcher(it.query!!)
                    if (matcher.find()) {
                        //println(matcher.group())
                        //println("queryHash: ${matcher.group(1)}")
                        Downloader.queryHash = matcher.group(1) ?: ""
                        context.getSharedPreferences("config", Context.MODE_PRIVATE).edit {
                            putString("queryHash", matcher.group(1))
                            commit()
                        }
                    }
                }
            }
            return super.shouldInterceptRequest(view, request)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            //println("url: $url")
            url?.let {
                val cookieManager = CookieManager.getInstance()
                val cookie = cookieManager.getCookie(it)
                // ig_did=4BB4DF38-B0AA-4AFF-9A38-722F3F37D1EF; mid=YAzT5wABAAExdIdAkGKRO4JsQZgX; ig_nrcb=1; csrftoken=SGbRXL5QXYtR60ihyqmewJTr4PAWzjc9; ds_user_id=9152946067; sessionid=9152946067%3A3j6tIcXVYUI321%3A22; shbid=5854; shbts=1611453812.9311044; rur=VLL; urlgen="{\"138.19.188.14\": 9269}:1l3VCY:4s85HGsz_IyonpbZZYjqTiQXSmY"
                //println("cookie: $cookie")
                val csrftokenPatt = Pattern.compile("csrftoken=(.{28,36});")
                val csrftokenMatcher = csrftokenPatt.matcher(cookie)
                if (csrftokenMatcher.find()) {
                    //println(csrftokenMatcher.group())
                    //println(csrftokenMatcher.group(1))
                    Downloader.csrftoken = csrftokenMatcher.group(1) ?: ""
                    context.getSharedPreferences("config", Context.MODE_PRIVATE).edit {
                        putString("csrftoken", csrftokenMatcher.group(1))
                        commit()
                    }
                }
                val sessionidPatt = Pattern.compile("sessionid=(.{28,36});")
                val sessionidMatcher = sessionidPatt.matcher(cookie)
                if (sessionidMatcher.find()) {
                    //println(sessionidMatcher.group())
                    //println(sessionidMatcher.group(1))
                    Downloader.sessionID = sessionidMatcher.group(1) ?: ""
                    context.getSharedPreferences("config", Context.MODE_PRIVATE).edit {
                        putString("sessionID", sessionidMatcher.group(1))
                        commit()
                    }
                }
            }

            super.onPageFinished(view, url)
        }
    }

}