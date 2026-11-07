package dev.offscript.social
import org.junit.Assert.*
import org.junit.Test
class SocialActionsTest {
    private val s = SocialState()
    @Test fun seedHasUniqueIds() { assertEquals(s.posts.size, s.posts.map { it.id }.toSet().size) }
    @Test fun likesIncrement() { assertEquals(249,SocialActions.like(s,1).posts.first().likes) }
    @Test fun unlikeRestoresCount() { assertEquals(s,SocialActions.like(SocialActions.like(s,1),1)) }
    @Test fun bookmarkToggles() { assertEquals(s,SocialActions.save(SocialActions.save(s,1),1)) }
    @Test fun savedCollectionFilters() { assertEquals(listOf(1L),SocialActions.filter(SocialActions.save(s,1),saved=true).map{it.id}) }
    @Test fun followToggles() { assertEquals(s,SocialActions.follow(SocialActions.follow(s,2),2)) }
    @Test fun cannotFollowSelf() { assertEquals(s,SocialActions.follow(s,-1)) }
    @Test fun cannotFollowMissingUser() { assertEquals(s,SocialActions.follow(s,99)) }
    @Test fun followingFilter() { assertTrue(SocialActions.filter(s,following=true).all{it.author in s.following}) }
    @Test fun categoryFilter() { assertTrue(SocialActions.filter(s,category="Travel").all{it.category=="Travel"}) }
    @Test fun searchIgnoresCase() { assertEquals(1,SocialActions.filter(s,query="POSITANO").size) }
    @Test fun searchMatchesPerson() { assertEquals(2,SocialActions.filter(s,query="sofia").size) }
    @Test fun emptySearchReturnsAll() { assertEquals(s.posts,SocialActions.filter(s,query="  ")) }
    @Test fun noSearchMatches() { assertTrue(SocialActions.filter(s,query="xyzzy123").isEmpty()) }
    @Test fun commentsTrim() { assertEquals(Comment("Alex Morgan","Hello"),SocialActions.comment(s,1," Hello ").posts.first().comments.last()) }
    @Test fun blankCommentIgnored() { assertEquals(s,SocialActions.comment(s,1," \n")) }
    @Test fun missingCommentTargetIgnored() { assertEquals(s,SocialActions.comment(s,999,"Hello")) }
    @Test fun commentsBounded() { assertEquals(500,SocialActions.comment(s,1,"x".repeat(900)).posts.first().comments.last().body.length) }
    @Test fun publishPrepends() { assertEquals(99L,SocialActions.publish(s,99,"Hello","coast","","Travel").posts.first().id) }
    @Test fun publishingUsesCurrentUser() { assertEquals(-1,SocialActions.publish(s,99,"Hello","","","Everyday").posts.first().author) }
    @Test fun blankPostIgnored() { assertEquals(s,SocialActions.publish(s,99," ","","","Everyday")) }
    @Test fun duplicatePostIdIgnored() { assertEquals(s,SocialActions.publish(s,1,"Hello","","","Everyday")) }
    @Test fun captionBounded() { assertEquals(600,SocialActions.publish(s,99,"x".repeat(900),"","","Everyday").posts.first().caption.length) }
    @Test fun locationFallback() { assertEquals("A moment from my day",SocialActions.publish(s,99,"Hello","","  ","Everyday").posts.first().location) }
    @Test fun cannotDeleteOtherUsersPost() { assertEquals(s,SocialActions.delete(s,1)) }
    @Test fun deleteOwnPost() { assertEquals(s,SocialActions.delete(SocialActions.publish(s,99,"Hello","","","Everyday"),99)) }
    @Test fun messagesPersistInState() { assertEquals("Hello",SocialActions.send(s,0," Hello ").messages[0]!!.last().text) }
    @Test fun blankMessageIgnored() { assertEquals(s,SocialActions.send(s,0,"  ")) }
    @Test fun unknownRecipientIgnored() { assertEquals(s,SocialActions.send(s,88,"Hi")) }
    @Test fun messageBounded() { assertEquals(2000,SocialActions.send(s,0,"x".repeat(3000)).messages[0]!!.first().text.length) }
    @Test fun messagesDoNotInventReplies() { assertEquals(1,SocialActions.send(s,0,"Hello").messages[0]!!.size) }
    @Test fun originalStateNotMutated() { SocialActions.like(s,1); assertFalse(s.posts.first().liked) }
    @Test fun authorFilter() { assertEquals(0,SocialActions.filter(s,author=-1).size) }
    @Test fun initialsFollowProfile() { assertEquals("AK",s.copy(name="Adarsh Kumar").person(-1).initials) }
}
