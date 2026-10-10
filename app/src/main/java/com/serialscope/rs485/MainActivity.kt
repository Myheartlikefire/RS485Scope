package com.serialscope.rs485

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.serialscope.rs485.ui.ScopeScreen
import com.serialscope.rs485.ui.ScopeViewModel
import com.serialscope.rs485.ui.theme.ScopeTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ScopeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel.onUsbIntent(intent)
        setContent {
            ScopeTheme {
                ScopeScreen(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.onUsbIntent(intent)
    }
}