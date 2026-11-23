package ai.nebula.app

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import ai.nebula.app.data.NebulaRepository
import ai.nebula.app.ui.NebulaApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Draw a unified dark background edge-to-edge while keeping content
        // below system bars through Compose insets.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = AndroidColor.TRANSPARENT
        window.navigationBarColor = AndroidColor.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
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
