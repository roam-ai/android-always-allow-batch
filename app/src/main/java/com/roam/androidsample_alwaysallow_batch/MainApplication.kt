package com.roam.androidsample_alwaysallow_batch

import android.app.Application
import com.roam.sdk.Roam
import com.roam.sdk.builder.RoamBatchPublish
import com.roam.sdk.enums.LogLevel

class MainApplication: Application() {
    override fun onCreate() {
        super.onCreate()
        Roam.initialize(this,"701ca389-e233-4f46-a931-d68fd22e9618")

        //batch sync
        val roamBatchPublish = RoamBatchPublish.Builder().enableAll().build()
        Roam.setConfig(true,roamBatchPublish)
    }
}