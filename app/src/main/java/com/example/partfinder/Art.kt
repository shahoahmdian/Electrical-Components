package com.example.partfinder

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

private val METAL = Color(0xFFB0B0B0)
private val BLACK = Color(0xFF222222)

enum class Kind { RES, POT, ELCAP, CERCAP, LED, DIODE, TO92, TO220, TO3, DIP, RELAY, XTAL, LDR, INDUCTOR, GENERIC }

fun kindOf(p: Part): Kind {
    val n = p.name
    return when {
        p.category == "مقاومت" -> if (n.contains("پتانسیومتر")) Kind.POT else Kind.RES
        p.category == "خازن" -> if (n.contains("الکترولیتی")) Kind.ELCAP else Kind.CERCAP
        p.category == "دیود نورانی" -> Kind.LED
        p.category == "دیود" -> Kind.DIODE
        n.contains("2N3055") -> Kind.TO3
        n.contains("TIP120") || p.category == "ماسفت" || n.contains("LM7805") || n.contains("LM317") -> Kind.TO220
        p.category == "ترانزیستور BJT" || n.contains("TL431") -> Kind.TO92
        p.category == "رله" -> Kind.RELAY
        p.category == "نوسان‌ساز" -> Kind.XTAL
        p.category == "سنسور" -> Kind.LDR
        p.category == "سلف" -> Kind.INDUCTOR
        p.category.startsWith("آی‌سی") || p.category == "میکروکنترلر" -> Kind.DIP
        else -> Kind.GENERIC
    }
}

@Composable
fun PartArt(p: Part, modifier: Modifier = Modifier) {
    if (p.imageUrl.isNotEmpty()) {
        AsyncImage(model = p.imageUrl, contentDescription = p.name, modifier = modifier, contentScale = ContentScale.Fit)
        return
    }
    val kind = kindOf(p)
    if (kind == Kind.RES) { ResistorArt(listOf(4, 7, 2, 10), modifier); return }
    Canvas(modifier) {
        when (kind) {
            Kind.POT -> pot(); Kind.ELCAP -> elcap(); Kind.CERCAP -> cercap(); Kind.LED -> led()
            Kind.DIODE -> diode(); Kind.TO92 -> to92(); Kind.TO220 -> to220(); Kind.TO3 -> to3()
            Kind.DIP -> dip(); Kind.RELAY -> relay(); Kind.XTAL -> xtal(); Kind.LDR -> ldr()
            Kind.INDUCTOR -> inductor(); else -> generic()
        }
    }
}

@Composable
fun ResistorArt(bands: List<Int>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val cy = size.height / 2
        val bw = size.width * 0.55f
        val bh = size.height * 0.5f
        val x0 = (size.width - bw) / 2
        drawLine(METAL, Offset(0f, cy), Offset(size.width, cy), strokeWidth = 8f)
        drawRoundRect(Color(0xFFE6CFA0), Offset(x0, cy - bh / 2), Size(bw, bh), CornerRadius(bh / 2))
        bands.forEachIndexed { i, ci ->
            val gap = if (i == bands.lastIndex) 0.06f else 0f
            val f = 0.16f + 0.13f * i + gap
            drawRect(RC[ci].color, Offset(x0 + bw * f, cy - bh / 2 + 3f), Size(bw * 0.075f, bh - 6f))
        }
    }
}

private fun DrawScope.leads(xs: List<Float>, y0: Float, y1: Float) =
    xs.forEach { drawLine(METAL, Offset(it, y0), Offset(it, y1), strokeWidth = 6f) }

private fun DrawScope.generic() {
    drawRoundRect(Color(0xFF888888), Offset(size.width * .3f, size.height * .2f), Size(size.width * .4f, size.height * .6f), CornerRadius(12f))
}

private fun DrawScope.pot() {
    val c = Offset(size.width / 2, size.height * .4f); val r = size.height * .32f
    leads(listOf(c.x - r * .6f, c.x, c.x + r * .6f), c.y, size.height * .95f)
    drawCircle(Color(0xFF3A6EA5), r, c); drawCircle(Color(0xFFDDDDDD), r * .55f, c)
    drawLine(BLACK, c, Offset(c.x + r * .4f, c.y - r * .4f), strokeWidth = 6f)
}

private fun DrawScope.elcap() {
    val w = size.width * .28f; val x = (size.width - w) / 2; val top = size.height * .1f; val h = size.height * .65f
    leads(listOf(size.width / 2 - w * .25f, size.width / 2 + w * .25f), top + h, size.height * .95f)
    drawRoundRect(Color(0xFF1B3A6B), Offset(x, top), Size(w, h), CornerRadius(10f))
    drawRect(Color(0xFFCCCCCC), Offset(x, top + 6f), Size(w * .22f, h - 12f))
    drawRect(METAL, Offset(x, top), Size(w, 8f))
}

private fun DrawScope.cercap() {
    val c = Offset(size.width / 2, size.height * .38f); val r = size.height * .3f
    leads(listOf(c.x - r * .4f, c.x + r * .4f), c.y, size.height * .95f)
    drawCircle(Color(0xFFE08A2E), r, c)
}

private fun DrawScope.led() {
    val cx = size.width / 2; val r = size.height * .22f; val cy = size.height * .35f
    drawLine(METAL, Offset(cx - r * .5f, cy), Offset(cx - r * .5f, size.height * .97f), strokeWidth = 6f)
    drawLine(METAL, Offset(cx + r * .5f, cy), Offset(cx + r * .5f, size.height * .8f), strokeWidth = 6f)
    drawRect(Color(0xCCE53935), Offset(cx - r, cy), Size(r * 2, r * .9f))
    drawArc(Color(0xCCE53935), 180f, 180f, true, Offset(cx - r, cy - r), Size(r * 2, r * 2))
    drawRect(Color(0xFFB71C1C), Offset(cx - r * 1.15f, cy + r * .9f), Size(r * 2.3f, 6f))
}

private fun DrawScope.diode() {
    val cy = size.height / 2; val bw = size.width * .28f; val bh = size.height * .28f; val x0 = (size.width - bw) / 2
    drawLine(METAL, Offset(0f, cy), Offset(size.width, cy), strokeWidth = 6f)
    drawRoundRect(BLACK, Offset(x0, cy - bh / 2), Size(bw, bh), CornerRadius(8f))
    drawRect(Color(0xFFDDDDDD), Offset(x0 + bw * .78f, cy - bh / 2), Size(bw * .1f, bh))
}

private fun DrawScope.to92() {
    val cx = size.width / 2; val r = size.height * .28f; val cy = size.height * .4f
    leads(listOf(cx - r * .6f, cx, cx + r * .6f), cy, size.height * .97f)
    drawArc(BLACK, 180f, 180f, true, Offset(cx - r, cy - r), Size(r * 2, r * 2))
    drawRect(BLACK, Offset(cx - r, cy), Size(r * 2, r * .5f))
}

private fun DrawScope.to220() {
    val cx = size.width / 2; val w = size.width * .26f; val x = cx - w / 2
    leads(listOf(x + w * .2f, cx, x + w * .8f), size.height * .68f, size.height * .97f)
    drawRect(METAL, Offset(x, size.height * .05f), Size(w, size.height * .22f))
    drawCircle(Color(0xFFF2F2F2), size.height * .06f, Offset(cx, size.height * .14f))
    drawRect(BLACK, Offset(x, size.height * .27f), Size(w, size.height * .41f))
}

private fun DrawScope.to3() {
    val c = Offset(size.width / 2, size.height / 2); val r = size.height * .38f
    drawCircle(METAL, r, c); drawCircle(Color(0xFF8A8A8A), r * .62f, c)
    drawCircle(BLACK, r * .1f, Offset(c.x - r * .3f, c.y)); drawCircle(BLACK, r * .1f, Offset(c.x + r * .3f, c.y))
    drawCircle(Color(0xFFF2F2F2), r * .12f, Offset(c.x - r * .95f, c.y)); drawCircle(Color(0xFFF2F2F2), r * .12f, Offset(c.x + r * .95f, c.y))
}

private fun DrawScope.dip() {
    val bw = size.width * .5f; val bh = size.height * .4f; val x0 = (size.width - bw) / 2; val y0 = (size.height - bh) / 2
    val n = 7; val step = bw / n
    for (i in 0 until n) {
        val px = x0 + step * (i + .5f)
        drawRect(METAL, Offset(px - 4f, y0 - size.height * .12f), Size(8f, size.height * .12f))
        drawRect(METAL, Offset(px - 4f, y0 + bh), Size(8f, size.height * .12f))
    }
    drawRoundRect(BLACK, Offset(x0, y0), Size(bw, bh), CornerRadius(6f))
    drawArc(Color(0xFF555555), 270f, 180f, true, Offset(x0 - bh * .12f, y0 + bh * .38f), Size(bh * .24f, bh * .24f))
}

private fun DrawScope.relay() {
    val bw = size.width * .34f; val bh = size.height * .5f; val x0 = (size.width - bw) / 2; val y0 = size.height * .15f
    for (i in 0..4) drawRect(METAL, Offset(x0 + bw * (i + .5f) / 5f - 3f, y0 + bh), Size(6f, size.height * .2f))
    drawRoundRect(Color(0xFF1E5AA8), Offset(x0, y0), Size(bw, bh), CornerRadius(8f))
    drawRect(Color(0xFFDDDDDD), Offset(x0 + bw * .15f, y0 + bh * .3f), Size(bw * .7f, 5f))
    drawRect(Color(0xFFDDDDDD), Offset(x0 + bw * .15f, y0 + bh * .55f), Size(bw * .5f, 5f))
}

private fun DrawScope.xtal() {
    val cx = size.width / 2; val w = size.width * .22f; val h = size.height * .45f; val y0 = size.height * .12f
    leads(listOf(cx - w * .3f, cx + w * .3f), y0 + h, size.height * .95f)
    drawRoundRect(METAL, Offset(cx - w / 2, y0), Size(w, h), CornerRadius(w / 2))
    drawRoundRect(Color(0xFFE0E0E0), Offset(cx - w / 2 + 6f, y0 + 6f), Size(w - 12f, h * .35f), CornerRadius(10f))
}

private fun DrawScope.ldr() {
    val c = Offset(size.width / 2, size.height * .38f); val r = size.height * .3f
    leads(listOf(c.x - r * .4f, c.x + r * .4f), c.y, size.height * .95f)
    drawCircle(Color(0xFFC79A4B), r, c)
    val path = Path().apply {
        moveTo(c.x - r * .6f, c.y - r * .5f)
        for (i in 1..5) lineTo(c.x + (if (i % 2 == 1) r * .6f else -r * .6f), c.y - r * .5f + i * r * .2f)
    }
    drawPath(path, Color(0xFF6D4C1F), style = Stroke(width = 4f))
}

private fun DrawScope.inductor() {
    val cy = size.height * .55f; val n = 5; val w = size.width * .5f; val x0 = (size.width - w) / 2; val step = w / n
    drawLine(METAL, Offset(0f, cy), Offset(x0, cy), strokeWidth = 6f)
    drawLine(METAL, Offset(x0 + w, cy), Offset(size.width, cy), strokeWidth = 6f)
    for (i in 0 until n) drawArc(Color(0xFFB8621B), 180f, 180f, false, Offset(x0 + step * i, cy - step / 2), Size(step, step), style = Stroke(width = 8f))
}
