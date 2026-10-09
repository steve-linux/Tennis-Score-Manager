# Tennis Score Manager — guia passo a passo

[Italiano](GUIDA.md) · [English](GUIDE.en.md) · [Français](GUIDE.fr.md) · [Deutsch](GUIDE.de.md) · [Español](GUIDE.es.md) · **Português**

App Android (Kotlin + Jetpack Compose) para marcar o placar do tênis segundo as regras da ITF, com anúncios por voz de árbitro de cadeira **em seis idiomas** (italiano, inglês, francês, alemão, espanhol, português: capítulo 4.1), duas pulseiras **M5StickS3** conectadas por Bluetooth LE e um **placar de LED na TV ou no monitor** (capítulo 8).

## 0. O que há no repositório

| Caminho | Para que serve |
|---|---|
| `TennisScoreManager/` | O projeto do Android Studio. |
| `firmware/TSM_Band/TSM_Band.ino` | O firmware da pulseira. |
| `deliver/installa_tsm.sh` | Alternativa ao clone: cria **todo** o projeto Android e o sketch só com blocos `cat << 'TSM_EOF'` (o jar do Gradle wrapper está em base64). |
| `deliver/GUIDE.pt.md` | Este guia (em português); o original em italiano é `deliver/GUIDA.md` e as outras traduções são `deliver/GUIDE.en.md`, `.fr`, `.de`, `.es`. |

Versões usadas e verificadas: app **2.4.1** · firmware **2.3** · Gradle 8.14.3 · Android Gradle Plugin 8.13.2 · Kotlin 2.2.21 · Compose BOM 2025.12.00 · compileSdk/targetSdk 36 · minSdk 26 (Android 8.0). Firmware: M5Unified ≥ 0.2.12, NimBLE-Arduino ≥ 2.1, core ESP32 3.x. Licença: GNU GPL versão 3 ou posterior (`LICENSE`); o app mostra a versão e a licença no fim da primeira página.

---

## 1. Preparar o computador e baixar o projeto

Funciona em **Windows 10/11**, **Ubuntu** (22.04 ou mais recente) ou **Fedora**; em outras distribuições Linux os passos são os do Ubuntu ou do Fedora, com o gerenciador de pacotes de cada uma. Nos comandos, `~` é a sua pasta pessoal: no Linux `/home/<usuário>`, no Windows `C:\Users\<usuário>`.

### 1.1 Programas para instalar

| | Windows 10/11 (PowerShell) | Ubuntu | Fedora |
|---|---|---|---|
| **Git** (baixar e atualizar o projeto) | `winget install --id Git.Git -e` | `sudo apt install git` | `sudo dnf install git` |
| **Android Studio** (o app) | `winget install --id Google.AndroidStudio -e` | `sudo snap install android-studio --classic` | pacote `.tar.gz` de developer.android.com/studio, extraído por exemplo em `~/android-studio` |
| **Arduino IDE 2** (as pulseiras) | `winget install --id ArduinoSA.IDE.stable -e` | Flatpak `cc.arduino.IDE2` (veja abaixo) | `flatpak install flathub cc.arduino.IDE2` |
| **Telefone conectado pelo cabo** | driver USB do fabricante, se necessário (veja abaixo) | `sudo apt install android-sdk-platform-tools-common` | `sudo dnf install android-tools` |
| **Porta serial da pulseira** | nada a fazer | `sudo usermod -aG dialout $USER` | `sudo usermod -aG dialout $USER` |

- **Windows**: o `winget` já vem no Windows 10/11 atualizado e é usado no *PowerShell* ou no *Terminal*. Também é possível baixar os instaladores normais em git-scm.com, developer.android.com/studio e arduino.cc/en/software.
- **Ubuntu, Arduino IDE**: se o Flatpak ainda não estiver instalado: `sudo apt install flatpak`, depois `flatpak remote-add --if-not-exists flathub https://dl.flathub.org/repo/flathub.flatpakrepo` e `flatpak install flathub cc.arduino.IDE2`; encerre a sessão e entre de novo para que ele apareça no menu. O AppImage do arduino.cc também funciona, mas nos Ubuntu recentes exige pacotes e opções extras: o Flatpak é mais simples.
- **Linux, Android Studio a partir do pacote**: é iniciado com `bin/studio.sh` dentro da pasta extraída (ex. `~/android-studio/bin/studio.sh`). No Ubuntu o snap também serve; no Fedora existe ainda o Flatpak `com.google.AndroidStudio`, mas o pacote oficial dá menos problemas com o telefone conectado.
- **Linux, grupo `dialout`** (porta serial) e pacotes para o telefone (regras udev): depois de instalá-los, **encerre a sessão e entre de novo** (ou reinicie), senão a porta e o telefone continuam inacessíveis.
- **Windows, driver do telefone**: muitos telefones funcionam de imediato. Se o Android Studio não reconhecer o telefone, instale o driver do fabricante: *Samsung Android USB Driver* (do site Samsung Developer) para os Samsung, *Google USB Driver* (Android Studio › **Tools › SDK Manager › SDK Tools**) para os Pixel. A pulseira não precisa de driver: aparece como porta `COM3`, `COM4`…
- Na primeira execução o Android Studio abre um assistente: escolha **Standard** e deixe que ele baixe o SDK do Android (precisa de internet, alguns GB).

### 1.2 Baixar o projeto

O repositório no GitHub é **privado**: é preciso uma conta GitHub à qual o proprietário tenha dado acesso.

**A. Com git (recomendado: depois basta atualizar com `git pull`)**

Linux (Ubuntu, Fedora), no Terminal:

```bash
mkdir -p ~/AndroidStudioProjects
cd ~/AndroidStudioProjects
git clone https://github.com/steve-linux/Tennis-Score-Manager.git
```

Windows, no PowerShell:

```powershell
mkdir -Force $HOME\AndroidStudioProjects
cd $HOME\AndroidStudioProjects
git clone https://github.com/steve-linux/Tennis-Score-Manager.git
```

- **Acesso**: no Windows o git abre sozinho a janela de login do GitHub. No Linux o GitHub não aceita a senha no `git clone`: o jeito mais simples é o GitHub CLI (`sudo apt install gh` ou `sudo dnf install gh`), depois `gh auth login` e, na pasta `~/AndroidStudioProjects`, `gh repo clone steve-linux/Tennis-Score-Manager`.
- O repositório fica em `~/AndroidStudioProjects/Tennis-Score-Manager`. O projeto a abrir no Android Studio é a subpasta **`TennisScoreManager`**; o sketch da pulseira está em `firmware/TSM_Band/`.
- Para atualizar: `git pull` dentro de `Tennis-Score-Manager`.

**B. Sem git**: na página do projeto no GitHub (com o login feito) **Code › Download ZIP**, depois extraia o ZIP, por exemplo em `~/AndroidStudioProjects`. Para uma versão nova, baixe o ZIP de novo.

**C. Com o script** `deliver/installa_tsm.sh`, que contém todo o projeto num único arquivo de texto (blocos `cat << 'TSM_EOF'`). Ele roda no Linux ou, no Windows, no *Git Bash* (que vem com o Git):

```bash
bash installa_tsm.sh
```

- O projeto vai para `~/AndroidStudioProjects/TennisScoreManager`, o sketch para `~/Arduino/TSM_Band/TSM_Band.ino`.
- Se a pasta do projeto já existir, ela é **movida** para `TennisScoreManager.backup-AAAAMMDD-hhmmss` (os layouts XML e as classes antigas fariam o build novo falhar).
- Para usar outras pastas: `bash installa_tsm.sh /caminho/projeto /caminho/sketch`.

## 2. Abrir e compilar no Android Studio

1. Abra o Android Studio: no Windows pelo menu Iniciar, no Ubuntu (snap) pelo menu de aplicativos, a partir do pacote com `bin/studio.sh` (1.1).
2. **File › Open** (ou **Open** na tela de boas-vindas) → escolha a pasta `TennisScoreManager` (dentro do clone ou do ZIP, ou a criada pelo script) → **Trust Project**.
3. Aguarde a sincronização do Gradle (na primeira vez ele baixa o Gradle, o plugin Android e as bibliotecas: só agora é preciso internet).
4. Se aparecer *"Failed to find target android-36"*, clique no link **Install missing platform** (ou **Tools › SDK Manager › SDK Platforms › Android 16 (API 36)**).
5. Se o Android Studio sugerir o **AGP Upgrade Assistant**, pode ignorá-lo: estas versões foram compiladas e testadas assim.
6. JDK do Gradle: **Settings › Build, Execution, Deployment › Build Tools › Gradle › Gradle JDK** = *jbr-21* (o incluído, que é o padrão).
7. **Build › Make Project**: deve terminar com *BUILD SUCCESSFUL*.
8. Opcional: os testes (95: regras, voz, idiomas, bateria e recarga, ajustes das pulseiras, placar na TV) são executados com o botão direito em `app/src/test` › **Run Tests**.

## 3. Instalar o app no telefone

1. No telefone: **Configurações › Sobre o telefone** → toque 7 vezes em *Número da versão* (nos Samsung: *Informações do software › Número de compilação*) → **Opções do desenvolvedor › Depuração USB** ativada.
2. Conecte o cabo, aceite a impressão digital da chave RSA, escolha o telefone na barra superior e clique em **▶ Run**.
   - Se o computador não reconhecer o telefone: no Windows, o driver USB do fabricante; no Linux, o pacote com as regras udev (1.1); depois reconecte o cabo. Como alternativa, sem cabo nem driver: **Device Manager › Pair devices using Wi-Fi** (depuração por Wi-Fi, Android 11+, computador e telefone na mesma rede).
3. Como alternativa, **Build › Build App Bundle(s) / APK(s) › Build APK(s)**, copie o APK para o telefone e instale-o (é preciso permitir a instalação de fontes desconhecidas).

## 4. A voz (funciona sem internet)

- Cada anúncio é lido **numa única frase** pela síntese de voz do telefone, com uma voz instalada: sem internet e com prosódia natural. Os nomes dos jogadores entram na frase.
- Página 2 › **Áudio e voz**:
  - **Mecanismo de síntese de voz**: *Padrão do telefone* (nos Samsung é o Samsung TTS) ou um mecanismo à sua escolha, por exemplo *Serviços de voz do Google*. Nos testes, feitos em italiano, o Google soou mais natural.
  - **Voz**: *Automática (melhor offline)* ou uma voz específica (o Google lista as vozes por código, como *Voz* seguido de três letras; as marcadas como *online* precisam de internet).
  - **Testar voz**: lê uma sequência de anúncios de exemplo com os nomes digitados. Enquanto lê, o botão vira **Parar o teste**: toque de novo para interrompê-lo (ele também para sozinho quando você sai da página). A síntese de voz não pode ser pausada no meio de uma frase, por isso o botão a interrompe; tocando de novo, ela recomeça do início.
- Se faltar a voz do idioma escolhido: **Configurações › Gerenciamento geral › Idioma › Conversão de texto em voz** (ou *Saída de conversão de texto em voz*) → baixe a voz desse idioma para o mecanismo escolhido (o app também mostra o botão **Instalar voz**). Se a síntese de voz nem chega a iniciar, o app avisa com uma mensagem própria e o botão **Abrir configurações** (configurações de síntese de voz do Android).
- **Pronúncia**: alguns mecanismos erram palavras do tênis (em italiano, "primo set" lido como "primo settembre", "tie-break" lido como "time break"). O app as corrige sozinho (`voice/Pronunciation.kt`); as correções foram verificadas transcrevendo o áudio real do Samsung e do Google.
- **Gravações personalizadas** (a voz mais natural de todas: a sua ou a de um árbitro): na pasta `Android/data/com.tennis.scoremanager/files/voice/` há o `LEGGIMI.txt` ("leia-me"; as explicações do início ficam no idioma do app e são reescritas quando você o muda) com a lista das 85 chaves e das frases. Grave os arquivos com esses nomes (`score_1_0.mp3` = "quinze-zero"…), coloque-os num ZIP com uma pasta por idioma (`it/`, `en/`, `fr/`, `de/`, `es/`, `pt/`) e use **Importar ZIP**. O `LEGGIMI.txt` tem uma coluna por idioma, separadas por tabulações, então também abre bem como planilha. As gravações sempre têm prioridade sobre a síntese; os nomes continuam sendo lidos pela síntese. No ZIP vale a pasta que contém o arquivo (também dentro de outras pastas, ex. `voice/pt/`); arquivos fora de uma pasta de idioma vão para o idioma atual e as pastas `tts/` são ignoradas, então dá para compactar também a pasta `voice/` do app. Um ZIP danificado ou incompleto não muda nada (*ZIP ilegível ou incompleto*). **Remover gravações** pede confirmação. Se um arquivo não puder ser reproduzido, a frase é dita pela síntese de voz e o arquivo não é mais usado até o app reiniciar.
- **Usar arquivos de áudio pré-gerados** (opcional): com **Gerar arquivos** o app cria uma vez os 85 arquivos com a voz escolhida (até mesmo uma voz *online*, se naquele momento houver internet) e depois os usa no lugar da síntese contínua. Soa mais "picotado", mas é útil para levar offline uma voz online. Os arquivos anteriores ficam até os novos estarem prontos (se a geração não chegar ao fim: *Geração não concluída*); durante a geração, idioma, mecanismo, voz, teste de voz e áudio ficam bloqueados.
- **Caixa de som externa** (coluna): basta pareá-la com o telefone por Bluetooth; a voz sai pelo canal de mídia (ajuste o volume de mídia).

### 4.1 Idiomas (app 2.3, firmware 2.2)

Página 2 › **Idioma**: Italiano, English, Français, Deutsch, Español, Português (cada idioma aparece escrito no próprio idioma, para que você o encontre mesmo com o app num idioma que não lê). A escolha vale ao mesmo tempo para **telas, voz do árbitro, placar na TV, resumo/compartilhamento e pulseiras**. As datas seguem o idioma (*mercredi 30 septembre 2026*, *Mittwoch, 30. September 2026*…).

Os anúncios não são traduções palavra por palavra: seguem os textos oficiais para árbitros de cadeira de cada federação, ou seja, FFT *L'arbitrage en 255 questions* e ITF em francês, os materiais DTB/BTV e Swiss Tennis, RFET *Deberes y procedimientos*, FPT *Deveres e Procedimentos* (o único roteiro português publicado). O inglês segue a ITF. Em inglês, o zero dos sets no fim da partida é dito *love* ("six love, six four"), como nos pontos.

| | Italiano | English | Français | Deutsch | Español | Português |
|---|---|---|---|---|---|---|
| Início | primo set · Rossi al servizio · gioco | first set · Rossi to serve · play | première manche · au service Rossi · jouez | erster Satz · Aufschlag Rossi · spielen | primer set · al servicio Rossi · jueguen | primeiro set · Rossi ao serviço · joguem |
| 15-0 · 15-15 | quindici zero · quindici pari | fifteen love · fifteen all | quinze-zéro · quinze A | fünfzehn null · fünfzehn beide | quince cero · quince iguales | quinze-zero · quinze iguais |
| 40-40 · vantagem | parità · vantaggio Rossi | deuce · advantage Rossi | égalité · avantage Rossi | Einstand · Vorteil Rossi | iguales · ventaja Rossi | iguais · vantagem Rossi |
| Fim do jogo | gioco Rossi, Rossi conduce tre giochi a due | game Rossi, Rossi leads three games to two | jeu Rossi, Rossi mène trois jeux à deux | Spiel Rossi, Rossi führt drei zu zwei | juego Rossi, Rossi gana tres juegos a dos | jogo Rossi, Rossi vence por três jogos a dois |
| Jogos iguais | due giochi pari | two games all | deux jeux partout | zwei beide | dos juegos iguales | dois jogos iguais |
| Tie-break | tre a uno Rossi · sei pari | three one Rossi · six all | trois un Rossi · six partout | drei zu eins Rossi · sechs beide | tres uno Rossi · seis iguales | três a um Rossi · seis iguais |
| Set | un set a zero · un set pari | one set to love · one set all | une manche à zéro · une manche partout | eins zu null in Sätzen · ein Satz beide | un set a cero · un set iguales | um set a zero · sets iguais |
| Fim da partida | gioco, set, partita Rossi | game, set and match Rossi | jeu, set et match Rossi | Spiel, Satz und Sieg Rossi | juego, set y partido Rossi | jogo, set e partida Rossi |
| Troca de lado | cambio campo | change ends | changement de côté | Seitenwechsel | cambio de lado | troca de lado |

Particularidades:
- Em francês e espanhol o nome vem **depois** de "au service"/"al servicio"; em alemão, depois de "Aufschlag". O francês chama o set de **manche** (exceto em "jeu, set et match") e o tie-break de **jeu décisif**.
- **10-6 … 10-9** no tie-break decisivo: em francês, espanhol e português diz-se "dix **à** huit", "diez **a** ocho", "dez **a** oito", porque "dix huit" / "diez ocho" / "dez oito" soam como *dezoito*.
- **Português**: uma única opção, com interface e voz preferida do Brasil (se faltar a voz brasileira, usa-se a de Portugal) e os anúncios do roteiro da FPT, com palavras válidas nos dois países ("jogo" e não "game", "partida"). "Um set a um" virou "sets iguais": no singular, *set* é pronunciado como *sete* e parecia 7-1.
- **Pronúncia** (`voice/Pronunciation.kt`): todas as frases de todos os idiomas foram lidas pelas vozes do Google e transcritas com o whisper. Correções acrescentadas: em alemão "Tie-Break" é lido como "Taibreak", com uma vírgula antes (senão sai "Teilbreg" ou "bei Detailbreak"); em francês o "à" isolado dos arquivos pré-gerados é lido como "a" (senão sai "a accent grave"). **O Samsung TTS nos novos idiomas ainda não foi testado.**
- **Pulseiras** (firmware 2.2): o app envia sozinho o idioma a cada conexão e quando você o muda; a pulseira o salva e o usa também quando está desconectada (busca do telefone, recarga, desligamento), sem acentos porque a fonte é ASCII (*EN CHARGE*, *LAEDT*, *CARGANDO*…). Com um firmware 2.1 ou anterior, as mensagens enviadas pelo app saem traduzidas, mas as mensagens internas da pulseira continuam em italiano. Uma pulseira recém-programada começa em italiano; a partir do firmware 2.2.2, a mensagem de conexão é reescrita no idioma do app assim que ele o envia, cerca de um segundo depois de conectar.

## 5. Firmware das pulseiras (Arduino IDE)

1. Instale o Arduino IDE 2 (1.1). No Linux também é preciso a permissão na porta serial (grupo `dialout`, 1.1), depois de encerrar a sessão e entrar de novo.
2. **File › Preferences › Additional boards manager URLs**:
   `https://static-cdn.m5stack.com/resource/arduino/package_m5stack_index.json`
3. **Boards Manager** → procure **M5Stack** → instale a versão **≥ 3.2.5**.
4. **Library Manager** → instale **M5Unified** (aceite "Install all" para a M5GFX) e **NimBLE-Arduino** de *h2zero* (≥ 2.1).
5. **File › Open** → `firmware/TSM_Band/TSM_Band.ino` do clone ou do ZIP (ou `~/Arduino/TSM_Band/TSM_Band.ino` se você usou o script).
6. **Tools › Board › M5Stack › M5StickS3**, **Tools › Port** → a porta da pulseira: no Linux `/dev/ttyACM0`, no Windows `COM3`, `COM4`… (a que aparece quando você conecta o cabo).
   *(Sem o pacote M5Stack também serve "ESP32S3 Dev Module": USB CDC On Boot = Enabled, Flash Size = 8MB, Partition = 8M with spiffs.)*
7. **Upload (→)**. Se a porta não aparecer ou o envio falhar: tente outro cabo USB-C (alguns servem só para carregar), depois mantenha o **botão lateral** pressionado por alguns segundos (modo download) e tente de novo; no modo download, no Windows, o número da porta COM pode mudar.
   **Depois do envio**, se a tela continuar preta (a pulseira ficou no modo de programação), pressione **uma vez** o botão lateral: ela reinicia com o programa novo.
8. Ao iniciar, a pulseira mostra o seu nome, ex. **TSM-3FA2** (pode ser mudado pelo app, veja 6.1). Repita para a segunda pulseira.

> **Firmware 2.3** (com o app 2.4): o bipe de KEY1/KEY2 toca quando o telefone recebeu o toque, os toques feitos durante uma queda curta são enviados assim que ela reconecta, *NAO ENVIADO* se o telefone não confirmar (6); depois de desligada com o cabo conectado, KEY1 ou KEY2 também a ligam de novo; leituras de bateria com falha não chegam mais ao app como 0 %. Com um app mais antigo, uma pulseira 2.3 funciona como antes. **Firmware 2.2.2**: a mensagem de conexão passa para o idioma do app assim que ele chega (4.1); em espanhol *VINCULANDO…/VINCULADA* e em português *PAREADA*, como no app; em francês *MANCHES* na tela dos jogos. **Firmware 2.2.1**: também a contagem regressiva antes de desligar no idioma do app (6). **Firmware 2.2**: os textos da pulseira no idioma do app (4.1). **Firmware 2.1**: a tela de recarga (6.2); os ajustes, *Identificar* e o desligamento pelo app exigem pelo menos o 2.0. Carregue o `TSM_Band.ino` em **ambas** as pulseiras; com um firmware antigo o app avisa no painel de ajustes e o resto continua funcionando.

## 6. Usar as pulseiras

| Botão | Ação |
|---|---|
| **KEY1** (frontal) curto | ponto para quem está usando a pulseira (também inicia a partida na página INÍCIO DA PARTIDA); um bipe confirma (firmware 2.3 + app 2.4: quando o telefone o recebeu, cerca de meio segundo depois) |
| **KEY1** longo (1 s) | mostra a bateria (e mantém a pulseira ligada se ela estiver prestes a se desligar por inatividade) |
| **KEY2** curto | anula o último ponto (também no popup de fim de partida); dois bipes mais graves (com o firmware 2.3, quando o telefone confirma) |
| **KEY2** longo (2 s) | desliga a pulseira (com o cabo conectado mostra *CONTINUA CARREGANDO*: ela carrega mesmo desligada; a partir do firmware 2.3, KEY1 ou KEY2 também a ligam de novo) |
| Botão lateral | um clique liga; clique duplo desliga (função do hardware) |

- Ao ligar, pisca **PAREANDO...** (acesa 0,35 s a cada 2 s, para economizar). O app se conecta sozinho às pulseiras que já conhece assim que é aberto; as novas ele encontra na página 2.
- Conectada: **PAREADA** por 3 segundos com dois bipes, depois **PAREADA COM** + o nome do jogador.
- A cada ponto a tela acende com o placar do jogo em tamanho grande (à esquerda o seu, à direita o do adversário; a bolinha verde indica quem serve) e depois apaga. No fim do jogo mostra jogos e sets. Se o telefone não confirmar um toque em 8 segundos (conexão perdida), aparece **NAO ENVIADO** em vermelho com dois bipes graves: pressione de novo quando ela tiver reconectado. Já um toque feito durante uma queda curta não se perde: é enviado assim que a pulseira reconecta, e o bipe toca nesse momento.

**Desligamento automático** (os tempos são mudados no app, 6.1):

| Situação | O que a pulseira faz | Padrão |
|---|---|---|
| Ligada, mas nenhum telefone se conecta | pisca PAREANDO e depois se desliga | **30 s** |
| Telefone perdido (desligado, fora de alcance, Bluetooth desligado, app fechado de repente) | pisca RECONECTANDO e se reconecta sozinha assim que possível; senão se desliga | 3 min |
| Conectada mas inativa (nenhum ponto, nenhuma mensagem) | 30 s antes avisa com **INATIVO · SEGURE KEY1** e um bipe, depois se desliga | 30 min |
| Partida encerrada (confirmada no telefone) ou **Sair** no app | mostra FIM DA PARTIDA / APP FECHADO e se desliga na hora | ativo (pode ser desativado, 7.2) |
| Bateria vazia (abaixo de 3,30 V em duas leituras seguidas) | mostra BATERIA VAZIA e se desliga, para não ficar funcionando pela metade | sempre |

- Nos últimos 10 segundos antes de se desligar sem telefone, mostra **DESLIGANDO · 8s - APERTE UM BOTAO** com um bipe: qualquer botão reinicia a contagem do desligamento.
- Quando se desliga sozinha, ela avisa o telefone: na partida, o quadro laranja mostra *PULSEIRA 1 DESLIGADA (SEM USO)*, *(BATERIA VAZIA)*… Depois do desligamento no fim da partida, o telefone para de procurá-la o tempo todo e reconecta sozinho quando você a liga de novo.

**Duração da bateria**: o que mais pesa é a placa ESP32-S3 com o Bluetooth conectado (cerca de 35 mA): o core Arduino é compilado sem a economia de energia profunda (light sleep) quando o Bluetooth está ligado, então não dá para descer muito abaixo disso. Tela, brilho e bipes acrescentam poucos mA. Com a bateria de 250 mAh a estimativa é de **cerca de 7 horas com a carga completa**, mais do que qualquer partida em melhor de três sets. O painel de ajustes mostra a estimativa em tempo real e, após 20 minutos de uso, a corrige com o consumo **medido** naquela pulseira. O registro completo fica em `files/battery_log.csv` do app.

O resto da economia: CPU a 80 MHz, tela apagada quando não é necessária, Bluetooth de baixo consumo (o rádio acorda 2-3 vezes por segundo, mas o toque no botão é enviado mesmo assim em até ~120 ms), bipe alimentado só enquanto toca, microfone/IMU/5V desligados.

### 6.1 Ajustes da pulseira

Na página 2 (**Ajustes** embaixo da pulseira de cada jogador) ou durante a partida (toque em **J1**/**J2** no alto, ou no ícone dos controles deslizantes). Eles são salvos **na pulseira** e continuam valendo mesmo depois de desligá-la; a cada mudança a pulseira mostra o nome com *AJUSTES OK*.

| Ajuste | Valores | Padrão |
|---|---|---|
| Nome | até 12 caracteres (ex. o nome do jogador ou "J1") | TSM-xxxx |
| Brilho da tela | 5-100 % | 20 % |
| Placar visível após cada ponto | Não, 2, 3, 5, 8 s (o resumo do fim do jogo dura 2 s a mais) | 3 s |
| Volume do bipe | Mudo-100 % | 50 % |
| Tela invertida | para usá-la no outro pulso | não |
| Desligamento: ao ligar, se nenhum telefone se conectar | 15 s - 5 min | 30 s |
| Desligamento: se perder a conexão com o telefone | 1-10 min | 3 min |
| Desligamento: se ficar conectada mas sem uso | 10-60 min | 30 min |

Embaixo fica a **estimativa de autonomia** (com a carga completa e com a carga atual), com o consumo dividido por item: ela muda enquanto você move os controles, antes mesmo de confirmar. Depois vêm **Identificar**, **Desligar** e **Copiar para a outra pulseira** (mesmos ajustes; o nome continua o dela). **Desligar** pede confirmação, também durante a partida.

### 6.2 Recarga (firmware 2.1)

Conecte o cabo USB-C: a pulseira emite um bipe e mostra a **tela de carga** por 30 segundos; depois a tela se apaga e a cada 10 segundos acende por 1,5 s (uma olhada rápida, como a luz de um carregador). **Qualquer botão** a acende por mais 30 segundos. Se a pulseira estava desligada, ligue-a com um clique no botão lateral para vê-la (a carga acontece de qualquer forma, mesmo desligada).

```
 TSM-3FA2   USB 5.01V           ← nome e tensão do cabo
 ┌──────────┐
 │██████ ⚡  │▌   78%             ← ícone da bateria e porcentagem de carga
 └──────────┘
      CARREGANDO                 ← ou CARGA COMPLETA / ALIMENTADO POR USB
 4.12V  HA 42 MIN  FIM ~25 MIN   ← tensão da bateria, há quanto tempo carrega, fim estimado
```

- **Porcentagem**: durante a carga a tensão medida é maior que a real (≈0,1 V); a pulseira a corrige, nunca a deixa cair e só chega a **100 %** quando o carregador informa que terminou. Com a carga completa, o texto vira **CARGA COMPLETA** com o tempo gasto (*CARREGADA EM 1H 25*) e a tela fica apagada (nada de clarões à noite).
- **Fim estimado**: o chip de alimentação (PM1) não mede a corrente de carga, então o tempo restante é calculado pelo quanto a carga subiu nos últimos 10 minutos: aparece depois de 10 minutos, arredondado de 5 em 5, e é uma estimativa.
- **ALIMENTADO POR USB**: o cabo está conectado mas a bateria não carrega (e não está cheia): cabo ou fonte fracos, ou bateria desconectada.
- **Com o cabo ela não se desliga sozinha** (nada de desligamento por falta de telefone ou por inatividade, nada de *bateria vazia*); pode ser desligada com KEY2 longo. Ao tirar o cabo, mostra **USB DESCONECTADO · BATERIA 97%** e a partir daí voltam a valer os tempos normais de desligamento (6).
- **Conectada ao telefone** (ex. com um powerbank durante a partida) o placar tem prioridade: só uma mensagem curta *CARREGANDO 78%*, e KEY1 longo mostra *CARGA COMPLETA* ou *CARREGANDO*.
- **No app**: a página 2 e os ajustes da pulseira mostram *Carregando 78%* ou *Carga completa*; na partida, as etiquetas J1/J2 têm o símbolo ⚡. O registro `files/battery_log.csv` também tem duas colunas a mais (tensão USB, carga completa): útil para verificar em quanto tempo ela carrega de verdade.
- Pulseira e app agora calculam a porcentagem com a **mesma curva** da LiPo (antes a pulseira usava uma reta menos precisa), então mostram o mesmo número.

> A verificar com as pulseiras reais: a recarga não pôde ser testada no hardware. Em especial, com que frequência o carregador indica *carga completa* e quão precisa é a porcentagem durante a carga.

## 7. Como usar o app

1. **Nova partida** (opcional): clube, quadra, simples/duplas, nomes (nas duplas, dois nomes por equipe). **Avançar**.
2. **Modo e regras**:
   - *Árbitro* ou *Pulseiras*. Com as pulseiras são obrigatórios Bluetooth ligado, permissão de localização e localização ativada: as linhas dos **Requisitos** se atualizam em tempo real (mesmo se você desligar o Bluetooth ou a localização pelo painel de notificações) e ficam vermelhas, com o botão para corrigir.
   - A **busca é automática e contínua** enquanto a página estiver aberta: ligue as pulseiras e elas ocupam sozinhas os lugares livres (primeiro Jogador 1, depois Jogador 2). **Identificar** faz aquela pulseira piscar na cor do jogador (amarelo ou vermelho) com bipes, para você ver logo qual tem na mão; **Trocar J1 ↔ J2** as inverte sem desconectá-las. No menu suspenso você sempre pode escolher outra ou *Nenhuma* (a que for tirada à mão não é recolocada pela busca). Uma pulseira não pode ficar com dois jogadores.
   - **Desligar as pulseiras no fim da partida e ao sair** (ativado por padrão): ao confirmar *Partida encerrada* e com *Sair*, as pulseiras se desligam em vez de esperar a inatividade.
   - No modo árbitro, tocando em **Avançar** sem localização aparece o convite para ativá-la (senão o local não vai constar no resumo).
   - Idioma (seis idiomas, 4.1), voz ligada/desligada, formato (*3 sets · tie-break a 7* ou *2 sets + tie-break decisivo a 10*), *Sem vantagem* (No-Ad).
   - **Sorteio**: a moeda gira e indica quem vence; você define quem serve e os lados da quadra **vistos da cadeira do árbitro** (esquema da quadra com **Inverter lados**). Nas duplas, escolha também quem serve primeiro em cada equipe.
3. **INÍCIO DA PARTIDA** pisca: toque no botão ou pressione KEY1 de uma pulseira. A voz diz *"Primeiro set" · "[nome] ao serviço" · "joguem"* com 2 segundos entre as frases; o **Match Time** começa em "joguem".
4. **Partida**: no alto à esquerda o tempo de partida, à direita a contagem regressiva (**Shot Clock** 25 s, **Changeover Time**, **Set Break Time**; em vermelho nos últimos 5 s). O quadro laranja acende por 5 s para *troca de lado, tie-break, set point, match point, break point…*. Os dois botões quadrados (amarelo = Jogador 1, vermelho = Jogador 2) ficam do lado em que os jogadores estão de verdade e trocam de lugar a cada troca de lado; embaixo de quem serve aparece **On Serve**. Embaixo: *Anular ponto*, *Suspender/Retomar*, áudio (ícone do alto-falante, riscado = desligado), *Nova partida*, **Sair**. No modo pulseiras, embaixo dos tempos ficam **J1**/**J2** com bateria e autonomia: tocando neles, abrem-se os ajustes da pulseira. Um toque duplo em *Anular ponto* tira um ponto só (um segundo toque em menos de 1 s não conta); logo depois de uma troca de página o segundo toque de um toque duplo é ignorado, para não cair num botão da página nova.
5. No último ponto aparece o popup **Partida encerrada / Anular o último ponto**. "Partida encerrada" só se confirma **no telefone**. Por meio segundo depois de aparecer, os botões dele ignoram toques: um toque duplo no último ponto não anula nada.
6. **Resumo**: vencedor, nomes, placar set a set com os pontos do tie-break, duração, hora de início e de fim, data, clube, quadra, local, formato, pontos e jogos ganhos. Botões **Salvar no histórico** (nome do arquivo + pasta à escolha + formatos .txt/.json/.png), **Compartilhar** (imagem 1080×1350 + texto para WhatsApp/Instagram/…), **Nova partida**, **Sair**. Se o resumo não foi salvo nem compartilhado, **Nova partida** e **Sair** pedem confirmação. Se o Android fechar o app enquanto você está no resumo (por exemplo, ao compartilhar), ele volta quando o app é reaberto. Salvando duas vezes com o mesmo nome na pasta padrão, o segundo vira *nome (1)*; na imagem os textos longos (nomes, endereço) se ajustam à largura.

**Sair**: **Sair** (na partida pede confirmação, e também está no resumo) fecha de verdade o app; o mesmo acontece se você o remover dos apps recentes. Ao reabrir, ele começa pela primeira página.

**Salvamento**: a partida é salva sozinha a cada ponto. *Suspender* para os tempos; se você sair, se o telefone desligar ou se o app for fechado, a partida fica em **Retomar partida suspensa** (página 3) e continua com *Retomar*. *Anular ponto* recalcula tudo desde o início, então funciona também depois do fim de um jogo, de um set ou da partida. A lixeira de uma partida suspensa pede confirmação. Se a partida foi iniciada por uma pulseira com a tela bloqueada (o Android não fornece a localização nesse caso), o local é obtido assim que você abrir o app de novo.

## 8. Placar na TV ou no monitor

O telefone do árbitro funciona como um **pequeno servidor** na rede Wi-Fi: o placar é uma página web em estilo LED (dígitos de 7 segmentos, amarelo contra vermelho, jogos e sets no centro, sets terminados e tempo de partida embaixo à esquerda, **SERVIÇO: 25 SEG** embaixo à direita) que se atualiza sozinha a cada ponto. O monitor não precisa ser "smart" e não é preciso uma rede do clube: basta o **hotspot** de um dos dois telefones.

### 8.1 Os caminhos possíveis

| Como chega ao monitor | O que é preciso | Prós | Contras |
|---|---|---|---|
| **Segundo telefone com saída de vídeo** + cabo USB-C/HDMI, app TSM em *Usar como placar* | um telefone com saída de vídeo pela USB-C (DisplayPort Alt Mode) | sem internet; o placar ocupa todo o monitor em 16:9 e o telefone fica livre | muitos telefones **não** têm saída de vídeo: em geral têm os Galaxy S/Note/Tab S (com DeX: escolha *Espelhamento de tela* ou desative o início automático do DeX), e quase nenhum Galaxy A. Procure "DisplayPort" / "saída de vídeo" na ficha técnica |
| **Chromecast** (ou Google TV Streamer) no monitor + um telefone qualquer com o TSM em *Usar como placar* e o botão de transmissão (**Transmitir**, **Transmissão** ou **Transmissão de tela**, conforme a versão do Android; Smart View nos Samsung) | Chromecast configurado uma vez com o Google Home na rede do hotspot | qualquer telefone serve, nenhum cabo longo | o Chromecast precisa de **internet** (dados móveis no hotspot); atraso de cerca de 1 s; a transmissão mostra a tela do telefone (na horizontal) |
| **Navegador** em qualquer aparelho ligado ao monitor (computador portátil, tablet, TV box, Fire TV Stick…) | ler o QR ou digitar o endereço | nenhum app para instalar | a tela se apaga sozinha se você não configurar; é preciso tocar em **TELA CHEIA** toda vez que abrir |

**Por que não por Bluetooth**: o telefone do árbitro já cuida das duas pulseiras por Bluetooth, onde os tempos dos botões importam; o Wi-Fi é separado, mais rápido e chega mais longe. **Por que não só do telefone do árbitro para o Chromecast** (sem segundo telefone): é possível, mas é preciso um app "receptor" registrado no Google (Google Cast Developer Console, US$ 5, pagamento único) e publicado num site https; é um próximo passo possível.

**Dicas de rede**:
- O placar envia pouquíssimos dados (uma mensagem a cada ponto e a cada 5 segundos) e não consome tráfego de internet.
- Se o telefone do árbitro fizer o hotspot, nos ajustes do hotspot escolha a banda de **5 GHz**, se houver: o Bluetooth das pulseiras trabalha em 2,4 GHz e assim um não atrapalha o outro.
- Com o Chromecast, convém que o hotspot seja feito pelo telefone do árbitro com os **dados móveis ativados**; o telefone-placar e o Chromecast se conectam a esse hotspot.
- Sem Chromecast, também funciona o contrário (hotspot no telefone-placar, como na ideia original): o app do árbitro se mantém conectado ao Wi-Fi mesmo sem internet.

### 8.2 No telefone do árbitro

1. Página 2 › **Placar na TV** › ative **Placar na TV ou monitor**. No Android 13 ou mais recente o app pede a permissão de notificações: ela é necessária para a notificação do serviço que mantém o placar ativo.
2. Aparecem o **endereço** (ex. `192.168.43.1:8080`), o **QR** e quantos placares estão conectados. Se aparecer *Sem rede*, ative o hotspot ou conecte-se ao do outro telefone.
3. **Prévia neste telefone** abre o placar no navegador do próprio telefone.
4. **Aparência do placar**: cor de cada jogador (8 cores), tempo de partida, relógio de serviço e pausas, sets terminados, mensagens (break point, set point, troca de lado…), bola ao lado de quem serve, segmentos apagados visíveis, texto embaixo (vazio = clube e quadra da página 1). As mudanças chegam na hora ao monitor.
5. Na partida, no alto ao centro, fica **TV · 1** (placares conectados): tocando nele, você vê de novo o endereço e o QR.

Com o placar ativado, um serviço em primeiro plano (notificação *Partida em andamento · placar na TV ativo*) mantém o servidor vivo mesmo com a tela apagada, também no modo árbitro. O placar é **somente leitura**: não é possível mudar nada por ali. Ele continua ativo também no resumo e nas outras páginas enquanto o placar estiver ativado (notificação *Placar na TV ativo*), assim o resultado final fica no monitor mesmo com o telefone bloqueado.

O que ele mostra, além da pontuação: *AGUARDANDO A PARTIDA* antes do início, *PRONTOS PARA JOGAR* na página INÍCIO DA PARTIDA, **TIE-BREAK** / **TIE-BREAK DECISIVO** no lugar de *VS*, *PARTIDA SUSPENSA* piscando, **VENCEDOR [nome]** no fim da partida com todos os sets; as vantagens aparecem como **AD** também nos dígitos de LED. Uma partida vencida no tie-break decisivo termina com 1-0 nos jogos e 2-1 nos sets, com [10-8] entre os sets concluídos; mensagens longas diminuem para caber na tela.

### 8.3 No telefone-placar

1. Página 1 › no fim, **Usar como placar**.
2. O telefone **procura sozinho** o telefone do árbitro (anúncio na rede e varredura do hotspot, poucos segundos) e lembra o último endereço. Se não o encontrar: verifique o hotspot e o *Placar na TV*, depois **Procurar de novo**, ou digite o endereço mostrado pelo árbitro e toque em **Conectar**. Ele o encontra mesmo se neste telefone também estiver ativado *Placar na TV ou monitor*; se o Wi-Fi só conectar depois, procura de novo sozinho.
3. O placar fica em tela cheia, na horizontal, com a tela sempre acesa.
   - **Com o cabo HDMI**: o placar vai para o monitor, no formato dele; o telefone mostra *O placar está no monitor externo* com o brilho no mínimo (**Mostrar aqui também** para vê-lo também no telefone). Tirando o cabo, ele volta para o telefone.
   - **Com o Chromecast**: abra o painel de configurações rápidas › **Transmitir** (ou **Transmissão**, **Transmissão de tela**) / **Smart View** › escolha o Chromecast.
4. Se o telefone do árbitro sumir (fora de alcance, app fechado), aparece *CONEXÃO PERDIDA - RECONECTANDO...* e depois de 20 segundos ele o procura de novo sozinho, mesmo que o endereço tenha mudado. Mesmo depois de uma queda do hotspot ele reconecta sozinho assim que a rede volta. Nunca passa para o telefone de outra quadra: para mudar de quadra, saia de *Usar como placar* e procure de novo.
5. Para sair: **voltar duas vezes**.

**Num navegador** (computador portátil, TV box): leia o QR ou digite o endereço, depois toque em **TELA CHEIA** (aparece ao mover o mouse ou tocar na tela). Configure o tempo limite da tela para *nunca*: numa página http o navegador não consegue mantê-la acesa sozinho. Depois de uma queda a página reconecta sozinha; placares demais abertos não bloqueiam mais um novo (o mais antigo é fechado).

**Prévia sem telefones**: `TennisScoreManager/app/src/main/assets/scoreboard.html?demo=1` num navegador (também `&lang=en`, `fr`, `de`, `es`, `pt` e `&state=ad`, `tb`, `end`, `idle`, `doubles`, `mtb`, `long`) mostra o placar com dados de teste.

## 9. Regras aplicadas (ITF) e escolhas combinadas

- **Jogo**: 0-15-30-40, iguais, vantagem, jogo. **Sem vantagem** (No-Ad): no 40-40, ponto decisivo ("iguais, ponto decisivo").
- **Set**: 6 jogos com 2 de diferença (7-5); no **6-6, tie-break**.
- **Tie-break**: a 7 com 2 de diferença; quem é da vez serve o 1º ponto, depois 2 pontos cada um; troca de lado **a cada 6 pontos** e no fim; quem serviu primeiro no tie-break **recebe** no primeiro jogo do set seguinte.
- **Tie-break decisivo** (formato de 2 sets): no 1-1 joga-se até 10 pontos, com 2 de diferença.
- **Troca de lado** (regra 10 da ITF): depois do 1º, 3º, 5º… jogo de cada set. No fim do set só se troca se o set teve um número ímpar de jogos (6-3, 7-6); senão (6-4), troca-se depois do primeiro jogo do set seguinte. O tie-break conta como um jogo.
- **Tempos**: shot clock de 25 s entre os pontos; changeover de 90 s; set break de 120 s no fim do set. Além disso, como foi pedido (não é regra da ITF): **30 s** para trocar de lado depois do 1º jogo de cada set, a cada troca de lado no tie-break e no 6-6; terminada qualquer pausa, começa o shot clock.
- **Duplas**: rotação do serviço A1-B1-A2-B2 durante todo o set, também no tie-break; no início de cada set o app pergunta a ordem (ela pode mudar, como manda o regulamento).
- **Anúncios**: placar dito a partir de quem serve ("quinze-zero", "zero-quarenta", "quinze iguais", "iguais", "vantagem Rossi"); no fim do jogo, "jogo Rossi, Rossi vence por três jogos a dois" / "dois jogos iguais" + "troca de lado" quando há troca; no 6-6, "jogo Rossi, seis jogos iguais, tie-break"; no tie-break, o placar é dito a partir de quem está na frente ("três a um Rossi", "seis iguais"); no fim do set, "jogo Rossi, Rossi vence por um set a zero" / "sets iguais"; no fim da partida, "jogo, set e partida Rossi, seis quatro, três seis, sete cinco"; "correção" + placar quando um ponto é anulado.

**Escolhas feitas em relação ao pedido original, para seguir a ITF:**
1. **No 6-6 não há troca de lado** (são 12 jogos, número par): o app faz a pausa de 30 s e diz "tie-break", mas não diz "troca de lado" nem troca os botões de lugar. A primeira troca é depois de 6 pontos do tie-break.
2. A troca no fim do set depende do número de jogos do set (veja acima), não acontece sempre.
3. No fim de um set ganho sem tie-break, o app usa a mesma fórmula do tie-break ("jogo Rossi, Rossi vence por um set a zero"). Muitos árbitros anunciam o jogo e o set juntos, seguidos do placar do set: é fácil mudar isso em `Calls.kt`.
4. No 1-1, no formato com tie-break decisivo, a voz acrescenta "tie-break decisivo".

## 10. Onde mexer

| O quê | Arquivo |
|---|---|
| Regras da pontuação | `model/ScoreEngine.kt` (+ testes em `app/src/test`) |
| Frases e anúncios por voz | `voice/Calls.kt` (construção), `voice/CallWords.kt` (palavras e ordem de cada idioma) |
| Correções de pronúncia | `voice/Pronunciation.kt` |
| Tempos (25/90/120/30 s), mensagens, fluxo da partida | `MatchController.kt` |
| Textos do app | `ui/Strings.kt` (italiano, inglês), `ui/StringsFr.kt`, `StringsDe.kt`, `StringsEs.kt`, `StringsPt.kt` |
| Acrescentar um idioma | entrada em `model/Rules.kt` (`Lang`), um `ui/StringsXx.kt`, um `XxWords` em `voice/CallWords.kt`, uma linha em `TXT[]` do `TSM_Band.ino`: os testes em `LanguagesTest` e `StringsTest` dizem o que falta |
| Protocolo Bluetooth (UUID, mensagens, ajustes) | `ble/BandProtocol.kt` e no início do `TSM_Band.ino` |
| Estimativa de autonomia | `ble/BatteryModel.kt` |
| Painel de ajustes da pulseira | `ui/BandSettingsPanel.kt` |
| Visual | `ui/screens/*.kt`, cores em `ui/Theme.kt` |
| Placar na TV: página e aparência | `app/src/main/assets/scoreboard.html` |
| Placar na TV: dados enviados, servidor, ajustes | `tv/TvModels.kt`, `tv/TvServer.kt`, `ui/TvSection.kt` |
| Telefone usado como placar (busca, monitor externo) | `tv/DisplayActivity.kt`, `tv/ScoreboardFinder.kt` |
| Tela de recarga da pulseira | `TSM_Band.ino` (`pollPower`, `drawCharge`) |


## 11. Problemas comuns

- **Pulseira não encontrada**: Bluetooth e localização ativados (linhas verdes)? A pulseira está piscando PAREANDO? (Se já estiver conectada a outro telefone, ela não aparece.) Se nesse meio-tempo ela se desligou sozinha (30 s), ligue-a de novo com um clique no botão lateral.
- **A pulseira mostra NAO ENVIADO**: o telefone não confirmou o toque em 8 segundos (conexão perdida ou app fechado). Pressione de novo quando a pulseira tiver reconectado. Caso raro: se o toque tinha chegado e só a confirmação se perdeu, o ponto conta duas vezes; a voz anuncia o placar, corrija com *Anular ponto*.
- **No painel de ajustes aparece "O firmware desta pulseira não tem ajustes"**: essa pulseira ainda tem o sketch antigo; carregue-o de novo (capítulo 5).
- **O Android Studio não reconhece o telefone**: Depuração USB ativada e impressão digital RSA aceita no telefone (3)? No Windows às vezes é preciso o driver do fabricante; no Linux, as regras udev e uma nova sessão (1.1). Ou use a depuração por Wi-Fi (3).
- **O Arduino IDE não mostra a porta da pulseira**: cabo USB-C de dados, e não só de carga; no Linux, grupo `dialout` e nova sessão (1.1); depois, o modo download (5, passo 7).
- **A voz não fala ou lê errado**: volume de mídia, *Audio On*, voz do idioma escolhido instalada (4); tente outro mecanismo ou outra voz em *Áudio e voz*.
- **Tela apagada durante a partida**: no modo pulseiras, um serviço em primeiro plano (notificação "Partida em andamento") mantém ativos Bluetooth, voz e cronômetros; no modo árbitro a tela fica acesa.
- **Falha no sync do Gradle por causa do JDK**: defina Gradle JDK = jbr-21 (passo 2.6).
- **O telefone-placar não encontra o árbitro**: mesma rede? (um dos dois faz o hotspot, o outro está conectado a ele). *Placar na TV* ativado no telefone do árbitro? Tente o endereço à mão. Alguns hotspots isolam os dispositivos conectados entre si ("isolamento de clientes"): se houver essa opção, desative-a.
- **O navegador do telefone não abre o endereço** com os dados móveis ativados: o Android manda o tráfego pelos dados móveis porque o hotspot não tem internet. Use o app em *Usar como placar* (ele resolve isso sozinho) ou desligue os dados móveis nesse telefone.
- **Monitor preto com o cabo**: esse telefone não tem saída de vídeo pela USB-C (8.1), ou o Samsung DeX foi iniciado: escolha *Espelhamento de tela*.
