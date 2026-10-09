// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui

import com.tennis.scoremanager.ble.BandProtocol

/** Español (España, terminología RFET). */
object EsStrings : Strings {
    override val playerTag: (Int) -> String = { "J$it" }
    override val datePattern = "EEEE, d 'de' MMMM 'de' yyyy"

    override val setupTitle = "Nuevo partido"
    override val setupSubtitle = "Configuración opcional: puedes dejarlo todo vacío y continuar."
    override val clubSection = "Club y pista"
    override val clubName = "Nombre del club de tenis"
    override val courtNumber = "Número de pista"
    override val singles = "Individual"
    override val doubles = "Dobles"
    override val doublesHint = "En dobles escribe dos nombres por pareja: el tanteo sigue las reglas ITF de dobles."
    override val player1 = "Jugador 1"
    override val player2 = "Jugador 2"
    override val playerName = "Nombre"
    override val clearFields = "Vaciar campos"
    override val next = "Siguiente"
    override val back = "Atrás"

    override val optionsTitle = "Modo y reglas"
    override val modeSection = "Modo de juego"
    override val modeReferee = "Juez"
    override val modeBands = "Pulseras"
    override val modeRefereeHint = "Los puntos se asignan desde el teléfono, como hace el juez de silla."
    override val modeBandsHint = "Cada jugador se anota el punto con KEY1 de su propio M5StickS3."
    override val requirements = "Requisitos"
    override val bluetooth = "Bluetooth"
    override val location = "Permiso de ubicación"
    override val locationServices = "Ubicación activada"
    override val enable = "Activar"
    override val allow = "Permitir"
    override val ok = "OK"
    override val bandFor: (String) -> String = { "Pulsera de $it" }
    override val noBand = "Ninguna"
    override val bandConnected = "Conectada"
    override val bandConnecting = "Conectando…"
    override val bandIdle = "No conectada"
    override val bandOff = "Apagada"
    override val battery = "Batería"
    override val bandCharging: (Int) -> String = { "Cargando $it %" }
    override val bandChargeFull = "Carga completa"
    override val autoSearch = "Búsqueda automática: enciende las pulseras (botón lateral) y se vinculan solas."
    override val autoSearchOff = "La búsqueda empieza cuando se cumplen los requisitos de arriba."
    override val identify = "Identificar"
    override val swapBands = "Intercambiar J1 ↔ J2"
    override val bandsOffAtEnd = "Apagar las pulseras al final del partido y al salir"
    override val bandsOffAtEndHint = "Se vuelven a encender con un clic en el botón lateral."
    override val bandNotReady = "Pulsera no conectada"

    override val bandSettings = "Ajustes de la pulsera"
    override val settingsShort = "Ajustes"
    override val bandSettingsNeedLink = "Conecta la pulsera para ver y cambiar sus ajustes."
    override val bandFirmwareOld = "El firmware de esta pulsera no tiene ajustes: carga TSM_Band.ino 2.0."
    override val bandFirmware: (String) -> String = { "Firmware $it" }
    override val bandName = "Nombre"
    override val brightness = "Brillo de la pantalla"
    override val scoreTime = "Marcador visible tras cada punto"
    override val scoreTimeHint = "El resumen de final de juego dura 2 segundos más."
    override val beeperVolume = "Volumen del pitido"
    override val mute = "Silencio"
    override val off = "No"
    override val flipDisplay = "Pantalla girada"
    override val flipDisplayHint = "Para llevar la pulsera en la otra muñeca."
    override val autoOff = "Apagado automático"
    override val pairTimeout = "Al encenderla, si no se conecta ningún teléfono"
    override val lostTimeout = "Si pierde la conexión con el teléfono"
    override val idleTimeout = "Si sigue conectada pero sin uso"
    override val estimateFull: (String) -> String = { "Autonomía estimada $it con la carga completa" }
    override val estimateNow: (String, Int) -> String = { h, p -> "$h con la carga actual ($p %)" }
    override val estimateBreakdown: (String, String, String, String) -> String = { tot, base, dsp, snd ->
        "Consumo medio $tot mA: placa y Bluetooth $base · pantalla $dsp · pitido $snd"
    }
    override val estimateMeasured = "Consumo base medido en esta pulsera durante el uso."
    override val estimateTheory = "Estimación teórica: tras 20 minutos de uso se corrige con el consumo medido."
    override val copyToOther = "Copiar a la otra pulsera"
    override val powerOff = "Apagar"
    override val bandPowerOffTitle = "¿Apagar la pulsera?"
    override val bandPowerOffText: (String) -> String = { "$it se apaga ahora mismo. Para volver a encenderla: un clic en el botón lateral." }
    override val languageSection = "Idioma"
    override val languageHint = "Se aplica a las pantallas, la voz del juez, el marcador de TV y las pulseras."
    override val audioSection = "Audio y voz"
    override val voiceCalls = "Cantos del juez por voz"
    override val voiceFiles: (Int, Int) -> String = { n, tot -> "Archivos generados: $n/$tot" }
    override val voiceFilesHint = "Todo funciona sin internet con las voces instaladas en el teléfono. Las grabaciones personalizadas (ZIP) siempre tienen prioridad sobre la síntesis de voz."
    override val generateVoice = "Generar archivos"
    override val generating: (Int, Int) -> String = { n, tot -> "Generando $n/$tot…" }
    override val voiceGenerationFailed = "Generación no completada: los archivos de voz anteriores no cambian."
    override val importVoiceZip = "Importar ZIP"
    override val voiceImportFailed = "ZIP ilegible o incompleto: no se ha cambiado ninguna grabación."
    override val deleteCustomVoice = "Quitar grabaciones"
    override val deleteCustomConfirmTitle = "¿Quitar las grabaciones?"
    override val deleteCustomConfirmText: (Int) -> String = { n -> "Las grabaciones personalizadas en español ($n) se borran del teléfono. Para recuperarlas, importa de nuevo el ZIP." }
    override val ttsEngine = "Motor de síntesis de voz"
    override val engineDefault = "Predeterminado del teléfono"
    override val ttsVoice = "Voz"
    override val voiceAuto = "Automática (la mejor sin conexión)"
    override val voiceName: (String) -> String = { "Voz $it" }
    override val online = "en línea"
    override val offline = "sin conexión"
    override val voiceFilesMode = "Usar archivos de audio pregenerados"
    override val voiceFilesModeHint = "Normalmente cada canto se lee en una sola frase (más natural). Actívalo para usar los archivos generados, por ejemplo para llevarte sin conexión una voz en línea."
    override val customRecordings: (Int, Int) -> String = { n, tot -> "Grabaciones personalizadas: $n/$tot" }
    override val voiceReadme: (String, String) -> String = { langs, formats ->
        "TENNIS SCORE MANAGER - ARCHIVOS DE VOZ\n" +
            "Pon las grabaciones en voice/<idioma>/ ($langs) con el nombre de la clave.\n" +
            "Formatos: $formats. Los nombres de los jugadores los lee siempre la síntesis de voz.\n" +
            "La carpeta tts/ contiene los archivos generados por la app: tus grabaciones tienen prioridad."
    }
    override val voiceReadmeKey = "CLAVE"
    override val testVoice = "Probar voz"
    override val stopVoiceTest = "Detener la prueba"
    override val ttsMissing = "La voz en español de la síntesis de voz no está instalada en el teléfono."
    override val installVoice = "Instalar voz"
    override val ttsEngineError = "Síntesis de voz no disponible: revisa el motor en los Ajustes de Android."
    override val openTtsSettings = "Abrir ajustes"
    override val formatSection = "Formato del partido"
    override val formatBestOfThree = "3 sets · tie-break a 7"
    override val formatBestOfThreeHint = "Al mejor de tres sets, tie-break con 6-6 en cada set."
    override val formatMatchTiebreak = "2 sets + súper tie-break a 10"
    override val formatMatchTiebreakHint = "Con un set iguales, el tercer set es un match tie-break a 10 puntos (2 de diferencia)."
    override val noAd = "Sin ventaja (punto decisivo)"
    override val noAdHint = "Con iguales se juega un solo punto: quien lo gana se lleva el juego."
    override val noAdShort = "Sin ventaja"
    override val coinToss = "Sorteo"
    override val tossCoin = "Lanzar la moneda"
    override val tossWinner: (String) -> String = { "Gana el sorteo: $it" }
    override val tossHint = "Quien gana elige: saque, resto o lado. Indica aquí la elección."
    override val serving = "Al servicio"
    override val courtSides = "Lados de la pista"
    override val umpireView = "Vista desde la silla del juez"
    override val swapSides = "Cambiar lados"
    override val firstServerOf: (String) -> String = { "Saca primero ($it)" }
    override val left = "Izquierda"
    override val right = "Derecha"
    override val net = "RED"
    override val umpireChair = "Juez de silla"

    override val locationDialogTitle = "Activa la ubicación"
    override val locationDialogText = "Si no activas la ubicación, el lugar no aparecerá en el resumen del partido."
    override val continueWithout = "Continuar sin ella"
    override val bandsRequiredTitle = "Bluetooth y ubicación obligatorios"
    override val bandsRequiredText = "Para usar las pulseras activa el Bluetooth, concede el permiso de ubicación y mantén la ubicación activada."
    override val bandsMissingTitle = "Pulseras no vinculadas"
    override val bandsMissingText: (List<String>) -> String = {
        (if (it.size > 1) "Faltan las pulseras de: " else "Falta la pulsera de: ") + it.joinToString(", ") + ". ¿Continuar de todos modos?"
    }
    override val continueAnyway = "Continuar"
    override val cancel = "Cancelar"

    override val startMatch = "INICIO DEL PARTIDO"
    override val startHint = "Pulsa el botón para empezar"
    override val startHintBands = "Pulsa el botón o KEY1 en una pulsera"
    override val startButton = "Empezar partido"
    override val resumeSaved = "Reanudar partido suspendido"
    override val noSavedMatches = "No hay partidos suspendidos guardados."
    override val savedMatchesTitle = "Partidos suspendidos"
    override val delete = "Eliminar"
    override val deleteSavedTitle = "¿Eliminar el partido suspendido?"
    override val deleteSavedText: (String) -> String = { "El partido $it ya no se podrá reanudar." }
    override val vs = "contra"

    override val matchTime = "Match Time"
    override val setsHeader = "SETS"
    override val gamesHeader = "JUEGOS"
    override val undoPoint = "Anular punto"
    override val suspend = "Suspender"
    override val resume = "Reanudar"
    override val audioOn = "Audio On"
    override val audioOff = "Audio Off"
    override val newMatch = "Nuevo partido"
    override val suspendedOverlay = "PARTIDO SUSPENDIDO"
    override val newMatchConfirmTitle = "¿Nuevo partido?"
    override val newMatchConfirmText = "El partido en curso queda guardado entre los partidos suspendidos y podrás reanudarlo."
    override val endDialogTitle = "Juego, set y partido"
    override val endDialogText: (String, String, Boolean) -> String = { name, score, team -> "${if (team) "Ganan" else "Gana"} $name\n$score" }
    override val matchConcluded = "Partido terminado"
    override val undoLastPoint = "Anular el último punto"
    override val serveOrderTitle: (Int) -> String = { "Orden de saque · set $it" }
    override val whoServesFirst: (String) -> String = { "¿Quién saca primero en $it?" }
    override val confirm = "Confirmar"
    override val backDisabled = "Durante el partido usa «Nuevo partido» o «Salir»."
    override val exit = "Salir"
    override val exitConfirmTitle = "¿Salir de la app?"
    override val exitConfirmText = "El partido queda guardado entre los partidos suspendidos y podrás reanudarlo."
    override val exitConfirmBands = "Las pulseras se apagarán."

    override val msgChangeEnds = "CAMBIO DE LADO"
    override val msgTiebreak = "TIE-BREAK"
    override val msgMatchTiebreak = "SÚPER TIE-BREAK"
    override val msgSetWon: (String) -> String = { "SET $it" }
    override val msgSetPoint = "BOLA DE SET"
    override val msgMatchPoint = "BOLA DE PARTIDO"
    override val msgBreakPoint = "BOLA DE BREAK"
    override val msgDecidingPoint = "PUNTO DECISIVO"
    override val msgPointUndone = "PUNTO ANULADO"
    override val msgSuspended = "PARTIDO SUSPENDIDO"
    override val msgResumed = "PARTIDO REANUDADO"
    override val msgBandConnected: (String) -> String = { "PULSERA $it CONECTADA" }
    override val msgBandLost: (String) -> String = { "PULSERA $it DESCONECTADA" }
    override val msgBandOff: (String, Int?) -> String = { n, why ->
        "PULSERA $n APAGADA" + when (why) {
            BandProtocol.OFF_IDLE -> " (SIN USO)"
            BandProtocol.OFF_BATTERY -> " (BATERÍA AGOTADA)"
            BandProtocol.OFF_TIMEOUT -> " (SIN TELÉFONO)"
            else -> ""
        }
    }
    override val msgBandBatteryLow: (String, Int) -> String = { n, p -> "PULSERA $n: BATERÍA $p %" }
    override val bandBatteryLow = "BATERÍA BAJA"
    override val autonomy: (String) -> String = { "autonomía ~$it" }
    override val bandsBattery = "Batería de las pulseras"

    override val notifChannel = "Partido en curso"
    override val notifText: (Boolean, Boolean) -> String = { bands, tv ->
        "Partido en curso · " + when {
            bands && tv -> "pulseras y marcador de TV activos"
            tv -> "marcador de TV activo"
            else -> "pulseras activas"
        }
    }
    override val notifTvOnly = "Marcador de TV activo"

    override val bandPaired = "VINCULADA A"
    override val bandPlay = "JUEGUEN"
    override val bandChangeEnds = "CAMBIO DE LADO"
    override val bandTiebreak = "TIE-BREAK"
    override val bandSet = "SET"
    override val bandSuspended = "SUSPENDIDO"
    override val bandGameSetMatch = "JUEGO SET PARTIDO"
    override val bandMatchOver = "FIN DEL PARTIDO"
    override val bandAppClosed = "APP CERRADA"
    override val bandOffFromApp = "APAGANDO"

    override val summaryTitle = "Partido terminado"
    override val winner = "Ganador"
    override val duration = "Duración"
    override val startTime = "Inicio"
    override val endTime = "Fin"
    override val date = "Fecha"
    override val club = "Club"
    override val court = "Pista"
    override val place = "Lugar"
    override val placeUnavailable = "Ubicación no disponible"
    override val format = "Formato"
    override val pointsWon = "Puntos ganados"
    override val gamesWon = "Juegos ganados"
    override val result = "Resultado"
    override val saveHistory = "Guardar en el historial"
    override val summaryLeaveTitle = "Resumen no guardado"
    override val summaryLeaveText = "No has guardado ni compartido el resumen del partido: después ya no podrás volver a verlo."
    override val share = "Compartir"
    override val saveDialogTitle = "Guardar en el historial"
    override val fileName = "Nombre"
    override val folder = "Carpeta"
    override val chooseFolder = "Elegir carpeta"
    override val defaultFolder = "Carpeta de la app (predeterminada)"
    override val formatReport = "Resumen (.txt)"
    override val formatData = "Datos del partido (.json)"
    override val formatImage = "Imagen (.png)"
    override val save = "Guardar"
    override val savedTo: (String) -> String = { "Guardado en $it" }
    override val saveError = "No se pudo guardar"
    override val shareSubject = "Resultado del partido de tenis"
    override val playerDefault: (Int) -> String = { "Jugador $it" }
    override val teamJoiner = " y "
    override val generatedWith = "Creado con Tennis Score Manager"

    override val tvGames = "JUEGOS"
    override val tvSet = "SETS"
    override val tvSec = "SEG"
    override val tvServe = "SAQUE"
    override val tvChangeover = "CAMBIO DE LADO"
    override val tvSetBreak = "DESCANSO"
    override val tvTiebreakBreak = "PAUSA"
    override val tvWaiting = "ESPERANDO EL PARTIDO"
    override val tvReady = "LISTOS PARA JUGAR"
    override val tvSuspended = "PARTIDO SUSPENDIDO"
    override val tvWinner = "GANADOR"
    override val tvMatchTiebreak = "SÚPER TIE-BREAK"
    override val tvLost = "CONEXIÓN PERDIDA - RECONECTANDO..."
    override val tvFullscreen = "PANTALLA COMPLETA"
    override val tvPageTitle = "Marcador TSM"

    override val tvSection = "Marcador en TV"
    override val tvEnable = "Marcador en TV o monitor"
    override val tvEnableHint = "Otro teléfono (o un ordenador, o un Chromecast) muestra el marcador en directo en un monitor. Los teléfonos deben estar en la misma red: el punto de acceso de uno de los dos."
    override val tvAddress = "Dirección del marcador"
    override val tvNoNetwork = "Sin red: activa el punto de acceso en uno de los dos teléfonos y conecta el otro."
    override val tvScreens: (Int) -> String = { if (it == 0) "Ningún marcador conectado" else if (it == 1) "1 marcador conectado" else "$it marcadores conectados" }
    override val tvQrHint = "En el otro teléfono: abre Tennis Score Manager y toca «Usar como marcador» (se conecta solo), o escanea el código con la cámara y ábrelo en el navegador."
    override val tvLook = "Aspecto del marcador"
    override val tvTitle = "Texto inferior"
    override val tvTitleHint: (String) -> String = { if (it.isEmpty()) "Vacío: sin texto" else "Vacío: «$it» (club y pista de la página 1)" }
    override val tvColorOf: (String) -> String = { "Color de $it" }
    override val tvShowClock = "Tiempo de partido"
    override val tvShowTimers = "Reloj de saque y descansos"
    override val tvShowSets = "Sets terminados"
    override val tvShowMessages = "Mensajes (bola de break, bola de set...)"
    override val tvShowServe = "Pelota junto a quien saca"
    override val tvGhost = "Segmentos apagados visibles"
    override val tvPreview = "Vista previa en este teléfono"
    override val tvChromecastHint = "Con un Chromecast: en el teléfono-marcador usa el botón «Enviar» de los ajustes rápidos («Enviar pantalla» hasta Android 14, «Smart View» en Samsung). El Chromecast necesita una red con internet: activa los datos móviles en el teléfono que hace de punto de acceso."

    override val displayMode = "Usar como marcador"
    override val displayModeHint = "Este teléfono muestra el marcador en el monitor (cable HDMI o Chromecast)"
    override val displaySearching = "Buscando el teléfono del juez…"
    override val displaySteps = "1. Activa el punto de acceso en uno de los dos teléfonos y conecta el otro.\n2. En el teléfono del juez: página 2 → «Marcador en TV» activado.\n3. Conecta este teléfono al monitor (cable USB-C/HDMI) o envía la pantalla a un Chromecast."
    override val displayManual = "Dirección (p. ej. 192.168.43.1:8080)"
    override val displayConnect = "Conectar"
    override val displayNotFound = "No encontrado. Comprueba que los dos teléfonos están en la misma red y que el marcador está activado en la app del juez."
    override val displayOnMonitor = "El marcador está en el monitor externo"
    override val displayShowHere = "Mostrar también aquí"
    override val displayBackAgain = "Pulsa atrás otra vez para salir"
    override val displayRetry = "Buscar de nuevo"
}
