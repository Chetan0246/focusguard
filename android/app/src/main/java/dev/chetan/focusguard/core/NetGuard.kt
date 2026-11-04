package dev.chetan.focusguard.core

/**
 * Privacy guard for the Muse Glimmer tier: raw session details may only go to a server on the
 * student's own machine / local network. Public hosts are refused unless the user explicitly
 * overrides it in Settings.
 */
object NetGuard {
    fun isPrivateHost(host: String): Boolean {
        val h = host.trim().lowercase().removePrefix("[").removeSuffix("]")
        if (h == "localhost" || h.endsWith(".local") || h.endsWith(".lan") || h.endsWith(".home.arpa")) return true
        if (h == "::1" || h.startsWith("fe80:") || h.startsWith("fd") || h.startsWith("fc")) return h.contains(':')
        val parts = h.split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size != 4 || parts.any { it !in 0..255 }) return false
        val (a, b) = parts[0] to parts[1]
        return a == 10 || a == 127 || (a == 192 && b == 168) || (a == 172 && b in 16..31) ||
            (a == 100 && b in 64..127) // CGNAT range used by Tailscale
    }

    fun hostOf(url: String): String? =
        Regex("""^https?://(\[[^\]]+]|[^/:?#]+)""", RegexOption.IGNORE_CASE).find(url.trim())?.groupValues?.get(1)
}
