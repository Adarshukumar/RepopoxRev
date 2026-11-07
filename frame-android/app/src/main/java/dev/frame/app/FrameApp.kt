package dev.frame.app

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.mozilla.geckoview.GeckoView

private val Lime=Color(0xFFC5F386)
private val Deep=Color(0xFF111B19)
private val Soft=Color(0xFF1D2A26)
private val Cream=Color(0xFFF1F5EB)
private val Quiet=Color(0xFFA2B1A7)
@Composable private fun Caption(text:String){Text(text.uppercase(),color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=10.sp,letterSpacing=1.7.sp,fontWeight=FontWeight.Medium)}
@Composable private fun Heading(text:String,size:Int=35){Text(text,color=MaterialTheme.colorScheme.onSurface,fontSize=size.sp,lineHeight=(size*1.12).sp,letterSpacing=(-1.2).sp,fontWeight=FontWeight.SemiBold)}
@Composable private fun Body(text:String,size:Int=13){Text(text,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=size.sp,lineHeight=(size*1.6).sp)}
@Composable private fun Mark(name:String="frame",color:Color=Lime,modifier:Modifier=Modifier){Canvas(modifier.size(23.dp)){
    val u=size.width/24;fun l(a:Float,b:Float,c:Float,d:Float){drawLine(color,Offset(a*u,b*u),Offset(c*u,d*u),1.7f*u,StrokeCap.Round)}
    when(name){
        "arrow"->{l(5f,19f,19f,5f);l(7f,5f,19f,5f);l(19f,5f,19f,17f)}
        "back"->{l(15f,5f,8f,12f);l(8f,12f,15f,19f)}
        "close"->{l(6f,6f,18f,18f);l(6f,18f,18f,6f)}
        "shield"->{val p=Path().apply{moveTo(12*u,2*u);lineTo(21*u,6*u);lineTo(20*u,15*u);quadraticTo(18*u,20*u,12*u,23*u);quadraticTo(6*u,20*u,4*u,15*u);lineTo(3*u,6*u);close()};drawPath(p,color,style=Stroke(1.5f*u));l(8f,12f,11f,15f);l(11f,15f,17f,9f)}
        "settings"->{l(4f,7f,20f,7f);l(4f,17f,20f,17f);drawCircle(color,3*u,Offset(9*u,7*u));drawCircle(color,3*u,Offset(16*u,17*u))}
        else->{l(3f,9f,3f,3f);l(3f,3f,9f,3f);l(15f,3f,21f,3f);l(21f,3f,21f,9f);l(21f,15f,21f,21f);l(21f,21f,15f,21f);l(9f,21f,3f,21f);l(3f,21f,3f,15f);l(9f,15f,15f,9f)}
    }
}}
@Composable private fun Action(text:String,enabled:Boolean=true,onClick:()->Unit){Button(onClick,Modifier.fillMaxWidth().height(57.dp),enabled=enabled,shape=RoundedCornerShape(17.dp),colors=ButtonDefaults.buttonColors(containerColor=Lime,contentColor=Deep)){Text(text,fontWeight=FontWeight.SemiBold,fontSize=15.sp);Spacer(Modifier.weight(1f));Mark("arrow",Deep)}}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun FrameApp(engine:BrowserEngine){
    var controls by rememberSaveable{mutableStateOf(false)}
    var editing by rememberSaveable{mutableStateOf(false)}
    var clearConfirm by rememberSaveable{mutableStateOf(false)}
    var about by rememberSaveable{mutableStateOf(false)}
    val dark=engine.theme=="Dark"||(engine.theme=="System"&&isSystemInDarkTheme())
    val colors=if(dark)darkColorScheme(primary=Lime,onPrimary=Deep,surface=Deep,background=Deep,onSurface=Cream,onSurfaceVariant=Quiet,surfaceVariant=Soft,secondaryContainer=Color(0xFF364C2E),onSecondaryContainer=Lime,outline=Color(0xFF37453D)) else lightColorScheme(primary=Color(0xFF385F24),onPrimary=Color.White,surface=Color(0xFFF5F7EF),background=Color(0xFFF5F7EF),onSurface=Deep,onSurfaceVariant=Color(0xFF5A6D5F),surfaceVariant=Color(0xFFE7EDDF),secondaryContainer=Color(0xFFD7E9C1),onSecondaryContainer=Deep,outline=Color(0xFFCCD6C5))
    val context=LocalContext.current
    LaunchedEffect(engine.fullScreen,dark){(context as? Activity)?.window?.let{w->val c=WindowCompat.getInsetsController(w,w.decorView);c.isAppearanceLightStatusBars=!dark;c.isAppearanceLightNavigationBars=!dark;c.systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE;if(engine.fullScreen)c.hide(WindowInsetsCompat.Type.systemBars())else c.show(WindowInsetsCompat.Type.systemBars())}}
    BackHandler(engine.home.isNotEmpty()){if(controls)controls=false else if(editing)editing=false else if(!engine.goBack())controls=true}
    MaterialTheme(colorScheme=colors){Surface(Modifier.fillMaxSize(),color=colors.background){
        when {
            engine.home.isEmpty()||editing -> Setup(engine.home,editing,{editing=false}){url->engine.saveHome(url);editing=false}
            !engine.ready -> Column(Modifier.fillMaxSize().safeDrawingPadding().padding(28.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){
                Mark(modifier=Modifier.size(60.dp));Spacer(Modifier.height(30.dp));Heading(if(engine.installError.isEmpty())"A quieter web\nis on its way." else "Let’s try that again.");Spacer(Modifier.height(20.dp));Body(if(engine.installError.isEmpty())"Starting the browser engine and the genuine uBlock Origin Lite extension. Your site opens after initialization." else engine.installError)
                Spacer(Modifier.height(28.dp));if(engine.installError.isEmpty())LinearProgressIndicator(Modifier.fillMaxWidth(),color=Lime)else Action("Retry extension setup"){engine.install()}
                TextButton({editing=true}){Text("Change website",color=colors.onSurfaceVariant)}
            }
            else -> Column(Modifier.fillMaxSize().then(if(engine.fullScreen)Modifier else Modifier.safeDrawingPadding())){
                if(engine.auxiliary)Row(Modifier.fillMaxWidth().background(colors.surface).height(58.dp).padding(horizontal=10.dp),verticalAlignment=Alignment.CenterVertically){IconButton({engine.closeAuxiliary()},Modifier.semantics{contentDescription="Close extension settings"}){Mark("back",colors.onSurface)};Column(Modifier.weight(1f)){Text("uBlock Origin Lite",color=colors.onSurface,fontWeight=FontWeight.SemiBold,fontSize=14.sp);Text("Official extension settings",color=colors.onSurfaceVariant,fontSize=10.sp)}}
                Box(Modifier.fillMaxSize()){
                    val session=engine.current
                    if(session!=null) AndroidView(
                        factory={ctx->GeckoView(ctx).apply{importantForAccessibility=android.view.View.IMPORTANT_FOR_ACCESSIBILITY_YES}},
                        update={view->if(view.session!==session){val previous=view.session;view.releaseSession();engine.onViewDetached(previous);view.setSession(session);engine.markActive(session)}},
                        modifier=Modifier.fillMaxSize(),
                        onRelease={view->val previous=view.session;view.releaseSession();engine.onViewDetached(previous)}
                    )
                    if(engine.pageError.isNotEmpty())Column(Modifier.align(Alignment.Center).padding(24.dp).clip(RoundedCornerShape(22.dp)).background(colors.surface).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){Mark("frame");Heading("A small detour.",28);Body(engine.pageError);Action("Try again"){engine.refresh()};TextButton({controls=true}){Text("Open app settings")}}
                    if(engine.loading)LinearProgressIndicator(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(2.dp),color=Lime,trackColor=Color.Transparent)
                    if(!engine.fullScreen&&!engine.auxiliary)Box(Modifier.align(Alignment.BottomEnd).padding(14.dp).size(42.dp).clip(CircleShape).background(Deep.copy(alpha=.88f)).border(1.dp,Lime.copy(alpha=.3f),CircleShape).clickable{controls=true}.semantics{contentDescription="App controls"},contentAlignment=Alignment.Center){Mark("settings",Lime,Modifier.size(18.dp))}
                }
            }
        }
        if(controls){ModalBottomSheet(onDismissRequest={controls=false},containerColor=colors.surface,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)){
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(bottom=28.dp),verticalArrangement=Arrangement.spacedBy(21.dp)){
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Caption("YOUR SPACE, YOUR RULES");Spacer(Modifier.height(8.dp));Heading("Make it yours.",30)};Mark("frame")}
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(colors.surfaceVariant).padding(17.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Caption("SAVED WEBSITE");Text(Address.host(engine.home),color=colors.onSurface,fontSize=20.sp,fontWeight=FontWeight.Medium);Text(engine.home,color=colors.onSurfaceVariant,fontSize=11.sp);TextButton({controls=false;editing=true},contentPadding=PaddingValues(0.dp)){Text("Change website ↗",color=colors.primary)}}
                Row(verticalAlignment=Alignment.CenterVertically){Mark("shield",colors.primary);Spacer(Modifier.width(13.dp));Column(Modifier.weight(1f)){Text("uBlock Origin Lite",color=colors.onSurface,fontWeight=FontWeight.SemiBold);Body(if(engine.changing)"Applying your choice…" else if(engine.enabled)"Extension enabled · ${BuildConfig.UBOL_VERSION}" else "Extension disabled",11)};Switch(engine.enabled,{engine.setBlocking(it){ok->if(ok)engine.refresh()}},enabled=!engine.changing)}
                if(engine.installError.isNotEmpty())Text(engine.installError,color=colors.error,fontSize=12.sp)
                SettingRow("Filtering & permissions","Open uBO Lite’s own dashboard","shield"){controls=false;engine.openOptions()}
                HorizontalDivider(color=colors.outline.copy(alpha=.4f))
                Caption("THE WAY IT FEELS")
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("Light","Dark","System").forEach{mode->FilterChip(engine.theme==mode,{engine.setAppearance(mode)},label={Text(mode)})}}
                ToggleRow("Desktop website","Request the desktop layout",engine.desktop){engine.setDesktopMode(it)}
                ToggleRow("Reduced motion","Keep app transitions simple",engine.reduceMotion){engine.setReducedMotion(it)}
                HorizontalDivider(color=colors.outline.copy(alpha=.4f))
                SettingRow("Return to your website","Open your saved home","arrow"){controls=false;engine.openHome()}
                SettingRow("Reload page","A fresh start for this page","frame"){controls=false;engine.refresh()}
                SettingRow("Clear browsing data","Sign out of websites and remove site storage","close"){clearConfirm=true}
                SettingRow("About Frame","Privacy, licenses, and what to expect","frame"){about=true}
                Body("Current page: ${engine.currentUrl}\nFrame 1.0.0 · GeckoView + real uBO Lite",10)
            }
        }}
        if(clearConfirm)AlertDialog(onDismissRequest={clearConfirm=false},title={Text("Clear browsing data?")},text={Text("This signs you out and removes cookies, cached files and website storage. Your saved URL stays. This cannot be undone.")},confirmButton={TextButton({clearConfirm=false;engine.clearData{controls=false;engine.openHome()}}){Text("Clear data")}},dismissButton={TextButton({clearConfirm=false}){Text("Keep data")}})
        if(about)AlertDialog(onDismissRequest={about=false},title={Text("A website. Your own space.")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)){Body("Frame embeds Mozilla GeckoView, not Chromium. Its bundled uBlock Origin Lite is the unmodified Firefox release by Raymond Hill. Settings in the extension dashboard are real, not simulated.");Body("The extension can access website content and network requests to filter them. Frame has no analytics or account system. Websites still receive your requests and apply their own privacy policies. Cookies persist until you clear them.");Body("No ad blocker can promise permanent YouTube ad blocking. DRM video, provider-restricted sign-in, camera/microphone capture, and some browser features may be unavailable. Frame is not affiliated with YouTube, Mozilla, or uBlock Origin.");Body("GPL-3.0: Frame and uBO Lite. GeckoView: MPL-2.0 and third-party notices. Corresponding source and provenance accompany the GitHub release.")}},confirmButton={TextButton({about=false}){Text("Got it")}})
    }}
}
@Composable private fun SettingRow(title:String,subtitle:String,icon:String,onClick:()->Unit){Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick=onClick).padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(title,color=MaterialTheme.colorScheme.onSurface,fontSize=14.sp,fontWeight=FontWeight.Medium);Body(subtitle,11)};Mark(icon,MaterialTheme.colorScheme.onSurfaceVariant,Modifier.size(19.dp))}}
@Composable private fun ToggleRow(title:String,subtitle:String,value:Boolean,onChange:(Boolean)->Unit){Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(title,color=MaterialTheme.colorScheme.onSurface,fontSize=14.sp);Body(subtitle,11)};Switch(value,onChange)}}
@Composable private fun Setup(initial:String,editing:Boolean,cancel:()->Unit,save:(String)->Unit){
    var url by rememberSaveable{mutableStateOf(initial)}
    var error by rememberSaveable{mutableStateOf("")}
    var consent by rememberSaveable{mutableStateOf(editing)}
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(26.dp),verticalArrangement=Arrangement.spacedBy(24.dp)){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Mark();Spacer(Modifier.width(10.dp));Text("frame",fontSize=30.sp,letterSpacing=(-1).sp,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.onSurface);Spacer(Modifier.weight(1f));Text("A LITTLE LESS BROWSER",fontSize=8.sp,letterSpacing=1.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement=Arrangement.spacedBy(14.dp)){Caption(if(editing)"A NEW PLACE TO CALL HOME" else "ONE WEBSITE. ALL YOURS.");Heading(if(editing)"Change your\npoint of view." else "Your favorite place.\nWithout the noise.",38);Body("Give a website its own space. No tabs. No address bar. Just the things you came for.",14)}
        PreviewCard()
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
            Caption("WHERE WOULD YOU LIKE TO GO?")
            OutlinedTextField(url,{url=it;error=""},Modifier.fillMaxWidth(),placeholder={Text("youtube.com",color=MaterialTheme.colorScheme.onSurfaceVariant)},singleLine=true,shape=RoundedCornerShape(16.dp),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri),leadingIcon={Mark("frame",MaterialTheme.colorScheme.onSurfaceVariant)},isError=error.isNotEmpty())
            if(error.isNotEmpty())Text(error,color=MaterialTheme.colorScheme.error,fontSize=12.sp)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("YouTube","Wikipedia","Reddit").forEach{site->SuggestionChip({url=when(site){"YouTube"->"https://www.youtube.com";"Wikipedia"->"https://www.wikipedia.org";else->"https://www.reddit.com"}},label={Text(site,fontSize=11.sp)})}}
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(14.dp),verticalAlignment=Alignment.CenterVertically){Mark("shield");Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text("Less clutter. More control.",color=MaterialTheme.colorScheme.onSurface,fontSize=13.sp,fontWeight=FontWeight.Medium);Body("Genuine uBlock Origin Lite, built in.",11)}}
        Row(verticalAlignment=Alignment.Top){Checkbox(consent,{consent=it});Text("Allow the bundled uBO Lite extension to filter requests and access website content. Change filtering and permissions in settings anytime.",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=11.sp,lineHeight=17.sp,modifier=Modifier.padding(top=8.dp))}
        Action(if(editing)"Save my website" else "Create my space",consent&&url.isNotBlank()) { try{save(Address.normalize(url))}catch(e:Exception){error=e.message?:"Please check the address."} }
        Body("Your URL is remembered on this device. Tap the small control button—or Back at the home page—to reach settings.",11)
        if(editing)TextButton(cancel,Modifier.fillMaxWidth()){Text("Keep my current website")}
    }
}
@Composable private fun PreviewCard(){
    Box(Modifier.fillMaxWidth().height(173.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF25392E))){
        Box(Modifier.size(160.dp).offset(x=210.dp,y=(-45).dp).clip(CircleShape).background(Color(0xFF3D5533)))
        Row(Modifier.fillMaxSize().padding(23.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(22.dp)){
            Box(Modifier.width(92.dp).height(128.dp).rotate(-7f).clip(RoundedCornerShape(15.dp)).background(Cream).padding(8.dp)){
                Column(verticalArrangement=Arrangement.spacedBy(6.dp)){Box(Modifier.width(25.dp).height(4.dp).clip(CircleShape).background(Color(0xFFBDC6B6)).align(Alignment.CenterHorizontally));Box(Modifier.fillMaxWidth().height(65.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFFB5CDA4)),contentAlignment=Alignment.Center){Mark("arrow",Color(0xFF3F5C2B),Modifier.size(28.dp))};Box(Modifier.width(52.dp).height(5.dp).background(Color(0xFFCBD3C4),CircleShape));Box(Modifier.width(37.dp).height(4.dp).background(Color(0xFFDFE5D8),CircleShape))}
            }
            Column(verticalArrangement=Arrangement.spacedBy(9.dp)){Text("Only what\nmatters.",fontSize=25.sp,fontWeight=FontWeight.Medium,lineHeight=28.sp,letterSpacing=(-.6).sp,color=Cream);Text("YOUR SITE. YOUR SPACE.",fontSize=8.sp,letterSpacing=1.sp,color=Color(0xFFB6C8A7));Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)){Box(Modifier.size(5.dp).background(Lime,CircleShape));Text("Protected by uBO Lite",fontSize=9.sp,color=Lime)}}
        }
    }
}
