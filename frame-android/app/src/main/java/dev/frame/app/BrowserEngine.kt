package dev.frame.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.*
import org.mozilla.geckoview.*

/** One runtime per process. No fake counters, injected CSS, or replacement blocking engine. */
class BrowserEngine private constructor(context: Context) {
    val app=context.applicationContext
    val prefs=app.getSharedPreferences("frame",0)
    val runtime=GeckoRuntime.create(app)
    val controller=runtime.webExtensionController
    var extension: WebExtension? = null
    var ready by mutableStateOf(false)
    var installing by mutableStateOf(false)
    var installError by mutableStateOf("")
    var enabled by mutableStateOf(prefs.getBoolean("blocking",true))
    var changing by mutableStateOf(false)
    var home by mutableStateOf(prefs.getString("home","") ?: "")
    var theme by mutableStateOf(prefs.getString("theme","Dark") ?: "Dark")
    var desktop by mutableStateOf(prefs.getBoolean("desktop",false))
    var reduceMotion by mutableStateOf(prefs.getBoolean("reduceMotion",false))
    var currentUrl by mutableStateOf("")
    var title by mutableStateOf("")
    var loading by mutableStateOf(false)
    var pageError by mutableStateOf("")
    var fullScreen by mutableStateOf(false)
    var canBack by mutableStateOf(false)
    var current: GeckoSession? by mutableStateOf(null)
    var auxiliary by mutableStateOf(false)
    var permissions: ((String, (Boolean)->Unit)->Unit)?=null
    var promptDelegate: GeckoSession.PromptDelegate?=null
        set(value) { field=value; sessions.forEach{it.promptDelegate=value} }
    var external: ((String)->Unit)?=null
    private val sessions=mutableListOf<GeckoSession>()
    private val retired=mutableSetOf<GeckoSession>()
    private val windows=mutableListOf<GeckoSession>()
    private val history=mutableMapOf<GeckoSession,Boolean>()
    private var web: GeckoSession?=null
    var optionsLoaded by mutableStateOf(false)
    private val handler=Handler(Looper.getMainLooper())
    init {
        controller.promptDelegate=object: WebExtensionController.PromptDelegate {
            override fun onOptionalPrompt(extension: WebExtension, permissions: Array<String>, origins: Array<String>, dataCollectionPermissions: Array<String>): GeckoResult<AllowOrDeny> {
                val result=GeckoResult<AllowOrDeny>()
                this@BrowserEngine.permissions?.invoke("uBlock Origin Lite requests:\n${(permissions.toList()+origins.toList()).joinToString("\n")}") { allow -> result.complete(if(allow)AllowOrDeny.ALLOW else AllowOrDeny.DENY) } ?: result.complete(AllowOrDeny.DENY)
                return result
            }
        }
    }
    fun install() {
        if(installing || ready)return
        installing=true;installError=""
        controller.ensureBuiltIn("resource://android/assets/ubol/",BuildConfig.UBOL_ID).accept({ ext ->
            if(ext==null){ fail("The engine returned no extension.");return@accept }
            attach(ext)
            val op=if(enabled)controller.enable(ext,WebExtensionController.EnableSource.USER) else controller.disable(ext,WebExtensionController.EnableSource.USER)
            op.accept({updated -> if(updated!=null)attach(updated);ready=true;installing=false;if(home.isNotEmpty())openHome() },{ error -> fail(error.toString()) })
        },{error -> fail(error.toString())})
    }
    private fun fail(message:String){installing=false;changing=false;installError="uBlock Origin Lite could not be initialized. $message"}
    private fun attach(ext:WebExtension){
        extension=ext
        ext.setTabDelegate(object:WebExtension.TabDelegate {
            override fun onNewTab(source:WebExtension,details:WebExtension.CreateTabDetails):GeckoResult<GeckoSession>{
                val s=newSession();auxiliary=true;current=s;optionsLoaded=false;return GeckoResult.fromValue(s)
            }
            override fun onOpenOptionsPage(source:WebExtension){openOptions()}
        })
    }
    fun setBlocking(value:Boolean, done: (Boolean)->Unit = {}) {
        val ext=extension ?: return
        if(changing)return
        changing=true
        val op=if(value)controller.enable(ext,WebExtensionController.EnableSource.USER)else controller.disable(ext,WebExtensionController.EnableSource.USER)
        op.accept({e->
            if(e!=null)attach(e)
            // Gecko acknowledges enablement before an MV3 background has finished
            // asynchronous ruleset registration. Keep the applying state during
            // this startup grace period before the automatic page reload.
            handler.postDelayed({enabled=value;prefs.edit().putBoolean("blocking",value).apply();changing=false;done(true)},if(value)2500L else 0L)
        },{e->changing=false;installError="Could not change extension state: $e";done(false)})
    }
    fun saveHome(url:String){home=url;prefs.edit().putString("home",url).apply();if(ready)openHome()else install()}
    fun setAppearance(value:String){theme=value;prefs.edit().putString("theme",value).apply()}
    fun setReducedMotion(value:Boolean){reduceMotion=value;prefs.edit().putBoolean("reduceMotion",value).apply()}
    fun setDesktopMode(value:Boolean){desktop=value;prefs.edit().putBoolean("desktop",value).apply();web?.settings?.userAgentMode=if(value)GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE;web?.reload()}
    fun openHome(){if(!ready)return;closeAuxiliary();if(web==null)web=newSession();current=web;auxiliary=false;load(home)}
    fun load(url:String){pageError="";title="";current?.loadUri(url)}
    fun refresh(){pageError="";current?.reload()}
    fun openOptions(){
        val url=extension?.metaData?.optionsPageUrl
        if(url==null){pageError="The extension has not provided a settings page yet.";return}
        closeAuxiliary();val s=newSession();auxiliary=true;current=s;optionsLoaded=false;title="";currentUrl="";s.loadUri(url)
    }
    fun closeAuxiliary(){if(auxiliary){current?.let{if(it!==web)retired.add(it)};current=web;auxiliary=false};optionsLoaded=false}
    fun goBack():Boolean {
        if(fullScreen){current?.exitFullScreen();return true}
        if(auxiliary){closeAuxiliary();return true}
        if(history[current]==true){current?.goBack();return true}
        if(windows.isNotEmpty()){val last=windows.removeAt(windows.lastIndex);current?.close();current=last;return true}
        return false
    }
    fun clearData(done:()->Unit){runtime.storageController.clearData(StorageController.ClearFlags.ALL).accept({done()},{pageError="Could not clear browsing data."})}
    private fun newSession(open:Boolean=true):GeckoSession {
        val s=GeckoSession()
        s.settings.userAgentMode=if(desktop)GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        s.promptDelegate=promptDelegate
        s.navigationDelegate=object:GeckoSession.NavigationDelegate {
            override fun onLocationChange(session:GeckoSession,url:String?,perms:MutableList<GeckoSession.PermissionDelegate.ContentPermission>,hasUserGesture:Boolean){if(session===current && url!=null)currentUrl=url}
            override fun onCanGoBack(session:GeckoSession,value:Boolean){history[session]=value;if(session===current)canBack=value}
            override fun onLoadRequest(session:GeckoSession,request:GeckoSession.NavigationDelegate.LoadRequest):GeckoResult<AllowOrDeny>{
                val uri=android.net.Uri.parse(request.uri)
                return when(uri.scheme?.lowercase()){
                    "http","https" -> GeckoResult.fromValue(AllowOrDeny.ALLOW)
                    "moz-extension" -> GeckoResult.fromValue(AllowOrDeny.ALLOW)
                    "about" -> GeckoResult.fromValue(if(request.uri=="about:blank")AllowOrDeny.ALLOW else AllowOrDeny.DENY)
                    else -> { if(request.hasUserGesture)external?.invoke(request.uri);GeckoResult.fromValue(AllowOrDeny.DENY) }
                }
            }
            override fun onNewSession(session:GeckoSession,uri:String):GeckoResult<GeckoSession>{
                val next=newSession(false);windows.add(session);current=next;return GeckoResult.fromValue(next)
            }
            override fun onLoadError(session:GeckoSession,uri:String?,error:WebRequestError):GeckoResult<String>?{if(session===current){loading=false;pageError="This page couldn’t open. Check your connection or try another website. Security errors are never bypassed."};return null}
        }
        s.progressDelegate=object:GeckoSession.ProgressDelegate {
            override fun onPageStart(session:GeckoSession,url:String){if(session===current){currentUrl=url;loading=true;pageError=""}}
            override fun onPageStop(session:GeckoSession,success:Boolean){if(session===current){loading=false;if(auxiliary&&success&&currentUrl.startsWith("moz-extension:"))optionsLoaded=true}}
        }
        s.contentDelegate=object:GeckoSession.ContentDelegate {
            override fun onTitleChange(session:GeckoSession,value:String?){if(session===current)title=value?:""}
            override fun onFullScreen(session:GeckoSession,value:Boolean){if(session===current)fullScreen=value}
            override fun onCloseRequest(session:GeckoSession){if(session===current)goBack()}
        }
        if(open)s.open(runtime);sessions.add(s)
        return s
    }
    fun onViewDetached(session:GeckoSession?) {
        if(session!=null && retired.remove(session)){session.close();sessions.remove(session);history.remove(session)}
    }
    fun markActive(session:GeckoSession){sessions.filter{it.isOpen}.forEach{controller.setTabActive(it,it===session)}}
    companion object {
        private var singleton:BrowserEngine?=null
        fun get(context:Context):BrowserEngine=singleton ?: BrowserEngine(context).also{singleton=it}
    }
}
