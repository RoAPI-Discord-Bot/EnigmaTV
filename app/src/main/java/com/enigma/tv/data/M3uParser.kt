package com.enigma.tv.data

import java.security.MessageDigest

object M3uParser {
    fun parse(text: String, defaultGroup: String = "General"): List<IptvChannel> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val channels = mutableListOf<IptvChannel>()
        var pendingName: String? = null
        var pendingGroup: String = defaultGroup
        var pendingLogo: String? = null

        for (line in lines) {
            when {
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    pendingGroup = extractAttr(line, "group-title") ?: defaultGroup
                    pendingLogo = extractAttr(line, "tvg-logo")
                    val comma = line.lastIndexOf(',')
                    pendingName = if (comma >= 0 && comma < line.length - 1) {
                        line.substring(comma + 1).trim()
                    } else {
                        extractAttr(line, "tvg-name") ?: "Channel"
                    }
                }
                !line.startsWith("#") && pendingName != null -> {
                    val url = line.trim()
                    if (url.startsWith("http")) {
                        val name = pendingName!!
                        val id = md5("$name|$url")
                        channels.add(
                            IptvChannel(
                                id = id,
                                name = name,
                                group = pendingGroup,
                                logoUrl = pendingLogo,
                                streamUrl = url
                            )
                        )
                    }
                    pendingName = null
                    pendingGroup = defaultGroup
                    pendingLogo = null
                }
            }
        }
        return channels.distinctBy { it.streamUrl }
    }

    private fun extractAttr(line: String, key: String): String? {
        val pattern = """$key="([^"]*)"""".toRegex(RegexOption.IGNORE_CASE)
        return pattern.find(line)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
