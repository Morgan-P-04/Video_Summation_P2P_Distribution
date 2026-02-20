package com.example.fyp

import android.app.Application
import com.example.fyp.core.database.AppDatabase

class MainApplication : Application() {
    //  create database instance lazily and keep for app lifecycle
    val database by lazy { AppDatabase.getDatabase(this) }
}