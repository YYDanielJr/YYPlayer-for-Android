package yy.daniel.yyplayerforandroid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import yy.daniel.yyplayerforandroid.playback.bilingualParts

@Composable
internal fun BilingualLyricText(text: String, modifier: Modifier = Modifier, style: LyricStyle,
    active: Boolean, primarySize: Float, color: Color, centered: Boolean = false) {
    val (primary, secondary) = remember(text) { bilingualParts(text) }
    val alignment = if (centered) TextAlign.Center else TextAlign.Start
    Column(modifier, verticalArrangement = Arrangement.spacedBy(style.bilingualSpacing.dp)) {
        Text(primary.ifBlank { "•••" }, Modifier.fillMaxWidth(), fontSize = primarySize.sp, lineHeight = (primarySize * 1.35f).sp,
            fontWeight = FontWeight(if (active) style.activeWeight else style.inactiveWeight), color = color, textAlign = alignment)
        if (style.bilingual && secondary.isNotEmpty()) {
            val size = if (active) style.translationActiveSize else style.translationSize
            Text(secondary, Modifier.fillMaxWidth(), fontSize = size.sp, lineHeight = (size * 1.35f).sp,
                fontWeight = FontWeight(style.translationWeight), color = color, textAlign = alignment)
        }
    }
}
