package com.nikhil.pullr

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Fallback for pages the engine can't read: finds videos, audio and images straight in the page
 * (meta tags, <video>/<audio>/<img>, direct file links, HLS playlists) and also accepts links that
 * point straight at a media file.
 */
object WebScraper {
    private const val MAX_VIDEOS = 10
    private const val MAX_AUDIO = 10
    private const val MAX_IMAGES = 30

    private val VIDEO_EXT = setOf("mp4", "m4v", "webm", "mov", "mkv")
    private val AUDIO_EXT = setOf("mp3", "m4a", "aac", "ogg", "opus", "wav", "flac")
    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "gif")

    private val FILE_URL = Regex(
        """https?:(?:\\?/){2}[^\s"'<>\\]+?(?:\\?/[^\s"'<>\\]+?)*\.(?:mp4|m4v|webm|mov|mkv|m3u8|mp3|m4a|aac|ogg|opus|wav|flac|jpe?g|png|webp|gif)(?:\?[^\s"'<>\\]*)?""",
        RegexOption.IGNORE_CASE,
    )
    private val META = Regex("""<meta[^>]+>""", RegexOption.IGNORE_CASE)
    private val ATTR = Regex("""([a-zA-Z:-]+)\s*=\s*["']([^"']*)["']""")
    private val TAG_SRC = Regex(
        """<(video|audio|source|img)\b[^>]*>""",
        RegexOption.IGNORE_CASE,
    )
    private val TITLE = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val JUNK = listOf("favicon", "sprite", "logo", "icon", "avatar", "blank", "pixel", "spacer", "badge", "emoji", "/ads/", "banner")

    fun ext(url: String): String =
        url.substringBefore('#').substringBefore('?').substringAfterLast('/', "").substringAfterLast('.', "").lowercase()

    fun isDirectMedia(url: String): Boolean = ext(url).let { it in VIDEO_EXT || it in AUDIO_EXT || it in IMAGE_EXT }

    /** A link that is itself a media file. */
    fun direct(url: String): Post? {
        val e = ext(url)
        val host = hostOf(url)
        val name = url.substringBefore('?').substringAfterLast('/').substringBeforeLast('.').ifEmpty { "file" }
        val kind = when (e) {
            in VIDEO_EXT -> MediaKind.VIDEO
            in AUDIO_EXT -> MediaKind.AUDIO
            in IMAGE_EXT -> if (e == "gif") MediaKind.GIF else MediaKind.PHOTO
            else -> return null
        }
        val thumb = if (kind == MediaKind.PHOTO || kind == MediaKind.GIF) url else null
        return Post(
            id = Integer.toHexString(url.hashCode()), authorName = "", authorHandle = "",
            text = name, media = listOf(MediaItem(kind, thumb, listOf(Variant(url, "Original", 0)))),
            source = host, pageUrl = url,
        )
    }

    class Scraped(val post: Post, val videoPage: Boolean)

    fun scrape(pageUrl: String): Scraped? {
        val html = fetchHtml(pageUrl) ?: return null
        val base = try { URL(pageUrl) } catch (e: Exception) { return null }

        val videos = LinkedHashSet<String>()
        val hls = LinkedHashSet<String>()
        val audios = LinkedHashSet<String>()
        val images = LinkedHashSet<String>()
        var ogImage: String? = null

        fun add(raw: String?, hint: String? = null) {
            val ref = raw?.trim()?.replace("\\/", "/")?.replace("&amp;", "&") ?: return
            if (ref.isEmpty() || ref.startsWith("data:") || ref.startsWith("blob:")) return
            val abs = try { URL(base, ref).toString() } catch (e: Exception) { return }
            val e = ext(abs)
            when {
                e == "m3u8" -> hls.add(abs)
                e in VIDEO_EXT -> videos.add(abs)
                e in AUDIO_EXT -> audios.add(abs)
                e in IMAGE_EXT -> if (JUNK.none { abs.lowercase().contains(it) }) images.add(abs)
                hint == "video" -> videos.add(abs)
                hint == "audio" -> audios.add(abs)
            }
        }

        for (m in META.findAll(html)) {
            val attrs = ATTR.findAll(m.value).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
            val key = (attrs["property"] ?: attrs["name"] ?: "").lowercase()
            val content = attrs["content"]
            when (key) {
                "og:video", "og:video:url", "og:video:secure_url", "twitter:player:stream" -> add(content, "video")
                "og:audio", "og:audio:url", "og:audio:secure_url" -> add(content, "audio")
                "og:image", "og:image:url", "og:image:secure_url", "twitter:image" -> {
                    add(content)
                    if (ogImage == null && content != null) ogImage = runCatching { URL(base, content).toString() }.getOrNull()
                }
            }
        }
        for (t in TAG_SRC.findAll(html)) {
            val tag = t.groupValues[1].lowercase()
            val attrs = ATTR.findAll(t.value).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
            val type = attrs["type"].orEmpty().lowercase()
            val hint = when {
                tag == "video" -> "video"
                tag == "audio" -> "audio"
                type.startsWith("video") -> "video"
                type.startsWith("audio") -> "audio"
                else -> null
            }
            if (tag == "img") {
                add(bigSrcset(attrs["srcset"] ?: attrs["data-srcset"]))
                for (k in listOf("data-src", "data-original", "data-lazy-src", "src")) add(attrs[k])
            } else {
                add(attrs["src"], hint)
                add(attrs["data-src"], hint)
                add(attrs["poster"])
            }
        }
        for (m in FILE_URL.findAll(html)) add(m.value)

        val title = TITLE.find(html)?.groupValues?.get(1)?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
            .replace("&amp;", "&").replace("&#39;", "'").replace("&quot;", "\"").take(100)
        val media = mutableListOf<MediaItem>()
        videos.take(MAX_VIDEOS).forEach { media.add(MediaItem(MediaKind.VIDEO, ogImage, listOf(Variant(it, "Original", 0)))) }
        hls.take(MAX_VIDEOS).forEach {
            media.add(MediaItem(MediaKind.VIDEO, ogImage, listOf(Variant(it, "Stream", 0, 0L, selector = "bv*+ba/b"))))
        }
        audios.take(MAX_AUDIO).forEach { media.add(MediaItem(MediaKind.AUDIO, null, listOf(Variant(it, "Original", 0)))) }
        images.take(MAX_IMAGES).forEach { media.add(MediaItem(MediaKind.PHOTO, it, listOf(Variant(it, "Original", 0)))) }
        if (media.isEmpty()) return null

        val host = hostOf(pageUrl)
        val low = html.lowercase()
        val videoPage = "<video" in low || "og:video" in low || "content=\"video" in low || "m3u8" in low ||
            "video-player" in low || "videoplayer" in low
        val post = Post(
            id = Integer.toHexString(pageUrl.hashCode()), authorName = "", authorHandle = "",
            text = title.ifEmpty { host }, media = media, source = host, pageUrl = pageUrl,
        )
        return Scraped(post, videoPage)
    }

    private fun bigSrcset(srcset: String?): String? =
        srcset?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.lastOrNull()?.substringBefore(' ')

    private fun hostOf(url: String): String =
        try { URI(url).host?.removePrefix("www.") ?: "Web" } catch (e: Exception) { "Web" }

    private fun fetchHtml(url: String): String? = try {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", MediaFetcher.USER_AGENT)
            setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*")
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        }
        try {
            if (conn.responseCode !in 200..299) null
            else conn.inputStream.use { s ->
                val buf = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(16 * 1024)
                while (buf.size() < 3_000_000) {
                    val n = s.read(chunk)
                    if (n < 0) break
                    buf.write(chunk, 0, n)
                }
                buf.toString("UTF-8")
            }
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        null
    }
}
