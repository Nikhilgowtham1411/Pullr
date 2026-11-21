package com.nikhil.pullr

import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/**
 * Reads a link with yt-dlp and turns the answer into a [Post]: one [MediaItem] per video/photo in
 * the post, each with the qualities that really exist and roughly how much data each will use.
 */
object EngineResolver {
    private const val MAX_ENTRIES = 10
    private const val MAX_QUALITIES = 7
    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp")

    fun load(url: String): Post {
        Engine.ensureReady()
        fun run(): String {
            val request = YoutubeDLRequest(url)
                .addOption("--dump-single-json")
                .addOption("--no-warnings")
                .addOption("--no-playlist")
                .addOption("--playlist-end", MAX_ENTRIES)
                .addOption("--socket-timeout", 20)
            return YoutubeDL.getInstance().execute(request, null, null).out
        }

        val out = try {
            try {
                run()
            } catch (e: YoutubeDLException) {
                // A stale yt-dlp is the usual reason a working site suddenly fails: update once and retry.
                if (!Engine.updateForRetry()) throw e
                run()
            }
        } catch (e: YoutubeDLException) {
            throw FetchException(Engine.friendlyError(e.message))
        } catch (e: YoutubeDL.CanceledException) {
            throw FetchException("Cancelled.")
        }

        val json = out.lineSequence().firstOrNull { it.trimStart().startsWith("{") }
            ?: throw FetchException("The site didn't return anything I can download.")
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw FetchException("Couldn't read the answer from the site.")
        }

        val isPlaylist = root.str("_type") == "playlist"
        val entries: List<JSONObject> = if (isPlaylist) {
            val arr = root.optJSONArray("entries") ?: JSONArray()
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
        } else {
            listOf(root)
        }

        val media = entries.mapIndexedNotNull { i, e -> toMedia(e, url, if (isPlaylist) i + 1 else 0) }
        val title = root.str("title").ifEmpty { entries.firstOrNull()?.str("title").orEmpty() }
        val uploader = firstNonEmpty(root, "uploader", "channel", "creator")
            .ifEmpty { entries.firstOrNull()?.let { firstNonEmpty(it, "uploader", "channel", "creator") }.orEmpty() }
        val id = root.str("id").ifEmpty { Integer.toHexString(url.hashCode()) }
        val source = prettySource(root.str("extractor_key"), url)

        return Post(
            id = "${source.lowercase()}_$id",
            authorName = uploader,
            authorHandle = "",
            text = title,
            media = media,
            source = source,
            pageUrl = url,
        )
    }

    private fun toMedia(e: JSONObject, pageUrl: String, item: Int): MediaItem? {
        val thumb = e.str("thumbnail").ifEmpty { lastThumbnail(e) }
        val formats = e.optJSONArray("formats")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
        }.orEmpty()
        val duration = e.optDouble("duration", 0.0)

        fun hasVideo(f: JSONObject) = f.str("vcodec") != "none" && f.optInt("height") > 0
        fun hasAudio(f: JSONObject) = f.str("acodec").let { it != "none" && it.isNotEmpty() }
        fun sizeOf(f: JSONObject): Long {
            val exact = f.optLong("filesize")
            if (exact > 0) return exact
            val approx = f.optLong("filesize_approx")
            if (approx > 0) return approx
            val tbr = f.optDouble("tbr", 0.0)
            return if (tbr > 0 && duration > 0) (tbr * 125.0 * duration).toLong() else 0L
        }

        val videoFormats = formats.filter { hasVideo(it) }
        val audioOnly = formats.filter { !hasVideo(it) && hasAudio(it) }
        val bestAudio = audioOnly.maxByOrNull { it.optDouble("abr", 0.0) }
        val audioSize = bestAudio?.let { sizeOf(it) } ?: 0L

        // A single image (Instagram photo posts, carousels).
        if (videoFormats.isEmpty() && audioOnly.isEmpty()) {
            val direct = e.str("url")
            val ext = e.str("ext").lowercase()
            if (direct.isNotEmpty() && ext in IMAGE_EXT) {
                return MediaItem(MediaKind.PHOTO, thumb.ifEmpty { direct }, listOf(Variant(direct, "Original", 0)))
            }
            // Unknown shape (direct file, odd site): let the engine pick its own best.
            if (e.str("url").isEmpty() && e.str("webpage_url").isEmpty()) return null
            return MediaItem(
                MediaKind.VIDEO, thumb.ifEmpty { null },
                listOf(Variant(pageUrl, "Best", 0, 0L, selector = "bv*+ba/b", item = item)),
            )
        }

        val variants = mutableListOf<Variant>()
        val heights = videoFormats.map { it.optInt("height") }.distinct().sortedDescending()
        val shown = (if (heights.size > MAX_QUALITIES) heights.take(MAX_QUALITIES) else heights)
        for (h in shown) {
            val atHeight = videoFormats.filter { it.optInt("height") == h }
            val pick = atHeight.firstOrNull { it.str("vcodec").startsWith("avc1") || it.str("vcodec").startsWith("h264") }
                ?: atHeight.maxByOrNull { sizeOf(it) }!!
            val size = sizeOf(pick) + if (hasAudio(pick)) 0L else audioSize
            // Name it by the shorter side (a 720x1280 reel is "720p"), but filter on the real height.
            val width = pick.optInt("width")
            val shortSide = if (width in 1 until h) width else h
            variants.add(
                Variant(
                    url = pageUrl,
                    label = "${shortSide}p",
                    bitrate = pick.optDouble("tbr", 0.0).toInt(),
                    size = size,
                    selector = "bv*[height<=$h]+ba/b[height<=$h]",
                    item = item,
                )
            )
        }
        if (bestAudio != null || videoFormats.any { hasAudio(it) }) {
            variants.add(Variant(pageUrl, "MP3", 0, audioSize, selector = AUDIO_SELECTOR, item = item))
        }
        if (variants.isEmpty()) return null

        val kind = if (videoFormats.isEmpty()) MediaKind.AUDIO else MediaKind.VIDEO
        return MediaItem(kind, thumb.ifEmpty { null }, variants)
    }

    /** org.json turns JSON null into the text "null"; yt-dlp sends many nulls, so read them as empty. */
    private fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key)

    private fun lastThumbnail(e: JSONObject): String {
        val arr = e.optJSONArray("thumbnails") ?: return ""
        for (i in arr.length() - 1 downTo 0) {
            val u = arr.optJSONObject(i)?.str("url").orEmpty()
            if (u.startsWith("http")) return u
        }
        return ""
    }

    private fun firstNonEmpty(o: JSONObject, vararg keys: String): String {
        for (k in keys) {
            val v = o.str(k)
            if (v.isNotEmpty() && v != "null") return v
        }
        return ""
    }

    /** "Youtube" -> "YouTube", "Twitter" -> "X"; unknown sites fall back to the domain name. */
    private fun prettySource(extractorKey: String, url: String): String {
        val key = extractorKey.lowercase()
        return when {
            key.startsWith("youtube") -> "YouTube"
            key.startsWith("instagram") -> "Instagram"
            key.startsWith("facebook") -> "Facebook"
            key.startsWith("tiktok") -> "TikTok"
            key.startsWith("twitter") -> "X"
            key.startsWith("reddit") -> "Reddit"
            key.startsWith("vimeo") -> "Vimeo"
            key.startsWith("pinterest") -> "Pinterest"
            key.startsWith("snapchat") -> "Snapchat"
            key.startsWith("linkedin") -> "LinkedIn"
            key.startsWith("dailymotion") -> "Dailymotion"
            key.startsWith("twitch") -> "Twitch"
            extractorKey.isNotEmpty() && key != "generic" -> extractorKey
            else -> try {
                URI(url).host?.removePrefix("www.") ?: "Web"
            } catch (e: Exception) {
                "Web"
            }
        }
    }
}
