package com.saysharp.shared.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val Ink = Color(0xFF203A35)
internal val Paper = Color(0xFFF6F5F0)
internal val Forest = Color(0xFF285D4E)
internal val Muted = Color(0xFF69766F)
internal val Line = Color(0xFFDEE3DC)
internal val Sage = Color(0xFFE8EEE7)
internal val Clay = Color(0xFFB46646)

@Composable
internal fun SaySharpTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Forest, onPrimary = Color.White, primaryContainer = Sage, onPrimaryContainer = Ink,
            secondary = Muted, secondaryContainer = Sage, onSecondaryContainer = Ink,
            background = Paper, onBackground = Ink, surface = Color.White, onSurface = Ink,
            surfaceVariant = Sage, onSurfaceVariant = Muted, outline = Muted, outlineVariant = Line,
            error = Color(0xFFAC4138),
        ),
        typography = Typography(
            headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 34.sp, lineHeight = 39.sp, letterSpacing = (-0.7).sp),
            headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontSize = 29.sp, lineHeight = 35.sp, letterSpacing = (-0.4).sp),
            titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
            titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
            bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
            bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
            bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
            labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
            labelMedium = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.1.sp),
            labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
        ),
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp)),
        content = content,
    )
}

internal enum class Symbol { Home, Programme, Progress, Settings, Arrow, Check, Play, Stop, Mic, Close, Shield }

/** Small, consistent line icons drawn in shared Compose code. */
@Composable
internal fun Glyph(symbol: Symbol, tint: Color = Ink, modifier: Modifier = Modifier.size(24.dp)) {
    Canvas(modifier) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(1.7f, cap = StrokeCap.Round)
            fun line(x: Float, y: Float, x2: Float, y2: Float) = drawLine(tint, Offset(x, y), Offset(x2, y2), 1.7f, StrokeCap.Round)
            when (symbol) {
                Symbol.Home -> {
                    val path = Path().apply { moveTo(3f, 10f); lineTo(12f, 3f); lineTo(21f, 10f); moveTo(5f, 9f); lineTo(5f, 21f); lineTo(10f, 21f); lineTo(10f, 15f); lineTo(14f, 15f); lineTo(14f, 21f); lineTo(19f, 21f); lineTo(19f, 9f) }
                    drawPath(path, tint, style = stroke)
                }
                Symbol.Programme -> {
                    drawRoundRect(tint, Offset(4f, 3f), Size(16f, 18f), CornerRadius(2f), style = stroke)
                    line(8f, 8f, 16f, 8f); line(8f, 12f, 16f, 12f); line(8f, 16f, 13f, 16f)
                }
                Symbol.Progress -> { line(5f, 20f, 5f, 14f); line(12f, 20f, 12f, 9f); line(19f, 20f, 19f, 4f) }
                Symbol.Settings -> {
                    line(4f, 6f, 20f, 6f); line(4f, 12f, 20f, 12f); line(4f, 18f, 20f, 18f)
                    listOf(Offset(9f, 6f), Offset(16f, 12f), Offset(8f, 18f)).forEach { drawCircle(Paper, 2.5f, it); drawCircle(tint, 2.5f, it, style = stroke) }
                }
                Symbol.Arrow -> { line(4f, 12f, 20f, 12f); line(14f, 6f, 20f, 12f); line(14f, 18f, 20f, 12f) }
                Symbol.Check -> { line(5f, 12f, 10f, 17f); line(10f, 17f, 20f, 7f) }
                Symbol.Play -> drawPath(Path().apply { moveTo(8f, 4f); lineTo(20f, 12f); lineTo(8f, 20f); close() }, tint)
                Symbol.Stop -> drawRoundRect(tint, Offset(6f, 6f), Size(12f, 12f), CornerRadius(2f))
                Symbol.Mic -> {
                    drawRoundRect(tint, Offset(9f, 3f), Size(6f, 12f), CornerRadius(3f), style = stroke)
                    drawArc(tint, 0f, 180f, false, Offset(5f, 6f), Size(14f, 13f), style = stroke)
                    line(12f, 19f, 12f, 22f); line(8f, 22f, 16f, 22f)
                }
                Symbol.Close -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
                Symbol.Shield -> drawPath(Path().apply { moveTo(12f, 3f); lineTo(20f, 6f); lineTo(19f, 14f); quadraticTo(17f, 19f, 12f, 22f); quadraticTo(7f, 19f, 5f, 14f); lineTo(4f, 6f); close() }, tint, style = stroke)
            }
        }
    }
}

@Composable
internal fun Eyebrow(text: String, color: Color = Muted, modifier: Modifier = Modifier) = Text(text.uppercase(), modifier, style = MaterialTheme.typography.labelMedium, color = color)

@Composable
internal fun PrimaryAction(label: String, onClick: () -> Unit, enabled: Boolean = true, symbol: Symbol = Symbol.Arrow) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(12.dp))
        Glyph(symbol, if (enabled) Color.White else Muted, Modifier.size(20.dp))
    }
}

@Composable
internal fun Panel(modifier: Modifier = Modifier, color: Color = Color.White, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), color = color, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@Composable
internal fun ProgressTrack(completed: Int, total: Int = 14) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(total) { index -> Box(Modifier.weight(1f).height(5.dp).background(if (index < completed) Forest else Line, CircleShape)) }
    }
}

@Composable
internal fun QuietText(text: String, modifier: Modifier = Modifier) = Text(text, modifier, color = Muted, style = MaterialTheme.typography.bodyMedium)
