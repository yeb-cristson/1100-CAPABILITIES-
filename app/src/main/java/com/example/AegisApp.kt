package com.example

import android.app.Application
import com.example.core.database.AppDatabase
import com.example.core.hub.ReconHub

class AegisApp : Application() {

  companion object {
    lateinit var instance: AegisApp
      private set
  }

  lateinit var database: AppDatabase
    private set

  lateinit var reconHub: ReconHub
    private set

  override fun onCreate() {
    super.onCreate()
    instance = this
    database = AppDatabase.getDatabase(this)
    reconHub = ReconHub.initialize(this, database)
  }
}
