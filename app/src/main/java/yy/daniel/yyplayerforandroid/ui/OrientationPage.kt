package yy.daniel.yyplayerforandroid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Full selection window, matching the supplied Default USB configuration reference. */
@Composable
internal fun OrientationPage(orientation: OrientationChoice, onSelect: (OrientationChoice) -> Unit, onBack: () -> Unit) {
    HyperPage("屏幕方向", onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding() + 12.dp, bottom = 32.dp + padding.calculateBottomPadding() + LocalPlaybackBottomSpace.current)) {
            OrientationChoice.entries.forEach { choice -> item {
                val selected = orientation == choice
                Surface(onClick = { onSelect(choice) }, shape = CardShape, color = MiuixTheme.colorScheme.surface,
                    modifier = Modifier.padding(horizontal = PageGutter, vertical = 6.dp).fillMaxWidth()
                        .semantics { this.selected = selected; role = Role.RadioButton }) {
                    Row(Modifier.padding(horizontal = PreferenceInset, vertical = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(28.dp)) { if (selected) Icon(PlayerIcons.Check, null, Modifier.size(20.dp), tint = MiuixTheme.colorScheme.primary) }
                        Text(choice.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                            color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface)
                    }
                }
            } }
            item { Text("自动根据视频的有效画面比例选择方向。手动选择将在后续视频播放中保持。", Modifier.padding(horizontal = PageTextInset, vertical = 18.dp), color = Muted, fontSize = 14.sp) }
        }
    }
}
