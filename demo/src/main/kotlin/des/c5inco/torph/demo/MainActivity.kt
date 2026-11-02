package des.c5inco.torph.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mode = intent.getStringExtra("firstLayout")
            if (mode == null) DemoApp()
            else des.c5inco.torph.demo.screens.FirstLayoutScreen(
                mode, intent.getIntExtra("labels", 8), intent.getBooleanExtra("repeated", false),
                intent.getBooleanExtra("logTimings", false),
            )
        }
    }
}
