package dev.frame.app
import org.junit.Assert.*
import org.junit.Test
class AddressTest {
    @Test fun addsHttps(){assertEquals("https://youtube.com/",Address.normalize("youtube.com"))}
    @Test fun trims(){assertEquals("https://youtube.com/",Address.normalize(" youtube.com "))}
    @Test fun lowercaseHost(){assertEquals("https://youtube.com/",Address.normalize("HTTPS://YOUTUBE.COM"))}
    @Test fun retainsPath(){assertEquals("https://example.com/a?q=x#b",Address.normalize("example.com/a?q=x#b"))}
    @Test fun retainsPort(){assertEquals("https://example.com:8443/",Address.normalize("example.com:8443"))}
    @Test(expected=IllegalArgumentException::class) fun refusesHttp(){Address.normalize("http://example.com")}
    @Test(expected=IllegalArgumentException::class) fun refusesJavascript(){Address.normalize("javascript://example.com/x")}
    @Test(expected=IllegalArgumentException::class) fun refusesFiles(){Address.normalize("file:///etc/passwd")}
    @Test(expected=IllegalArgumentException::class) fun refusesCredentials(){Address.normalize("https://user:pass@example.com")}
    @Test(expected=IllegalArgumentException::class) fun refusesSpaces(){Address.normalize("example .com")}
    @Test(expected=IllegalArgumentException::class) fun refusesEmpty(){Address.normalize("")}
    @Test(expected=IllegalArgumentException::class) fun refusesNoDomain(){Address.normalize("youtube")}
}
