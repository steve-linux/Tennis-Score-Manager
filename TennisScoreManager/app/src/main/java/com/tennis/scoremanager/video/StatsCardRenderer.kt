// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.video

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.tennis.scoremanager.model.Side

/**
 * Una schermata delle statistiche come nelle dirette dei tornei: in alto titolo, risultato e torneo; ai lati i
 * giocatori (foto o avatar con le iniziali) con la percentuale di punti vinti; al centro le righe, con il valore di
 * chi è avanti in giallo e la sua metà della linea accesa. Stile nostro, senza loghi. Disegnata su 1920×1080 e
 * adattata alla misura del video (con bande scure se il video non è 16:9).
 */
object StatsCardRenderer {
    private const val W = 1920f
    private const val H = 1080f
    private val BG = Color.rgb(9, 15, 30)
    private val BAND = Color.rgb(28, 58, 180)
    private val BAND_DARK = Color.rgb(20, 40, 130)
    private val PANEL = Color.argb(230, 14, 22, 42)
    private val LINE = Color.rgb(60, 70, 92)
    private val TEXT = Color.WHITE
    private val DIM = Color.rgb(170, 182, 200)
    private val YELLOW = Color.rgb(0xE8, 0xF5, 0x4A)
    private val P1 = Color.rgb(0xFF, 0xD6, 0x00)
    private val P2 = Color.rgb(0xE5, 0x39, 0x35)

    private fun paint(size: Float, color: Int = TEXT, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
            textAlign = align
        }

    /**
     * [photos]: le foto dei giocatori di ciascun lato, nello stesso ordine dei nomi (null = avatar con le iniziali).
     * [pointsWon]: la scritta sotto la percentuale nei pannelli ("PUNTI VINTI").
     * [credit] e [site]: in fondo "Creato con Tennis Score Manager · indirizzo del sito", come nell'immagine social.
     */
    fun render(
        card: HighlightStats.Card, page: List<HighlightStats.Section>, width: Int, height: Int, photos: Map<Side, List<Bitmap?>>, pointsWon: String,
        avatars: Avatars? = null, credit: String = "", site: String = "",
    ): Bitmap {
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(BG)
        val scale = minOf(width / W, height / H)
        c.translate((width - W * scale) / 2, (height - H * scale) / 2)
        c.scale(scale, scale)
        header(c, card)
        for (side in Side.entries) player(c, card, side, photos[side].orEmpty(), pointsWon, avatars)
        rows(c, page)
        footer(c, credit, site)
        return out
    }

    private fun header(c: Canvas, card: HighlightStats.Card) {
        val r = RectF(60f, 40f, W - 60f, 190f)
        c.drawRoundRect(r, 18f, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(r.left, 0f, r.right, 0f, BAND, BAND_DARK, Shader.TileMode.CLAMP)
        })
        c.drawText(card.title.uppercase(), 100f, 108f, paint(50f, bold = true))
        c.drawText(card.subtitle.uppercase(), 100f, 158f, paint(28f, DIM, bold = true))
        // Al centro i due lati col punteggio set per set; chi ha vinto in grassetto.
        val nameP = paint(36f, bold = true, align = Paint.Align.RIGHT)
        for ((i, side) in Side.entries.withIndex()) {
            val y = 103f + i * 52f
            val won = card.winner == side
            c.drawText(fit(card.sideNames[side].orEmpty().uppercase(), nameP, 380f), 1060f, y, nameP.apply { color = if (won) TEXT else DIM })
            card.sets[side].orEmpty().forEachIndexed { k, g ->
                val other = card.sets[side.other]?.getOrNull(k)?.toIntOrNull() ?: 0
                val mine = g.toIntOrNull() ?: 0
                c.drawText(g, 1110f + k * 56f, y, paint(38f, if (mine > other) YELLOW else TEXT, bold = true, align = Paint.Align.CENTER))
            }
        }
        // A destra il torneo (o circolo e campo).
        card.eventLines.take(2).forEachIndexed { i, line ->
            val p = paint(if (i == 0) 34f else 26f, if (i == 0) TEXT else DIM, bold = i == 0, align = Paint.Align.RIGHT)
            c.drawText(fit(line.uppercase(), p, 440f), W - 100f, if (i == 0) 108f else 154f, p)
        }
    }

    /** Ritratti stilizzati di un uomo e di una donna, per chi non ha la foto (dalle risorse dell'app). */
    class Avatars(val man: Bitmap, val woman: Bitmap)

    private fun player(c: Canvas, card: HighlightStats.Card, side: Side, photos: List<Bitmap?>, pointsWon: String, avatars: Avatars?) {
        val left = side == Side.P1
        val x0 = if (left) 60f else W - 520f
        val r = RectF(x0, 230f, x0 + 460f, 1040f)
        c.drawRoundRect(r, 18f, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PANEL })
        val accent = if (side == Side.P1) P1 else P2
        val p = card.players[side] ?: return
        val n = p.names.size.coerceAtLeast(1)
        // Foto o avatar: uno grande nel singolare, due affiancati nel doppio.
        val size = if (n == 1) 320f else 190f
        val gap = 24f
        val total = n * size + (n - 1) * gap
        var x = r.centerX() - total / 2
        for (i in 0 until n) {
            avatar(c, photos.getOrNull(i), p.female.getOrNull(i), accent, x, 270f, size, avatars)
            x += size + gap
        }
        // Nomi e classifiche.
        var y = 270f + size + 70f
        p.names.forEachIndexed { i, name ->
            val np = paint(if (n == 1) 52f else 40f, TEXT, bold = true, align = Paint.Align.CENTER)
            c.drawText(fit(name.uppercase(), np, 420f), r.centerX(), y, np)
            p.ranks.getOrNull(i)?.takeIf { it.isNotEmpty() }?.let { rank ->
                c.drawText(rank, r.centerX(), y + 40f, paint(30f, accent, bold = true, align = Paint.Align.CENTER))
                y += 40f
            }
            y += if (n == 1) 70f else 56f
        }
        // In fondo la percentuale di punti vinti, grande: un dato vero al posto del "rating" delle dirette.
        c.drawText(pointsWon.uppercase(), r.centerX(), 900f, paint(28f, DIM, bold = true, align = Paint.Align.CENTER))
        c.drawText("${p.pointsWonPct}%", r.centerX(), 1005f, paint(110f, if (card.winner == side) YELLOW else TEXT, bold = true, align = Paint.Align.CENTER))
        c.drawRect(RectF(r.left, r.bottom - 8f, r.right, r.bottom), Paint().apply { color = accent })
    }

    /**
     * Foto rotonda; senza foto il ritratto dell'uomo o della donna (immagini dell'autore, nelle risorse dell'app),
     * dell'uomo se il sesso non è detto (la sagoma neutra resta solo se mancano i ritratti).
     */
    private fun avatar(c: Canvas, photo: Bitmap?, female: Boolean?, accent: Int, x: Float, y: Float, size: Float, avatars: Avatars?) {
        val rect = RectF(x, y, x + size, y + size)
        val portrait = when (female) {
            true -> avatars?.woman
            false, null -> avatars?.man
        }
        if (photo == null && portrait != null) {
            c.drawBitmap(portrait, null, rect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            // Il ritratto è grigio: un anello appena fuori dice di che giocatore è.
            c.drawOval(RectF(rect.left - 7f, rect.top - 7f, rect.right + 7f, rect.bottom + 7f),
                Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f; color = accent })
            return
        }
        if (photo != null) {
            val shader = BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val s = maxOf(size / photo.width, size / photo.height)
            shader.setLocalMatrix(Matrix().apply {
                setScale(s, s)
                postTranslate(x + (size - photo.width * s) / 2, y + (size - photo.height * s) / 2)
            })
            c.drawOval(rect, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader })
        } else {
            c.drawOval(rect, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, Color.red(accent), Color.green(accent), Color.blue(accent)) })
            c.save()
            c.clipPath(Path().apply { addOval(rect, Path.Direction.CW) })
            silhouette(c, accent, rect)
            c.restore()
        }
        c.drawOval(rect, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f; color = accent })
    }

    /** Sagoma neutra nel cerchio [r] (sesso non detto): testa e spalle nel colore del giocatore. */
    private fun silhouette(c: Canvas, accent: Int, r: RectF) {
        val s = r.width()
        val cx = r.centerX()
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
        val headR = s * 0.17f
        val headY = r.top + s * 0.40f
        c.drawOval(RectF(cx - s * 0.36f, r.top + s * 0.67f, cx + s * 0.36f, r.top + s * 1.25f), body)
        c.drawRect(RectF(cx - headR * 0.42f, headY + headR * 0.6f, cx + headR * 0.42f, r.top + s * 0.72f), body)
        c.drawCircle(cx, headY, headR, body)
    }

    private fun rows(c: Canvas, page: List<HighlightStats.Section>) {
        val left = 580f
        val right = W - 580f
        val mid = (left + right) / 2
        val rowCount = page.sumOf { it.rows.size }
        val rowH = ((810f - page.size * 70f) / rowCount.coerceAtLeast(1)).coerceAtMost(84f)
        var y = 230f
        for (sec in page) {
            c.drawRect(RectF(left - 40f, y, right + 40f, y + 46f), Paint().apply { color = BAND })
            c.drawText(sec.title.uppercase(), mid, y + 33f, paint(26f, bold = true, align = Paint.Align.CENTER))
            y += 70f
            for (row in sec.rows) {
                val base = y + rowH * 0.55f
                val valueP = { s: Side -> paint(44f, if (row.best == s) YELLOW else DIM, bold = true, align = if (s == Side.P1) Paint.Align.LEFT else Paint.Align.RIGHT) }
                c.drawText(row.a, left, base, valueP(Side.P1))
                c.drawText(row.b, right, base, valueP(Side.P2))
                val lp = paint(26f, TEXT, bold = true, align = Paint.Align.CENTER)
                c.drawText(fit(row.label.uppercase(), lp, right - left - 260f), mid, base - 6f, lp)
                // Linea sotto la riga: accesa dal lato di chi è avanti.
                val ly = y + rowH * 0.78f
                c.drawRect(RectF(left, ly, right, ly + 4f), Paint().apply { color = LINE })
                row.best?.let { s ->
                    val half = if (s == Side.P1) RectF(left, ly, mid, ly + 4f) else RectF(mid, ly, right, ly + 4f)
                    c.drawRect(half, Paint().apply {
                        shader = if (s == Side.P1) LinearGradient(left, 0f, mid, 0f, YELLOW, Color.TRANSPARENT, Shader.TileMode.CLAMP)
                        else LinearGradient(mid, 0f, right, 0f, Color.TRANSPARENT, YELLOW, Shader.TileMode.CLAMP)
                    })
                }
                y += rowH
            }
            y += 6f
        }
    }

    private fun footer(c: Canvas, credit: String, site: String) {
        if (credit.isEmpty() && site.isEmpty()) return
        val lead = if (credit.isNotEmpty() && site.isNotEmpty()) "$credit · " else credit
        val cp = paint(26f, DIM)
        val sp = paint(26f, YELLOW, bold = true)
        val x = (W - cp.measureText(lead) - sp.measureText(site)) / 2
        c.drawText(lead, x, 1072f, cp)
        c.drawText(site, x + cp.measureText(lead), 1072f, sp)
    }

    /** Accorcia [text] con "…" finché sta in [max] punti. */
    private fun fit(text: String, p: Paint, max: Float): String {
        if (p.measureText(text) <= max) return text
        var t = text
        while (t.length > 1 && p.measureText("$t…") > max) t = t.dropLast(1)
        return "$t…"
    }
}
