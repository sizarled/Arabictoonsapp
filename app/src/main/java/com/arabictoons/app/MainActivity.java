package com.arabictoons.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
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
            "googleusercontent.com", "youtube.com"
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

    private Dialog popupDialog;
    private WebView popupWebView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        configure(webView);
        webView.getSettings().setSupportMultipleWindows(true); // لنافذة تسجيل الدخول بجوجل/فايسبوك

        webView.setWebViewClient(new Client(false));
        webView.setWebChromeClient(new WebChromeClient() {

            // النوافذ المنبثقة: تُفتح فقط بعد ضغطة المستخدم، وأي نافذة تذهب لموقع غير مسموح تُغلق فوراً
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog,
                                          boolean isUserGesture, Message resultMsg) {
                if (!isUserGesture) return false;
                openPopup(resultMsg);
                return true;
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
                view.setBackgroundColor(Color.BLACK);
                webView.setVisibility(View.GONE);
                root.addView(view, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                updateImmersive();
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) return;
                root.removeView(customView);
                customView = null;
                webView.setVisibility(View.VISIBLE);
                if (customViewCallback != null) customViewCallback.onCustomViewHidden();
                updateImmersive();
            }
        });

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl(HOME_URL);
        }
        updateImmersive();
    }

    /** إعدادات مشتركة بين الصفحة الرئيسية ونافذة تسجيل الدخول */
    private void configure(WebView w) {
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);

        // جوجل ترفض تسجيل الدخول من WebView العادي، لذلك نجعل هوية المتصفح تشبه Chrome
        String ua = s.getUserAgentString();
        if (ua != null) {
            s.setUserAgentString(ua.replace("; wv", "").replace(" Version/4.0", ""));
        }

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(w, true);
    }

    private class Client extends WebViewClient {
        private final boolean popup;

        Client(boolean popup) {
            this.popup = popup;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            if (!request.isForMainFrame()) return false;
            Uri uri = request.getUrl();
            if (popup) {
                String host = uri.getHost();
                if (host != null && isSiteOrAllowed(host)) return false;
                closePopup();
                return true;
            }
            return handleNavigation(uri);
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            String host = request.getUrl().getHost();
            if (host != null) {
                if (matches(host, AD_HOSTS)) return empty();
                if (popup && request.isForMainFrame() && !isSiteOrAllowed(host)) {
                    closePopup();
                    return empty();
                }
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

        private WebResourceResponse empty() {
            return new WebResourceResponse("text/plain", "utf-8",
                    new ByteArrayInputStream(new byte[0]));
        }
    }

    // ====== نافذة تسجيل الدخول المنبثقة ======
    private void openPopup(Message resultMsg) {
        destroyPopup();

        WebView p = new WebView(this);
        configure(p);
        p.setWebViewClient(new Client(true));
        p.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onCloseWindow(WebView window) {
                closePopup();
            }
        });

        Dialog d = new Dialog(this, android.R.style.Theme_Material_Light_NoActionBar);
        d.setContentView(p);
        d.setOnDismissListener(x -> destroyPopup());
        popupDialog = d;
        popupWebView = p;

        WebView.WebViewTransport t = (WebView.WebViewTransport) resultMsg.obj;
        t.setWebView(p);
        resultMsg.sendToTarget();

        d.show();
        if (d.getWindow() != null) {
            d.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    private void closePopup() {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (popupDialog != null) popupDialog.dismiss();
            else destroyPopup();
        });
    }

    private void destroyPopup() {
        WebView p = popupWebView;
        Dialog d = popupDialog;
        popupWebView = null;
        popupDialog = null;
        if (d != null && d.isShowing()) d.dismiss();
        if (p != null) {
            p.stopLoading();
            p.destroy();
        }
    }

    // ====== التنقل ======
    /** true = تم التعامل مع الرابط (لا يُحمّل في الـ WebView) */
    private boolean handleNavigation(Uri uri) {
        String scheme = uri.getScheme();
        String host = uri.getHost();

        if (host == null || scheme == null || !(scheme.equals("http") || scheme.equals("https"))) {
            return true; // حظر الروابط الغريبة (intent:, market:, ...)
        }
        if (matches(host, AD_HOSTS)) return true;
        if (isSiteOrAllowed(host)) return false;

        if (OPEN_EXTERNAL_IN_BROWSER) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (Exception ignored) { }
        }
        return true;
    }

    private static boolean isSiteOrAllowed(String host) {
        return matches(host, SITE_HOSTS) || matches(host, ALLOWED_HOSTS);
    }

    private static boolean matches(String host, String[] list) {
        host = host.toLowerCase();
        for (String d : list) {
            if (host.equals(d) || host.endsWith("." + d)) return true;
        }
        return false;
    }

    // ====== ملء الشاشة بالكامل (بدون حواف) ======
    private void updateImmersive() {
        boolean landscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        boolean videoFullscreen = customView != null;
        boolean immersive = landscape || videoFullscreen;
        Window w = getWindow();

        // السماح للفيديو بالامتداد تحت حافة الكاميرا (النوتش)
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.layoutInDisplayCutoutMode = videoFullscreen
                    ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
            w.setAttributes(lp);
        }

        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(!immersive);
            WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                if (immersive) {
                    c.hide(WindowInsets.Type.systemBars());
                    c.setSystemBarsBehavior(
                            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                } else {
                    c.show(WindowInsets.Type.systemBars());
                }
            }
        } else {
            w.getDecorView().setSystemUiVisibility(immersive
                    ? View.SYSTEM_UI_FLAG_FULLSCREEN
                      | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                      | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                      | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                      | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                      | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    : View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateImmersive();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) updateImmersive();
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
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    protected void onDestroy() {
        destroyPopup();
        root.removeAllViews();
        webView.destroy();
        super.onDestroy();
    }
}
