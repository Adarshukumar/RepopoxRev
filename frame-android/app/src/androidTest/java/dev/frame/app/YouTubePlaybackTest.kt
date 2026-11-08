package dev.frame.app

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.*
import org.junit.runner.RunWith
import org.json.JSONObject
import org.mozilla.geckoview.*
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/** Observes a real public video. Observing no ad is not proof an ad was removed. */
@RunWith(AndroidJUnit4::class)
class YouTubePlaybackTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private val samples=CopyOnWriteArrayList<JSONObject>()
    private var failure=""
    @Test fun realVideoAdvancesWithFullUbo(){
        val engine=rule.activity.engine
        val url="https://m.youtube.com/watch?v=aqz-KE-bpKQ" // Blender Foundation's Big Buck Bunny
        rule.runOnUiThread{engine.saveHome("about:blank")}
        rule.waitUntil(150000){engine.ready||engine.installError.isNotEmpty()}
        Assert.assertTrue(engine.installError,engine.ready)
        rule.waitUntil(30000){engine.currentUrl=="about:blank" && !engine.loading}
        var observer:WebExtension?=null
        rule.runOnUiThread{
            engine.controller.ensureBuiltIn("resource://android/assets/frame-evidence/","frame-evidence@test.invalid").accept({observer=it},{failure=it.toString()})
        }
        rule.waitUntil(30000){observer!=null||failure.isNotEmpty()}
        Assert.assertNotNull(failure,observer)
        rule.runOnUiThread{
            engine.current!!.webExtensionController.setMessageDelegate(observer!!,object:WebExtension.MessageDelegate{
                override fun onMessage(nativeApp:String,message:Any,sender:WebExtension.MessageSender):GeckoResult<Any>?{
                    if(nativeApp=="frameEvidence" && message is JSONObject && android.net.Uri.parse(message.optString("url")).host in setOf("m.youtube.com","www.youtube.com","youtube.com"))samples.add(message)
                    return GeckoResult.fromValue<Any>(true)
                }
            },"frameEvidence")
            engine.saveHome(url)
        }
        rule.waitUntil(60000){samples.isNotEmpty()||engine.pageError.isNotEmpty()}
        Assert.assertTrue(engine.pageError,engine.pageError.isEmpty())
        Assert.assertTrue("No actual YouTube document samples received",samples.isNotEmpty())
        rule.waitUntil(45000){samples.last().optDouble("tapX",-1.0)>0}
        val sample=samples.last()
        val point=IntArray(2)
        rule.runOnUiThread{
            fun find(v:View):GeckoView? {
                if(v is GeckoView)return v
                if(v is ViewGroup)for(i in 0 until v.childCount){find(v.getChildAt(i))?.let{return it}}
                return null
            }
            val view=find(rule.activity.window.decorView)!!
            view.getLocationOnScreen(point)
            val scale=view.width/sample.getDouble("width")
            point[0]+=(sample.getDouble("tapX")*scale).toInt()
            point[1]+=(sample.getDouble("tapY")*scale).toInt()
        }
        if(sample.optBoolean("paused",true)) UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).click(point[0],point[1])
        rule.waitUntil(60000){samples.any{it.optDouble("time")>=10 && it.optInt("frames")>10 && !it.optBoolean("paused")}}
        Assert.assertTrue("Video must decode frames and advance, not just load a YouTube title",samples.any{it.optDouble("time")>=10 && it.optInt("frames")>10})
    }
    @After fun evidence(){
        val inst=InstrumentationRegistry.getInstrumentation()
        val engine=rule.activity.engine
        android.util.Log.i("FrameTest","Playback end state: url=${engine.currentUrl}; title=${engine.title}; loading=${engine.loading}; error=${engine.pageError}; samples=${samples.size}")
        val dir=File("/sdcard/Download")
        // Use app-owned storage then copy via test shell; scoped-storage safe.
        val source=File(inst.targetContext.getExternalFilesDir(null),"youtube-playback-evidence.json")
        source.writeText(JSONObject().put("currentUrl",engine.currentUrl).put("documentTitle",engine.title).put("pageError",engine.pageError).put("samples",org.json.JSONArray(samples.map{it})).put("adMarkersObserved",samples.any{it.optBoolean("adMarker")}).put("limitation","Read-only observation. No visible ad does not prove an ad was removed; not a permanent ad-free guarantee.").put("observerError",failure).toString(2))
        inst.uiAutomation.executeShellCommand("cp ${source.absolutePath} $dir/youtube-playback-evidence.json").use{android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()}
        val shot=File(inst.targetContext.getExternalFilesDir(null),"frame-06-youtube-playback.png")
        inst.uiAutomation.takeScreenshot()?.let{bmp->shot.outputStream().use{bmp.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}
        inst.uiAutomation.executeShellCommand("cp ${shot.absolutePath} $dir/frame-06-youtube-playback.png").use{android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()}
        android.util.Log.i("FrameTest","YouTube samples=${samples.size}; videoAdvanced=${samples.any{it.optDouble("time")>=10 && it.optInt("frames")>10}}; adMarkersObserved=${samples.any{it.optBoolean("adMarker")}}")
    }
}
