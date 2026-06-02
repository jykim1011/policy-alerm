package com.policyalarm.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.policyalarm.ui.theme.Amber400
import com.policyalarm.ui.theme.GovBlueDeepC
import com.policyalarm.ui.theme.LocalAppColors

/** Render an emoji glyph at a given size (system color emoji). */
@Composable
fun Emoji(text: String, size: Int = 18, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = size.sp,
        lineHeight = size.sp,
        modifier = modifier,
    )
}

/**
 * The app brand mark: deep-blue gradient rounded square with a white bell and
 * an amber alert dot — "공신력 있는 정부 알림".
 */
@Composable
fun PolicyAppIcon(size: Int = 84, corner: Int = 23, modifier: Modifier = Modifier) {
    val s = size.dp
    Box(
        modifier = modifier
            .size(s)
            .clip(RoundedCornerShape(corner.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF2F63F0), Color(0xFF1D4ED8), GovBlueDeepC)
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Notifications,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(s * 0.52f),
        )
        // amber alert dot, top-right
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = s * 0.20f, end = s * 0.20f)
                .size(s * 0.20f)
                .clip(CircleShape)
                .background(Amber400)
                .border(s * 0.03f, Color.White.copy(alpha = 0.9f), CircleShape),
        )
    }
}

/** Selectable category pill used in the home filter row. */
@Composable
fun CategoryChip(
    label: String,
    emoji: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val c = LocalAppColors.current
    val bg by animateColorAsState(if (selected) c.accent else c.bgMuted, label = "chipBg")
    val fg by animateColorAsState(if (selected) Color.White else c.fgMuted, label = "chipFg")
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (emoji != null) Emoji(emoji, 14)
        Text(label, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/** Small tinted category chip shown on cards / detail header. */
@Composable
fun SubcatChip(category: String) {
    val c = LocalAppColors.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(c.govTint)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Emoji(catEmoji(category), 13)
        Text(category, color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Colored file-type badge (HWP / PDF / HTML …). */
@Composable
fun FileChip(type: String?) {
    val f = fileMeta(type) ?: return
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(f.color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(f.label, color = f.color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

/** Filled primary action button (52dp). */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Int = 52,
    leading: (@Composable () -> Unit)? = null,
) {
    val c = LocalAppColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) c.accent else c.borderStrong)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text,
            color = if (enabled) Color.White else c.fgFaint,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Outlined / surface "ghost" button. */
@Composable
fun GhostButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Int = 48,
    content: @Composable RowScope.() -> Unit,
) {
    val c = LocalAppColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(c.bgSurface)
            .border(1.dp, c.borderStrong, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** iOS-style toggle matching the design's Switch. */
@Composable
fun AppSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalAppColors.current
    val bg by animateColorAsState(if (on) c.accent else c.borderStrong, label = "switchBg")
    val offset by animateDpAsState(if (on) 18.dp else 0.dp, label = "switchKnob")
    Box(
        modifier = Modifier
            .width(46.dp)
            .height(28.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onChange(!on) }
            .padding(2.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = offset)
                .size(24.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

/** Segmented control (알림 시간 선택). */
@Composable
fun Segmented(
    value: String,
    options: List<Pair<String, String>>, // value to label
    onChange: (String) -> Unit,
) {
    val c = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(c.bgMuted)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (v, label) ->
            val on = v == value
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (on) c.bgSurface else Color.Transparent)
                    .clickable { onChange(v) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (on) c.accent else c.fgMuted,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** Card with border + surface used across detail / settings sections. */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    corner: Int = 16,
    content: @Composable () -> Unit,
) {
    val c = LocalAppColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner.dp))
            .background(c.bgSurface)
            .border(1.dp, c.border, RoundedCornerShape(corner.dp)),
    ) { content() }
}

private val secLabelStyle
    @Composable get() = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = LocalAppColors.current.fgSubtle,
    )

/** Section header label used in settings. */
@Composable
fun SectionLabel(text: String) {
    Text(text, style = secLabelStyle, modifier = Modifier.padding(start = 4.dp, bottom = 9.dp))
}
