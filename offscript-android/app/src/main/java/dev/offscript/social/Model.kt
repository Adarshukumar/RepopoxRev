package dev.offscript.social

/** Pure Kotlin domain. All counts are derived, never separately mutated. */
data class Person(val id: Int, val name: String, val handle: String, val initials: String, val bio: String, val color: Long)
data class Comment(val author: String, val body: String)
data class Post(val id: Long, val author: Int, val image: String, val caption: String, val location: String, val category: String, val baseLikes: Int = 0, val liked: Boolean = false, val saved: Boolean = false, val comments: List<Comment> = emptyList()) {
    val likes: Int get() = baseLikes + if (liked) 1 else 0
}
data class ChatMessage(val text: String, val incoming: Boolean = false)
data class SocialState(
    val posts: List<Post> = seedPosts(), val following: Set<Int> = setOf(0, 1),
    val messages: Map<Int, List<ChatMessage>> = emptyMap(),
    val name: String = "Alex Morgan", val bio: String = "Finding the extraordinary in the everyday.",
    val theme: String = "System", val reduceMotion: Boolean = false, val onboarded: Boolean = false
)
val PEOPLE = listOf(
    Person(0, "Sofia Chen", "sofiawanders", "SC", "Taking the scenic route. Photographer, wanderer, collector of little moments.", 0xFFE6C0A6),
    Person(1, "Noah Williams", "noah.in.frames", "NW", "Outside is my favorite place. Mountains, film cameras, and very early mornings.", 0xFFB4C7BA),
    Person(2, "Amara Okafor", "amarastudio", "AO", "A designer with a soft spot for good coffee and quiet corners.", 0xFFD8BBD7),
    Person(3, "Leo Martin", "leomakes", "LM", "Making things. Noticing things. Sharing a few of them here.", 0xFFB7C5D5)
)
fun SocialState.person(id: Int): Person = PEOPLE.getOrNull(id) ?: Person(-1, name, "you", name.split(" ").filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1) }.uppercase(), bio, 0xFFD9D6BD)
fun seedPosts() = listOf(
    Post(1, 0, "coast", "Took the long way home. Would take it again.", "Positano, Italy", "Travel", 248, comments = listOf(Comment("Noah", "The colors are unreal."), Comment("Amara", "Adding this to my list!"))),
    Post(2, 1, "lake", "No signal. No plans. Just a really good view.", "Dolomites, Italy", "Outdoors", 186, comments = listOf(Comment("Sofia", "This is my kind of weekend."))),
    Post(3, 2, "cafe", "Collecting quiet corners and very good coffee.", "A favorite little café", "Everyday", 92),
    Post(4, 3, "mountains", "A reminder to look up a little more often.", "Somewhere in the Alps", "Photography", 317),
    Post(5, 0, "italy", "The kind of afternoon you wish you could keep in your pocket.", "A slower kind of summer", "Travel", 142),
    Post(6, 2, "coffee", "Same order. New ideas. A small ritual worth keeping.", "The everyday", "Everyday", 78)
)
object SocialActions {
    fun like(s: SocialState, id: Long) = s.copy(posts = s.posts.map { if (it.id == id) it.copy(liked = !it.liked) else it })
    fun save(s: SocialState, id: Long) = s.copy(posts = s.posts.map { if (it.id == id) it.copy(saved = !it.saved) else it })
    fun follow(s: SocialState, id: Int): SocialState = if (id !in PEOPLE.indices) s else s.copy(following = if (id in s.following) s.following - id else s.following + id)
    fun comment(s: SocialState, id: Long, text: String): SocialState = if (text.isBlank()) s else s.copy(posts = s.posts.map { if (it.id == id) it.copy(comments = it.comments + Comment(s.name, text.trim().take(500))) else it })
    fun publish(s: SocialState, id: Long, caption: String, image: String, location: String, category: String): SocialState =
        if (caption.isBlank() || s.posts.any { it.id == id }) s else s.copy(posts = listOf(Post(id, -1, image, caption.trim().take(600), location.trim().take(80).ifEmpty { "A moment from my day" }, category)) + s.posts)
    fun delete(s: SocialState, id: Long) = s.copy(posts = s.posts.filterNot { it.id == id && it.author == -1 })
    fun send(s: SocialState, id: Int, text: String): SocialState = if (text.isBlank() || id !in PEOPLE.indices) s else s.copy(messages = s.messages + (id to ((s.messages[id] ?: emptyList()) + ChatMessage(text.trim().take(2000)))))
    fun filter(s: SocialState, query: String = "", category: String = "All", following: Boolean = false, saved: Boolean = false, author: Int? = null): List<Post> = s.posts.filter {
        (!saved || it.saved) && (!following || it.author in s.following) && (author == null || it.author == author) &&
        (category == "All" || it.category == category) && (query.isBlank() || "${it.caption} ${it.location} ${it.category} ${s.person(it.author).name}".contains(query.trim(), ignoreCase = true))
    }
}
