package dev.offscript.social

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

class SocialStore(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("offscript-v1", 0)
    private val disk = Executors.newSingleThreadExecutor()
    private val _state = MutableStateFlow(load())
    val state = _state.asStateFlow()
    val storageError = MutableStateFlow<String?>(null)
    private fun load(): SocialState = try {
        val raw = prefs.getString("state", null)
        if (raw == null) SocialState() else decode(JSONObject(raw))
    } catch (_: Exception) { SocialState() }
    fun update(action: (SocialState) -> SocialState) {
        val next = action(_state.value)
        _state.value = next
        // Serial IO keeps disk writes ordered, without blocking scroll or animation.
        disk.execute {
            try {
                if (!prefs.edit().putString("state", encode(next).toString()).commit()) storageError.value = "Could not save changes. Your device may be low on storage."
            } catch (_: Exception) { storageError.value = "Could not save changes to this device." }
        }
    }
    fun importPhoto(uri: Uri, result: (String?) -> Unit) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) {
                try {
                    val resolver = getApplication<Application>().contentResolver
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                    require(options.outWidth > 0 && options.outHeight > 0)
                    var sample = 1
                    while (maxOf(options.outWidth, options.outHeight) / sample > 1800) sample *= 2
                    val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
                    requireNotNull(bitmap)
                    val folder = File(getApplication<Application>().filesDir, "photos").apply { mkdirs() }
                    val out = File(folder, "${System.currentTimeMillis()}.jpg")
                    out.outputStream().use { require(bitmap.compress(Bitmap.CompressFormat.JPEG, 86, it)) }
                    bitmap.recycle()
                    out.absolutePath
                } catch (_: Exception) { null } catch (_: OutOfMemoryError) { null }
            }
            result(path)
        }
    }
    override fun onCleared() { disk.shutdown() }
}
private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
internal fun encode(s: SocialState): JSONObject = JSONObject().apply {
    put("name", s.name); put("bio", s.bio); put("theme", s.theme); put("motion", s.reduceMotion); put("onboarded", s.onboarded)
    put("following", JSONArray(s.following.toList()))
    put("posts", JSONArray(s.posts.map { p -> JSONObject().apply {
        put("id", p.id); put("author", p.author); put("image", p.image); put("caption", p.caption); put("location", p.location); put("category", p.category)
        put("likes", p.baseLikes); put("liked", p.liked); put("saved", p.saved)
        put("comments", JSONArray(p.comments.map { JSONObject().put("author", it.author).put("body", it.body) }))
    } }))
    put("messages", JSONObject().apply { s.messages.forEach { (id, list) -> put(id.toString(), JSONArray(list.map { JSONObject().put("text", it.text).put("incoming", it.incoming) })) } })
}
internal fun decode(o: JSONObject): SocialState {
    val following = o.getJSONArray("following")
    val chats = o.getJSONObject("messages")
    return SocialState(
        posts = o.getJSONArray("posts").objects().map { p -> Post(p.getLong("id"), p.getInt("author"), p.getString("image"), p.getString("caption"), p.getString("location"), p.getString("category"), p.getInt("likes"), p.getBoolean("liked"), p.getBoolean("saved"), p.getJSONArray("comments").objects().map { Comment(it.getString("author"), it.getString("body")) }) },
        following = (0 until following.length()).map { following.getInt(it) }.toSet(),
        messages = chats.keys().asSequence().associate { key -> key.toInt() to chats.getJSONArray(key).objects().map { ChatMessage(it.getString("text"), it.getBoolean("incoming")) } },
        name = o.getString("name"), bio = o.getString("bio"), theme = o.optString("theme", "System"), reduceMotion = o.optBoolean("motion"), onboarded = o.optBoolean("onboarded")
    )
}
