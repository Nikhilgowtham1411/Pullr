package com.nikhil.pullr

import android.app.Application

/** Starts the downloader engine in the background as soon as the app process exists. */
class PullrApp : Application() {
    companion object {
        @Volatile
        var instance: Application? = null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Engine.start(this)
    }
}
