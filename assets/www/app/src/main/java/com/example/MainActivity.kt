package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AnalyzerScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.VideoAnalyzerViewModel

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val viewModel: VideoAnalyzerViewModel = viewModel()
      val isDark by viewModel.isDarkMode.collectAsState(initial = false)
      
      MyApplicationTheme(darkTheme = isDark) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          AnalyzerScreen(
            viewModel = viewModel,
            modifier = Modifier.padding(innerPadding)
          )
        }
      }
    }
  }
}
