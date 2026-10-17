package com.example.read

import android.app.Application
import com.example.read.di.AppGraph

class FolioApplication : Application() {
    val graph by lazy { AppGraph(this) }
}
