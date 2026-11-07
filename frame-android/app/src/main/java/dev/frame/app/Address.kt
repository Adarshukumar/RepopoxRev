package dev.frame.app
import java.net.URI
import java.net.IDN

object Address {
    fun normalize(input: String): String {
        val text=input.trim()
        require(text.isNotEmpty()) { "Enter a website address." }
        require(text.none { it.isWhitespace() || it.code < 32 }) { "A website address cannot contain spaces." }
        val uri=URI(if("://" in text)text else "https://$text")
        require(uri.scheme.equals("https",true)) { "Use an HTTPS address for your saved app." }
        require(uri.userInfo==null) { "Addresses with embedded passwords are not supported." }
        val host=uri.host ?: throw IllegalArgumentException("Enter a complete address, such as youtube.com.")
        require('.' in host && !host.startsWith('.') && !host.endsWith('.')) { "Enter a complete website domain." }
        require(uri.port == -1 || uri.port in 1..65535) { "That port is not valid." }
        val ascii=IDN.toASCII(host.lowercase())
        return URI("https",null,ascii,uri.port,uri.path.ifEmpty{"/"},uri.query,uri.fragment).toASCIIString()
    }
    fun host(url: String): String = try { URI(url).host ?: url } catch(_:Exception){url}
}
