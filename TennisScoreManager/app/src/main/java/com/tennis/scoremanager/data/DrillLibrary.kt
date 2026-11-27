// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * La raccolta di esercizi illustrati: esercizi scritti per l'app (testi e disegni originali, nessuno copiato da libri,
 * manuali o video). Qui c'è quello che non dipende dalla lingua (argomento, livello, giocatori, durata, il disegno del
 * campo e, se c'è, l'esercizio guidato da aggiungere ai propri); i testi sono in assets/drills/<lingua>.json, per ora
 * solo it.json (scelta dell'autore: nelle altre lingue l'app mostra quelli italiani).
 */
object DrillLibrary {

    enum class Topic { SERVE, RETURN, FOREHAND, BACKHAND, VOLLEY, OVERHEAD, FOOTWORK, TACTICS, FITNESS }

    enum class Level { BEGINNER, INTERMEDIATE, ADVANCED }

    /**
     * Un punto del campo in metri, con l'origine al centro della rete: x verso destra (il campo largo 10,97 m va da
     * -5,485 a 5,485), y verso chi guarda (la metà vicina, quella di A, ha y positivo; le linee di fondo sono a ±11,885).
     */
    data class Pt(val x: Float, val y: Float)

    /**
     * Chi è in campo: [label] "A", "B"... ; [feeder] = l'allenatore che lancia le palle (col cesto); [backhand] = colpisce
     * di rovescio (la racchetta nel disegno sta dall'altra parte).
     */
    data class Who(val at: Pt, val label: String, val feeder: Boolean = false, val backhand: Boolean = false, val frame: Int? = null)

    /**
     * Una traiettoria: la palla (piena, nel colore e col numero [order] se l'ordine conta) o uno spostamento
     * (tratteggiata). [arc]: quanto sale la palla in metri (null = come un colpo normale; alto per i pallonetti).
     */
    data class Path(val points: List<Pt>, val move: Boolean = false, val order: Int? = null, val arc: Float? = null, val frame: Int? = null)

    /**
     * Una zona da [a] a [b]: un bersaglio ovale (con dentro [label], di solito il numero del colpo) o, con [area], una
     * parte di campo rettangolare (dove deve stare la palla).
     */
    data class Zone(val a: Pt, val b: Pt, val label: String = "", val area: Boolean = false, val frame: Int? = null)

    data class Diagram(
        val players: List<Who>,
        val paths: List<Path> = emptyList(),
        val zones: List<Zone> = emptyList(),
        val cones: List<Pt> = emptyList(),
        /** Dove tornare dopo il colpo: riquadri tratteggiati. */
        val recover: List<Zone> = emptyList(),
        /** Solo la metà vicina del campo (esercizi senza avversario): il disegno è più grande. */
        val half: Boolean = false,
    ) {
        /**
         * Quanti riquadri: gli esercizi con tanti colpi si disegnano in più riquadri (colpi 1-2, poi 3-4). Persone,
         * traiettorie e zone con [Who.frame] (e simili) stanno solo nel loro riquadro; senza, in tutti.
         */
        val frames: Int get() = (players.mapNotNull { it.frame } + paths.mapNotNull { it.frame } + zones.mapNotNull { it.frame } + recover.mapNotNull { it.frame }).maxOrNull() ?: 1

        /** Il riquadro [n] (da 1): solo quello che ci sta dentro. */
        fun frame(n: Int): Diagram = copy(
            players = players.filter { it.frame == null || it.frame == n },
            paths = paths.filter { it.frame == null || it.frame == n },
            zones = zones.filter { it.frame == null || it.frame == n },
            recover = recover.filter { it.frame == null || it.frame == n },
        )

        /** I numeri dei colpi di questo disegno, in ordine (per la scritta "Colpi 1-2"). */
        val orders: List<Int> get() = paths.mapNotNull { it.order }.distinct().sorted()
    }

    data class Item(
        val id: String,
        val topic: Topic,
        val level: Level,
        /** Quanti giocatori servono (l'allenatore che lancia non conta). */
        val players: Int,
        val minutes: Int,
        val diagram: Diagram,
        /** L'esercizio guidato da aggiungere ai propri (colpo e passi), per gli esercizi di un colpo solo. */
        val guided: Pair<TrainedStroke, List<DrillStep>>? = null,
    ) {
        /** Gioco di gambe e preparazione fisica: con l'avviso di adattarli a chi si allena. */
        val physical: Boolean get() = topic == Topic.FOOTWORK || topic == Topic.FITNESS
    }

    /** I testi di un esercizio in una lingua. */
    @Serializable
    data class Text(
        val name: String, val goal: String, val steps: List<String>, val easier: String, val harder: String,
        /** Per la voce durante l'allenamento: la frase chiave detta all'inizio. */
        val cue: String = "",
        /** Come fare ogni passo dell'esercizio guidato, uno per passo, detto quando il passo comincia. */
        val stepCues: List<String> = emptyList(),
        /** Il consiglio dopo tre colpi fuori di fila. */
        val missTip: String = "",
        /** La proposta dopo cinque colpi dentro di fila. */
        val streakTip: String = "",
    )

    private val json = Json { ignoreUnknownKeys = true }

    /** I testi di una lingua dal file della raccolta (id → testi). */
    fun texts(file: String): Map<String, Text> = json.decodeFromString(file)

    /** L'esercizio della raccolta da cui viene un esercizio guidato aggiunto ("lib-<id>-<ora>"), null se è uno dei propri. */
    fun itemIdOf(drillId: String): String? = drillId.takeIf { it.startsWith("lib-") }?.removePrefix("lib-")?.substringBeforeLast("-")

    /**
     * La voce durante un esercizio della raccolta: all'inizio nome, obiettivo e frase chiave; a ogni passo come farlo;
     * dopo [MISSES] colpi fuori di fila un consiglio, dopo [STREAK] dentro di fila una proposta più difficile (una volta
     * per passo). I conti dei colpi li dice già l'allenamento; qui solo le frasi in più.
     */
    class Coach(private val text: Text) {
        private var misses = 0
        private var ins = 0
        private var streakSaid = false

        /** "Volée di dritto e di rovescio. Volée ferme e precise… Piatto della racchetta stabile." */
        fun intro(): String = listOf(text.name, text.goal, text.cue).filter { it.isNotBlank() }.joinToString(" ") { it.trimEnd('.') + "." }

        /** Come fare il passo [i] (da 0), null se non c'è la frase. Ricomincia i conti delle serie. */
        fun step(i: Int): String? {
            misses = 0; ins = 0; streakSaid = false
            return text.stepCues.getOrNull(i)?.takeIf { it.isNotBlank() }
        }

        /** Dopo un colpo segnato: il consiglio o la proposta da dire, se è il momento. */
        fun shot(inPlay: Boolean): String? {
            if (inPlay) { ins++; misses = 0 } else { misses++; ins = 0 }
            if (misses >= MISSES) { misses = 0; return text.missTip.takeIf { it.isNotBlank() } }
            if (ins >= STREAK && !streakSaid) { streakSaid = true; return text.streakTip.takeIf { it.isNotBlank() } }
            return null
        }

        /** Tolto un colpo: le serie ripartono da zero (non si sa più com'erano). */
        fun undo() { misses = 0; ins = 0 }

        companion object {
            const val MISSES = 3
            const val STREAK = 5
        }
    }

    /** Gli esercizi che passano i filtri (null = tutti). */
    fun filter(topic: Topic?, level: Level?, players: Int?): List<Item> =
        ITEMS.filter { (topic == null || it.topic == topic) && (level == null || it.level == level) && (players == null || it.players == players) }

    /** L'esercizio guidato da aggiungere ai propri, col nome della raccolta. */
    fun toDrill(item: Item, name: String, now: Long): Drill? = item.guided?.let { (stroke, steps) ->
        Drill("lib-${item.id}-$now", name, stroke, steps, createdAt = now)
    }

    // ------------------------------------------------------------------ il campo

    private const val BASE = 11.885f
    private const val SERVICE = 6.40f
    private const val SINGLES = 4.115f

    private fun p(x: Float, y: Float) = Pt(x, y)
    private fun ball(vararg pts: Pt, order: Int? = null, arc: Float? = null, frame: Int? = null) = Path(pts.toList(), order = order, arc = arc, frame = frame)
    private fun move(vararg pts: Pt, frame: Int? = null) = Path(pts.toList(), move = true, frame = frame)

    /** Un bersaglio ovale largo [w] e lungo [h] metri attorno a ([x], [y]), col numero del colpo. */
    private fun target(x: Float, y: Float, label: String = "", w: Float = 2.4f, h: Float = 2.4f, frame: Int? = null) = Zone(p(x - w / 2, y - h / 2), p(x + w / 2, y + h / 2), label, frame = frame)
    private fun area(x1: Float, y1: Float, x2: Float, y2: Float, frame: Int? = null) = Zone(p(x1, y1), p(x2, y2), area = true, frame = frame)
    private fun recover(x: Float, y: Float) = Zone(p(x - 0.8f, y - 0.7f), p(x + 0.8f, y + 0.7f))

    val ITEMS: List<Item> = listOf(
        // Palleggio incrociato di dritto: la diagonale del pari, dritto contro dritto tra due destri. Dopo il colpo non si
        // torna al centro ma un po' verso il lato del vantaggio, a metà degli angoli possibili.
        Item(
            "fh_cross", Topic.FOREHAND, Level.INTERMEDIATE, 2, 15,
            Diagram(
                listOf(Who(p(3f, 12.4f), "A"), Who(p(-3f, -12.4f), "B")),
                paths = listOf(ball(p(3f, 12.4f), p(-2.6f, -9.3f), order = 1), ball(p(-3f, -12.4f), p(2.6f, 8.8f), order = 2)),
                zones = listOf(target(-2.6f, -9.3f, "1"), target(2.6f, 8.8f, "2")),
                recover = listOf(recover(1.4f, 12.6f)),
            ),
            guided = TrainedStroke.FOREHAND to listOf(DrillStep(20, direction = ShotDirection.CROSS)),
        ),
        Item(
            "bh_cross", Topic.BACKHAND, Level.INTERMEDIATE, 2, 15,
            Diagram(
                listOf(Who(p(-3f, 12.4f), "A", backhand = true), Who(p(3f, -12.4f), "B", backhand = true)),
                paths = listOf(ball(p(-3f, 12.4f), p(2.6f, -9.3f), order = 1), ball(p(3f, -12.4f), p(-2.6f, 8.8f), order = 2)),
                zones = listOf(target(2.6f, -9.3f, "1"), target(-2.6f, 8.8f, "2")),
                recover = listOf(recover(-1.4f, 12.6f)),
            ),
            guided = TrainedStroke.BACKHAND to listOf(DrillStep(20, direction = ShotDirection.CROSS)),
        ),
        // A gioca sempre lungolinea, B sempre incrociato: la palla disegna un otto.
        Item(
            "line_cross", Topic.FOREHAND, Level.INTERMEDIATE, 2, 15,
            Diagram(
                listOf(Who(p(3f, 12.4f), "A"), Who(p(3f, -12.4f), "B", backhand = true)),
                paths = listOf(
                    ball(p(3f, 12.4f), p(3f, -9.5f), order = 1),
                    ball(p(3f, -12.4f), p(-3f, 9.5f), order = 2),
                    move(p(3f, 13.1f), p(-2.4f, 13.1f)),
                ),
                zones = listOf(target(3f, -9.5f, "1"), target(-3f, 9.5f, "2")),
            ),
            guided = TrainedStroke.FOREHAND to listOf(DrillStep(10, direction = ShotDirection.LINE), DrillStep(10, direction = ShotDirection.CROSS)),
        ),
        Item(
            "deep_rally", Topic.FOREHAND, Level.BEGINNER, 2, 10,
            Diagram(
                listOf(Who(p(0f, 12.4f), "A"), Who(p(0f, -12.4f), "B")),
                paths = listOf(ball(p(0f, 12.4f), p(-1f, -9.5f)), ball(p(0f, -12.4f), p(1f, 9.5f))),
                zones = listOf(area(-SINGLES, -BASE, SINGLES, -SERVICE), area(-SINGLES, SERVICE, SINGLES, BASE)),
            ),
            guided = TrainedStroke.FOREHAND to listOf(DrillStep(20, direction = ShotDirection.MIDDLE)),
        ),
        Item(
            "mini_tennis", Topic.FOREHAND, Level.BEGINNER, 2, 10,
            Diagram(
                listOf(Who(p(0f, 5f), "A"), Who(p(0f, -5f), "B")),
                paths = listOf(ball(p(0f, 5f), p(-1.5f, -3.5f), arc = 1.2f), ball(p(0f, -5f), p(1.5f, 3.5f), arc = 1.2f)),
                zones = listOf(area(-SINGLES, -SERVICE, SINGLES, 0f), area(-SINGLES, 0f, SINGLES, SERVICE)),
            ),
            guided = TrainedStroke.FOREHAND to listOf(DrillStep(20, direction = ShotDirection.MIDDLE)),
        ),
        // Servizio dal lato del pari: al centro (T), al corpo, esterno.
        Item(
            "serve_targets", Topic.SERVE, Level.INTERMEDIATE, 1, 15,
            Diagram(
                listOf(Who(p(0.8f, 12.3f), "A")),
                paths = listOf(
                    ball(p(0.8f, 12.3f), p(-0.6f, -5.4f), order = 1), ball(p(0.8f, 12.3f), p(-2f, -5.4f), order = 2),
                    ball(p(0.8f, 12.3f), p(-3.5f, -5.4f), order = 3),
                ),
                zones = listOf(target(-0.7f, -5.3f, "1", 1.3f, 2.0f), target(-2.05f, -5.3f, "2", 1.3f, 2.0f), target(-3.4f, -5.3f, "3", 1.3f, 2.0f)),
            ),
            guided = TrainedStroke.SERVE to listOf(
                DrillStep(5, side = ServeSide.DEUCE, kind = ServeKind.FIRST, target = ServeTarget.T),
                DrillStep(5, side = ServeSide.DEUCE, kind = ServeKind.FIRST, target = ServeTarget.BODY),
                DrillStep(5, side = ServeSide.DEUCE, kind = ServeKind.FIRST, target = ServeTarget.WIDE),
                DrillStep(5, side = ServeSide.AD, kind = ServeKind.FIRST, target = ServeTarget.T),
                DrillStep(5, side = ServeSide.AD, kind = ServeKind.FIRST, target = ServeTarget.BODY),
                DrillStep(5, side = ServeSide.AD, kind = ServeKind.FIRST, target = ServeTarget.WIDE),
            ),
        ),
        Item(
            "second_serve", Topic.SERVE, Level.INTERMEDIATE, 1, 15,
            Diagram(
                listOf(Who(p(-0.8f, 12.3f), "A")),
                paths = listOf(ball(p(-0.8f, 12.3f), p(2.4f, -5.3f), arc = 2.2f)),
                zones = listOf(target(2.4f, -5.3f, "", 3.2f, 1.8f)),
            ),
            guided = TrainedStroke.SERVE to listOf(
                DrillStep(10, side = ServeSide.DEUCE, kind = ServeKind.SECOND),
                DrillStep(10, side = ServeSide.AD, kind = ServeKind.SECOND),
            ),
        ),
        // B serve dal lato del pari (per lui a destra, cioè a sinistra nel disegno), A risponde profondo al centro.
        Item(
            "return_deep", Topic.RETURN, Level.INTERMEDIATE, 2, 15,
            Diagram(
                listOf(Who(p(-0.8f, -12.3f), "B"), Who(p(3.2f, 12.2f), "A")),
                paths = listOf(ball(p(-0.8f, -12.3f), p(2.4f, 4.8f), order = 1), ball(p(3.2f, 12.2f), p(0f, -9.3f), order = 2)),
                zones = listOf(target(0f, -9.3f, "2", 3.6f, 3.6f)),
            ),
            guided = TrainedStroke.BACKHAND to listOf(DrillStep(20, direction = ShotDirection.MIDDLE)),
        ),
        Item(
            "volley_feed", Topic.VOLLEY, Level.BEGINNER, 1, 10,
            Diagram(
                listOf(Who(p(0f, 3.6f), "A"), Who(p(0f, -7f), "", feeder = true)),
                paths = listOf(
                    ball(p(0f, -7f), p(1f, 3.5f), order = 1, arc = 0.6f, frame = 1), ball(p(1f, 3.5f), p(-2.6f, -8.6f), order = 2, frame = 1),
                    ball(p(0f, -7f), p(-1f, 3.5f), order = 3, arc = 0.6f, frame = 2), ball(p(-1f, 3.5f), p(2.6f, -8.6f), order = 4, frame = 2),
                ),
                zones = listOf(target(-2.6f, -8.6f, "2", frame = 1), target(2.6f, -8.6f, "4", frame = 2)),
            ),
            guided = TrainedStroke.VOLLEY to listOf(
                DrillStep(10, wing = Wing.FOREHAND, direction = ShotDirection.CROSS),
                DrillStep(10, wing = Wing.BACKHAND, direction = ShotDirection.CROSS),
            ),
        ),
        // Palla corta, attacco lungolinea, avanti a rete e volée sul passante.
        Item(
            "approach_volley", Topic.VOLLEY, Level.INTERMEDIATE, 1, 15,
            Diagram(
                // A due volte: dove attacca e dove gioca la volée.
                listOf(Who(p(1.5f, 7.6f), "A", frame = 1), Who(p(0.6f, 3.6f), "A", frame = 2), Who(p(0f, -10f), "", feeder = true)),
                paths = listOf(
                    move(p(1f, 12.2f), p(1.5f, 8.4f), frame = 1),
                    ball(p(0f, -10f), p(1.5f, 7.4f), order = 1, frame = 1),
                    ball(p(1.5f, 7.4f), p(3.2f, -9f), order = 2, frame = 1),
                    move(p(1.5f, 7.2f), p(0.7f, 4.3f), frame = 2),
                    ball(p(0f, -10f), p(0.6f, 3.4f), order = 3, frame = 2),
                    ball(p(0.6f, 3.4f), p(-3f, -7.4f), order = 4, frame = 2),
                ),
                zones = listOf(target(3.2f, -9f, "2", frame = 1), target(-3f, -7.4f, "4", frame = 2)),
            ),
            guided = TrainedStroke.VOLLEY to listOf(DrillStep(10, direction = ShotDirection.CROSS)),
        ),
        Item(
            "smash", Topic.OVERHEAD, Level.INTERMEDIATE, 1, 10,
            Diagram(
                listOf(Who(p(0.3f, 7f), "A"), Who(p(0f, -11f), "", feeder = true)),
                paths = listOf(
                    move(p(0f, 2.6f), p(0.3f, 6.4f)),
                    ball(p(0f, -11f), p(0.3f, 7f), order = 1, arc = 6f),
                    ball(p(0.3f, 7f), p(-2.4f, -8.4f), order = 2, arc = 0.4f),
                ),
                zones = listOf(target(-2.4f, -8.4f, "2")),
            ),
        ),
        // A a rete contro due a fondo campo: volée alternate, una per parte.
        Item(
            "one_vs_two", Topic.VOLLEY, Level.ADVANCED, 3, 15,
            Diagram(
                listOf(Who(p(0f, 3.5f), "A"), Who(p(-2.8f, -12.3f), "B"), Who(p(2.8f, -12.3f), "C", backhand = true)),
                paths = listOf(
                    ball(p(-2.8f, -12.3f), p(0.9f, 3.3f), order = 1, frame = 1), ball(p(0.9f, 3.3f), p(2.6f, -10f), order = 2, frame = 1),
                    ball(p(2.8f, -12.3f), p(-0.9f, 3.3f), order = 3, frame = 2), ball(p(-0.9f, 3.3f), p(-2.6f, -10f), order = 4, frame = 2),
                ),
            ),
            guided = TrainedStroke.VOLLEY to listOf(DrillStep(10, wing = Wing.FOREHAND), DrillStep(10, wing = Wing.BACKHAND)),
        ),
        Item(
            "drop_recover", Topic.FOOTWORK, Level.INTERMEDIATE, 2, 10,
            Diagram(
                listOf(Who(p(1f, 2.8f), "A"), Who(p(0f, -8f), "B")),
                paths = listOf(
                    ball(p(0f, -8f), p(1.2f, 2.3f), order = 1, arc = 1.4f),
                    move(p(0f, 12.2f), p(0.9f, 3.4f)),
                    ball(p(1f, 2.6f), p(-2.2f, -4.6f), order = 2, arc = 0.7f),
                    move(p(0.6f, 3.4f), p(-0.2f, 11.8f)),
                ),
                recover = listOf(recover(0f, 12.6f)),
            ),
            guided = TrainedStroke.FOREHAND to listOf(DrillStep(10, direction = ShotDirection.CROSS)),
        ),
        // Il ragno: dal centro della linea di fondo a ogni cono e ritorno.
        Item(
            "spider", Topic.FITNESS, Level.INTERMEDIATE, 1, 10,
            Diagram(
                listOf(Who(p(0f, 12.3f), "A")),
                paths = listOf(
                    move(p(-0.2f, 11.9f), p(-3.8f, 11.9f)), move(p(-0.2f, 11.6f), p(-3.7f, SERVICE + 0.3f)), move(p(0f, 11.5f), p(0f, SERVICE + 0.4f)),
                    move(p(0.2f, 11.6f), p(3.7f, SERVICE + 0.3f)), move(p(0.2f, 11.9f), p(3.8f, 11.9f)),
                ),
                cones = listOf(p(-SINGLES, BASE), p(-SINGLES, SERVICE), p(0f, SERVICE), p(SINGLES, SERVICE), p(SINGLES, BASE)),
                half = true,
            ),
        ),
        Item(
            "split_step", Topic.FOOTWORK, Level.BEGINNER, 1, 10,
            Diagram(
                listOf(Who(p(0f, 12.3f), "A"), Who(p(0f, -7f), "", feeder = true)),
                paths = listOf(
                    ball(p(0f, -7f), p(2.8f, 10.6f), order = 1), move(p(0.3f, 12.3f), p(2.4f, 11.7f)),
                    ball(p(0f, -7f), p(-2.8f, 10.6f), order = 2), move(p(-0.3f, 12.3f), p(-2.4f, 11.7f)),
                ),
                recover = listOf(recover(0f, 12.6f)),
            ),
        ),
        Item(
            "baseline_shuffle", Topic.FITNESS, Level.BEGINNER, 1, 8,
            Diagram(
                listOf(Who(p(0f, 12.3f), "A")),
                paths = listOf(move(p(-0.3f, 12.4f), p(-3.9f, 12.4f)), move(p(0.3f, 12.4f), p(3.9f, 12.4f))),
                cones = listOf(p(-SINGLES, BASE), p(0f, BASE), p(SINGLES, BASE)),
                half = true,
            ),
        ),
        // Servizio esterno, risposta al centro, dritto nel campo aperto.
        Item(
            "serve_plus_one", Topic.TACTICS, Level.INTERMEDIATE, 2, 15,
            Diagram(
                listOf(Who(p(0.8f, 10.2f), "A"), Who(p(-3.6f, -12f), "B")),
                paths = listOf(
                    ball(p(0.8f, 12.3f), p(-3.6f, -5.2f), order = 1),
                    ball(p(-3.6f, -12f), p(0.7f, 9.6f), order = 2),
                    move(p(0.8f, 12.3f), p(0.8f, 10.6f)),
                    ball(p(0.7f, 9.8f), p(3f, -9f), order = 3),
                ),
                zones = listOf(target(-3.4f, -5.2f, "1", 1.6f, 1.8f), target(3f, -9f, "3")),
            ),
            guided = TrainedStroke.SERVE to listOf(DrillStep(10, side = ServeSide.DEUCE, kind = ServeKind.FIRST, target = ServeTarget.WIDE)),
        ),
        Item(
            "cross_points", Topic.TACTICS, Level.BEGINNER, 2, 20,
            Diagram(
                listOf(Who(p(2.6f, 12.4f), "A"), Who(p(-2.6f, -12.4f), "B")),
                paths = listOf(ball(p(2.6f, 12.4f), p(-2f, -8f)), ball(p(-2.6f, -12.4f), p(2f, 8f))),
                zones = listOf(area(-SINGLES, -BASE, 0f, 0f), area(0f, 0f, SINGLES, BASE)),
            ),
            guided = TrainedStroke.FOREHAND to listOf(DrillStep(20, direction = ShotDirection.CROSS)),
        ),
        // Re del campo: chi vince il punto resta (o sale) dalla parte del re.
        Item(
            "king_court", Topic.TACTICS, Level.INTERMEDIATE, 4, 20,
            Diagram(
                listOf(Who(p(0f, -12.4f), "A"), Who(p(0f, 12.4f), "B"), Who(p(6.2f, 9f), "C"), Who(p(6.2f, 10.6f), "D")),
                paths = listOf(ball(p(0f, 12.4f), p(-1.5f, -9f), order = 1), move(p(5.8f, 9.8f), p(1.4f, 12.4f))),
            ),
        ),
        // Doppio: A1 serve, A2 a rete incrocia sulla risposta incrociata e chiude in mezzo.
        Item(
            "doubles_poach", Topic.TACTICS, Level.ADVANCED, 4, 20,
            Diagram(
                listOf(
                    Who(p(0.8f, 12.3f), "A1"), Who(p(-2.5f, 3.6f), "A2", frame = 1), Who(p(0.2f, 3f), "A2", frame = 2),
                    Who(p(-3.2f, -12f), "B1"), Who(p(2.5f, -6.4f), "B2"),
                ),
                paths = listOf(
                    ball(p(0.8f, 12.3f), p(-2.2f, -5f), order = 1, frame = 1),
                    ball(p(-3.2f, -12f), p(0.2f, 3.2f), order = 2, frame = 1),
                    move(p(-2.5f, 3.6f), p(-0.2f, 3.1f), frame = 2),
                    ball(p(-3.2f, -12f), p(0.2f, 3.2f), order = 2, frame = 2),
                    ball(p(0.2f, 3f), p(0f, -7.5f), order = 3, frame = 2),
                ),
                zones = listOf(target(0f, -7.5f, "3", 2f, 2.4f, frame = 2)),
            ),
        ),

        // ------------------------------------------------------------ altri 40 (27 nov)

        // Dritto inside-out: A gira attorno alla palla sul lato del vantaggio e tira il dritto incrociato verso il
        // vantaggio dell'altro.
        Item(
            "fh_inside_out", Topic.FOREHAND, Level.INTERMEDIATE, 1, 15,
            Diagram(
                listOf(Who(p(-3f, 12.1f), "A"), Who(p(0f, -8f), "", feeder = true)),
                paths = listOf(
                    move(p(0.2f, 13.4f), p(-2.7f, 13.1f)),
                    ball(p(0f, -8f), p(-2.2f, 11.8f), order = 1), ball(p(-2.2f, 11.8f), p(2.8f, -9f), order = 2),
                ),
                zones = listOf(target(2.8f, -9f, "2")),
                recover = listOf(recover(-1f, 12.6f)),
            ),
            guided = TrainedStroke.FOREHAND to listOf(DrillStep(20, direction = ShotDirection.CROSS)),
        ),
        // Dritto inside-in: dallo stesso punto, ma lungolinea.
        Item(
            "fh_inside_in", Topic.FOREHAND, Level.ADVANCED, 1, 15,
            Diagram(
                listOf(Who(p(-3f, 12.1f), "A"), Who(p(0f, -8f), "", feeder = true)),
                paths = listOf(
                    move(p(0.2f, 13.4f), p(-2.7f, 13.1f)),
                    ball(p(0f, -8f), p(-2.2f, 11.8f), order = 1), ball(p(-2.2f, 11.8f), p(-3f, -9.2f), order = 2),
                ),
                zones = listOf(target(-3f, -9.2f, "2", 2f, 2.6f)),
                recover = listOf(recover(-0.6f, 12.6f)),
            ),
            guided = TrainedStroke.FOREHAND to listOf(DrillStep(20, direction = ShotDirection.LINE)),
        ),
        // Dritto carico: la palla passa alta sopra la rete e cade profonda.
        Item(
            "fh_topspin_height", Topic.FOREHAND, Level.INTERMEDIATE, 1, 15,
            Diagram(
                listOf(Who(p(1.5f, 12.2f), "A"), Who(p(0f, -8f), "", feeder = true)),
                paths = listOf(ball(p(0f, -8f), p(2.2f, 11.7f), order = 1), ball(p(2.2f, 11.7f), p(0.4f, -9.9f), order = 2, arc = 3.2f)),
                zones = listOf(target(0.4f, -9.9f, "2", 5f, 2.6f)),
            ),
            guided = TrainedStroke.FOREHAND to listOf(DrillStep(20, direction = ShotDirection.MIDDLE)),
        ),
        // Dritto in corsa: palla larga sul dritto, colpo incrociato e ritorno al centro.
        Item(
            "fh_run_wide", Topic.FOREHAND, Level.ADVANCED, 1, 15,
            Diagram(
                listOf(Who(p(3.6f, 11.8f), "A"), Who(p(0f, -8f), "", feeder = true)),
                paths = listOf(
                    move(p(-0.2f, 13.4f), p(3.2f, 12.9f)),
                    ball(p(0f, -8f), p(4.3f, 11.4f), order = 1), ball(p(4.3f, 11.4f), p(-2.5f, -9f), order = 2),
                    move(p(3.4f, 12.5f), p(0.6f, 12.7f)),
                ),
                zones = listOf(target(-2.5f, -9f, "2")),
                recover = listOf(recover(0f, 12.6f)),
            ),
            guided = TrainedStroke.FOREHAND to listOf(DrillStep(20, direction = ShotDirection.CROSS)),
        ),
        // Angolo corto di dritto: nel palleggio incrociato, la palla che esce dal campo vicino alla rete.
        Item(
            "fh_short_angle", Topic.FOREHAND, Level.ADVANCED, 2, 15,
            Diagram(
                listOf(Who(p(2.1f, 12.1f), "A"), Who(p(-3f, -12.2f), "B")),
                paths = listOf(
                    ball(p(-3f, -12.2f), p(2.7f, 11.7f), order = 1), ball(p(2.7f, 11.7f), p(-3.6f, -4.2f), order = 2, arc = 0.9f),
                    move(p(-3f, -11.6f), p(-4.6f, -6.4f)),
                ),
                zones = listOf(target(-3.6f, -4.2f, "2", 1.6f, 2f)),
            ),
            guided = TrainedStroke.FOREHAND to listOf(DrillStep(20, direction = ShotDirection.CROSS)),
        ),
        // Palla corta: A entra in campo e attacca di dritto lungolinea.
        Item(
            "fh_approach_line", Topic.FOREHAND, Level.INTERMEDIATE, 1, 15,
            Diagram(
                listOf(Who(p(1.9f, 7.3f), "A"), Who(p(0f, -8f), "", feeder = true)),
                paths = listOf(
                    move(p(1.4f, 12.2f), p(1.8f, 7.9f)),
                    ball(p(0f, -8f), p(2.6f, 7f), order = 1, arc = 1f), ball(p(2.6f, 7f), p(3.3f, -9.4f), order = 2),
                    move(p(1.8f, 6.7f), p(1.5f, 4f)),
                ),
                zones = listOf(target(3.3f, -9.4f, "2", 2f, 2.6f)),
                recover = listOf(recover(1.4f, 3.4f)),
            ),
            guided = TrainedStroke.FOREHAND to listOf(DrillStep(20, direction = ShotDirection.LINE)),
        ),
        // Rovescio lungolinea dal lato del vantaggio.
        Item(
            "bh_line", Topic.BACKHAND, Level.INTERMEDIATE, 1, 15,
            Diagram(
                listOf(Who(p(-2.9f, 12.1f), "A", backhand = true), Who(p(0f, -8f), "", feeder = true)),
                paths = listOf(ball(p(0f, -8f), p(-3.6f, 11.7f), order = 1), ball(p(-3.6f, 11.7f), p(-3.2f, -9.4f), order = 2)),
                zones = listOf(target(-3.2f, -9.4f, "2", 2f, 2.6f)),
                recover = listOf(recover(-0.8f, 12.6f)),
            ),
            guided = TrainedStroke.BACKHAND to listOf(DrillStep(20, direction = ShotDirection.LINE)),
        ),
        // Rovescio tagliato: basso sopra la rete e profondo.
        Item(
            "bh_slice", Topic.BACKHAND, Level.INTERMEDIATE, 1, 15,
            Diagram(
                listOf(Who(p(-1.5f, 12.1f), "A", backhand = true), Who(p(0f, -8f), "", feeder = true)),
                paths = listOf(ball(p(0f, -8f), p(-2.2f, 11.6f), order = 1), ball(p(-2.2f, 11.6f), p(-0.4f, -10.1f), order = 2, arc = 0.5f)),
                zones = listOf(target(-0.4f, -10.1f, "2", 4.4f, 2.4f)),
            ),
            guided = TrainedStroke.BACKHAND to listOf(DrillStep(20, direction = ShotDirection.MIDDLE)),
        ),
        // Rovescio incrociato e lungolinea, a serie.
        Item(
            "bh_cross_line", Topic.BACKHAND, Level.ADVANCED, 1, 20,
            Diagram(
                listOf(Who(p(-2.6f, 12.1f), "A", backhand = true), Who(p(0f, -8f), "", feeder = true)),
                paths = listOf(
                    ball(p(0f, -8f), p(-3.3f, 11.7f), order = 1, frame = 1), ball(p(-3.3f, 11.7f), p(3f, -9.3f), order = 2, frame = 1),
                    ball(p(0f, -8f), p(-3.3f, 11.7f), order = 3, frame = 2), ball(p(-3.3f, 11.7f), p(-3.2f, -9.3f), order = 4, frame = 2),
                ),
                zones = listOf(target(3f, -9.3f, "2", frame = 1), target(-3.2f, -9.3f, "4", 2f, 2.6f, frame = 2)),
            ),
            guided = TrainedStroke.BACKHAND to listOf(DrillStep(10, direction = ShotDirection.CROSS), DrillStep(10, direction = ShotDirection.LINE)),
        ),
        // Rovescio in difesa: spinti nell'angolo, palla alta e profonda per riprendere tempo.
        Item(
            "bh_defense", Topic.BACKHAND, Level.INTERMEDIATE, 1, 15,
            Diagram(
                listOf(Who(p(-4.1f, 12.3f), "A", backhand = true), Who(p(1.5f, -6.6f), "", feeder = true)),
                paths = listOf(
                    move(p(-0.4f, 13.4f), p(-3.7f, 13.1f)),
                    ball(p(1.5f, -6.6f), p(-4.8f, 11.9f), order = 1, arc = 2f), ball(p(-4.8f, 11.9f), p(-0.6f, -10.2f), order = 2, arc = 4.5f),
                ),
                zones = listOf(target(-0.6f, -10.2f, "2", 4f, 2.4f)),
                recover = listOf(recover(0f, 12.6f)),
            ),
            guided = TrainedStroke.BACKHAND to listOf(DrillStep(20, direction = ShotDirection.MIDDLE)),
        ),
        // Angolo corto di rovescio, nella diagonale del vantaggio.
        Item(
            "bh_short_angle", Topic.BACKHAND, Level.ADVANCED, 2, 15,
            Diagram(
                listOf(Who(p(-2.4f, 12.1f), "A", backhand = true), Who(p(3f, -12.2f), "B", backhand = true)),
                paths = listOf(
                    ball(p(3f, -12.2f), p(-3.1f, 11.7f), order = 1), ball(p(-3.1f, 11.7f), p(3.6f, -4.2f), order = 2, arc = 0.9f),
                    move(p(3f, -11.6f), p(4.6f, -6.4f)),
                ),
                zones = listOf(target(3.6f, -4.2f, "2", 1.6f, 2f)),
            ),
            guided = TrainedStroke.BACKHAND to listOf(DrillStep(20, direction = ShotDirection.CROSS)),
        ),
        // Primi rovesci: palle lente lanciate da vicino, A dentro il campo.
        Item(
            "bh_first", Topic.BACKHAND, Level.BEGINNER, 1, 10,
            Diagram(
                listOf(Who(p(-0.6f, 9f), "A", backhand = true), Who(p(0f, -4.5f), "", feeder = true)),
                paths = listOf(ball(p(0f, -4.5f), p(-1.3f, 8.6f), order = 1, arc = 1.4f), ball(p(-1.3f, 8.6f), p(0f, -8f), order = 2, arc = 1.4f)),
                zones = listOf(area(-SINGLES, -BASE, SINGLES, -SERVICE)),
            ),
            guided = TrainedStroke.BACKHAND to listOf(DrillStep(20, direction = ShotDirection.MIDDLE)),
        ),
        // Servizio da sinistra: al centro e esterno.
        Item(
            "serve_ad_targets", Topic.SERVE, Level.INTERMEDIATE, 1, 15,
            Diagram(
                listOf(Who(p(-0.8f, 12.3f), "A")),
                paths = listOf(ball(p(-0.8f, 12.3f), p(0.7f, -5.4f), order = 1), ball(p(-0.8f, 12.3f), p(3.4f, -5.4f), order = 2)),
                zones = listOf(target(0.7f, -5.3f, "1", 1.3f, 2f), target(3.4f, -5.3f, "2", 1.3f, 2f)),
            ),
            guided = TrainedStroke.SERVE to listOf(
                DrillStep(10, side = ServeSide.AD, kind = ServeKind.FIRST, target = ServeTarget.T),
                DrillStep(10, side = ServeSide.AD, kind = ServeKind.FIRST, target = ServeTarget.WIDE),
            ),
        ),
        // Primi servizi: lancio fermo e movimento completo, palla dentro il quadrato.
        Item(
            "serve_toss", Topic.SERVE, Level.BEGINNER, 1, 10,
            Diagram(
                listOf(Who(p(0.8f, 12.3f), "A")),
                paths = listOf(ball(p(0.8f, 12.3f), p(-2f, -4f), arc = 1.6f)),
                zones = listOf(area(-SINGLES, -SERVICE, 0f, 0f)),
            ),
            guided = TrainedStroke.SERVE to listOf(DrillStep(10, side = ServeSide.DEUCE, kind = ServeKind.FIRST), DrillStep(10, side = ServeSide.AD, kind = ServeKind.FIRST)),
        ),
        // Prime di fila: da destra e da sinistra, si contano quelle dentro.
        Item(
            "serve_first_pct", Topic.SERVE, Level.INTERMEDIATE, 1, 15,
            Diagram(
                listOf(Who(p(0.8f, 12.3f), "A", frame = 1), Who(p(-0.8f, 12.3f), "A", frame = 2)),
                paths = listOf(ball(p(0.8f, 12.3f), p(-2f, -4.4f), order = 1, frame = 1), ball(p(-0.8f, 12.3f), p(2f, -4.4f), order = 2, frame = 2)),
                zones = listOf(area(-SINGLES, -SERVICE, 0f, 0f, frame = 1), area(0f, -SERVICE, SINGLES, 0f, frame = 2)),
            ),
            guided = TrainedStroke.SERVE to listOf(
                DrillStep(5, side = ServeSide.DEUCE, kind = ServeKind.FIRST), DrillStep(5, side = ServeSide.AD, kind = ServeKind.FIRST),
                DrillStep(5, side = ServeSide.DEUCE, kind = ServeKind.FIRST), DrillStep(5, side = ServeSide.AD, kind = ServeKind.FIRST),
            ),
        ),
        // Servizio al corpo di chi risponde, dai due lati.
        Item(
            "serve_body", Topic.SERVE, Level.ADVANCED, 1, 15,
            Diagram(
                listOf(Who(p(0.8f, 12.3f), "A", frame = 1), Who(p(-0.8f, 12.3f), "A", frame = 2)),
                paths = listOf(ball(p(0.8f, 12.3f), p(-2.1f, -5.5f), order = 1, frame = 1), ball(p(-0.8f, 12.3f), p(2.1f, -5.5f), order = 2, frame = 2)),
                zones = listOf(target(-2.1f, -5.4f, "1", 1.3f, 1.8f, frame = 1), target(2.1f, -5.4f, "2", 1.3f, 1.8f, frame = 2)),
            ),
            guided = TrainedStroke.SERVE to listOf(
                DrillStep(10, side = ServeSide.DEUCE, kind = ServeKind.FIRST, target = ServeTarget.BODY),
                DrillStep(10, side = ServeSide.AD, kind = ServeKind.FIRST, target = ServeTarget.BODY),
            ),
        ),
        // Prima e seconda come in partita: la prima forte al centro, la seconda carica e sicura.
        Item(
            "serve_pressure", Topic.SERVE, Level.ADVANCED, 1, 20,
            Diagram(
                listOf(Who(p(0.8f, 12.3f), "A")),
                paths = listOf(ball(p(0.8f, 12.3f), p(-0.6f, -5.4f), order = 1, arc = 0.8f), ball(p(0.8f, 12.3f), p(-2.4f, -4.8f), order = 2, arc = 2.4f)),
                zones = listOf(target(-0.7f, -5.3f, "1", 1.3f, 2f), target(-2.6f, -4.6f, "2", 2.6f, 2.4f)),
            ),
            guided = TrainedStroke.SERVE to listOf(
                DrillStep(5, side = ServeSide.DEUCE, kind = ServeKind.FIRST, target = ServeTarget.T),
                DrillStep(5, side = ServeSide.DEUCE, kind = ServeKind.SECOND),
                DrillStep(5, side = ServeSide.AD, kind = ServeKind.FIRST, target = ServeTarget.T),
                DrillStep(5, side = ServeSide.AD, kind = ServeKind.SECOND),
            ),
        ),
        // Risposta bloccata contro il servizio forte: movimento corto, palla al centro.
        Item(
            "return_block", Topic.RETURN, Level.INTERMEDIATE, 2, 15,
            Diagram(
                listOf(Who(p(-0.8f, -12.3f), "B"), Who(p(3.1f, 12f), "A")),
                paths = listOf(ball(p(-0.8f, -12.3f), p(3.8f, 11.6f), order = 1, arc = 0.8f), ball(p(3.8f, 11.6f), p(0f, -9.4f), order = 2)),
                zones = listOf(target(0f, -9.4f, "2", 3.6f, 3f)),
            ),
            guided = TrainedStroke.FOREHAND to listOf(DrillStep(20, direction = ShotDirection.MIDDLE)),
        ),
        // Sulla seconda di servizio A entra in campo, gira attorno alla palla e attacca di dritto.
        Item(
            "return_second_attack", Topic.RETURN, Level.ADVANCED, 2, 15,
            Diagram(
                listOf(Who(p(0.8f, -12.3f), "B"), Who(p(-3.2f, 10.8f), "A")),
                paths = listOf(
                    move(p(-3.4f, 12.8f), p(-3.2f, 11.4f)),
                    ball(p(0.8f, -12.3f), p(-2.5f, 10.4f), order = 1, arc = 2.2f), ball(p(-2.5f, 10.4f), p(3f, -9f), order = 2),
                ),
                zones = listOf(target(3f, -9f, "2")),
            ),
            guided = TrainedStroke.FOREHAND to listOf(DrillStep(20, direction = ShotDirection.CROSS)),
        ),
        // Risposta di rovescio incrociata dal lato del vantaggio.
        Item(
            "return_cross", Topic.RETURN, Level.INTERMEDIATE, 2, 15,
            Diagram(
                listOf(Who(p(0.8f, -12.3f), "B"), Who(p(-2.8f, 12.2f), "A", backhand = true)),
                paths = listOf(ball(p(0.8f, -12.3f), p(-3.5f, 11.7f), order = 1), ball(p(-3.5f, 11.7f), p(3f, -9.3f), order = 2)),
                zones = listOf(target(3f, -9.3f, "2")),
            ),
            guided = TrainedStroke.BACKHAND to listOf(DrillStep(20, direction = ShotDirection.CROSS)),
        ),
        // Doppio: risposta con pallonetto sopra chi è a rete.
        Item(
            "return_lob", Topic.RETURN, Level.ADVANCED, 3, 15,
            Diagram(
                listOf(Who(p(-1f, -12.3f), "B1"), Who(p(2.4f, -3.8f), "B2"), Who(p(3f, 12.1f), "A")),
                paths = listOf(
                    ball(p(-1f, -12.3f), p(3.6f, 11.7f), order = 1), ball(p(3.6f, 11.7f), p(2.8f, -10f), order = 2, arc = 6f),
                    move(p(2.4f, -4.4f), p(2.7f, -7.4f)),
                ),
                zones = listOf(target(2.8f, -10f, "2", 2.6f, 2.4f)),
            ),
        ),
        // Volée bassa: palla sui piedi, ginocchia piegate, profonda al centro.
        Item(
            "volley_low", Topic.VOLLEY, Level.INTERMEDIATE, 1, 10,
            Diagram(
                listOf(Who(p(0.5f, 5.6f), "A"), Who(p(0f, -7f), "", feeder = true)),
                paths = listOf(ball(p(0f, -7f), p(1.2f, 5.4f), order = 1, arc = 0.5f), ball(p(1.2f, 5.4f), p(0f, -9.2f), order = 2, arc = 0.8f)),
                zones = listOf(target(0f, -9.2f, "2", 3.6f, 2.6f)),
            ),
            guided = TrainedStroke.VOLLEY to listOf(DrillStep(20, direction = ShotDirection.MIDDLE)),
        ),
        // Volée contro volée, tutti e due a rete: riflessi e piatto fermo.
        Item(
            "volley_reflex", Topic.VOLLEY, Level.INTERMEDIATE, 2, 10,
            Diagram(
                listOf(Who(p(0.3f, 2.8f), "A"), Who(p(-0.3f, -2.8f), "B")),
                paths = listOf(ball(p(0.3f, 2.8f), p(-0.9f, -2.6f), order = 1, arc = 0.3f), ball(p(-0.9f, -2.6f), p(0.9f, 2.6f), order = 2, arc = 0.3f)),
            ),
            guided = TrainedStroke.VOLLEY to listOf(DrillStep(20)),
        ),
        // Volée smorzata: la palla muore subito dopo la rete.
        Item(
            "volley_drop", Topic.VOLLEY, Level.ADVANCED, 1, 10,
            Diagram(
                listOf(Who(p(0.5f, 3.6f), "A"), Who(p(0f, -9f), "", feeder = true)),
                paths = listOf(ball(p(0f, -9f), p(1.3f, 3.3f), order = 1, arc = 0.6f), ball(p(1.3f, 3.3f), p(-1.8f, -2f), order = 2, arc = 0.35f)),
                zones = listOf(target(-1.8f, -2f, "2", 2.4f, 2.2f)),
            ),
            guided = TrainedStroke.VOLLEY to listOf(DrillStep(20, wing = Wing.FOREHAND, direction = ShotDirection.CROSS)),
        ),
        // Demi-volée sulla linea del servizio, poi avanti a rete.
        Item(
            "half_volley", Topic.VOLLEY, Level.ADVANCED, 1, 10,
            Diagram(
                listOf(Who(p(0.3f, 6.8f), "A"), Who(p(0f, -8f), "", feeder = true)),
                paths = listOf(
                    ball(p(0f, -8f), p(0.9f, 6.5f), order = 1, arc = 0.8f), ball(p(0.9f, 6.5f), p(0f, -9.6f), order = 2, arc = 1f),
                    move(p(0.2f, 6.2f), p(0.2f, 3.9f)),
                ),
                zones = listOf(target(0f, -9.6f, "2", 3.6f, 2.4f)),
                recover = listOf(recover(0.2f, 3.4f)),
            ),
            guided = TrainedStroke.VOLLEY to listOf(DrillStep(20, direction = ShotDirection.MIDDLE)),
        ),
        // Volée angolata per chiudere il punto: di dritto, poi di rovescio.
        Item(
            "volley_angle", Topic.VOLLEY, Level.INTERMEDIATE, 1, 15,
            Diagram(
                listOf(Who(p(0.3f, 3f), "A"), Who(p(0f, -6f), "", feeder = true)),
                paths = listOf(
                    ball(p(0f, -6f), p(1.1f, 2.9f), order = 1, arc = 0.5f, frame = 1), ball(p(1.1f, 2.9f), p(-4f, -2.6f), order = 2, arc = 0.4f, frame = 1),
                    ball(p(0f, -6f), p(-0.5f, 2.9f), order = 3, arc = 0.5f, frame = 2), ball(p(-0.5f, 2.9f), p(4f, -2.6f), order = 4, arc = 0.4f, frame = 2),
                ),
                zones = listOf(target(-4f, -2.6f, "2", 1.5f, 2f, frame = 1), target(4f, -2.6f, "4", 1.5f, 2f, frame = 2)),
            ),
            guided = TrainedStroke.VOLLEY to listOf(
                DrillStep(10, wing = Wing.FOREHAND, direction = ShotDirection.CROSS),
                DrillStep(10, wing = Wing.BACKHAND, direction = ShotDirection.CROSS),
            ),
        ),
        // Volée al volo da metà campo: la palla alta e lenta non si lascia rimbalzare.
        Item(
            "swing_volley", Topic.VOLLEY, Level.ADVANCED, 1, 10,
            Diagram(
                listOf(Who(p(1.2f, 7.6f), "A"), Who(p(0f, -8f), "", feeder = true)),
                paths = listOf(
                    move(p(1f, 11.8f), p(1.2f, 8.2f)),
                    ball(p(0f, -8f), p(2f, 7.3f), order = 1, arc = 3.5f), ball(p(2f, 7.3f), p(-2.8f, -9f), order = 2),
                ),
                zones = listOf(target(-2.8f, -9f, "2")),
            ),
            guided = TrainedStroke.VOLLEY to listOf(DrillStep(20, wing = Wing.FOREHAND, direction = ShotDirection.CROSS)),
        ),
        // Smash da fondo: il pallonetto profondo si lascia rimbalzare, poi smash.
        Item(
            "smash_bounce", Topic.OVERHEAD, Level.INTERMEDIATE, 1, 10,
            Diagram(
                listOf(Who(p(0.5f, 11.2f), "A"), Who(p(0f, -8f), "", feeder = true)),
                paths = listOf(
                    move(p(0.4f, 6.4f), p(0.5f, 10.6f)),
                    ball(p(0f, -8f), p(0.9f, 11f), order = 1, arc = 8f), ball(p(0.9f, 11f), p(1.6f, -9.4f), order = 2, arc = 0.8f),
                ),
                zones = listOf(target(1.6f, -9.4f, "2", 3f, 2.6f)),
            ),
        ),
        // Pallonetto e smash a coppie: B tira il pallonetto, A a rete arretra e schiaccia.
        Item(
            "lob_smash", Topic.OVERHEAD, Level.INTERMEDIATE, 2, 15,
            Diagram(
                listOf(Who(p(0.6f, 5.6f), "A"), Who(p(-0.5f, -12.2f), "B")),
                paths = listOf(
                    move(p(0.4f, 3.2f), p(0.6f, 5f)),
                    ball(p(-0.5f, -12.2f), p(1.3f, 5.3f), order = 1, arc = 7f), ball(p(1.3f, 5.3f), p(2.6f, -8.4f), order = 2, arc = 0.4f),
                ),
                zones = listOf(target(2.6f, -8.4f, "2")),
            ),
        ),
        // Volée e smash: prima una volée, poi il pallonetto da schiacciare.
        Item(
            "volley_smash", Topic.OVERHEAD, Level.INTERMEDIATE, 1, 15,
            Diagram(
                listOf(Who(p(0.5f, 4.2f), "A", frame = 1), Who(p(0.6f, 6.8f), "A", frame = 2), Who(p(0f, -8f), "", feeder = true)),
                paths = listOf(
                    ball(p(0f, -8f), p(1.2f, 4f), order = 1, arc = 0.6f, frame = 1), ball(p(1.2f, 4f), p(-2.5f, -9f), order = 2, frame = 1),
                    move(p(0.5f, 4.4f), p(0.6f, 6.3f), frame = 2),
                    ball(p(0f, -8f), p(1.2f, 6.6f), order = 3, arc = 7f, frame = 2), ball(p(1.2f, 6.6f), p(2.6f, -8.5f), order = 4, arc = 0.4f, frame = 2),
                ),
                zones = listOf(target(-2.5f, -9f, "2", frame = 1), target(2.6f, -8.5f, "4", frame = 2)),
            ),
        ),
        // Slalom tra i coni a passi corti.
        Item(
            "fw_cones", Topic.FOOTWORK, Level.BEGINNER, 1, 8,
            Diagram(
                listOf(Who(p(-3.9f, 12.2f), "A")),
                paths = listOf(move(p(-3.6f, 11.7f), p(-3f, 10.3f), p(-1.5f, 8.8f), p(0f, 10.3f), p(1.5f, 8.8f), p(3f, 10.3f))),
                cones = listOf(p(-3f, 10f), p(-1.5f, 8.5f), p(0f, 10f), p(1.5f, 8.5f), p(3f, 10f)),
                half = true,
            ),
        ),
        // Colpisci e torna: palle larghe a destra e a sinistra, dopo ogni colpo al centro.
        Item(
            "fw_recover", Topic.FOOTWORK, Level.INTERMEDIATE, 1, 10,
            Diagram(
                listOf(Who(p(2.9f, 11.9f), "A", frame = 1), Who(p(-2.9f, 11.9f), "A", backhand = true, frame = 2), Who(p(0f, -7f), "", feeder = true)),
                paths = listOf(
                    ball(p(0f, -7f), p(3.6f, 11.5f), order = 1, frame = 1), ball(p(3.6f, 11.5f), p(-2.5f, -9f), order = 2, frame = 1),
                    move(p(2.7f, 12.5f), p(0.6f, 12.7f), frame = 1),
                    ball(p(0f, -7f), p(-3.6f, 11.5f), order = 3, frame = 2), ball(p(-3.6f, 11.5f), p(2.5f, -9f), order = 4, frame = 2),
                    move(p(-2.7f, 12.5f), p(-0.6f, 12.7f), frame = 2),
                ),
                recover = listOf(recover(0f, 12.6f)),
            ),
        ),
        // Fuori dalla palla al corpo: un passo di lato per colpire di dritto.
        Item(
            "fw_body_ball", Topic.FOOTWORK, Level.BEGINNER, 1, 10,
            Diagram(
                listOf(Who(p(-0.8f, 12.2f), "A"), Who(p(0f, -7f), "", feeder = true)),
                paths = listOf(
                    move(p(0f, 12.7f), p(-0.6f, 12.7f)),
                    ball(p(0f, -7f), p(0f, 11.8f), order = 1), ball(p(0f, 11.8f), p(0f, -9.4f), order = 2),
                ),
                zones = listOf(target(0f, -9.4f, "2", 3.6f, 2.6f)),
            ),
        ),
        // Avanti e indietro: prima la palla corta, poi quella profonda.
        Item(
            "fw_forward_back", Topic.FOOTWORK, Level.INTERMEDIATE, 1, 10,
            Diagram(
                listOf(Who(p(0.8f, 7.4f), "A", frame = 1), Who(p(1f, 11.2f), "A", frame = 2), Who(p(0f, -8f), "", feeder = true)),
                paths = listOf(
                    move(p(0.5f, 12.3f), p(0.7f, 8f), frame = 1),
                    ball(p(0f, -8f), p(1.4f, 7f), order = 1, arc = 1f, frame = 1), ball(p(1.4f, 7f), p(-2f, -9f), order = 2, frame = 1),
                    move(p(0.8f, 7.8f), p(1f, 10.6f), frame = 2),
                    ball(p(0f, -8f), p(1.6f, 10.8f), order = 3, arc = 5f, frame = 2), ball(p(1.6f, 10.8f), p(2f, -9f), order = 4, frame = 2),
                ),
                zones = listOf(target(-2f, -9f, "2", frame = 1), target(2f, -9f, "4", frame = 2)),
            ),
        ),
        // Scatti alle linee: linea del servizio, rete, metà, sempre ritorno alla linea di fondo.
        Item(
            "fit_lines", Topic.FITNESS, Level.INTERMEDIATE, 1, 8,
            Diagram(
                listOf(Who(p(0f, 12.6f), "A")),
                paths = listOf(move(p(-1.2f, 12.1f), p(-1.2f, SERVICE + 0.2f)), move(p(0f, 12.1f), p(0f, 0.6f)), move(p(1.2f, 12.1f), p(1.2f, 3.4f))),
                cones = listOf(p(-1.2f, SERVICE), p(0f, 0.3f), p(1.2f, 3.1f)),
                half = true,
            ),
        ),
        // Colpi a vuoto: dritto, rovescio e avanti, a intervalli, senza palla.
        Item(
            "fit_shadow", Topic.FITNESS, Level.BEGINNER, 1, 8,
            Diagram(
                listOf(Who(p(0f, 12.2f), "A")),
                paths = listOf(move(p(0.3f, 12.4f), p(2.6f, 12f)), move(p(-0.3f, 12.4f), p(-2.6f, 12f)), move(p(0f, 11.6f), p(0.4f, 8.6f))),
                recover = listOf(recover(0f, 12.6f)),
                half = true,
            ),
        ),
        // Il quadrato del servizio: di lato, indietro, di lato, avanti, attorno ai coni.
        Item(
            "fit_box", Topic.FITNESS, Level.INTERMEDIATE, 1, 8,
            Diagram(
                listOf(Who(p(-0.4f, 7f), "A")),
                paths = listOf(
                    move(p(0.3f, 6.7f), p(3.8f, 6.7f)), move(p(4f, 6.2f), p(4f, 1.3f)),
                    move(p(3.7f, 1f), p(0.5f, 1f)), move(p(0.2f, 1.3f), p(0.2f, 6.1f)),
                ),
                cones = listOf(p(0.2f, 6.4f), p(4f, 6.4f), p(4f, 1f), p(0.2f, 1f)),
                half = true,
            ),
        ),
        // Attacco e passante: A attacca lungolinea e va a rete, B prova il passante; poi si gioca il punto.
        Item(
            "tac_approach_pass", Topic.TACTICS, Level.INTERMEDIATE, 2, 20,
            Diagram(
                listOf(
                    Who(p(1.3f, 7.4f), "A", frame = 1), Who(p(1.2f, 3.6f), "A", frame = 2),
                    Who(p(-0.5f, -12.2f), "B", frame = 1), Who(p(3.4f, -10.2f), "B", backhand = true, frame = 2),
                ),
                paths = listOf(
                    ball(p(-0.5f, -12.2f), p(1.9f, 7.1f), order = 1, arc = 1f, frame = 1), ball(p(1.9f, 7.1f), p(3.3f, -9.4f), order = 2, frame = 1),
                    move(p(1.3f, 7f), p(1.2f, 4.1f), frame = 2),
                    ball(p(3.3f, -9.4f), p(3.8f, 8.6f), order = 3, frame = 2),
                ),
                zones = listOf(target(3.3f, -9.4f, "2", 2f, 2.6f, frame = 1), target(3.8f, 8.6f, "3", 1.6f, 2.6f, frame = 2)),
            ),
        ),
        // Doppio: tutto al centro, tra i due avversari.
        Item(
            "tac_middle", Topic.TACTICS, Level.INTERMEDIATE, 4, 20,
            Diagram(
                listOf(Who(p(1.6f, 12.2f), "A1"), Who(p(-2.2f, 3.6f), "A2"), Who(p(-1.6f, -12.2f), "B1"), Who(p(2.2f, -3.6f), "B2")),
                paths = listOf(ball(p(1.6f, 12.2f), p(0f, -8.8f), order = 1)),
                zones = listOf(area(-1f, -BASE, 1f, -1f)),
            ),
        ),
        // Palla alta e palla tesa: cambiare ritmo nel palleggio incrociato.
        Item(
            "tac_high_low", Topic.TACTICS, Level.INTERMEDIATE, 2, 15,
            Diagram(
                listOf(Who(p(2.5f, 12.2f), "A"), Who(p(-2.5f, -12.2f), "B")),
                paths = listOf(
                    ball(p(2.5f, 12.2f), p(-2f, -10.2f), order = 1, arc = 4.5f, frame = 1),
                    ball(p(2.5f, 12.2f), p(-2.4f, -9.2f), order = 2, arc = 0.6f, frame = 2),
                ),
                zones = listOf(target(-2f, -10.2f, "1", frame = 1), target(-2.4f, -9.2f, "2", frame = 2)),
            ),
            guided = TrainedStroke.FOREHAND to listOf(DrillStep(20, direction = ShotDirection.CROSS)),
        ),
    )
}
