package com.nikhil.pullr

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.concurrent.ConcurrentHashMap

/**
 * Last resort for sites that load their video with JavaScript: opens the page in a hidden browser,
 * presses play, and records the video/audio files the page itself requests.
 */
object WebSniffer {
    class Result(val title: String, val videos: List<String>, val audios: List<String>)

    /** media file link -> the page it came from (some servers only hand the file to that page). */
    val referers = ConcurrentHashMap<String, String>()

    private val VIDEO_EXT = setOf("mp4", "m4v", "webm", "mov", "m3u8")
    private val AUDIO_EXT = setOf("mp3", "m4a", "aac", "ogg", "opus")
    private val AD_WORDS = listOf("doubleclick", "adserver", "/ads/", "/ad/", "preroll", "banner", "googlesyndication", "popunder")

    private const val PLAY_JS = """
(function(){
  try{
    var t=/^(enter|i am 18|i'm 18|i am over 18|i am 18 or older|18\+|agree|yes|continue|accept)/i;
    document.querySelectorAll('button,a,div[role=button],span').forEach(function(e){
      var s=(e.innerText||'').trim(); if(s.length<30 && t.test(s)){ try{e.click()}catch(x){} }
    });
    document.querySelectorAll('video').forEach(function(v){ v.muted=true; try{v.play()}catch(x){} });
    ['.vjs-big-play-button','.jw-icon-display','.plyr__control--overlaid','.play-button','.mhp1138_play','[class*=play]'].forEach(function(q){
      var e=document.querySelector(q); if(e){ try{e.click()}catch(x){} }
    });
    var out=[];
    document.querySelectorAll('video,source').forEach(function(v){ var u=v.currentSrc||v.src; if(u) out.push(u); });
    return out.join('\n');
  }catch(x){ return ''; }
})()
"""

    private fun extOf(url: String): String =
        url.substringBefore('#').substringBefore('?').substringAfterLast('/', "").substringAfterLast('.', "").lowercase()

    @SuppressLint("SetJavaScriptEnabled")
    fun sniff(pageUrl: String, timeoutMs: Long = 20_000): Result? {
        val ctx = PullrApp.instance ?: return null
        val found = LinkedHashSet<String>()
        var title = ""
        var web: WebView? = null
        val main = Handler(Looper.getMainLooper())

        fun consider(raw: String?) {
            val u = raw?.trim() ?: return
            if (!u.startsWith("http")) return
            val e = extOf(u)
            if (e !in VIDEO_EXT && e !in AUDIO_EXT) return
            if (AD_WORDS.any { u.lowercase().contains(it) }) return
            synchronized(found) { found.add(u) }
        }

        main.post {
            try {
                val w = WebView(ctx)
                web = w
                w.settings.javaScriptEnabled = true
                w.settings.domStorageEnabled = true
                w.settings.mediaPlaybackRequiresUserGesture = false
                w.settings.userAgentString = MediaFetcher.USER_AGENT
                w.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                        consider(request.url.toString())
                        return null
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        title = view.title.orEmpty()
                        for (delay in longArrayOf(0, 2500, 6000, 10000)) {
                            main.postDelayed({
                                view.evaluateJavascript(PLAY_JS) { res ->
                                    res?.trim('"')?.replace("\\n", "\n")?.lines()?.forEach { consider(it) }
                                }
                            }, delay)
                        }
                    }
                }
                w.loadUrl(pageUrl)
            } catch (e: Throwable) {
                // no WebView on this phone
            }
        }

        val end = System.currentTimeMillis() + timeoutMs
        var firstHit = 0L
        while (System.currentTimeMillis() < end) {
            Thread.sleep(400)
            val any = synchronized(found) { found.any { extOf(it) in VIDEO_EXT } }
            if (any && firstHit == 0L) firstHit = System.currentTimeMillis()
            if (firstHit != 0L && System.currentTimeMillis() - firstHit > 2500) break
        }
        main.post { runCatching { web?.stopLoading(); web?.destroy() } }

        val all = synchronized(found) { found.toList() }
        // Short "preview" clips of listing pages are not the video; drop them when something better exists.
        fun isPreview(u: String) = listOf("preview", "thumb", "trailer", "teaser").any { u.lowercase().contains(it) }
        var videos = all.filter { extOf(it) in VIDEO_EXT }
        if (videos.any { !isPreview(it) }) videos = videos.filter { !isPreview(it) }
        videos = videos.sortedBy { if (extOf(it) == "m3u8") 1 else 0 }.take(3)
        val audios = all.filter { extOf(it) in AUDIO_EXT }.take(3)
        if (videos.isEmpty() && audios.isEmpty()) return null
        (videos + audios).forEach { referers[it] = pageUrl }
        return Result(title.trim().take(100), videos, audios)
    }
}
