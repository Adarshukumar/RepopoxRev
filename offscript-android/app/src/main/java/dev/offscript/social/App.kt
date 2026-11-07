package dev.offscript.social

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun OffscriptApp(store: SocialStore, systemMotion: Boolean = true) {
    val state by store.state.collectAsStateWithLifecycle()
    val storageError by store.storageError.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf("Home") }
    var sheet by rememberSaveable { mutableStateOf("") }
    var selectedPost by rememberSaveable { mutableLongStateOf(1) }
    var selectedPerson by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var draft by rememberSaveable { mutableStateOf("") }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val motion = systemMotion && !state.reduceMotion
    val dark = state.theme == "Dark" || (state.theme == "System" && isSystemInDarkTheme())
    SideEffect {
        (context as? android.app.Activity)?.window?.let { window ->
            androidx.core.view.WindowCompat.getInsetsController(window,window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    fun notice(s: String) { scope.launch { snack.showSnackbar(s) } }
    fun openPost(type: String, id: Long) { selectedPost = id; sheet = type }
    fun openPerson(id: Int) { if (id == -1) page = "Profile" else { selectedPerson = id; sheet = "person" } }
    LaunchedEffect(storageError) { storageError?.let { snack.showSnackbar(it); store.storageError.value = null } }
    OffscriptTheme(dark, motion) {
        Surface(Modifier.fillMaxSize(), color = C.bg) {
            if (!state.onboarded) Onboarding { store.update { it.copy(onboarded = true) } }
            else {
                BackHandler(page != "Home" && sheet.isEmpty()) { page = "Home"; query = "" }
                Scaffold(containerColor = C.bg, snackbarHost = { SnackbarHost(snack) }, bottomBar = {
                    if (page != "Notifications") BottomNavigation(page, { page = it; query = "" }, { sheet = "compose" })
                }) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 10.dp).height(66.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (page == "Notifications") IconAction("back", "Back") { page = "Home" } else Brand(29)
                            Spacer(Modifier.weight(1f))
                            IconAction("search", "Search moments") { page = "Explore" }
                            IconAction("chat", "Messages", tint = if(page == "Messages") Orange else C.ink) { page = "Messages" }
                            IconAction(if(page == "Profile") "settings" else "bell", if(page == "Profile") "Settings" else "Notifications") { if(page == "Profile") sheet = "settings" else page = "Notifications" }
                        }
                        HorizontalDivider(color = C.line, thickness = .7.dp)
                        AnimatedContent(page, transitionSpec = {
                            (fadeIn(tween(if(motion)240 else 0)) + slideInHorizontally(tween(if(motion)300 else 0)) { it / 12 }).togetherWith(fadeOut(tween(if(motion)120 else 0)))
                        }, label = "navigation") { target ->
                            when(target) {
                                "Messages" -> Inbox(state) { selectedPerson = it; sheet = "chat" }
                                "Notifications" -> Notifications { openPerson(it) }
                                else -> FeedScreen(state, target, query, { query = it },
                                    onLike = { id -> store.update { SocialActions.like(it, id) }; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                                    onSave = { id -> val saved = state.posts.find { it.id == id }?.saved == true; store.update { SocialActions.save(it,id) }; notice(if(saved) "Removed from saved moments" else "Kept in your saved moments") },
                                    onComment = { openPost("comments",it) }, onPhoto = { openPost("photo",it) }, onPerson = { openPerson(it) },
                                    onShare = { p -> context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "${p.caption}\n${p.location}\n— Shared from offscript (offline demo)") }, "Share this moment")) },
                                    onCompose = { sheet = "compose" }, onPrompt = { draft = "Today’s little moment: "; sheet = "compose" }, onSettings = { sheet = "settings" },
                                    onFollow = { id -> store.update { SocialActions.follow(it,id) } }, onDelete = { openPost("delete",it) }, onEdit = { sheet = "edit" })
                            }
                        }
                    }
                }
            }
            if (sheet.isNotEmpty()) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(onDismissRequest = { sheet = "" }, sheetState = sheetState, containerColor = C.surface, dragHandle = { BottomSheetDefaults.DragHandle(color = C.line) }) {
                    Box(Modifier.fillMaxWidth().heightIn(max = 760.dp).imePadding()) {
                        when(sheet) {
                            "compose" -> Composer(store, draft, { draft = it }, onDismiss = { sheet = "" }) { caption, image, place, category ->
                                store.update { SocialActions.publish(it, System.currentTimeMillis(), caption, image, place, category) }
                                draft = ""; sheet = ""; page = "Home"; query = ""; notice("Your moment is now in the feed")
                            }
                            "comments" -> state.posts.find { it.id == selectedPost }?.let { p -> CommentsSheet(p) { body -> store.update { SocialActions.comment(it, p.id, body) } } }
                            "photo" -> state.posts.find { it.id == selectedPost }?.let { p -> PhotoViewer(p, state.person(p.author)) { sheet = "comments" } }
                            "person" -> PersonSheet(state.person(selectedPerson), state, { store.update { SocialActions.follow(it,selectedPerson) } }, { sheet = "chat" }, { openPost("photo",it) })
                            "settings" -> SettingsSheet(state, { store.update(it) }, { sheet = "about" })
                            "edit" -> EditProfile(state) { name, bio -> store.update { it.copy(name = name, bio = bio) }; sheet = ""; notice("Profile updated") }
                            "chat" -> ChatSheet(state.person(selectedPerson), state.messages[selectedPerson] ?: emptyList()) { body -> store.update { SocialActions.send(it,selectedPerson,body) } }
                            "delete" -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) { Title("Let this moment go?"); Txt("This removes your post from this device. It can’t be undone.", color = C.muted); Primary("Delete my post", Modifier.fillMaxWidth()) { store.update { SocialActions.delete(it,selectedPost) }; sheet = ""; notice("Post deleted") }; TextButton({ sheet = "" }, Modifier.fillMaxWidth()) { Text("Keep this moment", color = C.ink) } }
                            else -> AboutSheet()
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun BottomNavigation(page: String, navigate: (String) -> Unit, compose: () -> Unit) {
    Surface(color = C.surface, shadowElevation = 7.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 9.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
            listOf("Home" to "home", "Explore" to "explore", "Create" to "plus", "Saved" to "bookmark", "Profile" to "person").forEach { (name, icon) ->
                if(name == "Create") Box(Modifier.size(49.dp).clip(RoundedCornerShape(17.dp)).background(Orange).clickable(onClick = compose).semantics { contentDescription = "Create a post" }, contentAlignment = Alignment.Center) { Glyph(icon, Color.White, Modifier.size(25.dp)) }
                else Column(Modifier.widthIn(min = 53.dp).clip(RoundedCornerShape(13.dp)).clickable { navigate(name) }.padding(5.dp).semantics { contentDescription = "Navigate $name" }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Glyph(icon, if(page == name) Orange else C.muted, Modifier.size(22.dp), page == name && icon == "bookmark")
                    Txt(name, 9, if(page == name) Orange else C.muted, if(page == name) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable private fun FeedScreen(state: SocialState, page: String, query: String, onQuery: (String) -> Unit, onLike: (Long) -> Unit, onSave: (Long) -> Unit, onComment: (Long) -> Unit, onPhoto: (Long) -> Unit, onPerson: (Int) -> Unit, onShare: (Post) -> Unit, onCompose: () -> Unit, onPrompt: () -> Unit, onSettings: () -> Unit, onFollow: (Int) -> Unit, onDelete: (Long) -> Unit, onEdit: () -> Unit) {
    var category by rememberSaveable(page) { mutableStateOf("All") }
    var following by rememberSaveable { mutableStateOf(false) }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val motion = LocalMotion.current
    val posts = SocialActions.filter(state, query, category, following && page == "Home", page == "Saved", if(page == "Profile") -1 else null)
    val contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 28.dp)
    LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item("heading") {
            if(page == "Profile") ProfileHeader(state, onEdit, onSettings)
            else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Eyebrow(when(page) { "Explore" -> "A NEW PERSPECTIVE IS WAITING"; "Saved" -> "THE MOMENTS YOU WANT TO KEEP"; else -> "A LITTLE INSPIRATION. A LOT OF REAL LIFE." })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Title(when(page) { "Explore" -> "Follow your\ncuriosity."; "Saved" -> "Keep what\nmoves you."; else -> "Good things\nhappen here." }, Modifier.weight(1f), 34)
                    Glyph("spark", Color(0xFFB5BEA1), Modifier.size(64.dp).rotate(12f))
                }
                Txt(when(page) { "Explore" -> "A new place. A new perspective. Your kind of people."; "Saved" -> "Your own collection of little discoveries."; else -> "Fresh perspectives, familiar faces, and little moments worth sharing." }, 12, C.muted)
            }
        }
        if(page == "Home") item("stories") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(17.dp), modifier = Modifier.fillMaxWidth()) {
                item { Column(Modifier.width(62.dp).clickable(onClick = onCompose), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) { Box { Avatar(state.person(-1),58); Box(Modifier.size(22.dp).align(Alignment.BottomEnd).clip(CircleShape).background(C.ink).border(2.dp,C.bg,CircleShape), contentAlignment = Alignment.Center) { Glyph("plus",C.bg,Modifier.size(13.dp)) } };Txt("Your moment",9,C.muted) } }
                items(PEOPLE) { p -> Column(Modifier.width(63.dp).clickable { onPhoto((p.id + 1).toLong()) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) { Box(Modifier.size(62.dp).border(1.5.dp, if(p.id%2==0) Orange.copy(alpha=.7f) else Color(0xFF9AAE82),CircleShape).padding(4.dp)) { Photo(listOf("italy","lake","cafe","mountains")[p.id], "${p.name}'s moment",Modifier.fillMaxSize().clip(CircleShape)) };Txt(p.name.substringBefore(" "),10) } }
            }
        }
        if(page == "Explore") {
            item("search") { OutlinedTextField(query, onQuery, Modifier.fillMaxWidth(), placeholder = { Txt("Places, people, little moments…",13,C.muted) }, leadingIcon = { Glyph("search",C.muted,Modifier.size(20.dp)) }, trailingIcon = { if(query.isNotEmpty()) IconAction("close","Clear search") { onQuery("") } }, singleLine = true, shape = RoundedCornerShape(16.dp)) }
            item("people") { Column(verticalArrangement = Arrangement.spacedBy(13.dp)) { Eyebrow("YOUR KIND OF PEOPLE"); LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(PEOPLE) { p -> Column(Modifier.width(145.dp).clip(RoundedCornerShape(18.dp)).background(C.surface).border(1.dp,C.line,RoundedCornerShape(18.dp)).padding(15.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) { Avatar(p,48) { onPerson(p.id) };Txt(p.name,12,weight=FontWeight.SemiBold); TextButton({ onFollow(p.id) }, colors = ButtonDefaults.textButtonColors(contentColor = if(p.id in state.following) C.muted else Orange)) { Text(if(p.id in state.following) "Following ✓" else "+ Follow",fontSize=12.sp) } } } } } }
        }
        item("filters") {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if(page == "Home") {
                        listOf(false to "For you",true to "Following").forEach { (f,t) -> Column(Modifier.clickable { following = f }.padding(end = 25.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Txt(t,14,if(following==f) C.ink else C.muted,FontWeight.SemiBold);Box(Modifier.width(28.dp).height(2.dp).background(if(following==f) Orange else Color.Transparent)) } }
                    } else Eyebrow(if(page=="Profile") "YOUR MOMENTS" else "${posts.size} MOMENTS")
                    Spacer(Modifier.weight(1f)); Txt(if(page=="Home") "Made for you ✦" else "OFFLINE DEMO",9,C.muted)
                }
                HorizontalDivider(color=C.line)
                LazyRow(horizontalArrangement=Arrangement.spacedBy(7.dp)) { items(listOf("All","Photography","Travel","Everyday","Outdoors")) { c -> Chip(c,category==c) { category=c } } }
            }
        }
        if(posts.isEmpty()) item("empty") { Column(Modifier.fillMaxWidth().padding(vertical=30.dp), horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) { Glyph(if(page=="Saved")"bookmark" else "image",C.muted,Modifier.size(36.dp));Title(if(page=="Saved")"A little space for inspiration." else "Your next moment starts here.",size=23);Txt(if(page=="Saved")"Tap a bookmark on any post to keep it here." else "Try another filter, follow someone, or share a moment.",12,C.muted);Primary("Share a moment",onClick=onCompose) } }
        itemsIndexed(posts, key = { _,p -> p.id }) { index,p ->
            Column(Modifier.then(if(motion) Modifier.animateItem() else Modifier),verticalArrangement=Arrangement.spacedBy(22.dp)) {
                PostCard(p,state.person(p.author),{ onLike(p.id) },{ onSave(p.id) },{ onComment(p.id) },{ onPhoto(p.id) },{ onPerson(p.author) },{ onShare(p) },{ onDelete(p.id) })
                if(index==1 && page=="Home" && !following) DailyPrompt(onPrompt)
            }
        }
        item("end") { Column(Modifier.fillMaxWidth().padding(vertical=12.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) { Glyph("spark",Color(0xFFA5B18F),Modifier.size(28.dp));Txt("You’re all caught up. Go make a little life.",11,C.muted);TextButton({ scope.launch { if(motion)list.animateScrollToItem(0) else list.scrollToItem(0) } }) { Text("Back to the top",color=C.muted,fontSize=11.sp) } } }
    }
}

@Composable fun PostCard(p: Post, person: Person, like: () -> Unit, save: () -> Unit, comment: () -> Unit, photo: () -> Unit, profile: () -> Unit, share: () -> Unit, delete: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(21.dp)).background(C.surface).border(1.dp,C.line,RoundedCornerShape(21.dp))) {
        Row(Modifier.fillMaxWidth().padding(start=14.dp,end=7.dp,top=10.dp,bottom=10.dp),verticalAlignment=Alignment.CenterVertically) {
            Avatar(person,36,profile);Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f).clickable(onClick=profile)) { Txt(person.name,12,weight=FontWeight.SemiBold);Txt(p.location,10,C.muted,maxLines=1) }
            IconAction("bookmark",if(p.saved) "Unsave post ${p.id}" else "Save post ${p.id}",tint=if(p.saved) Orange else C.ink, selected=p.saved,onClick=save)
        }
        if(p.image.isNotEmpty()) Box(Modifier.fillMaxWidth().aspectRatio(if(p.category=="Outdoors")1.05f else .95f).clickable(onClick=photo)) {
            Photo(p.image,p.location,Modifier.fillMaxSize())
            Row(Modifier.align(Alignment.BottomStart).padding(14.dp).clip(RoundedCornerShape(7.dp)).background(Color.White.copy(alpha=.91f)).padding(horizontal=9.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) { Glyph("pin",Color(0xFF454C3B),Modifier.size(12.dp));Txt(p.location,9,Color(0xFF454C3B),maxLines=1) }
        }
        Column(Modifier.padding(horizontal=13.dp).padding(bottom=17.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                IconAction("heart","Like post ${p.id}",tint=if(p.liked)Orange else C.ink, selected=p.liked,onClick=like);Txt(p.likes.toString(),11)
                IconAction("chat","Comments on post ${p.id}",onClick=comment);Txt(p.comments.size.toString(),11)
                IconAction("send","Share post ${p.id}",onClick=share)
                Spacer(Modifier.weight(1f));Txt(p.category,9,C.muted)
            }
            Txt(p.caption,13,modifier=Modifier.padding(horizontal=3.dp),maxLines=8)
            Spacer(Modifier.height(7.dp))
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Txt("#${p.category.lowercase()}  #offscript",10,C.muted,modifier=Modifier.weight(1f)); if(p.author == -1) TextButton(delete, contentPadding=PaddingValues(0.dp)) { Text("Delete",fontSize=10.sp,color=C.muted) } }
        }
    }
}
@Composable fun DailyPrompt(onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color(0xFFE8EDDC)).padding(23.dp),verticalArrangement=Arrangement.spacedBy(13.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) { Text("THE DAILY PROMPT",color=Color(0xFF667452),fontSize=9.sp,letterSpacing=1.4.sp);Spacer(Modifier.weight(1f));Glyph("spark",Color(0xFF6B7B55),Modifier.size(22.dp)) }
        Row(verticalAlignment=Alignment.CenterVertically) { Text("Find beauty in\nthe ordinary.",fontSize=28.sp,fontWeight=FontWeight.SemiBold,letterSpacing=(-.8).sp,color=Color(0xFF303A29),modifier=Modifier.weight(1f));Box(Modifier.size(70.dp).clip(CircleShape).background(Color(0xFFEE744B)),contentAlignment=Alignment.Center) { Glyph("spark",Color(0xFF314B32),Modifier.size(64.dp).rotate(15f)) } }
        Txt("Your morning light. A familiar street. Show us a little moment from today.",12,Color(0xFF677255))
        TextButton(onClick,contentPadding=PaddingValues(0.dp)) { Text("Share your moment",color=Color(0xFF37412C),fontSize=13.sp,fontWeight=FontWeight.SemiBold);Spacer(Modifier.width(15.dp));Glyph("arrow",Color(0xFF37412C),Modifier.size(18.dp)) }
    }
}
@Composable private fun ProfileHeader(s: SocialState, edit: () -> Unit, settings: () -> Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(17.dp)) {
        Eyebrow("YOUR LITTLE CORNER")
        Row(verticalAlignment=Alignment.CenterVertically) { Avatar(s.person(-1),76);Spacer(Modifier.width(18.dp));Column { Title(s.name,size=27);Txt("@you · offline creator",12,C.muted) };Spacer(Modifier.weight(1f));IconAction("settings","Profile settings",onClick=settings) }
        Txt(s.bio,13,C.muted)
        Row(horizontalArrangement=Arrangement.spacedBy(28.dp)) { listOf("${s.posts.count{it.author == -1}}" to "posts","${s.following.size}" to "following","${s.posts.count{it.saved}}" to "saved").forEach { (count,label) -> Column { Txt(count,22,weight=FontWeight.SemiBold);Txt(label,11,C.muted) } } }
        OutlinedButton(edit,Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)) { Text("Edit your profile",color=C.ink) }
    }
}
@Composable private fun Inbox(s: SocialState, open: (Int) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(22.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        item { Column(verticalArrangement=Arrangement.spacedBy(10.dp)) { Eyebrow("GOOD CONVERSATIONS START SMALL");Title("A little less\ndistance.",size=34);Txt("Demo inbox · messages stay on this device",12,C.muted) } }
        items(PEOPLE) { p -> Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.surface).clickable { open(p.id) }.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) { Avatar(p,48);Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) { Txt(p.name,14,weight=FontWeight.SemiBold);Txt(s.messages[p.id]?.lastOrNull()?.text ?: "Say hello. Make a little connection.",12,C.muted,maxLines=1) };Glyph("chevron",C.muted,Modifier.size(18.dp)) } }
        item { Txt("These are fictional profiles. Messages are saved locally, not delivered to another person.",11,C.muted) }
    }
}
@Composable private fun Notifications(open: (Int) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(22.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        item { Title("You’re part\nof the story.",size=34) };item { Eyebrow("SAMPLE ACTIVITY · NOT LIVE") }
        items(PEOPLE.take(3)) { p -> Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(C.surface).clickable { open(p.id) }.padding(16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) { Avatar(p);Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) { Txt(p.name,13,weight=FontWeight.SemiBold);Txt(listOf("liked a little moment from your day.","started following your perspective.","invited you to notice the everyday.")[p.id],12,C.muted);Txt("Sample notification",9,C.muted) };Glyph("heart",Orange,Modifier.size(18.dp)) } }
    }
}
