package com.github.irdcat.workout

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.github.irdcat.workout.ui.Application
import com.github.irdcat.workout.ui.WorkoutTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WorkoutTheme {
                Application()
            }
        }
    }
}

