package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.ScrapApp
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.ScrapViewModel

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      // Force Dark Mode context globally for the sleek, sci-fi aesthetic requested
      MyApplicationTheme(darkTheme = true, dynamicColor = false) {
        val viewModel: ScrapViewModel = viewModel()
        ScrapApp(viewModel = viewModel)
      }
    }
  }
}
