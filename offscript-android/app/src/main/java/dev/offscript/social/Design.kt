package dev.offscript.social

import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

val Orange = Color(0xFFEF5B35)
data class Palette(val bg: Color, val surface: Color, val ink: Color, val muted: Color, val line: Color, val soft: Color, val accentSoft: Color)
val LightPalette = Palette(Color(0xFFF7F7F2), Color.White, Color(0xFF252720), Color(0xFF697061), Color(0xFFE4E6DD), Color(0xFFEEEFE6), Color(0xFFFBE9E0))
val DarkPalette = Palette(Color(0xFF1D211D), Color(0xFF272D26), Color(0xFFE8EDDF), Color(0xFFAAB4A1), Color(0xFF3C4537), Color(0xFF323B2D), Color(0xFF4B3328))
val LocalPalette = staticCompositionLocalOf { LightPalette }
val LocalMotion = staticCompositionLocalOf { true }
val C: Palette @Composable get() = LocalPalette.current

@Composable fun OffscriptTheme(dark: Boolean, motion: Boolean, content: @Composable () -> Unit) {
    val p = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) darkColorScheme(primary = Orange, background = p.bg, surface = p.surface, onSurface = p.ink, onBackground = p.ink, secondaryContainer = p.soft, outline = p.line) else lightColorScheme(primary = Orange, background = p.bg, surface = p.surface, onSurface = p.ink, onBackground = p.ink, secondaryContainer = p.soft, outline = p.line)
    CompositionLocalProvider(LocalPalette provides p, LocalMotion provides motion) { MaterialTheme(colorScheme = scheme, content = content) }
}
@Composable fun Txt(text: String, size: Int = 14, color: Color = C.ink, weight: FontWeight = FontWeight.Normal, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) {
    Text(text, modifier, color, fontSize = size.sp, fontWeight = weight, lineHeight = (size * 1.45).sp, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}
@Composable fun Eyebrow(text: String, modifier: Modifier = Modifier) { Text(text.uppercase(), modifier, color = C.muted, fontSize = 9.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Medium) }
@Composable fun Title(text: String, modifier: Modifier = Modifier, size: Int = 30) { Text(text, modifier, color = C.ink, fontSize = size.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.1).sp, lineHeight = (size * 1.16).sp) }
@Composable fun Avatar(p: Person, size: Int = 42, onClick: (() -> Unit)? = null) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Color(p.color)).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier), contentAlignment = Alignment.Center) { Txt(p.initials, (size * .28).toInt(), Color(0xFF42493C), FontWeight.SemiBold) }
}
@Composable fun Brand(size: Int = 30) {
    Row(verticalAlignment = Alignment.CenterVertically) { Text("offscript", color = C.ink, fontSize = size.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.6).sp); Spacer(Modifier.width(3.dp)); Glyph("spark", Orange, Modifier.size((size + 3).dp)) }
}
@Composable fun Primary(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick, modifier.heightIn(min = 50.dp), enabled, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Orange, contentColor = Color.White), contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp)) { Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold); Spacer(Modifier.width(12.dp)); Glyph("arrow", Color.White, Modifier.size(18.dp)) }
}
@Composable fun IconAction(icon: String, label: String, modifier: Modifier = Modifier, tint: Color = C.ink, selected: Boolean = false, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (selected) 1.12f else 1f, spring(dampingRatio = .45f), label = "icon feedback")
    IconButton(onClick, modifier.size(48.dp).semantics { contentDescription = label }) { Glyph(icon, tint, Modifier.size(23.dp).scale(if (LocalMotion.current) scale else 1f), selected) }
}
@Composable fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.heightIn(min = 42.dp).clip(RoundedCornerShape(10.dp)).background(if (selected) C.ink else C.surface).border(1.dp, if (selected) C.ink else C.line, RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 15.dp, vertical = 11.dp), contentAlignment = Alignment.Center) { Txt(text, 12, if (selected) C.bg else C.muted, if (selected) FontWeight.Medium else FontWeight.Normal) }
}
@Composable fun Glyph(name: String, color: Color = C.ink, modifier: Modifier = Modifier, filled: Boolean = false) {
    Canvas(modifier) {
        val u = size.width / 24f
        val stroke = Stroke(1.65f * u, cap = StrokeCap.Round)
        fun line(x: Float, y: Float, a: Float, b: Float) = drawLine(color, Offset(x*u,y*u),Offset(a*u,b*u),1.65f*u,StrokeCap.Round)
        fun path(vararg points: Pair<Float,Float>, close: Boolean = false) { val p=Path(); points.forEachIndexed { i,v -> if(i==0)p.moveTo(v.first*u,v.second*u) else p.lineTo(v.first*u,v.second*u) }; if(close)p.close();drawPath(p,color,style=stroke) }
        when(name) {
            "spark" -> for(i in 0..3) { val a=i*Math.PI/4; val x=cos(a).toFloat()*10;val y=sin(a).toFloat()*10;line(12-x,12-y,12+x,12+y) }
            "home" -> { path(3f to 10f,12f to 3f,21f to 10f);path(5f to 9f,5f to 21f,10f to 21f,10f to 14f,14f to 14f,14f to 21f,19f to 21f,19f to 9f) }
            "explore" -> { drawCircle(color,10*u,Offset(12*u,12*u),style=stroke);path(16f to 8f,14f to 14f,8f to 16f,10f to 10f,close=true) }
            "heart" -> { val p=Path().apply { moveTo(12*u,21*u);cubicTo(10*u,18*u,2*u,13*u,2*u,7*u);cubicTo(2*u,1*u,10*u,1*u,12*u,6*u);cubicTo(14*u,1*u,22*u,1*u,22*u,7*u);cubicTo(22*u,13*u,14*u,18*u,12*u,21*u);close() };if(filled)drawPath(p,color)else drawPath(p,color,style=stroke) }
            "bookmark" -> { val p=Path().apply{moveTo(6*u,3*u);lineTo(18*u,3*u);lineTo(18*u,22*u);lineTo(12*u,17*u);lineTo(6*u,22*u);close()};if(filled)drawPath(p,color)else drawPath(p,color,style=stroke) }
            "chat" -> { drawRoundRect(color,Offset(3*u,3*u),Size(18*u,15*u),androidx.compose.ui.geometry.CornerRadius(5*u),style=stroke);path(8f to 18f,5f to 22f,5f to 17f) }
            "send" -> { path(2f to 3f,22f to 2f,18f to 22f,11f to 13f,2f to 3f);line(11f,13f,22f,2f) }
            "plus" -> {line(12f,4f,12f,20f);line(4f,12f,20f,12f)}
            "close" -> {line(6f,6f,18f,18f);line(6f,18f,18f,6f)}
            "arrow" -> {line(5f,19f,19f,5f);path(7f to 5f,19f to 5f,19f to 17f)}
            "back" -> {path(14f to 5f,7f to 12f,14f to 19f)}
            "chevron" -> {path(9f to 5f,16f to 12f,9f to 19f)}
            "search" -> {drawCircle(color,7*u,Offset(10*u,10*u),style=stroke);line(15f,15f,21f,21f)}
            "check" -> path(4f to 12f,9f to 17f,20f to 6f)
            "pin" -> {drawCircle(color,7*u,Offset(12*u,9*u),style=stroke);path(6f to 13f,12f to 22f,18f to 13f);drawCircle(color,2*u,Offset(12*u,9*u),style=stroke)}
            "image" -> {drawRoundRect(color,Offset(2*u,3*u),Size(20*u,18*u),androidx.compose.ui.geometry.CornerRadius(3*u),style=stroke);path(3f to 17f,9f to 11f,14f to 16f,18f to 12f,22f to 17f);drawCircle(color,2*u,Offset(16*u,8*u))}
            "bell" -> {path(4f to 18f,6f to 15f,6f to 9f);drawArc(color,180f,180f,false,Offset(6*u,3*u),Size(12*u,12*u),style=stroke);path(18f to 9f,18f to 15f,20f to 18f,4f to 18f);drawArc(color,0f,180f,false,Offset(9*u,18*u),Size(6*u,4*u),style=stroke)}
            "settings" -> {drawCircle(color,4*u,Offset(12*u,12*u),style=stroke);for(i in 0..7){rotate(i*45f){line(12f,2f,12f,5f)}};drawCircle(color,8*u,Offset(12*u,12*u),style=stroke)}
            "moon" -> {drawArc(color,45f,270f,false,Offset(3*u,3*u),Size(18*u,18*u),style=stroke);drawArc(color,90f,180f,false,Offset(11*u,1*u),Size(12*u,16*u),style=stroke)}
            else -> {drawCircle(color,4*u,Offset(12*u,7*u),style=stroke);drawArc(color,180f,180f,false,Offset(3*u,13*u),Size(18*u,16*u),style=stroke)}
        }
    }
}
fun resourceFor(name: String): Int = when(name) { "coast" -> R.drawable.coast; "italy" -> R.drawable.italy; "lake" -> R.drawable.lake; "mountains" -> R.drawable.mountains; "coffee" -> R.drawable.coffee; else -> R.drawable.cafe }
private val photos = android.util.LruCache<String, ImageBitmap>(8)
@Composable fun Photo(source: String, description: String, modifier: Modifier = Modifier) {
    if (!source.startsWith("/")) Image(painterResource(resourceFor(source)), description, modifier, contentScale = ContentScale.Crop)
    else {
        val bitmap by produceState<ImageBitmap?>(photos.get(source), source) {
            value = withContext(Dispatchers.IO) { photos.get(source) ?: try { BitmapFactory.decodeFile(source)?.asImageBitmap()?.also { photos.put(source,it) } } catch (_: Exception) { null } }
        }
        val b = bitmap
        if (b != null) Image(b, description, modifier, contentScale = ContentScale.Crop)
        else Box(modifier.background(C.soft), contentAlignment = Alignment.Center) { Glyph("image", C.muted, Modifier.size(28.dp)) }
    }
}
