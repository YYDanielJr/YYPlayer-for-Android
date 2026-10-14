package yy.daniel.yyplayerforandroid.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

// Scoped to the video page: music and portrait retain their Miuix windows.
internal val LocalVideoSettingsInDrawer = staticCompositionLocalOf { false }
private val LocalVideoSettingsBack = staticCompositionLocalOf<(() -> Unit)?> { null }

@Composable
internal fun <T : Any> VideoSettingsHost(panel: T?, reduced: Boolean, onDismiss: () -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    content: @Composable (T?) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth <= maxHeight) {
            content(panel)
        } else {
            var retainedPanel by remember { mutableStateOf(panel) }
            if (panel != null) retainedPanel = panel
            val opened = panel != null
            val duration = if (reduced) 0 else 260
            val width = (maxWidth * .46f).coerceIn(320.dp, 420.dp).coerceAtMost(maxWidth - 48.dp)
            BackHandler(opened) { (onNavigateBack ?: onDismiss)() }
            AnimatedVisibility(opened, enter = fadeIn(tween(duration)), exit = fadeOut(tween(duration))) {
                Box(Modifier.fillMaxSize().testTag("video-settings-scrim")
                    .background(Color.Black.copy(alpha = .22f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                        onClickLabel = "关闭视频设置", onClick = { if (opened) onDismiss() }))
            }
            AnimatedVisibility(opened, modifier = Modifier.align(Alignment.CenterEnd),
                enter = slideInHorizontally(tween(duration)) { it },
                exit = slideOutHorizontally(tween(duration)) { it }) {
                // Keep the last page for the exit animation, but consume input immediately on close.
                Column(Modifier.width(width).fillMaxHeight().testTag("video-settings-drawer")
                    .then(if (!opened) Modifier.clearAndSetSemantics { } else Modifier)
                    .pointerInput(opened) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (!opened) event.changes.forEach { it.consume() }
                                // While open, leave all passes untouched so child drag detectors can scroll.
                                // This pointer node already owns the hit path; siblings behind it cannot receive it.
                            }
                        }
                    }
                    .clip(RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp))
                    .background(MiuixTheme.colorScheme.surface.copy(alpha = .90f))
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.End))) {
                    CompositionLocalProvider(LocalVideoSettingsInDrawer provides true,
                        LocalVideoSettingsBack provides onNavigateBack) {
                        content(retainedPanel)
                    }
                }
            }
        }
    }
}

@Composable
internal fun VideoDrawerPage(title: String, onDismiss: () -> Unit, buttonText: String,
    scrollContent: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().semantics { paneTitle = title }) {
        val navigateBack = LocalVideoSettingsBack.current
        Row(Modifier.fillMaxWidth().padding(start = if (navigateBack != null) 12.dp else 24.dp,
            end = 24.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (navigateBack != null) {
                HyperIconButton(PlayerIcons.Back, "返回上一级", navigateBack)
                Spacer(Modifier.width(4.dp))
            }
            Text(title, Modifier.weight(1f).padding(vertical = 6.dp),
                fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
        CompositionLocalProvider(LocalPreferenceInset provides DialogContentInset, LocalDialogAction provides true) {
            // A new page starts at the top; returning to another setting never inherits its scroll offset.
            key(title) {
                Column(Modifier.weight(1f).testTag("video-settings-content").then(if (scrollContent) Modifier.verticalScroll(rememberScrollState()) else Modifier),
                    content = content)
            }
        }
        HyperActionButton(buttonText, onDismiss, primary = false, compact = true,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
    }
}
