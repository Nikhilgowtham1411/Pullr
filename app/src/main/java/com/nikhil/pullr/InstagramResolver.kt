package com.nikhil.pullr

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Instagram without logging in.
 *
 * yt-dlp cannot read Instagram photo posts or carousels, and often needs a login for reels. So for
 * Instagram links we first read the public "embed" page of the post (the same page websites use to
 * embed a post). It lists every photo and video of a carousel in full size. If that is closed we
 * fall back to the page's preview tags, and finally to the yt-dlp engine.
 *
 * Files found here are plain links, so our own fast downloader fetches them (no engine needed).
 */
object InstagramResolver {

    private val CODE_REGEX = Regex("""instagram\.com/(?:[A-Za-z0-9_.]+/)?(?:p|reel|reels|tv)/([A-Za-z0-9_-]+)""")

    private const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Safari/537.36"
    private const val CRAWLER_UA = "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)"

    fun isInstagram(url: String): Boolean = url.contains("instagram.com/", ignoreCase = true)

    fun load(url: String): Post {
        val code = CODE_REGEX.find(url)?.groupValues?.get(1)
        if (code == null) {
            // Profiles, stories, highlights: only the engine can try these.
            return EngineResolver.load(url)
        }

        val isReel = Regex("""/(reel|reels|tv)/""").containsMatchIn(url)
        fun hasVideo(p: Post) = p.media.any { it.kind == MediaKind.VIDEO }

        // 1. Embed page. A full read (JSON) is trusted; a video is always good.
        val embed = fromEmbed(code, url)
        if (embed != null && (hasVideo(embed.post) || (embed.trusted && !isReel))) return embed.post

        // 2. Preview tags: only worth it when they carry a real video file.
        val preview = fromPreviewTags(code, url)
        if (preview != null && hasVideo(preview)) return preview

        // 3. Anything that only gave a preview picture is not the real file for a reel/video:
        //    let the engine try (yt-dlp reads public reels and videos).
        var engineError: FetchException? = null
        try {
            val p = EngineResolver.load(url)
            if (p.media.isNotEmpty()) return p
        } catch (e: FetchException) {
            engineError = e
        }

        // 4. Picture-only fallback is fine for photo posts, never for reels.
        if (!isReel) (embed?.post ?: preview)?.let { return it }

        throw FetchException(
            "Instagram didn't give the video without a login. Try again in a minute, " +
                "or switch between Wi-Fi and mobile data (turn off VPN). Private accounts can't be saved." +
                (engineError?.message?.let { "\n($it)" } ?: "")
        )
    }

    // ---- 1. embed page -------------------------------------------------------------------

    private class Embed(val post: Post, val trusted: Boolean)

    private fun fromEmbed(code: String, pageUrl: String): Embed? {
        var fallback: Embed? = null
        for (path in listOf("p/$code/embed/captioned/", "p/$code/embed/")) {
            val html = get("https://www.instagram.com/$path", DESKTOP_UA) ?: continue
            val media = findMediaJson(html)
            if (media != null) buildFromJson(media, code, pageUrl)?.let { return Embed(it, true) }
            if (fallback == null) buildFromRaw(html, code, pageUrl)?.let { fallback = Embed(it, false) }
        }
        return fallback
    }

    /** The embed page hides the post as JSON in a few different places depending on Instagram's mood. */
    private fun findMediaJson(html: String): JSONObject? {
        // a) "contextJSON":"{...escaped json...}"
        val marker = "\"contextJSON\":\""
        var from = 0
        while (true) {
            val at = html.indexOf(marker, from)
            if (at < 0) break
            val start = at + marker.length
            val literal = readJsonString(html, start)
            if (literal != null) {
                try {
                    val inner = JSONObject(JSONObject("{\"s\":\"$literal\"}").getString("s"))
                    findKey(inner, listOf("shortcode_media", "xdt_shortcode_media"))?.let { return it }
                } catch (e: Exception) {
                    // try the next one
                }
            }
            from = start
        }
        // b) window.__additionalDataLoaded('extra', {...})
        val loaded = html.indexOf("__additionalDataLoaded")
        if (loaded >= 0) {
            val brace = html.indexOf('{', loaded)
            if (brace >= 0) {
                balancedObject(html, brace)?.let { text ->
                    try {
                        findKey(JSONObject(text), listOf("shortcode_media", "xdt_shortcode_media"))?.let { return it }
                    } catch (e: Exception) {
                    }
                }
            }
        }
        // c) any object that starts at "shortcode_media":
        for (key in listOf("\"shortcode_media\":", "\"xdt_shortcode_media\":")) {
            val at = html.indexOf(key)
            if (at >= 0) {
                val brace = html.indexOf('{', at)
                if (brace >= 0) {
                    balancedObject(html, brace)?.let { text ->
                        try {
                            return JSONObject(text)
                        } catch (e: Exception) {
                        }
                    }
                }
            }
        }
        return null
    }

    private fun buildFromJson(m: JSONObject, code: String, pageUrl: String): Post? {
        val nodes = mutableListOf<JSONObject>()
        val children = m.optJSONObject("edge_sidecar_to_children")?.optJSONArray("edges")
        if (children != null && children.length() > 0) {
            for (i in 0 until children.length()) {
                children.optJSONObject(i)?.optJSONObject("node")?.let { nodes += it }
            }
        } else {
            val carousel = m.optJSONArray("carousel_media")
            if (carousel != null && carousel.length() > 0) {
                for (i in 0 until carousel.length()) carousel.optJSONObject(i)?.let { nodes += it }
            } else {
                nodes += m
            }
        }

        // A video whose file link is missing means the answer is incomplete: don't pass off its picture.
        if (nodes.any { isVideoNode(it) && videoUrlOf(it).isEmpty() }) return null
        val items = nodes.mapNotNull { nodeToItem(it) }
        if (items.isEmpty()) return null

        val owner = m.optJSONObject("owner") ?: m.optJSONObject("user")
        val caption = m.optJSONObject("edge_media_to_caption")?.optJSONArray("edges")
            ?.optJSONObject(0)?.optJSONObject("node")?.optString("text")
            ?: m.optJSONObject("caption")?.optString("text")
            ?: ""
        return Post(
            id = code,
            authorName = owner?.optString("full_name").orEmpty(),
            authorHandle = owner?.optString("username").orEmpty(),
            text = caption.trim(),
            media = items,
            source = "Instagram",
            pageUrl = pageUrl,
        )
    }

    private fun videoUrlOf(n: JSONObject): String = n.optString("video_url").ifEmpty {
        n.optJSONArray("video_versions")?.optJSONObject(0)?.optString("url").orEmpty()
    }

    private fun isVideoNode(n: JSONObject): Boolean =
        n.optBoolean("is_video") || n.optString("__typename").contains("Video", ignoreCase = true) ||
            n.optInt("media_type") == 2

    private fun nodeToItem(n: JSONObject): MediaItem? {
        val photo = bestImage(n)
        val videoUrl = videoUrlOf(n)
        if (videoUrl.isNotEmpty()) {
            return MediaItem(MediaKind.VIDEO, photo, listOf(Variant(videoUrl, "Original", 0)))
        }
        if (photo == null) return null
        return MediaItem(MediaKind.PHOTO, photo, listOf(Variant(photo, "Original", 0)))
    }

    private fun bestImage(n: JSONObject): String? {
        val res = n.optJSONArray("display_resources")
        if (res != null && res.length() > 0) {
            var best: String? = null
            var width = -1
            for (i in 0 until res.length()) {
                val r = res.optJSONObject(i) ?: continue
                val w = r.optInt("config_width")
                if (w > width && r.optString("src").isNotEmpty()) {
                    width = w
                    best = r.optString("src")
                }
            }
            if (best != null) return best
        }
        n.optString("display_url").ifEmpty { null }?.let { return it }
        return n.optJSONObject("image_versions2")?.optJSONArray("candidates")
            ?.optJSONObject(0)?.optString("url")?.ifEmpty { null }
    }

    /** Last resort on the embed page: pull the file links straight out of the text. */
    private fun buildFromRaw(html: String, code: String, pageUrl: String): Post? {
        val text = html
            .replace("\\/", "/")
            .replace("\\u0026", "&")
            .replace("\\\\u0026", "&")
            .replace("&amp;", "&")
        val videos = Regex(""""video_url":"(https?:[^"]+)"""").findAll(text).map { it.groupValues[1] }.toList().distinct()
        val photos = Regex(""""display_url":"(https?:[^"]+)"""").findAll(text).map { it.groupValues[1] }.toList().distinct()
        val items = mutableListOf<MediaItem>()
        val thumb = photos.firstOrNull() ?: Regex("""class="EmbeddedMediaImage"[^>]*src="([^"]+)"""")
            .find(text)?.groupValues?.get(1)
        videos.forEach { items += MediaItem(MediaKind.VIDEO, thumb, listOf(Variant(it, "Original", 0))) }
        if (videos.isEmpty()) {
            val imgs = if (photos.isNotEmpty()) photos else listOfNotNull(thumb)
            imgs.forEach { items += MediaItem(MediaKind.PHOTO, it, listOf(Variant(it, "Original", 0))) }
        }
        if (items.isEmpty()) return null
        return Post(code, "", "", "", items, "Instagram", pageUrl)
    }

    // ---- 2. preview tags -----------------------------------------------------------------

    private fun fromPreviewTags(code: String, pageUrl: String): Post? {
        val html = get("https://www.instagram.com/p/$code/", CRAWLER_UA) ?: return null
        fun meta(name: String): String? =
            Regex("""<meta[^>]+(?:property|name)="$name"[^>]+content="([^"]*)"""").find(html)?.groupValues?.get(1)
                ?.replace("&amp;", "&")?.replace("&quot;", "\"")?.replace("&#039;", "'")
        val video = meta("og:video:secure_url") ?: meta("og:video")
        val image = meta("og:image")
        val ok = { u: String? -> u != null && (u.contains("cdninstagram.com") || u.contains("fbcdn.net")) }
        val item = when {
            ok(video) -> MediaItem(MediaKind.VIDEO, if (ok(image)) image else null, listOf(Variant(video!!, "Original", 0)))
            ok(image) -> MediaItem(MediaKind.PHOTO, image, listOf(Variant(image!!, "Original", 0)))
            else -> return null
        }
        val title = meta("og:title").orEmpty()
        return Post(code, "", "", title, listOf(item), "Instagram", pageUrl)
    }

    // ---- helpers -------------------------------------------------------------------------

    private fun get(url: String, ua: String): String? {
        return try {
            val c = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 15_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", ua)
                setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            }
            try {
                if (c.responseCode !in 200..299) null else c.inputStream.bufferedReader().use { it.readText() }
            } finally {
                c.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Reads a JSON string body starting right after its opening quote; returns the still-escaped text. */
    private fun readJsonString(s: String, start: Int): String? {
        var i = start
        while (i < s.length) {
            when (s[i]) {
                '\\' -> i += 2
                '"' -> return s.substring(start, i)
                else -> i++
            }
        }
        return null
    }

    /** The {...} object that begins at [start], found by matching braces (strings respected). */
    private fun balancedObject(s: String, start: Int): String? {
        var depth = 0
        var inString = false
        var i = start
        while (i < s.length) {
            val ch = s[i]
            if (inString) {
                if (ch == '\\') i++ else if (ch == '"') inString = false
            } else when (ch) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return s.substring(start, i + 1)
                }
            }
            i++
        }
        return null
    }

    private fun findKey(node: Any?, keys: List<String>): JSONObject? {
        when (node) {
            is JSONObject -> {
                for (k in keys) node.optJSONObject(k)?.let { return it }
                val keysIt = node.keys()
                while (keysIt.hasNext()) findKey(node.opt(keysIt.next()), keys)?.let { return it }
            }
            is JSONArray -> for (i in 0 until node.length()) findKey(node.opt(i), keys)?.let { return it }
        }
        return null
    }
}
