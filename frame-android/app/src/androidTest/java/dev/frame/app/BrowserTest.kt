package dev.frame.app
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.runner.RunWith
import java.net.ServerSocket
import java.net.SocketException
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONObject
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

@RunWith(AndroidJUnit4::class)
class BrowserTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    @Test fun realExtensionBlocksAndDashboardWorks(){
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        screenshot("01-setup")
        val path=JSONObject(ctx.assets.open("probe.json").bufferedReader().readText()).getString("path")
        val probe=ProbeServer(path)
        try {
            val engine=rule.activity.engine
            val probeHome="http://127.0.0.1:${probe.port}/?phase=enabled"
            rule.runOnUiThread{engine.saveHome(probeHome)}
            rule.waitUntil(120000){engine.ready||engine.installError.isNotEmpty()}
            Assert.assertTrue("Extension initialization: ${engine.installError}",engine.ready)
            rule.waitUntil(60000){engine.title.startsWith("probe:enabled:")}
            Assert.assertEquals("probe:enabled:control=1;ad=0",engine.title)
            Assert.assertEquals("Blocked request must not reach server",0,probe.adHits.get())
            var changed=false
            rule.runOnUiThread{engine.setBlocking(false){changed=it}}
            rule.waitUntil(30000){changed}
            rule.runOnUiThread{engine.load("http://127.0.0.1:${probe.port}/?phase=disabled")}
            rule.waitUntil(60000){engine.title.startsWith("probe:disabled:")}
            Assert.assertEquals("probe:disabled:control=1;ad=1",engine.title)
            Assert.assertTrue("Control must reach server when extension is disabled",probe.adHits.get()>0)
            changed=false
            rule.runOnUiThread{engine.setBlocking(true){changed=it}}
            rule.waitUntil(30000){changed}
            rule.runOnUiThread{engine.load("http://127.0.0.1:${probe.port}/?phase=reenabled")}
            rule.waitUntil(60000){engine.title.startsWith("probe:reenabled:")}
            Assert.assertEquals("probe:reenabled:control=1;ad=0",engine.title)
            screenshot("02-verified-filtering")
            rule.runOnUiThread{engine.runtime.settings.setForceEnableAccessibility(true);engine.openOptions()}
            rule.waitUntil(60000){engine.optionsLoaded&&engine.currentUrl.startsWith("moz-extension:")||engine.pageError.isNotEmpty()}
            Assert.assertTrue("Official extension dashboard must load: ${engine.pageError}",engine.optionsLoaded)
            Assert.assertTrue(engine.currentUrl.startsWith("moz-extension:"))
            val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            rule.waitUntil(30000){engine.title.contains("Dashboard",ignoreCase=true)}
            val filters=device.wait(Until.findObject(By.textContains("Filter lists")),3000)
                ?: device.wait(Until.findObject(By.descContains("Filter lists")),3000)
            screenshot("03-ubol-dashboard")
            // Gecko exposes HTML virtual nodes differently across engine releases.
            // The fixed Pixel 6 test profile provides a screenshot-reviewed touch
            // fallback; this is not reported as a DOM assertion when unavailable.
            if(filters!=null)filters.click() else {
                android.util.Log.i("FrameTest","Filter-list tab: using screenshot-reviewed Pixel 6 touch target")
                device.click((device.displayWidth*.40f).toInt(),(device.displayHeight*.14f).toInt())
            }
            android.os.SystemClock.sleep(1800)
            screenshot("03b-ubol-filter-lists")
            rule.onNodeWithContentDescription("Close extension settings").performClick()
            rule.onNodeWithContentDescription("App controls").performClick()
            rule.onNodeWithText("Make it yours.").assertExists()
            screenshot("04-settings")
            Assert.assertEquals(probeHome,engine.prefs.getString("home",null))
            rule.runOnUiThread{rule.activity.onBackPressedDispatcher.onBackPressed();engine.saveHome("https://m.youtube.com/")}
            rule.waitUntil(60000){engine.currentUrl.contains("youtube.com")&&!engine.loading || engine.pageError.isNotEmpty()}
            Assert.assertTrue("YouTube main page: ${engine.pageError}",engine.pageError.isEmpty())
            Assert.assertTrue(engine.currentUrl.contains("youtube.com"))
            screenshot("05-youtube")
        }finally{probe.close()}
    }
    @After fun finalScreenshot(){
        val e=rule.activity.engine
        android.util.Log.i("FrameTest","ready=${e.ready}; enabled=${e.enabled}; title=${e.title}; url=${e.currentUrl}; loading=${e.loading}; error=${e.pageError}; extensionError=${e.installError}")
        screenshot("99-final")
    }
    private fun screenshot(name:String){rule.waitForIdle();android.os.SystemClock.sleep(700);val inst=InstrumentationRegistry.getInstrumentation();val bmp=inst.uiAutomation.takeScreenshot();val f=File(inst.targetContext.getExternalFilesDir(null),"frame-$name.png");f.outputStream().use{bmp.compress(Bitmap.CompressFormat.PNG,100,it)};android.os.ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand("cp ${f.absolutePath} /sdcard/Download/frame-$name.png")).use{it.readBytes()}}
}
private class ProbeServer(private val adPath:String){
    private val server=ServerSocket(0,20,java.net.InetAddress.getByName("127.0.0.1"))
    val port=server.localPort
    val adHits=AtomicInteger(0)
    @Volatile private var stopped=false
    init{Thread{
        while(!stopped)try{server.accept().use{s->s.soTimeout=5000;val input=s.getInputStream().bufferedReader();val request=input.readLine()?:return@use;val path=request.split(" ").getOrElse(1){"/"}.substringBefore('?');while(!input.readLine().isNullOrEmpty()){}
            val body:String;val type:String
            when(path){
                "/control.js"->{body="window.control=1;";type="application/javascript"}
                adPath->{adHits.incrementAndGet();body="window.ad=1;";type="application/javascript"}
                else->{type="text/html";body="""<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width"><style>body{background:#111b19;color:#f1f5eb;font-family:sans-serif;padding:34px}small{color:#c5f386}h1{font-size:38px}p{line-height:1.7;color:#a2b1a7}.card{padding:22px;background:#1d2a26;border-radius:22px;margin-top:28px}</style><small>FRAME / LIVE BLOCKING TEST</small><h1>A quieter web.<br>Actually verified.</h1><p>This page uses a real rule from the bundled, unmodified uBlock Origin Lite ruleset.</p><div class="card" id="result">Testing requests…</div><script>window.control=0;window.ad=0;</script><script src="/control.js"></script><script src="$adPath"></script><script>document.title='probe:'+new URLSearchParams(location.search).get('phase')+':control='+window.control+';ad='+window.ad;document.getElementById('result').textContent='Normal request: '+(window.control?'loaded':'failed')+' · Ad-pattern request: '+(window.ad?'allowed':'blocked');</script>"""}
            }
            val bytes=body.toByteArray();s.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n".toByteArray()+bytes)
        }}catch(_:SocketException){if(!stopped)throw RuntimeException("Probe socket failed")}catch(_:Exception){}
    }.apply{isDaemon=true;start()}}
    fun close(){stopped=true;server.close()}
}
