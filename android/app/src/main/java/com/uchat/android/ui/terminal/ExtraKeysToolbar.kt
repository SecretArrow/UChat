package com.uchat.android.ui.terminal

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uchat.android.terminal.keys.ExtraKey
import com.uchat.android.terminal.keys.ExtraKeyType
import com.uchat.android.terminal.keys.KeyLayout
import com.uchat.android.terminal.keys.ModifierKey
import com.uchat.android.terminal.keys.ModifierStateHolder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Terminal chrome scheme (matches assets/terminal/index.html — dark, high contrast).
 * Both the dark and light instance keep the same role names so every screen of the terminal
 * UI switches theme without touching its call sites.
 */
@androidx.compose.runtime.Immutable
data class TerminalScheme(
    val isLight: Boolean,
    val Background: Color,
    val Surface: Color,
    val SurfaceRaised: Color,
    val Outline: Color,
    val Foreground: Color,
    val ForegroundDim: Color,
    val Accent: Color,
    val AccentDim: Color,
    val Active: Color,
)

/** Dark terminal chrome (matches assets/terminal/index.html — high contrast). */
val DarkTerminalScheme =
    TerminalScheme(
        isLight = false,
        Background = Color(0xFF0B0D12),
        Surface = Color(0xFF171A21),
        SurfaceRaised = Color(0xFF1F232D),
        Outline = Color(0xFF31363F),
        Foreground = Color(0xFFE8EAF0),
        ForegroundDim = Color(0xFF9BA1AE),
        Accent = Color(0xFFE5484D),
        AccentDim = Color(0x66E5484D),
        Active = Color(0xFF4CC38A),
    )

/** Light terminal chrome: same roles, luminance rebalanced for a near-white canvas. */
val LightTerminalScheme =
    TerminalScheme(
        isLight = true,
        Background = Color(0xFFF6F7FA),
        Surface = Color(0xFFEDEFF4),
        SurfaceRaised = Color(0xFFE2E5EC),
        Outline = Color(0xFFC9CEDA),
        Foreground = Color(0xFF1B1F28),
        ForegroundDim = Color(0xFF5B6270),
        Accent = Color(0xFFC43238),
        AccentDim = Color(0x66C43238),
        Active = Color(0xFF12855E),
    )

/** Active terminal scheme, provided by [com.uchat.android.ui.theme.UChatTheme]. */
val LocalTerminalPalette =
    androidx.compose.runtime.staticCompositionLocalOf<TerminalScheme> { DarkTerminalScheme }

/**
 * Composable accessor that keeps the historical `TerminalPalette.X` call sites working while
 * the underlying scheme now follows the app's dark/light theme.
 */
val TerminalPalette: TerminalScheme
    @Composable get() = LocalTerminalPalette.current

/**
 * Layer 4/5 UI: the fully customizable extra-key toolbar.
 * - horizontal scrolling, compact dark design
 * - modifiers render their sticky state (latched = dim accent, locked = accent)
 * - press → tap callback; long-press → long-press callback; repeatable keys auto-repeat while held
 *   (only when the global repeat setting is on)
 * - layout switcher chip + editor shortcut on the leading edge
 */
@Composable
fun ExtraKeysToolbar(
    layout: KeyLayout?,
    keyHeightDp: Int,
    keyMinWidthDp: Int,
    hapticsEnabled: Boolean,
    repeatEnabled: Boolean,
    modifierStates: Map<ModifierKey, ModifierStateHolder.State>,
    onKeyTap: (ExtraKey) -> Unit,
    onKeyLongPress: (ExtraKey) -> Unit,
    onModifierTap: (ModifierKey) -> Unit,
    onModifierLockToggle: (ModifierKey) -> Unit,
    onSwitchLayout: () -> Unit,
    onEditKeys: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier.fillMaxWidth().background(TerminalPalette.Surface).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onSwitchLayout) {
            Icon(
                Icons.Filled.Keyboard,
                contentDescription = layout?.name ?: "",
                tint = TerminalPalette.ForegroundDim,
            )
        }
        LazyRow(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            items(items = layout?.keys ?: emptyList(), key = { it.id }) { key ->
                val modState =
                    if (key.type == ExtraKeyType.MODIFIER) {
                        key.modifiers.firstOrNull()?.let { modifierStates[it] }
                            ?: ModifierStateHolder.State.OFF
                    } else {
                        null
                    }
                ExtraKeyButton(
                    key = key,
                    modifierState = modState,
                    keyHeightDp = keyHeightDp,
                    keyMinWidthDp = keyMinWidthDp,
                    hapticsEnabled = hapticsEnabled,
                    repeatEnabled = repeatEnabled,
                    onTap = {
                        if (key.type == ExtraKeyType.MODIFIER) {
                            key.modifiers.firstOrNull()?.let(onModifierTap)
                        } else {
                            onKeyTap(key)
                        }
                    },
                    onLongPress = {
                        if (key.type == ExtraKeyType.MODIFIER) {
                            key.modifiers.firstOrNull()?.let(onModifierLockToggle)
                        } else {
                            onKeyLongPress(key)
                        }
                    },
                )
            }
        }
        IconButton(onClick = onEditKeys) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = null,
                tint = TerminalPalette.ForegroundDim,
            )
        }
    }
}

@Composable
private fun ExtraKeyButton(
    key: ExtraKey,
    modifierState: ModifierStateHolder.State?,
    keyHeightDp: Int,
    keyMinWidthDp: Int,
    hapticsEnabled: Boolean,
    repeatEnabled: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }

    fun haptic(long: Boolean) {
        if (!hapticsEnabled || !key.haptic) return
        view.performHapticFeedback(
            if (long) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.KEYBOARD_TAP
        )
    }

    val background =
        when {
            modifierState == ModifierStateHolder.State.LOCKED -> TerminalPalette.Accent
            modifierState == ModifierStateHolder.State.LATCHED -> TerminalPalette.AccentDim
            pressed -> TerminalPalette.SurfaceRaised
            else -> TerminalPalette.Surface
        }
    val labelColor =
        if (modifierState == ModifierStateHolder.State.LOCKED) Color.White
        else TerminalPalette.Foreground

    val repeatable =
        repeatEnabled &&
            key.repeat == com.uchat.android.terminal.keys.RepeatBehavior.HOLD &&
            key.longPressText == null &&
            key.type != ExtraKeyType.MODIFIER

    Box(
        modifier =
            Modifier.padding(horizontal = 3.dp)
                .height(keyHeightDp.dp)
                .defaultMinSize(minWidth = keyMinWidthDp.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(background)
                .border(1.dp, TerminalPalette.Outline, RoundedCornerShape(10.dp))
                .pointerInput(key.id, repeatable) {
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            if (!repeatable) {
                                tryAwaitRelease()
                                pressed = false
                                return@detectTapGestures
                            }
                            // Repeat loop runs while the finger stays down.
                            val loop =
                                scope.launch {
                                    delay(400)
                                    while (true) {
                                        onTap()
                                        delay(60)
                                    }
                                }
                            tryAwaitRelease()
                            loop.cancel()
                            pressed = false
                        },
                        onTap = {
                            haptic(false)
                            onTap()
                        },
                        onLongPress = {
                            haptic(true)
                            onLongPress()
                        },
                    )
                }
                .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = key.label,
            color = labelColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        if (modifierState == ModifierStateHolder.State.LOCKED) {
            Box(
                Modifier.align(Alignment.TopEnd)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.9f))
                    .padding(2.dp)
            )
        }
    }
}

/** Small round status dot used in session tabs. */
@Composable
fun SessionStatusDot(running: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(CircleShape)
            .background(if (running) TerminalPalette.Active else TerminalPalette.ForegroundDim)
            .padding(3.dp)
    )
}
