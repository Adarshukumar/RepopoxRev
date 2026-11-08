package dev.frame.app

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import org.mozilla.geckoview.GeckoSession
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicBoolean

/** First-install capability check, not a replacement filter or synthetic counter.
 * Full uBO deliberately unsuspends requests on first install before its lists are
 * ready (upstream start.js/onVersionReady). Do not open the user's site in that gap.
 * Only runs on a fresh full-uBO profile: later user filter-list choices are respected.
 */
internal class FilteringWarmup(private val engine:BrowserEngine, private val done:(Boolean)->Unit) {
    private val handler=Handler(Looper.getMainLooper())
    private val stopped=AtomicBoolean(false)
    private val server=ServerSocket(0,8,InetAddress.getByName("127.0.0.1"))
    private val session=GeckoSession()
    private var attempt=0
    private val path=JSONObject(engine.app.assets.open("probe.json").bufferedReader().use{it.readText()}).getString("path")
    private val deadline=Runnable{finish(false)}
    fun start(){
        Thread({
            while(!stopped.get())try {
                server.accept().use { socket ->
                    socket.soTimeout=3000
                    val reader=socket.getInputStream().bufferedReader()
                    val uri=reader.readLine()?.split(" ")?.getOrNull(1) ?: return@use
                    while(!reader.readLine().isNullOrEmpty()){}
                    val script=uri.substringBefore('?')
                    val (type,body)=when(script){
                        "/frame-control.js" -> "application/javascript" to "window.frameControl=1;"
                        path -> "application/javascript" to "window.frameAd=1;"
                        else -> "text/html" to """<!doctype html><script>window.frameControl=0;window.frameAd=0;</script><script src="/frame-control.js"></script><script src="$path"></script><script>document.title='frame-warmup:'+window.frameControl+':'+window.frameAd;</script>"""
                    }
                    val bytes=body.toByteArray()
                    socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n".toByteArray()+bytes)
                }
            }catch(_:Exception){ /* Closing the server terminates the worker. */ }
        },"Frame-first-install-check").apply{isDaemon=true;start()}
        session.contentDelegate=object:GeckoSession.ContentDelegate{
            override fun onTitleChange(s:GeckoSession,title:String?){
                if(stopped.get() || title?.startsWith("frame-warmup:")!=true)return
                android.util.Log.i("FrameWarmup","attempt=$attempt; result=$title")
                if(title=="frame-warmup:1:0")finish(true)
                else handler.postDelayed({load()},1500)
            }
        }
        session.open(engine.runtime)
        handler.postDelayed(deadline,120000)
        load()
    }
    private fun load(){if(!stopped.get()){attempt++;session.loadUri("http://127.0.0.1:${server.localPort}/?attempt=$attempt")}}
    private fun finish(ok:Boolean){
        if(!stopped.compareAndSet(false,true))return
        handler.removeCallbacksAndMessages(null)
        server.close();session.close()
        android.util.Log.i("FrameWarmup","First-install filtering check: $ok")
        done(ok)
    }
}
