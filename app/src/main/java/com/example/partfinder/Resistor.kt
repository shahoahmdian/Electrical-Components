package com.example.partfinder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

data class RCol(val name: String, val color: Color)

val RC = listOf(
    RCol("مشکی", Color(0xFF000000)), RCol("قهوه‌ای", Color(0xFF7B3F00)), RCol("قرمز", Color(0xFFE53935)),
    RCol("نارنجی", Color(0xFFFB8C00)), RCol("زرد", Color(0xFFFDD835)), RCol("سبز", Color(0xFF43A047)),
    RCol("آبی", Color(0xFF1E88E5)), RCol("بنفش", Color(0xFF8E24AA)), RCol("خاکستری", Color(0xFF9E9E9E)),
    RCol("سفید", Color(0xFFFFFFFF)), RCol("طلایی", Color(0xFFCFA93B)), RCol("نقره‌ای", Color(0xFFC0C0C0))
)

// tolerance (%) by color index
val TOL = mapOf(1 to 1.0, 2 to 2.0, 5 to 0.5, 6 to 0.25, 7 to 0.1, 8 to 0.05, 10 to 5.0, 11 to 10.0)

fun fmtNum(x: Double): String = BigDecimal(x).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

fun fmtOhm(v: Double): String = when {
    v >= 1e9 -> fmtNum(v / 1e9) + " GΩ"
    v >= 1e6 -> fmtNum(v / 1e6) + " MΩ"
    v >= 1e3 -> fmtNum(v / 1e3) + " kΩ"
    else -> fmtNum(v) + " Ω"
}

/** Parses 4.7k, 4k7, 220, 1M, 0.47, 10R ... into ohms. */
fun parseOhm(s: String): Double? {
    val t = s.trim().replace("Ω", "").replace(" ", "").replace(",", ".")
    if (t.isEmpty()) return null
    fun m(c: Char) = when (c.lowercaseChar()) { 'k' -> 1e3; 'm' -> 1e6; 'g' -> 1e9; 'r' -> 1.0; else -> null }
    Regex("^(\\d+)([kKmMgGrR])(\\d*)$").find(t)?.let {
        val mul = m(it.groupValues[2][0]) ?: return null
        return (it.groupValues[1] + "." + it.groupValues[3].ifEmpty { "0" }).toDouble() * mul
    }
    Regex("^(\\d*\\.?\\d+)([kKmMgGrR]?)$").find(t)?.let {
        val mul = if (it.groupValues[2].isEmpty()) 1.0 else (m(it.groupValues[2][0]) ?: return null)
        return it.groupValues[1].toDouble() * mul
    }
    return null
}

/** Returns color indices: digits..., multiplier, tolerance (gold). Null if not representable. */
fun bandsFor(v: Double): List<Int>? {
    if (v <= 0) return null
    for (digits in 2..3) {
        val lo = if (digits == 2) 10 else 100
        val hi = if (digits == 2) 99 else 999
        for (m in -2..9) {
            val s = v / 10.0.pow(m)
            val r = s.roundToInt()
            if (r in lo..hi && abs(s - r) < 1e-4) {
                val ds = r.toString().map { it - '0' }
                val mi = if (m >= 0) m else if (m == -1) 10 else 11
                return ds + mi + 10
            }
        }
    }
    return null
}

@Composable
fun ColorRow(label: String, options: List<Int>, selected: Int, onSel: (Int) -> Unit) {
    Text("$label: ${RC[selected].name}", style = MaterialTheme.typography.labelMedium)
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { i ->
            val sel = i == selected
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(RC[i].color)
                    .border(if (sel) 3.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else Color.Gray, CircleShape)
                    .clickable { onSel(i) }
            )
        }
    }
}

@Composable
fun ResistorScreen() {
    var count by remember { mutableStateOf(4) }
    val d = remember { mutableStateListOf(4, 7, 0) }
    var mult by remember { mutableStateOf(2) }
    var tol by remember { mutableStateOf(10) }
    var text by remember { mutableStateOf("") }

    val n = if (count == 4) 2 else 3
    var num = 0
    for (i in 0 until n) num = num * 10 + d[i]
    val exp = if (mult <= 9) mult else if (mult == 10) -1 else -2
    val ohm = num * 10.0.pow(exp)
    val bands = d.take(n) + mult + tol

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("رنگ ← مقدار مقاومت", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = count == 4, onClick = { count = 4 }, label = { Text("4 نواره") })
            FilterChip(selected = count == 5, onClick = { count = 5 }, label = { Text("5 نواره") })
        }
        ResistorArt(bands, Modifier.fillMaxWidth().height(110.dp))
        Text("${fmtOhm(ohm)}  ±${TOL[tol]}%", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        for (i in 0 until n) ColorRow("رقم ${i + 1}", (0..9).toList(), d[i]) { d[i] = it }
        ColorRow("ضریب", (0..11).toList(), mult) { mult = it }
        ColorRow("تلرانس", TOL.keys.toList().sorted(), tol) { tol = it }

        HorizontalDivider(Modifier.padding(vertical = 16.dp))
        Text("مقدار ← رنگ", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            label = { Text("مثلاً 4.7k ، 4k7 ، 220 ، 1M ، 0.47") }
        )
        val v = parseOhm(text)
        val b = v?.let { bandsFor(it) }
        if (text.isNotBlank()) {
            if (v == null) Text("مقدار نامعتبر است")
            else if (b == null) Text("این مقدار با ۳ رقم معنی‌دار قابل نمایش با نوار رنگی نیست.")
            else {
                ResistorArt(b, Modifier.fillMaxWidth().height(110.dp))
                Text(fmtOhm(v) + " ← " + b.joinToString(" - ") { RC[it].name } + "  (تلرانس طلایی 5%)")
            }
        }
    }
}
