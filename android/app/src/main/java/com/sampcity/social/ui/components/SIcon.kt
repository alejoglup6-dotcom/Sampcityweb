package com.sampcity.social.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sampcity.social.ui.theme.LocalDark

/** Colores de los iconos de la web (variables --ik, --if, --ig, --ic, --t, --iw de style.css). */
class IconPalette(val ik: Color, val `if`: Color, val ig: Color, val ic: Color, val t: Color, val iw: Color) {
    fun of(code: Int): Color = when (code) { 1 -> ik; 2 -> ig; 3 -> ic; 4 -> t; 5 -> `if`; 6 -> iw; else -> Color.Transparent }
}
private val DarkPal = IconPalette(Color(0xFFE8392F), Color(0x24E8392F), Color(0xFFB3A3A8), Color(0xFF1FB5D4), Color(0xFFF6F1F2), Color(0xFF181115))
private val LightPal = IconPalette(Color(0xFFE8392F), Color(0xFFFDEBEE), Color(0xFF6A5A60), Color(0xFF1FB5D4), Color(0xFF241A1E), Color(0xFFFFFFFF))
/** Como los iconos dentro de un botón rojo en la web: todo en un solo color (blanco, por ejemplo). */
private fun mono(c: Color) = IconPalette(c, c.copy(alpha = .22f), c.copy(alpha = .8f), c, c, Color.Transparent)

private class Built(val path: Path, val op: SOp)
private val cache = HashMap<String, List<Built>>()

private fun build(name: String): List<Built> = cache.getOrPut(name) {
    (SPRITE[name] ?: emptyList()).map { op ->
        val p = when (op.k) {
            'p' -> PathParser().parsePathString(op.d).toPath()
            'r' -> Path().apply { addRoundRect(RoundRect(Rect(op.n[0], op.n[1], op.n[0] + op.n[2], op.n[1] + op.n[3]), CornerRadius(op.n[4], op.n[5]))) }
            'c' -> Path().apply { addOval(Rect(op.n[0] - op.n[2], op.n[1] - op.n[2], op.n[0] + op.n[2], op.n[1] + op.n[2])) }
            else -> Path().apply { addOval(Rect(op.n[0] - op.n[2], op.n[1] - op.n[3], op.n[0] + op.n[2], op.n[1] + op.n[3])) }
        }
        Built(p, op)
    }
}

/**
 * Icono de la web de SampCity (sprite.js), dibujado igual: trazo rojo con relleno suave. `color` los pinta de un solo color (botones, barras oscuras).
 */
@Composable
fun SIcon(name: String, modifier: Modifier = Modifier, size: Dp = 24.dp, color: Color? = null) {
    val pal = if (color != null) remember(color) { mono(color) } else if (LocalDark.current) DarkPal else LightPal
    val ops = remember(name) { build(name) }
    Canvas(modifier.size(size)) {
        val s = this.size.width / 24f
        scale(s, s, Offset.Zero) {
            for (b in ops) {
                val o = b.op
                val draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit = {
                    if (o.fill != 0) drawPath(b.path, pal.of(o.fill))
                    if (o.stroke != 0) drawPath(b.path, pal.of(o.stroke), style = Stroke(o.sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                fun apply(i: Int) { if (i >= o.rot.size) draw() else { val r = o.rot[i]; rotate(r[0], Offset(r[1], r[2])) { apply(i + 1) } } }
                apply(0)
            }
        }
    }
}
