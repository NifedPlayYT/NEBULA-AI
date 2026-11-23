package ai.nebula.app

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import ai.nebula.app.data.NebulaRepository
import ai.nebula.app.ui.NebulaApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = AndroidColor.rgb(9, 11, 18)
        window.navigationBarColor = AndroidColor.rgb(9, 11, 18)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val repository = NebulaRepository(applicationContext)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFB9A3FF),
                    secondary = Color(0xFFF1D5A5),
                    background = Color(0xFF090B12),
                    surface = Color(0xFF151827),
                    onBackground = Color(0xFFF5F3FC),
                    onSurface = Color(0xFFF5F3FC)
                )
            ) {
                NebulaApp(repository)
            }
        }
    }
}
