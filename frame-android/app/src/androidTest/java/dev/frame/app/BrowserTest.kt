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
            rule.runOnUiThread{engine.saveHome("https://example.com/")}
            rule.waitUntil(120000){engine.ready||engine.installError.isNotEmpty()}
            Assert.assertTrue("Extension initialization: ${engine.installError}",engine.ready)
            rule.runOnUiThread{engine.load("http://127.0.0.1:${probe.port}/?phase=enabled")}
            rule.waitUntil(60000){engine.title.startsWith("probe:")}
            Assert.assertEquals("probe:control=1;ad=0",engine.title)
            Assert.assertEquals("Blocked request must not reach server",0,probe.adHits.get())
            var changed=false
            rule.runOnUiThread{engine.setBlocking(false){changed=it}}
            rule.waitUntil(30000){changed}
            rule.runOnUiThread{engine.load("http://127.0.0.1:${probe.port}/?phase=disabled")}
            rule.waitUntil(60000){engine.title.startsWith("probe:")}
            Assert.assertEquals("probe:control=1;ad=1",engine.title)
            Assert.assertTrue("Control must reach server when extension is disabled",probe.adHits.get()>0)
            changed=false
            rule.runOnUiThread{engine.setBlocking(true){changed=it}}
            rule.waitUntil(30000){changed}
            rule.runOnUiThread{engine.load("http://127.0.0.1:${probe.port}/?phase=reenabled")}
            rule.waitUntil(60000){engine.title.startsWith("probe:")}
            Assert.assertEquals("probe:control=1;ad=0",engine.title)
            screenshot("02-verified-filtering")
            rule.runOnUiThread{engine.openOptions()}
            rule.waitUntil(60000){engine.optionsLoaded||engine.pageError.isNotEmpty()}
            Assert.assertTrue("Official extension dashboard must load: ${engine.pageError}",engine.optionsLoaded)
            Assert.assertTrue(engine.currentUrl.startsWith("moz-extension:"))
            screenshot("03-ubol-dashboard")
            rule.onNodeWithContentDescription("Close extension settings").performClick()
            rule.onNodeWithContentDescription("App controls").performClick()
            rule.onNodeWithText("Make it yours.").assertExists()
            screenshot("04-settings")
            Assert.assertEquals("https://example.com/",engine.prefs.getString("home",null))
        }finally{probe.close()}
    }
    @After fun finalScreenshot(){screenshot("99-final")}
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
                else->{type="text/html";body="""<!doctype html><meta name="viewport" content="width=device-width"><style>body{background:#111b19;color:#f1f5eb;font-family:sans-serif;padding:34px}small{color:#c5f386}h1{font-size:38px}p{line-height:1.7;color:#a2b1a7}.card{padding:22px;background:#1d2a26;border-radius:22px;margin-top:28px}</style><small>FRAME / LIVE BLOCKING TEST</small><h1>A quieter web.<br>Actually verified.</h1><p>This page uses a real rule from the bundled, unmodified uBlock Origin Lite ruleset.</p><div class="card" id="result">Testing requests…</div><script>window.control=0;window.ad=0;</script><script src="/control.js"></script><script src="$adPath"></script><script>document.title='probe:control='+window.control+';ad='+window.ad;document.getElementById('result').textContent='Normal request: '+(window.control?'loaded':'failed')+' · Ad-pattern request: '+(window.ad?'allowed':'blocked');</script>"""}
            }
            val bytes=body.toByteArray();s.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n".toByteArray()+bytes)
        }}catch(_:SocketException){if(!stopped)throw RuntimeException("Probe socket failed")}catch(_:Exception){}
    }.apply{isDaemon=true;start()}}
    fun close(){stopped=true;server.close()}
}
