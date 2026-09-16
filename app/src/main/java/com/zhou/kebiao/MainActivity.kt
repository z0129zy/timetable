package com.zhou.kebiao

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.zhou.kebiao.ui.AppRoot
import com.zhou.kebiao.ui.theme.课表Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            课表Theme {
                AppRoot()
            }
        }
    }
}
