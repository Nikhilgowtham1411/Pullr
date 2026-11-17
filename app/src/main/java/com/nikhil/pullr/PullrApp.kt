package com.nikhil.pullr

import android.app.Application

/** Starts the downloader engine in the background as soon as the app process exists. */
class PullrApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Engine.start(this)
    }
}
