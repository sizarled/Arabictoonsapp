package com.arabictoons.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import java.io.ByteArrayInputStream;

public class MainActivity extends Activity {

    // ====== الإعدادات ======
    private static final String HOME_URL = "https://www.arabic-toons.com/index.php";

    // false = تجاهل أي رابط خارجي (لا يفتح لا في التطبيق ولا في المتصفح)
    // true  = فتح الروابط الخارجية في متصفح الهاتف
    private static final boolean OPEN_EXTERNAL_IN_BROWSER = false;

    // نطاقات الموقع نفسه (تشمل النطاقات الفرعية مثل profile.arabic-toons.com)
    private static final String[] SITE_HOSTS = { "arabic-toons.com" };

    // نطاقات مسموح بفتحها داخل التطبيق (فايسبوك و جوجل)
    private static final String[] ALLOWED_HOSTS = {
            "facebook.com", "fb.com", "fbcdn.net", "fb.me",
            "google.com", "gstatic.com", "googleapis.com",
            "googleusercontent.com", "accounts.google.com"
    };

    // قائمة حظر الإعلانات (تُحظر قبل أي فحص آخر)
    private static final String[] AD_HOSTS = {
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "adservice.google.com", "pagead2.googlesyndication.com",
            "adnxs.com", "popads.net", "popcash.net", "propellerads.com",
            "propellerclick.com", "exoclick.com", "exosrv.com", "juicyads.com",
            "adsterra.com", "highperformanceformat.com", "profitablecpmrate.com",
            "revenuecpmgate.com", "profitableratecpm.com", "hilltopads.net",
            "clickadu.com", "trafficjunky.net", "mgid.com", "taboola.com",
            "outbrain.com", "onclickads.net", "adcash.com", "admaven.com",
            "ad-maven.com", "a-ads.com", "adskeeper.com", "yllix.com",
            "popcdn.com", "pushnotifications.pro", "monetag.com", "adexchangeclear.com",
            "tpc.googlesyndication.com", "amazon-adsystem.com", "criteo.com",
            "rubiconproject.com", "pubmatic.com", "openx.net", "adform.net"
    };

    private static final String HIDE_ADS_JS =
            "(function(){var s=document.createElement('style');"
          + "s.innerHTML='ins.adsbygoogle,iframe[src*=\"doubleclick\"],iframe[src*=\"googlesyndication\"],"
          + "[id^=\"google_ads\"],[class*=\"adsbox\"],[class*=\"ad-banner\"],[id*=\"ad-banner\"],"
          + "[class*=\"popunder\"]{display:none!important;height:0!important;}';"
          + "document.head.appendChild(s);})();";

    private WebView webView;
    private FrameLayout root;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        root = new FrameLayout(this);
        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setJavaScriptCanOpenWindowsAutomatically(false); // منع النوافذ المنبثقة
        s.setSupportMultipleWindows(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // الإطارات الداخلية (مشغلات الفيديو) تُترك، والإعلانات تُحظر في shouldInterceptRequest
                if (!request.isForMainFrame()) return false;
                return handleNavigation(request.getUrl());
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String host = request.getUrl().getHost();
                if (host != null && matches(host, AD_HOSTS)) {
                    return new WebResourceResponse("text/plain", "utf-8",
                            new ByteArrayInputStream(new byte[0]));
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                view.evaluateJavascript(HIDE_ADS_JS, null);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.evaluateJavascript(HIDE_ADS_JS, null);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            // منع النوافذ المنبثقة (popups)
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog,
                                          boolean isUserGesture, android.os.Message resultMsg) {
                return false;
            }

            // دعم ملء الشاشة للفيديو
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                webView.setVisibility(View.GONE);
                root.addView(view, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                hideSystemBars(true);
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) return;
                root.removeView(customView);
                customView = null;
                webView.setVisibility(View.VISIBLE);
                if (customViewCallback != null) customViewCallback.onCustomViewHidden();
                hideSystemBars(false);
            }
        });

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl(HOME_URL);
        }
    }

    /** true = تم التعامل مع الرابط (لا يُحمّل في الـ WebView) */
    private boolean handleNavigation(Uri uri) {
        String scheme = uri.getScheme();
        String host = uri.getHost();

        if (host == null || scheme == null || !(scheme.equals("http") || scheme.equals("https"))) {
            return true; // حظر الروابط الغريبة (intent:, market:, ...)
        }
        if (matches(host, AD_HOSTS)) return true;                 // إعلان
        if (matches(host, SITE_HOSTS)) return false;              // الموقع نفسه
        if (matches(host, ALLOWED_HOSTS)) return false;           // فايسبوك / جوجل داخل التطبيق

        if (OPEN_EXTERNAL_IN_BROWSER) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (Exception ignored) { }
        }
        return true; // تم الحظر / التحويل للمتصفح
    }

    private static boolean matches(String host, String[] list) {
        host = host.toLowerCase();
        for (String d : list) {
            if (host.equals(d) || host.endsWith("." + d)) return true;
        }
        return false;
    }

    private void hideSystemBars(boolean hide) {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(hide
                ? View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                  | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                : View.SYSTEM_UI_FLAG_VISIBLE);
    }

    @Override
    public void onBackPressed() {
        if (customView != null) {
            webView.getWebChromeClient().onHideCustomView();
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    protected void onDestroy() {
        root.removeAllViews();
        webView.destroy();
        super.onDestroy();
    }
}
