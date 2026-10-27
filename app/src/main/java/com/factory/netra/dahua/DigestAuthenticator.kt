package com.factory.netra.dahua

import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * HTTP Digest (RFC 2617, MD5 / qop=auth) authenticator for the Dahua CGI API.
 * Falls back to Basic when the device asks for it. Gives up after one failed
 * attempt so a wrong password cannot lock the Dahua account.
 */
class DigestAuthenticator(private val user: String, private val pass: String) : Authenticator {
    private val random = SecureRandom()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.priorResponse != null) return null // already tried once: wrong credentials
        val challenges = response.headers("WWW-Authenticate")
        val digest = challenges.firstOrNull { it.trim().startsWith("Digest", ignoreCase = true) }
        if (digest == null) {
            if (challenges.any { it.trim().startsWith("Basic", ignoreCase = true) }) {
                return response.request.newBuilder()
                    .header("Authorization", Credentials.basic(user, pass)).build()
            }
            return null
        }
        val p = parse(digest.trim().substring(6))
        val realm = p["realm"] ?: return null
        val nonce = p["nonce"] ?: return null
        val opaque = p["opaque"]
        val algorithm = p["algorithm"]
        val qop = p["qop"]?.split(',')?.map { it.trim() }?.firstOrNull { it.equals("auth", true) }

        val url = response.request.url
        val uri = url.encodedPath + (url.encodedQuery?.let { "?$it" } ?: "")
        val method = response.request.method
        val cnonce = ByteArray(8).also { random.nextBytes(it) }.toHex()
        val nc = "00000001"

        var ha1 = md5("$user:$realm:$pass")
        if (algorithm.equals("MD5-sess", ignoreCase = true)) ha1 = md5("$ha1:$nonce:$cnonce")
        val ha2 = md5("$method:$uri")
        val resp = if (qop != null) md5("$ha1:$nonce:$nc:$cnonce:$qop:$ha2") else md5("$ha1:$nonce:$ha2")

        val header = buildString {
            append("Digest username=\"").append(user).append("\", realm=\"").append(realm)
            append("\", nonce=\"").append(nonce).append("\", uri=\"").append(uri)
            append("\", response=\"").append(resp).append('"')
            if (algorithm != null) append(", algorithm=").append(algorithm)
            if (opaque != null) append(", opaque=\"").append(opaque).append('"')
            if (qop != null) append(", qop=").append(qop).append(", nc=").append(nc)
                .append(", cnonce=\"").append(cnonce).append('"')
        }
        return response.request.newBuilder().header("Authorization", header).build()
    }

    private fun parse(s: String): Map<String, String> {
        val out = HashMap<String, String>()
        Regex("""(\w+)\s*=\s*(?:"([^"]*)"|([^,\s]*))""").findAll(s).forEach {
            out[it.groupValues[1].lowercase()] = it.groupValues[2].ifEmpty { it.groupValues[3] }
        }
        return out
    }

    private fun md5(s: String) =
        MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8)).toHex()

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
