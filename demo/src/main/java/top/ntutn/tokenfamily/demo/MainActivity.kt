package top.ntutn.tokenfamily.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import top.ntutn.tokenfamily.demo.ui.DemoApp
import top.ntutn.tokenfamily.sdk.TokenFamily

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TokenFamily.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            DemoApp()
        }
    }
}
