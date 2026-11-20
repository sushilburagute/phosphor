package dev.sush.phosphor.platform

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal const val RELEASES_URL = "https://github.com/sushilburagute/phosphor/releases"
private val stableVersion = Regex("""(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)""")

internal fun isNewerRelease(candidate: String, installed: String): Boolean {
    fun parts(version: String) = requireNotNull(stableVersion.matchEntire(version))
        .groupValues.drop(1).map { it.toBigInteger() }
    val comparisons = parts(candidate).zip(parts(installed.substringBefore('+'))) { a, b -> a.compareTo(b) }
    return (comparisons.firstOrNull { it != 0 } ?: 0) > 0
}

internal suspend fun latestReleaseVersion(): String? = withContext(Dispatchers.IO) {
    val url = URL("https://api.github.com/repos/sushilburagute/phosphor/releases/latest")
    val connection = url.openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "Phosphor-Android")
        when (val status = connection.responseCode) {
            HttpURLConnection.HTTP_NOT_FOUND -> null
            HttpURLConnection.HTTP_OK -> {
                val json = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
                val tag = json.getString("tag_name")
                val version = tag.removePrefix("v")
                if (!tag.startsWith("v") || !stableVersion.matches(version)) throw IOException("Invalid release tag")
                version
            }
            else -> throw IOException("GitHub returned HTTP $status")
        }
    } finally {
        connection.disconnect()
    }
}
