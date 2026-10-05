package yy.daniel.yyplayerforandroid.ui

import android.os.Build
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** No API33 or Miuix blur type may cross this facade into API26–32 UI classes. */
internal class HyperBackdrop(val capture: Modifier = Modifier, val supported: Boolean = false,
    val effect: @Composable (Modifier, Shape, Float, Boolean) -> Modifier = { modifier, _, _, _ -> modifier })

@Composable
internal fun rememberHyperBackdrop(tint: Color = MiuixTheme.colorScheme.surface): HyperBackdrop = if (Build.VERSION.SDK_INT >= 33) {
    Api33MiuixBlur.rememberBackdrop(MiuixTheme.colorScheme.background, tint)
} else remember { HyperBackdrop() }

internal fun Modifier.hyperCapture(backdrop: HyperBackdrop) = then(backdrop.capture)

@Composable
internal fun Modifier.hyperGlass(backdrop: HyperBackdrop, shape: Shape, alpha: Float = .65f, navigationStyle: Boolean = false) = backdrop.effect(this, shape, alpha, navigationStyle)
