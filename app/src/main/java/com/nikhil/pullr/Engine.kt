package com.nikhil.pullr

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * The on-device downloader engine (yt-dlp + Python + FFmpeg, bundled in the app).
 * It knows how to read YouTube, Instagram, Facebook, TikTok, Reddit, Vimeo and 1000+ other sites,
 * and FFmpeg merges separate video/audio streams into one file (needed for 1080p+ YouTube).
 *
 * The first start unpacks Python and takes a few seconds, so it runs on a background thread;
 * anything that needs the engine calls [ensureReady] first.
 */
object Engine {
    private const val PREFS = "pullr_engine"
    private const val KEY_LAST_UPDATE = "last_update"
    private const val UPDATE_EVERY_MS = 24L * 60 * 60 * 1000

    private val ready = CountDownLatch(1)

    @Volatile
    private var failure: String? = null

    @Volatile
    private var started = false

    @Volatile
    private var appCtx: Context? = null

    @Volatile
    private var lastForcedUpdate = 0L

    fun start(app: Context) {
        if (started) return
        started = true
        appCtx = app.applicationContext
        thread(name = "engine-init", isDaemon = true) {
            try {
                YoutubeDL.getInstance().init(app)
                FFmpeg.getInstance().init(app)
            } catch (e: Throwable) {
                failure = e.message ?: e.javaClass.simpleName
            } finally {
                ready.countDown()
            }
            // Sites change their pages all the time; yt-dlp ships fixes within days. Refresh once a day.
            if (failure == null) autoUpdate(app)
        }
    }

    /**
     * Called when a link fails: pulls the newest yt-dlp once (at most every 10 minutes) so the retry
     * can succeed even if the bundled yt-dlp is stale. Returns true when a newer version was installed.
     */
    fun updateForRetry(): Boolean {
        val app = appCtx ?: return false
        val now = System.currentTimeMillis()
        if (now - lastForcedUpdate < 10 * 60 * 1000L) return false
        lastForcedUpdate = now
        return try {
            YoutubeDL.getInstance().updateYoutubeDL(app, YoutubeDL.UpdateChannel.STABLE) == YoutubeDL.UpdateStatus.DONE
        } catch (e: Throwable) {
            false
        }
    }

    /** Blocks until the engine can be used. Call from a background thread only. */
    fun ensureReady() {
        if (!started) throw FetchException("The downloader engine hasn't started yet. Reopen the app.")
        if (!ready.await(120, TimeUnit.SECONDS)) {
            throw FetchException("The downloader engine is still getting ready. Try again in a moment.")
        }
        failure?.let { throw FetchException("The downloader engine couldn't start: $it") }
    }

    fun version(app: Context): String =
        try {
            YoutubeDL.getInstance().versionName(app) ?: "unknown"
        } catch (e: Throwable) {
            "unknown"
        }

    /** Manual update (the "Update engine" button). Returns a short message for the screen. */
    fun update(app: Context): String {
        return try {
            ensureReady()
            val status = YoutubeDL.getInstance().updateYoutubeDL(app, YoutubeDL.UpdateChannel.STABLE)
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(KEY_LAST_UPDATE, System.currentTimeMillis()).apply()
            when (status) {
                YoutubeDL.UpdateStatus.DONE -> "Engine updated to ${version(app)}."
                else -> "Engine is already up to date (${version(app)})."
            }
        } catch (e: Throwable) {
            "Couldn't update the engine: ${e.message ?: "check your internet"}"
        }
    }

    private fun autoUpdate(app: Context) {
        try {
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val last = prefs.getLong(KEY_LAST_UPDATE, 0L)
            if (System.currentTimeMillis() - last < UPDATE_EVERY_MS) return
            YoutubeDL.getInstance().updateYoutubeDL(app, YoutubeDL.UpdateChannel.STABLE)
            prefs.edit().putLong(KEY_LAST_UPDATE, System.currentTimeMillis()).apply()
        } catch (e: Throwable) {
            // Offline or rate limited: try again next launch.
        }
    }

    /** Turns yt-dlp's raw error text into something a person can act on. */
    fun friendlyError(raw: String?): String {
        val text = raw.orEmpty()
        val message = explain(text)
        val detail = (text.lines().lastOrNull { it.trim().startsWith("ERROR") }
            ?: text.lines().lastOrNull { it.isNotBlank() }
            ?: "").removePrefix("ERROR:").trim().take(140)
        return if (detail.isNotEmpty() && !message.contains(detail.take(40))) "$message\n($detail)" else message
    }

    private fun explain(text: String): String {
        val lower = text.lowercase()
        return when {
            "login" in lower || "log in" in lower || "cookies" in lower || "rate-limit" in lower ->
                "This link needs a login (private, age-restricted or temporarily rate-limited). Public posts work without one."
            "private" in lower -> "This video is private."
            "unsupported url" in lower -> "This site or link type isn't supported."
            "not available" in lower || "unavailable" in lower || "removed" in lower || "does not exist" in lower ->
                "This video isn't available. It may have been removed or blocked in your country."
            "unable to download webpage" in lower || "timed out" in lower || "name or service not known" in lower ||
                "network is unreachable" in lower -> "Couldn't reach the site. Check your internet and try again."
            "ffmpeg" in lower && "not found" in lower -> "The video merger isn't ready yet. Try again in a moment."
            else -> {
                val line = text.lines().lastOrNull { it.trim().startsWith("ERROR") }
                    ?: text.lines().lastOrNull { it.isNotBlank() }
                    ?: "Something went wrong."
                line.removePrefix("ERROR:").trim().take(160)
            }
        }
    }
}
