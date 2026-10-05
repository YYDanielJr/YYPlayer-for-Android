package yy.daniel.yyplayerforandroid.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.theme.*

@Composable
fun YYPlayerTheme(mode: ColorSchemeMode = ColorSchemeMode.System, accent: Color = Color(0xFF3482FF), content: @Composable () -> Unit) {
    val dark = mode == ColorSchemeMode.Dark || (mode == ColorSchemeMode.System && isSystemInDarkTheme())
    val controller = remember(dark, accent) {
        ThemeController(colorSchemeMode = if (dark) ColorSchemeMode.Dark else ColorSchemeMode.Light,
            lightColors = lightColorScheme(primary = accent, onPrimary = Color.White, background = Color(0xFFF3F3F3), surface = Color.White, surfaceContainer = Color.White),
            darkColors = darkColorScheme(primary = accent, onPrimary = Color.White, background = Color(0xFF101010), surface = Color(0xFF1C1C1E), surfaceContainer = Color(0xFF1C1C1E)), isDark = dark)
    }
    val typography = remember {
        defaultTextStyles(main = TextStyle(fontSize = 17.sp), paragraph = TextStyle(fontSize = 17.sp, lineHeight = 24.sp),
            body1 = TextStyle(fontSize = 16.sp), body2 = TextStyle(fontSize = 14.sp),
            button = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium), subtitle = TextStyle(fontSize = 14.sp),
            title1 = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Normal), title2 = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Medium))
    }
    MiuixTheme(controller = controller, textStyles = typography, content = content)
}
