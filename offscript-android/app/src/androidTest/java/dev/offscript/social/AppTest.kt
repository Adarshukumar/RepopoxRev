package dev.offscript.social
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun enter() {
        rule.waitForIdle()
        if(rule.onAllNodesWithText("Skip").fetchSemanticsNodes().isNotEmpty()) { screenshot("00-welcome"); rule.onNodeWithText("Skip").performClick() }
        rule.waitForIdle()
    }
    @Test fun launchNavigateAndCompose() {
        enter()
        rule.onNodeWithText("Good things\nhappen here.").assertExists()
        screenshot("01-home")
        rule.onNodeWithContentDescription("Navigate Explore").performClick()
        rule.onNodeWithText("Follow your\ncuriosity.").assertExists()
        screenshot("02-explore")
        rule.onNodeWithContentDescription("Navigate Saved").performClick()
        rule.onNodeWithText("Keep what\nmoves you.").assertExists()
        rule.onNodeWithContentDescription("Create a post").performClick()
        rule.onNodeWithText("Life, unfiltered.").assertExists()
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("A native Android moment")
        rule.onNodeWithText("Share moment").performScrollTo().performClick()
        rule.onNodeWithText("Good things\nhappen here.").assertExists()
        rule.onNodeWithContentDescription("Navigate Profile").performClick()
        rule.onAllNodesWithText("Alex Morgan")[0].assertExists()
        rule.onNodeWithText("A native Android moment").performScrollTo().assertIsDisplayed()
        screenshot("03-profile")
    }
    @Test fun settingsAndPersistenceCodec() {
        enter()
        rule.onNodeWithContentDescription("Navigate Profile").performClick()
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Dark").performClick()
        rule.onNodeWithText("Your own little world.").assertExists()
        screenshot("04-dark-settings")
        val s=SocialActions.send(SocialActions.comment(SocialActions.like(SocialState(),1),1,"Hello"),0,"Hi").copy(name="Adarsh",theme="Dark",onboarded=true)
        assertEquals(s,decode(encode(s)))
    }
    @Test fun feedSaveLikeAndComment() {
        enter()
        rule.onNodeWithContentDescription("Like post 1").performScrollTo().performClick()
        rule.onNodeWithText("249").assertExists()
        screenshot("05-photo-feed")
        rule.onNodeWithContentDescription("Save post 1").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Navigate Saved").performClick()
        rule.onNodeWithText("Took the long way home. Would take it again.").performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("Comments on post 1").performScrollTo().performClick()
        rule.onNodeWithText("Good things, shared.").assertExists()
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("Beautiful little moment")
        rule.onNodeWithContentDescription("Send comment").performClick()
        rule.onNodeWithText("Beautiful little moment").assertExists()
        screenshot("06-comments")
    }
    @Test fun localMessageAndProfile() {
        enter()
        rule.onNodeWithContentDescription("Messages").performClick()
        rule.onNodeWithText("Sofia Chen").performClick()
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("Hello from my local demo")
        rule.onNodeWithContentDescription("Send message").performClick()
        rule.onNodeWithText("Hello from my local demo").assertExists()
        rule.onNodeWithText("Saved locally").assertExists()
        screenshot("07-conversation")
    }
    private fun screenshot(name: String) {
        rule.waitForIdle()
        val inst=InstrumentationRegistry.getInstrumentation()
        val bmp=inst.uiAutomation.takeScreenshot()
        val file=File(inst.targetContext.getExternalFilesDir(null),"$name.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
}
