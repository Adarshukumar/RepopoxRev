package dev.abyssal.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * ABYSSAL for Android.
 *
 * The app is a canvas/Web-Audio piece that is already complete as a web app, so this
 * is a wrapper rather than a rewrite: a single fullscreen WebView pointed at the
 * assets that CI copies in from ../../abyssal. Wrapping keeps one implementation of
 * the descent, one set of physics, and one renderer, instead of two that drift apart.
 *
 * Three things this Activity actually has to get right, because a default WebView
 * gets all three wrong:
 *
 *   1. DOM storage on, or localStorage throws and the viewer's settings and last
 *      depth do not survive a restart. (The web app wraps those calls in try/catch,
 *      so a failure degrades to "no persistence" rather than a blank screen.)
 *   2. configChanges in the manifest plus no reload here, or rotating the device
 *      destroys the WebView and throws away the descent.
 *   3. Back must close the specimen panel before it exits the app, which means
 *      asking the page rather than guessing -- see onBackPressed.
 *
 * There is deliberately no INTERNET permission in the manifest. The APK is not
 * "offline by policy", it is offline because it has no way to make a request.
 */
public class MainActivity extends Activity {

    private static final String START_URL = "file:///android_asset/abyssal/index.html";
    /* matches --c-bottom at depth, so there is no white flash before the canvas paints */
    private static final int ABYSS_BACKDROP = 0xFF01080F;

    private WebView web;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window w = getWindow();
        w.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                   WindowManager.LayoutParams.FLAG_FULLSCREEN);
        /* a guided descent takes ~35s; the screen must not sleep partway down */
        w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        w.setStatusBarColor(ABYSS_BACKDROP);
        w.setNavigationBarColor(ABYSS_BACKDROP);

        web = new WebView(this);
        web.setBackgroundColor(ABYSS_BACKDROP);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);        // localStorage: settings + last depth
        s.setDatabaseEnabled(true);
        /* assets and resources stay reachable over file:///android_asset even with
           file-system access off, so there is no reason to grant it */
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);

        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        /* the page's own audio design is "silent until you ask": the ambience starts
           from a tap on the sound button, so the gesture requirement should stay */
        s.setMediaPlaybackRequiresUserGesture(true);
        /* the HUD is laid out in px/clamp(); letting the accessibility font scale
           inflate it would push the gauges off the rail, so pin it and leave the
           system setting alone everywhere else */
        s.setTextZoom(100);

        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        web.setWebViewClient(new WebViewClient());

        applyImmersive();
        web.loadUrl(START_URL);
    }

    /** Edge-to-edge with the system bars hidden but recoverable by a swipe. */
    private void applyImmersive() {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
              View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        /* sticky immersive is cancelled by any system-bar interaction, so re-assert
           it whenever focus comes back */
        if (hasFocus) applyImmersive();
    }

    /**
     * Back should behave the way it does in the browser build: close the specimen
     * panel first, and only then leave the app. The page already exposes the state
     * this needs, so ask it instead of duplicating the logic here and letting the
     * two implementations diverge.
     *
     * evaluateJavascript is asynchronous, so the decision arrives in the callback --
     * finish() has to be called from there, not after it.
     */
    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
            return;
        }
        if (web == null) {
            super.onBackPressed();
            return;
        }
        final String script =
            "(function(){try{" +
            "  if(window.Sheet&&window.Sheet.isOpen()){window.Sheet.close();return '1';}" +
            "  var h=document.getElementById('help');" +
            "  if(h&&!h.hidden&&h.classList.contains('is-on')){h.classList.remove('is-on');return '1';}" +
            "  return '0';" +
            "}catch(e){return '0';}})()";
        web.evaluateJavascript(script, new android.webkit.ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                /* the result comes back JSON-quoted, so '1' arrives as "\"1\"" */
                if (value != null && "1".equals(value.replace("\"", "").trim())) return;
                MainActivity.super.onBackPressed();
            }
        });
    }

    /* Pausing the WebView stops its timers and rAF, which is what keeps a backgrounded
       app from burning battery on a canvas nobody can see. The page separately zeroes
       its own frame delta on visibilitychange, so a return cannot arrive with one
       enormous dt either. */
    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
        applyImmersive();
    }

    @Override
    protected void onPause() {
        if (web != null) web.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.stopLoading();
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
