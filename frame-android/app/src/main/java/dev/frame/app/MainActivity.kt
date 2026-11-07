package dev.frame.app

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import org.mozilla.geckoview.*

class MainActivity: ComponentActivity() {
    lateinit var engine:BrowserEngine
    private var pendingFile:Pair<GeckoSession.PromptDelegate.FilePrompt,GeckoResult<GeckoSession.PromptDelegate.PromptResponse>>?=null
    private val filePicker=registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        pendingFile?.let { (prompt,result) -> result.complete(if(uris.isEmpty())prompt.dismiss()else if(prompt.type==GeckoSession.PromptDelegate.FilePrompt.Type.SINGLE)prompt.confirm(this,uris.first())else prompt.confirm(this,uris.toTypedArray())) };pendingFile=null
    }
    override fun onCreate(savedInstanceState:Bundle?){
        val splash=installSplashScreen();super.onCreate(savedInstanceState);enableEdgeToEdge()
        splash.setOnExitAnimationListener { p ->
            val motion=android.provider.Settings.Global.getFloat(contentResolver,android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,1f)!=0f && !getSharedPreferences("frame",0).getBoolean("reduceMotion",false)
            if(motion)p.view.animate().alpha(0f).setDuration(180).withEndAction{p.remove()}.start()else p.remove()
        }
        engine=BrowserEngine.get(this)
        engine.permissions={message,reply->AlertDialog.Builder(this).setTitle("Extension permission").setMessage(message).setPositiveButton("Allow"){_,_->reply(true)}.setNegativeButton("Not now"){_,_->reply(false)}.setOnCancelListener{reply(false)}.show()}
        engine.external={url->
            if(url.startsWith("mailto:")||url.startsWith("tel:")||url.startsWith("https:")) AlertDialog.Builder(this).setTitle("Leave Frame?").setMessage("Open this link in another app?\n$url").setPositiveButton("Open"){_,_->try{startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url)))}catch(_:Exception){}}.setNegativeButton("Stay here",null).show()
        }
        engine.promptDelegate=object:GeckoSession.PromptDelegate {
            override fun onAlertPrompt(session:GeckoSession,prompt:GeckoSession.PromptDelegate.AlertPrompt):GeckoResult<GeckoSession.PromptDelegate.PromptResponse>{
                val r=GeckoResult<GeckoSession.PromptDelegate.PromptResponse>();AlertDialog.Builder(this@MainActivity).setTitle("Website message").setMessage(prompt.message).setPositiveButton("OK"){_,_->r.complete(prompt.dismiss())}.setOnCancelListener{r.complete(prompt.dismiss())}.show();return r
            }
            override fun onButtonPrompt(session:GeckoSession,prompt:GeckoSession.PromptDelegate.ButtonPrompt):GeckoResult<GeckoSession.PromptDelegate.PromptResponse>{
                val r=GeckoResult<GeckoSession.PromptDelegate.PromptResponse>();AlertDialog.Builder(this@MainActivity).setTitle("Website confirmation").setMessage(prompt.message).setPositiveButton("OK"){_,_->r.complete(prompt.confirm(GeckoSession.PromptDelegate.ButtonPrompt.Type.POSITIVE))}.setNegativeButton("Cancel"){_,_->r.complete(prompt.dismiss())}.setOnCancelListener{r.complete(prompt.dismiss())}.show();return r
            }
            override fun onTextPrompt(session:GeckoSession,prompt:GeckoSession.PromptDelegate.TextPrompt):GeckoResult<GeckoSession.PromptDelegate.PromptResponse>{
                val r=GeckoResult<GeckoSession.PromptDelegate.PromptResponse>();val field=EditText(this@MainActivity);field.setText(prompt.defaultValue);AlertDialog.Builder(this@MainActivity).setTitle(prompt.message?:"Website input").setView(field).setPositiveButton("OK"){_,_->r.complete(prompt.confirm(field.text.toString()))}.setNegativeButton("Cancel"){_,_->r.complete(prompt.dismiss())}.setOnCancelListener{r.complete(prompt.dismiss())}.show();return r
            }
            override fun onChoicePrompt(session:GeckoSession,prompt:GeckoSession.PromptDelegate.ChoicePrompt):GeckoResult<GeckoSession.PromptDelegate.PromptResponse>{
                val r=GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                fun flatten(c:Array<GeckoSession.PromptDelegate.ChoicePrompt.Choice>):List<GeckoSession.PromptDelegate.ChoicePrompt.Choice> = c.flatMap{if(it.items!=null)flatten(it.items!!)else listOf(it)}.filter{!it.disabled&&!it.separator}
                val choices=flatten(prompt.choices);val builder=AlertDialog.Builder(this@MainActivity).setTitle(prompt.title?:"Choose an option")
                if(prompt.type==GeckoSession.PromptDelegate.ChoicePrompt.Type.MULTIPLE){val checked=choices.map{it.selected}.toBooleanArray();builder.setMultiChoiceItems(choices.map{it.label}.toTypedArray(),checked){_,i,v->checked[i]=v}.setPositiveButton("Done"){_,_->r.complete(prompt.confirm(choices.filterIndexed{i,_->checked[i]}.map{it.id}.toTypedArray()))}}
                else builder.setItems(choices.map{it.label}.toTypedArray()){_,i->r.complete(prompt.confirm(choices[i].id))}
                builder.setNegativeButton("Cancel"){_,_->r.complete(prompt.dismiss())}.setOnCancelListener{r.complete(prompt.dismiss())}.show();return r
            }
            override fun onFilePrompt(session:GeckoSession,prompt:GeckoSession.PromptDelegate.FilePrompt):GeckoResult<GeckoSession.PromptDelegate.PromptResponse>{
                pendingFile?.let{it.second.complete(it.first.dismiss())}
                val r=GeckoResult<GeckoSession.PromptDelegate.PromptResponse>();pendingFile=prompt to r
                filePicker.launch(prompt.mimeTypes?.filter{it.contains('/')}?.takeIf{it.isNotEmpty()}?.toTypedArray() ?: arrayOf("*/*"));return r
            }
        }
        if(engine.home.isNotEmpty())engine.install()
        setContent{FrameApp(engine)}
    }
    override fun onResume(){super.onResume();if(::engine.isInitialized)engine.current?.setActive(true)}
    override fun onPause(){if(::engine.isInitialized)engine.current?.setActive(false);super.onPause()}
    override fun onDestroy(){if(::engine.isInitialized){engine.permissions=null;engine.external=null;engine.promptDelegate=null};pendingFile?.let{it.second.complete(it.first.dismiss())};pendingFile=null;super.onDestroy()}
}
