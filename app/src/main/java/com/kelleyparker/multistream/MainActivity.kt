package com.kelleyparker.multistream

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.kelleyparker.multistream.ui.AppRoot
import com.kelleyparker.multistream.ui.MainViewModel
import com.kelleyparker.multistream.ui.theme.MultistreamTheme

class MainActivity : ComponentActivity() {
  private val viewModel: MainViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MultistreamTheme {
        AppRoot(viewModel)
      }
    }
  }
}
