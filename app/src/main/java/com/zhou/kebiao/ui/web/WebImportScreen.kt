package com.zhou.kebiao.ui.web

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.zhou.kebiao.parser.ParseResult
import com.zhou.kebiao.parser.PdfHeader
import com.zhou.kebiao.ui.AppState
import com.zhou.kebiao.ui.theme.Canvas
import com.zhou.kebiao.ui.theme.DividerLine
import com.zhou.kebiao.ui.theme.InkPrimary
import com.zhou.kebiao.ui.theme.InkSecondary
import com.zhou.kebiao.ui.theme.InkTertiary
import com.zhou.kebiao.ui.theme.NextCourseFill
import com.zhou.kebiao.ui.theme.RadiusPanel
import com.zhou.kebiao.ui.theme.SemanticBlue
import com.zhou.kebiao.ui.theme.Surface as SurfaceColor
import com.zhou.kebiao.ui.theme.TextBodySmall
import com.zhou.kebiao.ui.theme.TextCaption
import com.zhou.kebiao.ui.theme.TextItem
import com.zhou.kebiao.ui.theme.TextTitle
import com.zhou.kebiao.web.KbJsonParser
import com.zhou.kebiao.web.KbParseResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * 从教务系统导入课表。
 *
 * 登录在网页里完成：进入学校统一身份认证登录页，用户直接在学校页面上
 * 输入账号密码；App 不弹自己的登录框、不接管密码。
 * 登录后点「课表查询页」，再在课表页点「识别导入」，读正方课表 JSON 导入，不下载 PDF。
 */

/**
 * 学校统一认证的登录页（登录后回到门户首页）。
 * state 每次随机 —— OAuth 的 state 本来就该一次一换；登录回跳时服务端会校验。
 */
private fun buildStartUrl(): String {
    val state = UUID.randomUUID().toString().replace("-", "")
    return "http://jwsso.xust.edu.cn/auth/oauth/authorize" +
        "?scope=default%2Cuser_advance%2Cuser_dept%2Cuser_group" +
        "&redirect_uri=http%3A%2F%2Fjwsso.xust.edu.cn%2Fapp.php%2Fportal_v3%2Fhome%2F" +
        "&state=$state" +
        "&response_type=code&approval_prompt=auto" +
        "&client_id=fdf12e7e-5f8a-4c66-bbe4-ed86b98ef9ca"
}

/**
 * 正方通过学校统一认证自动登录的入口（门户「教务系统 → 打开」用的就是它）。
 * 它会拿统一认证的会话自动登录正方，然后重定向到个人课表页；
 * 直接打开课表页不会带登录，会落到正方自己的登录页 —— 之前串号就是这里来的。
 */
private const val AUTH_URL = "http://59.74.174.150/jwglxt/auth/auth_casAuthLogin.html"
private const val TIMETABLE_URL =
    "http://59.74.174.150/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151&layout=default"
private const val JW_HOST = "59.74.174.150"
private const val SSO_HOST = "jwsso.xust.edu.cn"
private const val BRIDGE_NAME = "KebiaoBridge"
private const val EXTRACT_TIMEOUT_MS = 10_000L

/** 只把课表识别结果交回 App；页面本身不被信任做别的事。 */
class KebiaoBridge(
    private val onJson: (String) -> Unit,
    private val onError: (String) -> Unit,
) {
    @JavascriptInterface
    fun onKbJson(json: String) = onJson(json)

    @JavascriptInterface
    fun onKbError(message: String) = onError(message)
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebImportScreen(state: AppState, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pageUrl by remember { mutableStateOf("") }
    var pageLoading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var extractToken by remember { mutableIntStateOf(0) }
    var showSwitchDialog by remember { mutableStateOf(false) }
    var pendingTimetableJump by remember { mutableStateOf(false) }
    val startUrl = remember { buildStartUrl() }

    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                WebView.setWebContentsDebuggingEnabled(true)
            }
        }
    }

    fun runExtract() {
        if (busy) return
        if (!pageUrl.contains("kbcx")) {
            error = "请先点「课表查询页」，等课表显示出来再点识别导入"
            return
        }
        error = null
        busy = true
        val token = ++extractToken
        webView.evaluateJavascript(EXTRACT_JS, null)
        scope.launch {
            delay(EXTRACT_TIMEOUT_MS)
            if (token == extractToken && busy) {
                busy = false
                error = "识别超时，请重试"
            }
        }
    }

    val bridge = remember {
        KebiaoBridge(
            onJson = { raw ->
                webView.post {
                    if (!isSchoolHost(webView.url)) return@post
                    scope.launch {
                        val parsed = runCatching {
                            withContext(Dispatchers.IO) { KbJsonParser.parse(raw) }
                        }.getOrElse {
                            KbParseResult.Failure("课表数据解析失败")
                        }
                        busy = false
                        when (parsed) {
                            is KbParseResult.Success -> {
                                val reason = state.importParsed(
                                    ParseResult.Success(
                                        PdfHeader("", "", ""),
                                        parsed.courses,
                                        parsed.otherCourses,
                                    )
                                )
                                if (reason == null) {
                                    Toast.makeText(
                                        context,
                                        "已导入 ${parsed.courses.size} 门课",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    onClose()
                                } else {
                                    error = reason
                                }
                            }

                            is KbParseResult.Failure -> {
                                runCatching {
                                    File(context.filesDir, "kb_debug.json").writeText(raw)
                                }
                                error = parsed.reason
                            }
                        }
                    }
                }
            },
            onError = { message ->
                webView.post {
                    busy = false
                    error = message
                }
            },
        )
    }

    val client = remember {
        object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean = !isSchoolHost(request.url.toString())

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                val current = url ?: return
                pageUrl = current
                pageLoading = true
                if (pendingTimetableJump) {
                    when {
                        // CAS 直接落在了课表页
                        current.contains("kbcx") -> pendingTimetableJump = false
                        // CAS 登录完成、URL 已经变成正方主菜单：立刻改开课表页，
                        // 不等主菜单整页（脚本/AJAX）加载完 —— 那一页很重，会白等好几秒
                        current.contains(JW_HOST) &&
                            !current.contains("login_slogin") &&
                            !current.contains("auth_casAuthLogin") -> {
                            pendingTimetableJump = false
                            view.loadUrl("$TIMETABLE_URL&_t=${System.currentTimeMillis()}")
                        }
                    }
                }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                val current = url ?: return
                pageUrl = current
                pageLoading = false
                if (isSsoHost(current)) {
                    // 门户用 window.open 打开应用；WebView 默认把新窗口丢掉，
                    // 这里改成当前页跳转，用户才能从门户点「教务系统 → 打开」。
                    view.evaluateJavascript(PORTAL_NAV_JS, null)
                    if (current.contains("/auth/")) {
                        // 统一认证默认展示企业微信扫码，自动切到「账号密码」，用户在网页上输。
                        view.evaluateJavascript(SSO_ACCOUNT_JS, null)
                    }
                }
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                errorDesc: WebResourceError,
            ) {
                if (request.isForMainFrame) {
                    pageLoading = false
                    error = "网页打不开：${errorDesc.description}"
                }
            }
        }
    }

    DisposableEffect(Unit) {
        webView.webViewClient = client
        webView.addJavascriptInterface(bridge, BRIDGE_NAME)
        onDispose {
            webView.removeJavascriptInterface(BRIDGE_NAME)
            CookieManager.getInstance().flush()
            webView.destroy()
        }
    }

    LaunchedEffect(Unit) {
        webView.loadUrl(startUrl)
    }

    BackHandler {
        if (webView.canGoBack()) webView.goBack() else onClose()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Canvas)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        TopBar(
            loading = pageLoading,
            onClose = onClose,
            onSwitchAccount = { showSwitchDialog = true },
        )

        Box(Modifier.weight(1f)) {
            AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())

            if (busy) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Canvas.copy(alpha = 0.75f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = SemanticBlue)
                        Spacer(Modifier.height(10.dp))
                        Text("正在识别课表…", style = TextItem, color = InkSecondary)
                    }
                }
            }
        }

        BottomBar(
            busy = busy,
            loading = pageLoading,
            onOpenTimetable = {
                error = null
                // 走统一认证 → 正方 CAS 自动登录；URL 一变到主菜单就立刻跳课表页
                pendingTimetableJump = true
                webView.loadUrl(AUTH_URL)
            },
            onExtract = { runExtract() },
        )
    }

    if (error != null && !busy) {
        AlertDialog(
            shape = RoundedCornerShape(RadiusPanel),
            onDismissRequest = { error = null },
            title = { Text("没能导入", style = TextTitle) },
            text = { Text(error.orEmpty(), style = TextItem) },
            confirmButton = {
                TextButton(onClick = { error = null }) {
                    Text("知道了", style = TextItem, color = SemanticBlue)
                }
            },
        )
    }

    if (showSwitchDialog) {
        AlertDialog(
            shape = RoundedCornerShape(RadiusPanel),
            onDismissRequest = { showSwitchDialog = false },
            title = { Text("切换账号", style = TextTitle) },
            text = {
                Text(
                    "会退出统一认证和教务系统的当前登录，需要重新登录一次。",
                    style = TextItem,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showSwitchDialog = false
                    CookieManager.getInstance().removeAllCookies {
                        CookieManager.getInstance().flush()
                        webView.post {
                            webView.clearCache(true)
                            webView.clearHistory()
                            pendingTimetableJump = false
                            pageUrl = ""
                            webView.loadUrl(buildStartUrl())
                        }
                    }
                }) {
                    Text("退出并切换", style = TextItem, color = SemanticBlue)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSwitchDialog = false }) {
                    Text("取消", style = TextItem, color = InkTertiary)
                }
            },
        )
    }
}

@Composable
private fun TopBar(
    loading: Boolean,
    onClose: () -> Unit,
    onSwitchAccount: () -> Unit,
) {
    Column(Modifier.background(SurfaceColor)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "✕",
                style = TextTitle,
                color = InkSecondary,
                modifier = Modifier
                    .clickable(onClick = onClose)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Text(
                text = "从教务系统导入",
                style = TextTitle,
                color = InkPrimary,
                modifier = Modifier.padding(start = 4.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "切换账号",
                style = TextCaption,
                color = SemanticBlue,
                modifier = Modifier
                    .clickable(onClick = onSwitchAccount)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
        if (loading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = SemanticBlue,
                trackColor = DividerLine,
            )
        } else {
            HorizontalDivider(color = DividerLine, thickness = 1.dp)
        }
    }
}

@Composable
private fun BottomBar(
    busy: Boolean,
    loading: Boolean,
    onOpenTimetable: () -> Unit,
    onExtract: () -> Unit,
) {
    Column(Modifier.background(SurfaceColor)) {
        HorizontalDivider(color = DividerLine, thickness = 1.dp)

        // 步骤提示放在按钮上方：先读怎么用，再点按钮。浅蓝底 + 蓝竖条让它在
        // 深色按钮旁边足够醒目，又比卡片轻。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, top = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(NextCourseFill)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            ) {
                Box(
                    Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(SemanticBlue)
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = "首先输入账号密码登录，然后可以点击课表查询页，等课表显示出来，再点识别导入",
                        style = TextBodySmall,
                        color = InkSecondary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "学校服务器较慢，课表页加载可能要几秒，请耐心等待",
                        style = TextCaption,
                        color = InkSecondary,
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = onOpenTimetable,
                enabled = !busy && !loading,
                modifier = Modifier.weight(1f),
            ) {
                Text("课表查询页", style = TextItem)
            }
            Button(
                onClick = onExtract,
                enabled = !busy && !loading,
                modifier = Modifier.weight(1f),
            ) {
                Text("识别导入", style = TextItem)
            }
        }
    }
}

private fun isSchoolHost(url: String?): Boolean {
    val host = url?.let { runCatching { Uri.parse(it).host }.getOrNull() } ?: return false
    return host == JW_HOST || host == "xust.edu.cn" || host.endsWith(".xust.edu.cn")
}

private fun isSsoHost(url: String?): Boolean =
    url?.let { runCatching { Uri.parse(it).host }.getOrNull() } == SSO_HOST

/**
 * 门户的「打开」是 window.open 出新窗口；WebView 默认丢掉，这里改成当前页跳转，
 * 同时把 target=_blank 的链接也收进来。
 */
private val PORTAL_NAV_JS = """
    (function(){
      if(window.__kebiaoNav) return;
      window.__kebiaoNav = true;
      var orig = window.open;
      window.open = function(url){
        if(url){ window.location.href = url; return window; }
        return orig ? orig.apply(window, arguments) : null;
      };
      document.addEventListener('click', function(e){
        var a = e.target && e.target.closest ? e.target.closest('a[target="_blank"]') : null;
        if(a && a.href){ e.preventDefault(); window.location.href = a.href; }
      }, true);
    })()
""".trimIndent()

/** 统一认证默认是企业微信扫码，自动点开「账号密码」，让用户直接在学校页面上输入。 */
private val SSO_ACCOUNT_JS = """
    (function(){
      if(window.__kebiaoSsoAccount) return;
      window.__kebiaoSsoAccount = true;
      var tries = 0;
      var timer = setInterval(function(){
        if(document.getElementById('username')){ clearInterval(timer); return; }
        var link = document.querySelector('a[href*="login_type=normal"]');
        if(link && link.offsetParent !== null){
          try{ link.click(); }catch(e){}
          clearInterval(timer);
        } else if(++tries > 20){
          clearInterval(timer);
        }
      }, 300);
    })()
""".trimIndent()

/**
 * 在课表页上执行：读学年/学期 → 调正方自己的课表 JSON 接口 → 把 JSON 交回 App。
 * 接口路径与参数来自正方 jwglxt 的公开前端约定（xskbcx_cxXsgrkb.html）。
 */
private val EXTRACT_JS = """
    (function(){
      function val(id){
        var e=document.getElementById(id)
          || document.querySelector('select[name="'+id+'"]')
          || document.querySelector('input[name="'+id+'"]');
        return e ? e.value : '';
      }
      var xnm = val('xnm'), xqm = val('xqm');
      if(!xnm){ try{ xnm = window.xnm || ''; }catch(e){} }
      if(!xqm){ try{ xqm = window.xqm || ''; }catch(e){} }
      if(!xnm || !xqm){
        KebiaoBridge.onKbError('请先在页面上选好学年和学期并查询，再点识别导入');
        return;
      }
      var body = 'xnm='+encodeURIComponent(xnm)+'&xqm='+encodeURIComponent(xqm)+'&kzlx=ck&xsdm=';
      var urls = [
        '/jwglxt/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151',
        '/jwglxt/kbcx/xskbcx_cxXskbcx.html?gnmkdm=N2151'
      ];
      function tryNext(i){
        if(i >= urls.length){
          KebiaoBridge.onKbError('教务系统没有返回课表数据，接口可能已改版');
          return;
        }
        fetch(urls[i], {
          method:'POST',
          headers:{
            'Content-Type':'application/x-www-form-urlencoded;charset=utf-8',
            'X-Requested-With':'XMLHttpRequest'
          },
          body:body,
          credentials:'include'
        }).then(function(r){ return r.text(); }).then(function(text){
          var data = null;
          try{ data = JSON.parse(text); }catch(e){}
          var list = data && (data.kbList || (data.data && data.data.kbList));
          if(list){ KebiaoBridge.onKbJson(JSON.stringify(data)); }
          else if(text && text.indexOf('login') >= 0){ KebiaoBridge.onKbError('登录已过期，请重新登录'); }
          else { tryNext(i + 1); }
        }).catch(function(){ tryNext(i + 1); });
      }
      tryNext(0);
    })()
""".trimIndent()
