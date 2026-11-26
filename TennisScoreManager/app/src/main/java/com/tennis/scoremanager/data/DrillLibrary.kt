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

    /** Chi è in campo: [label] "A", "B"... ; [feeder] = l'allenatore che lancia le palle (col cesto). */
    data class Who(val at: Pt, val label: String, val feeder: Boolean = false)

    /** Una traiettoria: la palla (linea piena, col numero [order] se l'ordine conta) o uno spostamento (tratteggiata). */
    data class Path(val points: List<Pt>, val move: Boolean = false, val order: Int? = null)

    /** Una zona bersaglio (rettangolo da [a] a [b]). */
    data class Zone(val a: Pt, val b: Pt)

    data class Diagram(
        val players: List<Who>,
        val paths: List<Path> = emptyList(),
        val zones: List<Zone> = emptyList(),
        val cones: List<Pt> = emptyList(),
        /** Solo la metà vicina del campo (esercizi senza avversario): il disegno è più grande. */
        val half: Boolean = false,
    )

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
    data class Text(val name: String, val goal: String, val steps: List<String>, val easier: String, val harder: String)

    private val json = Json { ignoreUnknownKeys = true }

    /** I testi di una lingua dal file della raccolta (id → testi). */
    fun texts(file: String): Map<String, Text> = json.decodeFromString(file)

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
    private fun ball(vararg pts: Pt, order: Int? = null) = Path(pts.toList(), order = order)
    private fun move(vararg pts: Pt) = Path(pts.toList(), move = true)

    /** Le zone che tornano spesso: fondo campo lontano (tra la linea del servizio e quella di fondo), a sinistra o a destra. */
    private val farDeepLeft = Zone(p(-SINGLES, -BASE), p(0f, -SERVICE))
    private val farDeepRight = Zone(p(0f, -BASE), p(SINGLES, -SERVICE))
    private val nearDeepLeft = Zone(p(-SINGLES, SERVICE), p(0f, BASE))
    private val nearDeepRight = Zone(p(0f, SERVICE), p(SINGLES, BASE))

    val ITEMS: List<Item> = listOf(
        // Palleggio incrociato di dritto: la diagonale del pari, dritto contro dritto tra due destri.
        Item(
            "fh_cross", Topic.FOREHAND, Level.INTERMEDIATE, 2, 15,
            Diagram(
                listOf(Who(p(3f, 12.4f), "A"), Who(p(-3f, -12.4f), "B")),
                paths = listOf(ball(p(3f, 12.4f), p(-2.6f, -9.5f)), ball(p(-3f, -12.4f), p(2.6f, 9.5f))),
                zones = listOf(farDeepLeft, nearDeepRight),
            ),
            guided = TrainedStroke.FOREHAND to listOf(DrillStep(20, direction = ShotDirection.CROSS)),
        ),
        Item(
            "bh_cross", Topic.BACKHAND, Level.INTERMEDIATE, 2, 15,
            Diagram(
                listOf(Who(p(-3f, 12.4f), "A"), Who(p(3f, -12.4f), "B")),
                paths = listOf(ball(p(-3f, 12.4f), p(2.6f, -9.5f)), ball(p(3f, -12.4f), p(-2.6f, 9.5f))),
                zones = listOf(farDeepRight, nearDeepLeft),
            ),
            guided = TrainedStroke.BACKHAND to listOf(DrillStep(20, direction = ShotDirection.CROSS)),
        ),
        // A gioca sempre lungolinea, B sempre incrociato: la palla disegna un otto.
        Item(
            "line_cross", Topic.FOREHAND, Level.INTERMEDIATE, 2, 15,
            Diagram(
                listOf(Who(p(3f, 12.4f), "A"), Who(p(3f, -12.4f), "B")),
                paths = listOf(
                    ball(p(3f, 12.4f), p(3f, -10f), order = 1),
                    ball(p(3f, -12.4f), p(-3f, 10f), order = 2),
                    ball(p(-3f, 12.4f), p(-3f, -10f), order = 3),
                    ball(p(-3f, -12.4f), p(3f, 10f), order = 4),
                    move(p(3f, 12.6f), p(-2.6f, 12.6f)),
                    move(p(3f, -12.6f), p(-2.6f, -12.6f)),
                ),
            ),
            guided = TrainedStroke.FOREHAND to listOf(DrillStep(10, direction = ShotDirection.LINE), DrillStep(10, direction = ShotDirection.CROSS)),
        ),
        Item(
            "deep_rally", Topic.FOREHAND, Level.BEGINNER, 2, 10,
            Diagram(
                listOf(Who(p(0f, 12.4f), "A"), Who(p(0f, -12.4f), "B")),
                paths = listOf(ball(p(0f, 12.4f), p(-1f, -9.5f)), ball(p(0f, -12.4f), p(1f, 9.5f))),
                zones = listOf(Zone(p(-SINGLES, -BASE), p(SINGLES, -SERVICE)), Zone(p(-SINGLES, SERVICE), p(SINGLES, BASE))),
            ),
            guided = TrainedStroke.FOREHAND to listOf(DrillStep(20, direction = ShotDirection.MIDDLE)),
        ),
        Item(
            "mini_tennis", Topic.FOREHAND, Level.BEGINNER, 2, 10,
            Diagram(
                listOf(Who(p(0f, 5f), "A"), Who(p(0f, -5f), "B")),
                paths = listOf(ball(p(0f, 5f), p(-1.5f, -3.5f)), ball(p(0f, -5f), p(1.5f, 3.5f))),
                zones = listOf(Zone(p(-SINGLES, -SERVICE), p(SINGLES, 0f)), Zone(p(-SINGLES, 0f), p(SINGLES, SERVICE))),
            ),
        ),
        // Servizio dal lato del pari: al centro (T), al corpo, esterno.
        Item(
            "serve_targets", Topic.SERVE, Level.INTERMEDIATE, 1, 15,
            Diagram(
                listOf(Who(p(0.8f, 12.2f), "A")),
                paths = listOf(ball(p(0.8f, 12.2f), p(-0.5f, -5.6f)), ball(p(0.8f, 12.2f), p(-2f, -5.6f)), ball(p(0.8f, 12.2f), p(-3.6f, -5.6f))),
                cones = listOf(p(-0.5f, -5.6f), p(-2f, -5.6f), p(-3.6f, -5.6f)),
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
                listOf(Who(p(-0.8f, 12.2f), "A")),
                paths = listOf(ball(p(-0.8f, 12.2f), p(3f, -5.2f))),
                zones = listOf(Zone(p(0f, -SERVICE), p(SINGLES, -4.4f))),
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
                listOf(Who(p(-0.8f, -12.2f), "B"), Who(p(3.2f, 12f), "A")),
                paths = listOf(ball(p(-0.8f, -12.2f), p(2.5f, 5f), order = 1), ball(p(3.2f, 12f), p(0f, -9.5f), order = 2)),
                zones = listOf(Zone(p(-2f, -BASE), p(2f, -SERVICE))),
            ),
        ),
        Item(
            "volley_feed", Topic.VOLLEY, Level.BEGINNER, 1, 10,
            Diagram(
                listOf(Who(p(0f, 3.5f), "A"), Who(p(0f, -7f), "", feeder = true)),
                paths = listOf(
                    ball(p(0f, -7f), p(1.2f, 3.3f), order = 1), ball(p(1.2f, 3.3f), p(-2.8f, -8.5f)),
                    ball(p(0f, -7f), p(-1.2f, 3.3f), order = 2), ball(p(-1.2f, 3.3f), p(2.8f, -8.5f)),
                ),
                zones = listOf(farDeepLeft, farDeepRight),
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
                listOf(Who(p(1f, 12f), "A"), Who(p(0f, -10f), "", feeder = true)),
                paths = listOf(
                    ball(p(0f, -10f), p(1.5f, 7.5f), order = 1),
                    move(p(1f, 12f), p(1.5f, 8f)),
                    ball(p(1.5f, 7.5f), p(3.3f, -9f), order = 2),
                    move(p(1.5f, 7.8f), p(1f, 3.6f)),
                    ball(p(0f, -10f), p(0.2f, 3.3f), order = 3),
                    ball(p(0.2f, 3.3f), p(-3.2f, -7.5f), order = 4),
                ),
            ),
            guided = TrainedStroke.VOLLEY to listOf(DrillStep(10, direction = ShotDirection.CROSS)),
        ),
        Item(
            "smash", Topic.OVERHEAD, Level.INTERMEDIATE, 1, 10,
            Diagram(
                listOf(Who(p(0f, 2.5f), "A"), Who(p(0f, -11f), "", feeder = true)),
                paths = listOf(
                    move(p(0f, 2.5f), p(0.3f, 7f)),
                    ball(p(0f, -11f), p(0.4f, 7.3f), order = 1),
                    ball(p(0.4f, 7.3f), p(-2.5f, -8f), order = 2),
                    move(p(0.5f, 7.2f), p(0.2f, 2.8f)),
                ),
                zones = listOf(farDeepLeft),
            ),
        ),
        // A a rete contro due a fondo campo: volée alternate, una per parte.
        Item(
            "one_vs_two", Topic.VOLLEY, Level.ADVANCED, 3, 15,
            Diagram(
                listOf(Who(p(0f, 3.4f), "A"), Who(p(-2.8f, -12.3f), "B"), Who(p(2.8f, -12.3f), "C")),
                paths = listOf(
                    ball(p(-2.8f, -12.3f), p(1f, 3.2f), order = 1), ball(p(1f, 3.2f), p(2.6f, -10f), order = 2),
                    ball(p(2.8f, -12.3f), p(-1f, 3.2f), order = 3), ball(p(-1f, 3.2f), p(-2.6f, -10f), order = 4),
                ),
            ),
        ),
        Item(
            "drop_recover", Topic.FOOTWORK, Level.INTERMEDIATE, 2, 10,
            Diagram(
                listOf(Who(p(0f, 12.4f), "A"), Who(p(0f, -8f), "B")),
                paths = listOf(
                    ball(p(0f, -8f), p(1.2f, 2.2f), order = 1),
                    move(p(0f, 12.2f), p(1f, 2.8f)),
                    ball(p(1f, 2.6f), p(-2f, -5f), order = 2),
                    move(p(0.8f, 3.2f), p(0.2f, 11.8f)),
                ),
            ),
        ),
        // Il ragno: dal centro della linea di fondo a ogni cono e ritorno.
        Item(
            "spider", Topic.FITNESS, Level.INTERMEDIATE, 1, 10,
            Diagram(
                listOf(Who(p(0f, BASE), "A")),
                paths = listOf(
                    move(p(0f, 11.6f), p(-3.8f, 11.6f)), move(p(0f, 11.4f), p(-3.6f, SERVICE + 0.3f)), move(p(0f, 11.3f), p(0f, SERVICE + 0.4f)),
                    move(p(0f, 11.4f), p(3.6f, SERVICE + 0.3f)), move(p(0f, 11.6f), p(3.8f, 11.6f)),
                ),
                cones = listOf(p(-SINGLES, BASE), p(-SINGLES, SERVICE), p(0f, SERVICE), p(SINGLES, SERVICE), p(SINGLES, BASE)),
                half = true,
            ),
        ),
        Item(
            "split_step", Topic.FOOTWORK, Level.BEGINNER, 1, 10,
            Diagram(
                listOf(Who(p(0f, 12.2f), "A"), Who(p(0f, -7f), "", feeder = true)),
                paths = listOf(
                    ball(p(0f, -7f), p(2.8f, 10.5f), order = 1), move(p(0f, 12.2f), p(2.6f, 11.6f)),
                    ball(p(0f, -7f), p(-2.8f, 10.5f), order = 2), move(p(0f, 12.2f), p(-2.6f, 11.6f)),
                ),
            ),
        ),
        Item(
            "baseline_shuffle", Topic.FITNESS, Level.BEGINNER, 1, 8,
            Diagram(
                listOf(Who(p(0f, BASE), "A")),
                paths = listOf(move(p(0f, 11.4f), p(-3.9f, 11.4f)), move(p(0f, 11.1f), p(3.9f, 11.1f))),
                cones = listOf(p(-SINGLES, BASE), p(0f, BASE), p(SINGLES, BASE)),
                half = true,
            ),
        ),
        // Servizio esterno, risposta al centro, dritto nel campo aperto.
        Item(
            "serve_plus_one", Topic.TACTICS, Level.INTERMEDIATE, 2, 15,
            Diagram(
                listOf(Who(p(0.8f, 12.2f), "A"), Who(p(-3.4f, -12f), "B")),
                paths = listOf(
                    ball(p(0.8f, 12.2f), p(-3.6f, -5.4f), order = 1),
                    ball(p(-3.6f, -12f), p(0.6f, 9.5f), order = 2),
                    move(p(0.8f, 12.2f), p(0.8f, 10.2f)),
                    ball(p(0.8f, 9.8f), p(3.2f, -9f), order = 3),
                ),
                zones = listOf(farDeepRight),
            ),
        ),
        Item(
            "cross_points", Topic.TACTICS, Level.BEGINNER, 2, 20,
            Diagram(
                listOf(Who(p(2.6f, 12.4f), "A"), Who(p(-2.6f, -12.4f), "B")),
                paths = listOf(ball(p(2.6f, 12.4f), p(-2f, -8f)), ball(p(-2.6f, -12.4f), p(2f, 8f))),
                zones = listOf(Zone(p(-SINGLES, -BASE), p(0f, 0f)), Zone(p(0f, 0f), p(SINGLES, BASE))),
            ),
        ),
        // Re del campo: chi vince il punto resta (o sale) dalla parte del re.
        Item(
            "king_court", Topic.TACTICS, Level.INTERMEDIATE, 4, 20,
            Diagram(
                listOf(Who(p(0f, -12.4f), "A"), Who(p(0f, 12.4f), "B"), Who(p(5.9f, 2f), "C"), Who(p(5.9f, 3.2f), "D")),
                paths = listOf(ball(p(0f, 12.4f), p(-1.5f, -9f)), move(p(5.6f, 2.6f), p(1.5f, 12.4f))),
            ),
        ),
        // Doppio: B1 serve, B2 a rete incrocia sulla risposta incrociata e chiude in mezzo.
        Item(
            "doubles_poach", Topic.TACTICS, Level.ADVANCED, 4, 20,
            Diagram(
                listOf(
                    Who(p(0.8f, 12.2f), "A1"), Who(p(-2.5f, 3.6f), "A2"),
                    Who(p(-3.2f, -12f), "B1"), Who(p(2.5f, -6.4f), "B2"),
                ),
                paths = listOf(
                    ball(p(0.8f, 12.2f), p(-2.2f, -5f), order = 1),
                    ball(p(-3.2f, -12f), p(0.2f, 3.2f), order = 2),
                    move(p(-2.5f, 3.6f), p(0.2f, 3f)),
                    ball(p(0.2f, 3f), p(0f, -7.5f), order = 3),
                ),
            ),
        ),
    )
}
