package com.example.brainreels.tracking

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.example.brainreels.data.SourceApp
import java.security.MessageDigest

/**
 * Tunable heuristics. IMPORTANT: these tokens are best-effort guesses about the apps' UI and
 * can change with every Instagram/Facebook release. They have NOT been validated on real devices.
 * Update them using docs/DEVICE_TEST_CHECKLIST.md.
 */
object SignalRules {
    @Volatile var positiveIdTokens = listOf("clips_viewer", "clips_video", "clips_ufi", "clips_author", "clips_", "reels_viewer", "reel_player")
    /** NOTE: Instagram uses "reel_viewer" internally for Stories, so it is a negative signal. */
    @Volatile var storyIdTokens = listOf("reel_viewer", "story_viewer", "stories_viewer", "story_ring")
    @Volatile var commentIdTokens = listOf("comment_composer", "comment_thread", "comments_sheet")
    /** Screens that may contain private content. If seen, extraction stops immediately. */
    @Volatile var privateScreenIdTokens = listOf("direct_", "message_list", "thread_view", "inbox", "composer")
    @Volatile var labelWords = setOf("reel", "reels", "clips")
    @Volatile var adWords = setOf("sponsored")
    @Volatile var identityIdTokens = listOf("username", "author", "audio_title", "music_title")
}

/**
 * Reads a bounded slice of the active window and reduces it to an [Observation].
 * Privacy: password/editable fields are skipped, text is only compared against word lists or
 * hashed, and nothing textual leaves this function.
 */
class SignalExtractor(private val screenWidth: Int, private val screenHeight: Int) {

    companion object {
        private const val MAX_NODES = 300
        private const val MAX_DEPTH = 28
    }

    @Suppress("DEPRECATION")
    fun extract(
        root: AccessibilityNodeInfo, source: SourceApp, nowMs: Long, scrolled: Boolean, pagerIndex: Int?
    ): Observation {
        var idHint = false
        var labelHint = false
        var pager = false
        var railCount = 0
        var negative: NegativeContext? = null
        var privateScreen = false
        val identity = StringBuilder()
        val rect = Rect()

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        val depths = ArrayDeque<Int>()
        queue.addLast(root); depths.addLast(0)
        var visited = 0

        while (queue.isNotEmpty() && visited < MAX_NODES && !privateScreen) {
            val node = queue.removeFirst()
            val depth = depths.removeFirst()
            visited++

            if (!node.isPassword && !node.isEditable) {
                val id = node.viewIdResourceName?.lowercase()
                if (id != null) {
                    if (SignalRules.privateScreenIdTokens.any { id.contains(it) }) privateScreen = true
                    if (SignalRules.storyIdTokens.any { id.contains(it) }) negative = NegativeContext.STORY
                    else if (SignalRules.commentIdTokens.any { id.contains(it) } && negative == null) negative = NegativeContext.COMMENTS
                    if (SignalRules.positiveIdTokens.any { id.contains(it) }) idHint = true
                    if (SignalRules.identityIdTokens.any { id.contains(it) }) {
                        val t = node.text?.toString()
                        if (t != null && t.length <= 120) identity.append(t).append('|')
                    }
                }
                for (raw in arrayOf(node.contentDescription, node.text)) {
                    val s = raw?.toString() ?: continue
                    if (s.length > 40) continue
                    val words = s.lowercase().split(Regex("[^a-z]+")).filter { it.isNotEmpty() }
                    if (words.any { it in SignalRules.labelWords }) labelHint = true
                    if (words.any { it in SignalRules.adWords } && negative == null) negative = NegativeContext.AD
                }
                if (node.isScrollable || node.isClickable) {
                    node.getBoundsInScreen(rect)
                    if (node.isScrollable && rect.height() >= screenHeight * 0.8 && rect.width() >= screenWidth * 0.8) pager = true
                    if (node.isClickable && rect.centerX() >= screenWidth * 0.7) railCount++
                }
            }

            if (depth < MAX_DEPTH && !privateScreen) {
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    queue.addLast(child); depths.addLast(depth + 1)
                }
            }
            if (node !== root) node.recycle()
        }
        for (n in queue) if (n !== root) n.recycle()

        if (privateScreen) return Observation(source, nowMs, ViewerSignals(), null, scrolled, pagerIndex, null)

        val signals = ViewerSignals(idHint, labelHint, pager, railCount >= 3)
        val fp = if (identity.isEmpty()) null else sha(identity.toString())
        return Observation(source, nowMs, signals, negative, scrolled, pagerIndex, fp)
    }

    private fun sha(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).take(6).joinToString("") { "%02x".format(it) }
}
