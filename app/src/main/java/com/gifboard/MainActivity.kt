package com.gifboard

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check if tutorial has been completed
        val destination = if (TutorialPreferences.isTutorialCompleted(this)) {
            SettingsActivity::class.java
        } else {
            TutorialActivity::class.java
        }
        
        startActivity(Intent(this, destination))
        finish()
    }
}
