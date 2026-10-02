package com.tennis.scoremanager.ui

import com.tennis.scoremanager.ble.BandProtocol

/**
 * Português: grafia do Brasil onde as variantes divergem (tela, salvar, quadra), termos das regras
 * da FPT e da CBT (árbitro de cadeira, sorteio, tie-break decisivo).
 */
object PtStrings : Strings {
    override val playerTag: (Int) -> String = { "J$it" }
    override val datePattern = "EEEE, d 'de' MMMM 'de' yyyy"

    override val setupTitle = "Nova partida"
    override val setupSubtitle = "Configuração opcional: você pode deixar tudo em branco e continuar."
    override val clubSection = "Clube e quadra"
    override val clubName = "Nome do clube de tênis"
    override val courtNumber = "Número da quadra"
    override val singles = "Simples"
    override val doubles = "Duplas"
    override val doublesHint = "Nas duplas, escreva dois nomes por equipe: a contagem segue as regras ITF de duplas."
    override val player1 = "Jogador 1"
    override val player2 = "Jogador 2"
    override val playerName = "Nome"
    override val clearFields = "Limpar campos"
    override val next = "Avançar"
    override val back = "Voltar"

    override val optionsTitle = "Modo e regras"
    override val modeSection = "Modo de jogo"
    override val modeReferee = "Árbitro"
    override val modeBands = "Pulseiras"
    override val modeRefereeHint = "Os pontos são marcados no telefone, como faz o árbitro de cadeira."
    override val modeBandsHint = "Cada jogador marca o ponto com o KEY1 do seu próprio M5StickS3."
    override val requirements = "Requisitos"
    override val bluetooth = "Bluetooth"
    override val location = "Permissão de localização"
    override val locationServices = "Localização ativada"
    override val enable = "Ativar"
    override val allow = "Permitir"
    override val ok = "OK"
    override val bandFor: (String) -> String = { "Pulseira de $it" }
    override val noBand = "Nenhuma"
    override val bandConnected = "Conectada"
    override val bandConnecting = "Conectando…"
    override val bandIdle = "Não conectada"
    override val bandOff = "Desligada"
    override val battery = "Bateria"
    override val bandCharging: (Int) -> String = { "Carregando $it%" }
    override val bandChargeFull = "Carga completa"
    override val autoSearch = "Busca automática: ligue as pulseiras (botão lateral), elas se pareiam sozinhas."
    override val autoSearchOff = "A busca começa quando os requisitos acima estiverem ok."
    override val identify = "Identificar"
    override val swapBands = "Trocar J1 ↔ J2"
    override val bandsOffAtEnd = "Desligar as pulseiras no fim da partida e ao sair"
    override val bandsOffAtEndHint = "Um clique no botão lateral volta a ligá-las."
    override val bandNotReady = "Pulseira não conectada"

    override val bandSettings = "Ajustes da pulseira"
    override val settingsShort = "Ajustes"
    override val bandSettingsNeedLink = "Conecte a pulseira para ver e mudar os ajustes."
    override val bandFirmwareOld = "O firmware desta pulseira não tem ajustes: carregue o TSM_Band.ino 2.0."
    override val bandFirmware: (String) -> String = { "Firmware $it" }
    override val bandName = "Nome"
    override val brightness = "Brilho da tela"
    override val scoreTime = "Placar visível após cada ponto"
    override val scoreTimeHint = "O resumo do fim do jogo fica 2 segundos a mais."
    override val beeperVolume = "Volume do bipe"
    override val mute = "Mudo"
    override val off = "Não"
    override val flipDisplay = "Tela invertida"
    override val flipDisplayHint = "Para usar a pulseira no outro pulso."
    override val autoOff = "Desligamento automático"
    override val pairTimeout = "Ao ligar, se nenhum telefone se conectar"
    override val lostTimeout = "Se perder a conexão com o telefone"
    override val idleTimeout = "Se ficar conectada mas sem uso"
    override val estimateFull: (String) -> String = { "Autonomia estimada $it com a carga completa" }
    override val estimateNow: (String, Int) -> String = { h, p -> "$h com a carga atual ($p%)" }
    override val estimateBreakdown: (String, String, String, String) -> String = { tot, base, dsp, snd ->
        "Consumo médio $tot mA: placa e Bluetooth $base · tela $dsp · bipe $snd"
    }
    override val estimateMeasured = "Consumo base medido nesta pulseira durante o uso."
    override val estimateTheory = "Estimativa teórica: após 20 minutos de uso ela é corrigida com o consumo medido."
    override val copyToOther = "Copiar para a outra pulseira"
    override val powerOff = "Desligar"
    override val bandPowerOffTitle = "Desligar a pulseira?"
    override val bandPowerOffText: (String) -> String = { "$it desliga agora. Para ligá-la de novo: um clique no botão lateral." }
    override val languageSection = "Idioma"
    override val languageHint = "Vale para as telas, a voz do árbitro, o placar na TV e as pulseiras."
    override val audioSection = "Áudio e voz"
    override val voiceCalls = "Anúncios do árbitro por voz"
    override val voiceFiles: (Int, Int) -> String = { n, tot -> "Arquivos gerados: $n/$tot" }
    override val voiceFilesHint = "Tudo funciona sem internet com as vozes instaladas no telefone. As gravações personalizadas (ZIP) sempre têm prioridade sobre a síntese de voz."
    override val generateVoice = "Gerar arquivos"
    override val generating: (Int, Int) -> String = { n, tot -> "Gerando $n/$tot…" }
    override val importVoiceZip = "Importar ZIP"
    override val deleteCustomVoice = "Remover gravações"
    override val ttsEngine = "Mecanismo de síntese de voz"
    override val engineDefault = "Padrão do telefone"
    override val ttsVoice = "Voz"
    override val voiceAuto = "Automática (melhor offline)"
    override val voiceName: (String) -> String = { "Voz $it" }
    override val online = "online"
    override val offline = "offline"
    override val voiceFilesMode = "Usar arquivos de áudio pré-gerados"
    override val voiceFilesModeHint = "Normalmente cada anúncio é lido numa única frase (mais natural). Ative para usar os arquivos gerados, por exemplo para levar offline uma voz online."
    override val customRecordings: (Int, Int) -> String = { n, tot -> "Gravações personalizadas: $n/$tot" }
    override val voiceReadme: (String, String) -> String = { langs, formats ->
        "TENNIS SCORE MANAGER - ARQUIVOS DE VOZ\n" +
            "Coloque as gravações em voice/<idioma>/ ($langs) com o nome da chave.\n" +
            "Formatos: $formats. Os nomes dos jogadores são sempre lidos pela síntese de voz.\n" +
            "A pasta tts/ contém os arquivos gerados pelo app: suas gravações têm prioridade."
    }
    override val voiceReadmeKey = "CHAVE"
    override val testVoice = "Testar voz"
    override val stopVoiceTest = "Parar o teste"
    override val ttsMissing = "A voz em português da síntese de voz não está instalada no telefone."
    override val installVoice = "Instalar voz"
    override val formatSection = "Formato da partida"
    override val formatBestOfThree = "3 sets · tie-break a 7"
    override val formatBestOfThreeHint = "Melhor de três sets, tie-break no 6-6 em cada set."
    override val formatMatchTiebreak = "2 sets + tie-break decisivo a 10"
    override val formatMatchTiebreakHint = "No 1-1 em sets, o terceiro set é um tie-break decisivo a 10 pontos (2 de diferença)."
    override val noAd = "Sem vantagem (ponto decisivo)"
    override val noAdHint = "No 40-40 joga-se um único ponto: quem ganhar leva o jogo."
    override val noAdShort = "Sem vantagem"
    override val coinToss = "Sorteio"
    override val tossCoin = "Jogar a moeda"
    override val tossWinner: (String) -> String = { "Venceu o sorteio: $it" }
    override val tossHint = "Quem vence escolhe: serviço, recepção ou lado. Marque a escolha aqui."
    override val serving = "No serviço"
    override val courtSides = "Lados da quadra"
    override val umpireView = "Vista da cadeira do árbitro"
    override val swapSides = "Inverter lados"
    override val firstServerOf: (String) -> String = { "Serve primeiro ($it)" }
    override val left = "Esquerda"
    override val right = "Direita"
    override val net = "REDE"
    override val umpireChair = "Árbitro de cadeira"

    override val locationDialogTitle = "Ative a localização"
    override val locationDialogText = "Sem a localização, o local não aparece no resumo da partida."
    override val continueWithout = "Continuar sem"
    override val bandsRequiredTitle = "Bluetooth e localização obrigatórios"
    override val bandsRequiredText = "Para usar as pulseiras, ative o Bluetooth, conceda a permissão de localização e mantenha a localização ativada."
    override val bandsMissingTitle = "Pulseiras não associadas"
    override val bandsMissingText: (List<String>) -> String = {
        (if (it.size > 1) "Faltam as pulseiras de: " else "Falta a pulseira de: ") + it.joinToString(", ") + ". Continuar mesmo assim?"
    }
    override val continueAnyway = "Continuar"
    override val cancel = "Cancelar"

    override val startMatch = "INÍCIO DA PARTIDA"
    override val startHint = "Toque no botão para começar"
    override val startHintBands = "Toque no botão ou aperte KEY1 numa pulseira"
    override val startButton = "Começar partida"
    override val resumeSaved = "Retomar partida suspensa"
    override val noSavedMatches = "Nenhuma partida suspensa salva."
    override val savedMatchesTitle = "Partidas suspensas"
    override val delete = "Excluir"
    override val deleteSavedTitle = "Excluir a partida suspensa?"
    override val deleteSavedText: (String) -> String = { "A partida $it não poderá mais ser retomada." }
    override val vs = "x"

    override val matchTime = "Match Time"
    override val setsHeader = "SETS"
    override val gamesHeader = "JOGOS"
    override val undoPoint = "Anular ponto"
    override val suspend = "Suspender"
    override val resume = "Retomar"
    override val audioOn = "Audio On"
    override val audioOff = "Audio Off"
    override val newMatch = "Nova partida"
    override val suspendedOverlay = "PARTIDA SUSPENSA"
    override val newMatchConfirmTitle = "Nova partida?"
    override val newMatchConfirmText = "A partida em andamento fica salva entre as partidas suspensas e você poderá retomá-la."
    override val endDialogTitle = "Jogo, set e partida"
    override val endDialogText: (String, String, Boolean) -> String = { name, score, _ -> "Vitória de $name\n$score" }
    override val matchConcluded = "Partida encerrada"
    override val undoLastPoint = "Anular o último ponto"
    override val serveOrderTitle: (Int) -> String = { "Ordem de serviço · set $it" }
    override val whoServesFirst: (String) -> String = { "Quem serve primeiro em $it?" }
    override val confirm = "Confirmar"
    override val backDisabled = "Durante a partida use «Nova partida» ou «Sair»."
    override val exit = "Sair"
    override val exitConfirmTitle = "Sair do app?"
    override val exitConfirmText = "A partida fica salva entre as partidas suspensas e você poderá retomá-la."
    override val exitConfirmBands = "As pulseiras serão desligadas."

    override val msgChangeEnds = "TROCA DE LADO"
    override val msgTiebreak = "TIE-BREAK"
    override val msgMatchTiebreak = "TIE-BREAK DECISIVO"
    override val msgSetWon: (String) -> String = { "SET $it" }
    override val msgSetPoint = "SET POINT"
    override val msgMatchPoint = "MATCH POINT"
    override val msgBreakPoint = "BREAK POINT"
    override val msgDecidingPoint = "PONTO DECISIVO"
    override val msgPointUndone = "PONTO ANULADO"
    override val msgSuspended = "PARTIDA SUSPENSA"
    override val msgResumed = "PARTIDA RETOMADA"
    override val msgBandConnected: (String) -> String = { "PULSEIRA $it CONECTADA" }
    override val msgBandLost: (String) -> String = { "PULSEIRA $it DESCONECTADA" }
    override val msgBandOff: (String, Int?) -> String = { n, why ->
        "PULSEIRA $n DESLIGADA" + when (why) {
            BandProtocol.OFF_IDLE -> " (SEM USO)"
            BandProtocol.OFF_BATTERY -> " (BATERIA VAZIA)"
            BandProtocol.OFF_TIMEOUT -> " (SEM TELEFONE)"
            else -> ""
        }
    }
    override val msgBandBatteryLow: (String, Int) -> String = { n, p -> "PULSEIRA $n: BATERIA $p%" }
    override val bandBatteryLow = "BATERIA FRACA"
    override val autonomy: (String) -> String = { "autonomia ~$it" }
    override val bandsBattery = "Bateria das pulseiras"

    override val notifChannel = "Partida em andamento"
    override val notifText: (Boolean, Boolean) -> String = { bands, tv ->
        "Partida em andamento · " + when {
            bands && tv -> "pulseiras e placar na TV ativos"
            tv -> "placar na TV ativo"
            else -> "pulseiras ativas"
        }
    }

    override val bandPaired = "PAREADA COM"
    override val bandPlay = "JOGUEM"
    override val bandChangeEnds = "TROCA DE LADO"
    override val bandTiebreak = "TIE-BREAK"
    override val bandSet = "SET"
    override val bandSuspended = "SUSPENSA"
    override val bandGameSetMatch = "JOGO SET PARTIDA"
    override val bandMatchOver = "FIM DA PARTIDA"
    override val bandAppClosed = "APP FECHADO"
    override val bandOffFromApp = "DESLIGANDO"

    override val summaryTitle = "Partida encerrada"
    override val winner = "Vencedor"
    override val duration = "Duração"
    override val startTime = "Início"
    override val endTime = "Fim"
    override val date = "Data"
    override val club = "Clube"
    override val court = "Quadra"
    override val place = "Local"
    override val placeUnavailable = "Localização indisponível"
    override val format = "Formato"
    override val pointsWon = "Pontos ganhos"
    override val gamesWon = "Jogos ganhos"
    override val result = "Resultado"
    override val saveHistory = "Salvar no histórico"
    override val summaryLeaveTitle = "Resumo não salvo"
    override val summaryLeaveText = "Você não salvou nem compartilhou o resumo da partida: depois não poderá mais vê-lo."
    override val share = "Compartilhar"
    override val saveDialogTitle = "Salvar no histórico"
    override val fileName = "Nome"
    override val folder = "Pasta"
    override val chooseFolder = "Escolher pasta"
    override val defaultFolder = "Pasta do app (padrão)"
    override val formatReport = "Relatório (.txt)"
    override val formatData = "Dados da partida (.json)"
    override val formatImage = "Imagem (.png)"
    override val save = "Salvar"
    override val savedTo: (String) -> String = { "Salvo em $it" }
    override val saveError = "Não foi possível salvar"
    override val shareSubject = "Resultado da partida de tênis"
    override val playerDefault: (Int) -> String = { "Jogador $it" }
    override val teamJoiner = " e "
    override val generatedWith = "Criado com Tennis Score Manager"

    override val tvGames = "JOGOS"
    override val tvSet = "SETS"
    override val tvSec = "SEG"
    override val tvServe = "SERVIÇO"
    override val tvChangeover = "TROCA DE LADO"
    override val tvSetBreak = "INTERVALO"
    override val tvTiebreakBreak = "PAUSA"
    override val tvWaiting = "AGUARDANDO A PARTIDA"
    override val tvReady = "PRONTOS PARA JOGAR"
    override val tvSuspended = "PARTIDA SUSPENSA"
    override val tvWinner = "VENCEDOR"
    override val tvMatchTiebreak = "TIE-BREAK DECISIVO"
    override val tvLost = "CONEXÃO PERDIDA - RECONECTANDO..."
    override val tvFullscreen = "TELA CHEIA"
    override val tvPageTitle = "Placar TSM"

    override val tvSection = "Placar na TV"
    override val tvEnable = "Placar na TV ou monitor"
    override val tvEnableHint = "Outro telefone (ou um computador, ou um Chromecast) mostra o placar ao vivo num monitor. Os telefones precisam estar na mesma rede: o hotspot de um dos dois."
    override val tvAddress = "Endereço do placar"
    override val tvNoNetwork = "Sem rede: ative o hotspot num dos dois telefones e conecte o outro."
    override val tvScreens: (Int) -> String = { if (it == 0) "Nenhum placar conectado" else if (it == 1) "1 placar conectado" else "$it placares conectados" }
    override val tvQrHint = "No outro telefone: abra o Tennis Score Manager e toque em «Usar como placar» (conecta sozinho), ou leia o código com a câmera e abra no navegador."
    override val tvLook = "Aparência do placar"
    override val tvTitle = "Texto embaixo"
    override val tvTitleHint: (String) -> String = { if (it.isEmpty()) "Vazio: sem texto" else "Vazio: «$it» (clube e quadra da página 1)" }
    override val tvColorOf: (String) -> String = { "Cor de $it" }
    override val tvShowClock = "Tempo de partida"
    override val tvShowTimers = "Relógio de serviço e pausas"
    override val tvShowSets = "Sets terminados"
    override val tvShowMessages = "Mensagens (break point, set point...)"
    override val tvShowServe = "Bola ao lado de quem serve"
    override val tvGhost = "Segmentos apagados visíveis"
    override val tvPreview = "Prévia neste telefone"
    override val tvChromecastHint = "Com um Chromecast: no telefone-placar use o botão de transmissão das configurações rápidas («Transmitir», «Transmissão» ou «Transmissão de tela», conforme a versão do Android; «Smart View» nos Samsung). O Chromecast precisa de uma rede com internet: ative os dados móveis no telefone que faz o hotspot."

    override val displayMode = "Usar como placar"
    override val displayModeHint = "Este telefone mostra o placar no monitor (cabo HDMI ou Chromecast)"
    override val displaySearching = "Procurando o telefone do árbitro…"
    override val displaySteps = "1. Ative o hotspot num dos dois telefones e conecte o outro.\n2. No telefone do árbitro: página 2 → «Placar na TV» ativado.\n3. Ligue este telefone ao monitor (cabo USB-C/HDMI) ou transmita a tela para um Chromecast."
    override val displayManual = "Endereço (ex. 192.168.43.1:8080)"
    override val displayConnect = "Conectar"
    override val displayNotFound = "Não encontrado. Verifique se os dois telefones estão na mesma rede e se o placar está ativado no app do árbitro."
    override val displayOnMonitor = "O placar está no monitor externo"
    override val displayShowHere = "Mostrar aqui também"
    override val displayBackAgain = "Toque em voltar de novo para sair"
    override val displayRetry = "Procurar de novo"
}
