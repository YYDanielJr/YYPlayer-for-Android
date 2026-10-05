package yy.daniel.yyplayerforandroid.ui

import androidx.annotation.Keep
import androidx.annotation.RequiresApi
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import top.yukonga.miuix.kmp.blur.*
import top.yukonga.miuix.kmp.blur.highlight.Highlight

/** Kept as a separate class so R8 cannot inline API33 effect calls into the low-API facade. */
@Keep
@RequiresApi(33)
internal object Api33MiuixBlur {
    @Composable
    fun rememberBackdrop(background: Color, surface: Color): HyperBackdrop {
        if (!isRuntimeShaderSupported()) return remember { HyperBackdrop() }
        val native = rememberLayerBackdrop { drawRect(background); drawContent() }
        val highlight = if (background.luminance() < .5f) Highlight.GlassStrokeMiddleDark else Highlight.GlassStrokeMiddleLight
        return remember(native, surface, highlight) {
            HyperBackdrop(Modifier.layerBackdrop(native), true) { modifier, shape, alpha, navigationStyle ->
                modifier.textureBlur(backdrop = native, shape = shape, blurRadius = if (navigationStyle) 25f else 32f,
                    highlight = if (navigationStyle) highlight else null,
                    colors = BlurDefaults.blurColors(
                        blendColors = listOf(BlendColorEntry(surface.copy(alpha = alpha), BlurBlendMode.SrcOver)),
                        contrast = if (navigationStyle) 1f else 1.03f, saturation = if (navigationStyle) 1f else 1.1f))
            }
        }
    }
}
