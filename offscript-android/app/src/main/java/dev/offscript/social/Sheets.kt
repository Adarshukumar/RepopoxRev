package dev.offscript.social

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable fun Onboarding(done: () -> Unit) {
    val pager = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    val motion = LocalMotion.current
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val alpha by animateFloatAsState(if(entered)1f else 0f,tween(if(motion)650 else 0),label="welcome reveal")
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp).alpha(alpha),horizontalAlignment=Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Brand();Spacer(Modifier.weight(1f));TextButton(done) { Text("Skip",color=C.muted,fontSize=12.sp) } }
        Spacer(Modifier.height(20.dp))
        HorizontalPager(pager,Modifier.weight(1f),pageSpacing=24.dp) { index ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                Box(Modifier.fillMaxWidth().heightIn(min=170.dp,max=330.dp).aspectRatio(1.05f).clip(RoundedCornerShape(topStart=55.dp,topEnd=55.dp,bottomStart=20.dp,bottomEnd=20.dp))) {
                    Photo(listOf("coast","lake","cafe")[index],"A little inspiration",Modifier.fillMaxSize())
                    Box(Modifier.align(Alignment.BottomEnd).padding(16.dp).size(64.dp).clip(CircleShape).background(Orange),contentAlignment=Alignment.Center) { Glyph("spark",Color.White,Modifier.size(42.dp).rotate(index*15f)) }
                    Text("0${index+1} / OFF THE BEATEN FEED",color=Color.White,fontSize=9.sp,letterSpacing=1.sp,modifier=Modifier.align(Alignment.BottomStart).padding(20.dp).background(Color.Black.copy(alpha=.3f),RoundedCornerShape(5.dp)).padding(6.dp))
                }
                Eyebrow(listOf("LESS ALGORITHM. MORE YOU.","YOUR KIND OF PEOPLE.","SMALL MOMENTS. REAL JOY.")[index])
                Title(listOf("Life happens\noff script.","Find a little\ngood company.","Make room for\nthe everyday.")[index],size=38)
                Txt(listOf("A quieter corner for your photos, your people, and the little things worth sharing.","Follow a fresh perspective. Save what moves you. Start a conversation, just because.","This is your fully offline playground. Create, explore, and make it yours—no account needed.")[index],14,C.muted)
            }
        }
        Row(Modifier.padding(vertical=20.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)) { repeat(3) { i -> Box(Modifier.height(5.dp).width(if(i==pager.currentPage)25.dp else 7.dp).clip(CircleShape).background(if(i==pager.currentPage)Orange else C.line)) } }
        Primary(if(pager.currentPage==2)"Make yourself at home" else "A little more life",Modifier.fillMaxWidth()) { if(pager.currentPage==2)done() else scope.launch { if(motion)pager.animateScrollToPage(pager.currentPage+1) else pager.scrollToPage(pager.currentPage+1) } }
        Spacer(Modifier.height(12.dp));Txt("No sign-up. No tracking. Just a local demo.",10,C.muted)
    }
}

@Composable fun Composer(store: SocialStore, draft: String, onDraft: (String) -> Unit, onDismiss: () -> Unit, publish: (String,String,String,String) -> Unit) {
    var image by rememberSaveable { mutableStateOf("") }
    var place by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("Everyday") }
    var importing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) { importing=true;error="";store.importPhoto(uri) { path -> importing=false;if(path==null)error="That image could not be opened. Try a JPEG or PNG photo." else image=path } }
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(17.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Eyebrow("A LITTLE PIECE OF YOUR WORLD");Spacer(Modifier.height(7.dp));Title("Life, unfiltered.") };IconAction("close","Close composer",onClick=onDismiss) }
        Txt("Visible only on this device · offline demo",11,C.muted)
        OutlinedTextField(draft,{ onDraft(it.take(600)) },Modifier.fillMaxWidth().heightIn(min=135.dp),placeholder={ Txt("What’s your little moment today?",15,C.muted) },shape=RoundedCornerShape(17.dp),supportingText={ Text("${draft.length}/600") })
        if(image.isNotEmpty()) Box(Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(16.dp))) { Photo(image,"Selected photo",Modifier.fillMaxSize());IconAction("close","Remove selected photo",Modifier.align(Alignment.TopEnd).background(C.surface,CircleShape)) { image="" } }
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { OutlinedButton({ picker.launch(arrayOf("image/*")) },enabled=!importing,shape=RoundedCornerShape(12.dp)) { Glyph("image",Orange,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text(if(importing)"Opening photo…" else "Choose a photo",fontSize=12.sp) };Spacer(Modifier.weight(1f));Txt("or try one below",10,C.muted) }
        if(importing) LinearProgressIndicator(Modifier.fillMaxWidth(),color=Orange)
        if(error.isNotEmpty()) Txt(error,12,MaterialTheme.colorScheme.error)
        LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)) { items(listOf("coast","lake","cafe","mountains","italy","coffee")) { source -> Box(Modifier.size(67.dp).clip(RoundedCornerShape(12.dp)).border(if(image==source)3.dp else 0.dp,Orange,RoundedCornerShape(12.dp)).clickable { image=source }) { Photo(source,"Use $source photo",Modifier.fillMaxSize());if(image==source)Box(Modifier.align(Alignment.BottomEnd).background(Orange,CircleShape).padding(3.dp)) { Glyph("check",Color.White,Modifier.size(14.dp)) } } } }
        OutlinedTextField(place,{ place=it.take(80) },Modifier.fillMaxWidth(),label={ Text("Add a place (optional)") },leadingIcon={ Glyph("pin",C.muted,Modifier.size(19.dp)) },singleLine=true,shape=RoundedCornerShape(14.dp))
        LazyRow(horizontalArrangement=Arrangement.spacedBy(7.dp)) { items(listOf("Everyday","Photography","Travel","Outdoors")) { c -> Chip(c,category==c) { category=c } } }
        Primary("Share moment",Modifier.fillMaxWidth(),draft.isNotBlank()&&!importing) { publish(draft,image,place,category) }
    }
}

@Composable fun CommentsSheet(p: Post, send: (String) -> Unit) {
    var text by rememberSaveable(p.id) { mutableStateOf("") }
    val list=rememberLazyListState()
    LaunchedEffect(p.comments.size) { if(p.comments.isNotEmpty())list.animateScrollToItem(p.comments.lastIndex) }
    Column(Modifier.fillMaxWidth().padding(horizontal=24.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Eyebrow("A LITTLE CONVERSATION");Title("Good things, shared.",size=27)
        if(p.comments.isEmpty()) Txt("Be the first to add a little kindness.",13,C.muted)
        LazyColumn(Modifier.weight(1f,fill=false).heightIn(max=380.dp),state=list,verticalArrangement=Arrangement.spacedBy(16.dp)) { itemsIndexed(p.comments) { _,comment -> Row(horizontalArrangement=Arrangement.spacedBy(11.dp)) { Box(Modifier.size(32.dp).clip(CircleShape).background(C.soft),contentAlignment=Alignment.Center) { Txt(comment.author.take(1),12) };Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) { Txt(comment.author,11,weight=FontWeight.SemiBold);Txt(comment.body,13) } } } }
        HorizontalDivider(color=C.line)
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) { OutlinedTextField(text,{ text=it.take(500) },Modifier.weight(1f),placeholder={ Txt("Add a little kindness…",12,C.muted) },shape=RoundedCornerShape(15.dp),maxLines=4);IconButton({ if(text.isNotBlank()){send(text);text=""} },modifier=Modifier.semantics { contentDescription = "Send comment" },enabled=text.isNotBlank()) { Glyph("send",if(text.isNotBlank())Orange else C.muted,Modifier.size(23.dp)) } }
        Txt("Comments are saved to this device.",10,C.muted)
    }
}
@Composable fun PhotoViewer(p: Post, person: Person, comment: () -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=20.dp).padding(bottom=25.dp),verticalArrangement=Arrangement.spacedBy(17.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) { Avatar(person);Column { Txt(person.name,14,weight=FontWeight.SemiBold);Txt(p.location,11,C.muted) } }
        if(p.image.isNotEmpty())Photo(p.image,p.location,Modifier.fillMaxWidth().aspectRatio(.86f).clip(RoundedCornerShape(20.dp)))
        Title(p.location,size=24);Txt(p.caption,15,C.muted)
        TextButton(comment) { Text("Join the conversation · ${p.comments.size}",color=Orange) }
    }
}
@Composable fun PersonSheet(p: Person, s: SocialState, follow: () -> Unit, message: () -> Unit, photo: (Long) -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Avatar(p,84);Title(p.name,size=29);Txt("@${p.handle} · fictional demo profile",11,C.muted);Txt(p.bio,14,C.muted)
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) { Button(follow,shape=RoundedCornerShape(12.dp),colors=ButtonDefaults.buttonColors(containerColor=if(p.id in s.following)C.soft else Orange,contentColor=if(p.id in s.following)C.ink else Color.White)) { Text(if(p.id in s.following)"Following ✓" else "Follow perspective") };OutlinedButton(message,shape=RoundedCornerShape(12.dp)) { Text("Message",color=C.ink) } }
        HorizontalDivider(color=C.line);Eyebrow("MOMENTS FROM ${p.name.substringBefore(" ")}")
        LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp)) { items(s.posts.filter{it.author==p.id}) { post -> Photo(post.image,post.location,Modifier.width(180.dp).height(225.dp).clip(RoundedCornerShape(17.dp)).clickable { photo(post.id) }) } }
    }
}
@Composable fun ChatSheet(p: Person, messages: List<ChatMessage>, send: (String) -> Unit) {
    var text by rememberSaveable(p.id) { mutableStateOf("") }
    val list=rememberLazyListState()
    val motion=LocalMotion.current
    LaunchedEffect(messages.size) { if(messages.isNotEmpty()) { if(motion)list.animateScrollToItem(messages.lastIndex) else list.scrollToItem(messages.lastIndex) } }
    Column(Modifier.fillMaxWidth().padding(horizontal=20.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) { Avatar(p,44);Column { Txt(p.name,16,weight=FontWeight.SemiBold);Txt("Local demo conversation · not delivered",10,C.muted) } }
        HorizontalDivider(color=C.line)
        LazyColumn(state=list,modifier=Modifier.heightIn(min=160.dp,max=370.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(messages.isEmpty())item { Column(Modifier.padding(vertical=25.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) { Title("Start with hello.",size=25);Txt("Try sending a message. It stays on this device; there’s no live person or automated reply on the other end.",13,C.muted) } }
            itemsIndexed(messages) { index,m -> Row(Modifier.fillMaxWidth().then(if(motion)Modifier.animateItem()else Modifier),horizontalArrangement=if(m.incoming)Arrangement.Start else Arrangement.End) { Column(Modifier.widthIn(max=270.dp).clip(RoundedCornerShape(16.dp)).background(if(m.incoming)C.soft else C.accentSoft).padding(14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) { Txt(m.text,14);Txt("Saved locally",9,C.muted) } } }
        }
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) { OutlinedTextField(text,{ text=it.take(2000) },Modifier.weight(1f),placeholder={ Txt("Say something nice…",13,C.muted) },shape=RoundedCornerShape(16.dp),maxLines=4);IconButton({ if(text.isNotBlank()){send(text);text=""} },modifier=Modifier.semantics { contentDescription = "Send message" },enabled=text.isNotBlank()) { Glyph("send",if(text.isNotBlank())Orange else C.muted,Modifier.size(23.dp)) } }
    }
}
@Composable fun SettingsSheet(s: SocialState, update: ((SocialState)->SocialState)->Unit, about: () -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(22.dp)) {
        Eyebrow("MAKE YOURSELF AT HOME");Title("Your own little world.",size=28)
        Txt("Appearance",15,weight=FontWeight.SemiBold)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("System","Light","Dark").forEach { t -> Chip(t,s.theme==t) { update { it.copy(theme=t) } } } }
        HorizontalDivider(color=C.line)
        Row(verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) { Txt("Gentler motion",15,weight=FontWeight.SemiBold);Txt("Reduce screen transitions and feedback animations.",12,C.muted) };Switch(s.reduceMotion,{ v -> update { it.copy(reduceMotion=v) } }) }
        Txt("Android’s system animation preference is also respected.",11,C.muted)
        HorizontalDivider(color=C.line)
        Row(Modifier.fillMaxWidth().clickable(onClick=about).padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Txt("About this little corner",15);Txt("Privacy, demo scope & image credits",11,C.muted) };Glyph("chevron",C.muted,Modifier.size(20.dp)) }
        Txt("offscript 1.0.0 · native Android preview",10,C.muted)
    }
}
@Composable fun EditProfile(s: SocialState, save: (String,String)->Unit) {
    var name by rememberSaveable { mutableStateOf(s.name) };var bio by rememberSaveable { mutableStateOf(s.bio) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Title("A little more you.");Avatar(s.person(-1),64)
        OutlinedTextField(name,{name=it.take(40)},Modifier.fillMaxWidth(),label={Text("Your name")},singleLine=true,shape=RoundedCornerShape(14.dp))
        OutlinedTextField(bio,{bio=it.take(160)},Modifier.fillMaxWidth(),label={Text("A few words about you")},maxLines=4,shape=RoundedCornerShape(14.dp))
        Primary("Save profile",Modifier.fillMaxWidth(),name.isNotBlank()) { save(name.trim(),bio.trim()) }
    }
}
@Composable fun AboutSheet() {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(19.dp)) {
        Brand(35);Title("Less algorithm.\nMore you.",size=30)
        Txt("A native Kotlin / Jetpack Compose social-app playground. The people, activity, likes and locations in the starter feed are fictional sample content.",14,C.muted)
        Eyebrow("PRIVATE BY CONSTRUCTION")
        Txt("This version requests no internet or storage permissions. Posts, profile details, comments and messages remain in the app’s private storage. Imported photos are copied into the app; uninstalling or clearing app data removes everything. The system share button sends text only when you choose a destination.",13,C.muted)
        Eyebrow("BUILT TO TRY, NOT A LIVE SOCIAL NETWORK")
        Txt("No accounts, backend, live notifications or delivered messages. Settings, posts and interactions persist across restarts. Reinstalling with the same signing key preserves data; uninstalling does not.",13,C.muted)
        Eyebrow("PHOTOGRAPHY & CREDITS")
        Txt("Reference photography sourced via The Blonde Scout, TravelReveal, BlissLights and Unsplash. See the repository’s offscript/README.md for source pages. Images are demo references, not cleared commercial assets. Names and captions do not identify the photographed people or verify locations.",12,C.muted)
        Txt("offscript 1.0.0 · Android 8.0+ · Offline preview",11,C.muted)
    }
}
