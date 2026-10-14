package com.roboko.app

import java.math.BigInteger
import org.json.JSONObject

internal data class UpdateCheckResult(
    val version: String? = null,
    val notes: String = "",
    val downloadUrl: String? = null,
    val status: String? = null
)

/** GitHub's latest release is not necessarily newer than the installed application. */
internal object AppUpdates {
    private val versionPattern = Regex("^[vV]?(\\d+)\\.(\\d+)\\.(\\d+)(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$")
    private data class Version(val numbers: List<BigInteger>, val prerelease: String)

    private fun parse(value: String): Version {
        val match = versionPattern.matchEntire(value.trim())
            ?: throw IllegalArgumentException("无法识别版本号：$value")
        return Version((1..3).map { match.groupValues[it].toBigInteger() }, match.groupValues[4])
    }

    fun isNewer(remote: String, installed: String): Boolean {
        val next = parse(remote)
        val current = parse(installed)
        next.numbers.zip(current.numbers).forEach { (a, b) ->
            if (a != b) return a > b
        }
        if (next.prerelease == current.prerelease) return false
        if (next.prerelease.isEmpty()) return true
        if (current.prerelease.isEmpty()) return false
        val a = next.prerelease.split('.')
        val b = current.prerelease.split('.')
        for (index in 0 until minOf(a.size, b.size)) {
            if (a[index] == b[index]) continue
            val aNumber = a[index].toBigIntegerOrNull()
            val bNumber = b[index].toBigIntegerOrNull()
            val order = when {
                aNumber != null && bNumber != null -> aNumber.compareTo(bNumber)
                aNumber != null -> -1
                bNumber != null -> 1
                else -> a[index].compareTo(b[index])
            }
            if (order != 0) return order > 0
        }
        return a.size > b.size
    }

    fun evaluate(payload: String, installed: String): UpdateCheckResult {
        val release = JSONObject(payload)
        val tag = release.optString("tag_name").trim()
        val remote = parse(tag)
        if (release.optBoolean("draft") || release.optBoolean("prerelease") || remote.prerelease.isNotEmpty()) {
            return UpdateCheckResult(status = "暂无可用的正式版更新")
        }
        if (!isNewer(tag, installed)) return UpdateCheckResult(status = "当前已是最新版本")
        val assets = release.optJSONArray("assets")
        val asset = assets?.let { list ->
            (0 until list.length()).map { list.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
        }
        return UpdateCheckResult(
            version = tag.removePrefix("v").removePrefix("V"),
            notes = release.optString("body").take(600),
            downloadUrl = asset?.optString("browser_download_url")?.takeIf { it.isNotBlank() }
        )
    }
}
