# Tennis Score Manager — guia passo a passo

[Italiano](GUIDA.md) · [English](GUIDE.en.md) · [Français](GUIDE.fr.md) · [Deutsch](GUIDE.de.md) · [Español](GUIDE.es.md) · **Português**

App Android (Kotlin + Jetpack Compose) para marcar o placar do tênis segundo as regras da ITF, com anúncios por voz de árbitro de cadeira **em seis idiomas** (italiano, inglês, francês, alemão, espanhol, português: capítulo 4.1), duas pulseiras **M5StickS3** conectadas por Bluetooth LE (ou dois **controles Bluetooth de selfie** bem baratos: capítulo 6.3) e um **placar de LED na TV ou no monitor** (capítulo 8).

## 0. O que há no repositório

| Caminho | Para que serve |
|---|---|
| `TennisScoreManager/` | O projeto do Android Studio. |
| `firmware/TSM_Band/TSM_Band.ino` | O firmware da pulseira. |
| `deliver/installa_tsm.sh` | Alternativa ao clone: cria **todo** o projeto Android e o sketch só com blocos `cat << 'TSM_EOF'` (o jar do Gradle wrapper está em base64). |
| `deliver/GUIDE.pt.md` | Este guia (em português); o original em italiano é `deliver/GUIDA.md` e as outras traduções são `deliver/GUIDE.en.md`, `.fr`, `.de`, `.es`. |

Versões usadas e verificadas: app **2.5.0** · firmware **2.3** · Gradle 8.14.3 · Android Gradle Plugin 8.13.2 · Kotlin 2.2.21 · Compose BOM 2025.12.00 · compileSdk/targetSdk 36 · minSdk 26 (Android 8.0). Firmware: M5Unified ≥ 0.2.12, NimBLE-Arduino ≥ 2.1, core ESP32 3.x. Licença: GNU GPL versão 3 ou posterior (`LICENSE`); o app mostra a versão e a licença no fim da aba **Mais**.

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

### 3.1 Como o app está organizado (app 2.5)

Na **primeira vez** o app faz três perguntas: o idioma, como você vai usá-lo (marcar o placar, jogar com as pulseiras, treinar, filmar partidas) e o seu nome; dá para pular e refazer em **Mais › Apresentação do app**. Embaixo há sempre **cinco abas**:

- **Jogar**: **Nova partida**, a partida suspensa para retomar, **Como da última vez** (mesmos jogadores, quadra e regras: as regras cabem numa linha e vai-se direto para o sorteio; *Mudar modo ou regras* reabre-as), o **torneio entre amigos** (Americano, Mexicano) e **Usar este telefone como** placar ou câmera. No alto o estado dos dispositivos (pulseiras, cintas, câmera, TV): um toque para conectá-los.
- **Treinar**: treino com a cinta cardíaca e **treino de golpes** (saque, forehand, backhand, voleio) com as sessões salvas.
- **Vídeo**: **Gravar com este telefone**, **Seus vídeos** (capítulos, melhores momentos, filtros, importar) e o telefone-câmera.
- **Progresso**: **Rating TSM · Progressos** (com o seu nome mostra na hora o seu rating), **Histórico**, **As minhas raquetes**.
- **Mais**: **Você** (o seu nome), **Jogadores** (o banco: 7.7), **Perfil e dados** (backup, restauração, apagar: 7.8), **Dispositivos** (pulseiras, controles, cintas, segundo telefone, placar na TV), **Idioma e voz**, **Ajuda** (este guia, com pesquisa e referências para tocar), raquetes, transferir partidas, apresentação, versão e licença.

**Nova partida** são dois passos: **quem joga** (simples ou duplas, os nomes, com os jogadores recentes para tocar, e o esporte; clube, quadra e torneio em *Mais detalhes*) e **como você marca o placar e as regras**. Tudo é opcional: dá para seguir mesmo sem nomes. De cada tela **Voltar** retorna à aba de onde se veio.

## 4. A voz (funciona sem internet)

- Cada anúncio é lido **numa única frase** pela síntese de voz do telefone, com uma voz instalada: sem internet e com prosódia natural. Os nomes dos jogadores entram na frase.
- Mais › **Idioma e voz** › **Áudio e voz**:
  - **Mecanismo de síntese de voz**: *Padrão do telefone* (nos Samsung é o Samsung TTS) ou um mecanismo à sua escolha, por exemplo *Serviços de voz do Google*. Nos testes, feitos em italiano, o Google soou mais natural.
  - **Voz**: *Automática (melhor offline)* ou uma voz específica (o Google lista as vozes por código, como *Voz* seguido de três letras; as marcadas como *online* precisam de internet).
  - **Testar voz**: lê uma sequência de anúncios de exemplo com os nomes digitados. Enquanto lê, o botão vira **Parar o teste**: toque de novo para interrompê-lo (ele também para sozinho quando você sai da página). A síntese de voz não pode ser pausada no meio de uma frase, por isso o botão a interrompe; tocando de novo, ela recomeça do início.
- Se faltar a voz do idioma escolhido: **Configurações › Gerenciamento geral › Idioma › Conversão de texto em voz** (ou *Saída de conversão de texto em voz*) → baixe a voz desse idioma para o mecanismo escolhido (o app também mostra o botão **Instalar voz**). Se a síntese de voz nem chega a iniciar, o app avisa com uma mensagem própria e o botão **Abrir configurações** (configurações de síntese de voz do Android).
- **Pronúncia**: alguns mecanismos erram palavras do tênis (em italiano, "primo set" lido como "primo settembre", "tie-break" lido como "time break"). O app as corrige sozinho (`voice/Pronunciation.kt`); as correções foram verificadas transcrevendo o áudio real do Samsung e do Google.
- **Gravações personalizadas** (a voz mais natural de todas: a sua ou a de um árbitro): na pasta `Android/data/io.github.steve_linux.tsm/files/voice/` há o `LEGGIMI.txt` ("leia-me"; as explicações do início ficam no idioma do app e são reescritas quando você o muda) com a lista das 85 chaves e das frases. Grave os arquivos com esses nomes (`score_1_0.mp3` = "quinze-zero"…), coloque-os num ZIP com uma pasta por idioma (`it/`, `en/`, `fr/`, `de/`, `es/`, `pt/`) e use **Importar ZIP**. O `LEGGIMI.txt` tem uma coluna por idioma, separadas por tabulações, então também abre bem como planilha. As gravações sempre têm prioridade sobre a síntese; os nomes continuam sendo lidos pela síntese. No ZIP vale a pasta que contém o arquivo (também dentro de outras pastas, ex. `voice/pt/`); arquivos fora de uma pasta de idioma vão para o idioma atual e as pastas `tts/` são ignoradas, então dá para compactar também a pasta `voice/` do app. Um ZIP danificado ou incompleto não muda nada (*ZIP ilegível ou incompleto*). **Remover gravações** pede confirmação. Se um arquivo não puder ser reproduzido, a frase é dita pela síntese de voz e o arquivo não é mais usado até o app reiniciar.
- **Usar arquivos de áudio pré-gerados** (opcional): com **Gerar arquivos** o app cria uma vez os 85 arquivos com a voz escolhida (até mesmo uma voz *online*, se naquele momento houver internet) e depois os usa no lugar da síntese contínua. Soa mais "picotado", mas é útil para levar offline uma voz online. Os arquivos anteriores ficam até os novos estarem prontos (se a geração não chegar ao fim: *Geração não concluída*); durante a geração, idioma, mecanismo, voz, teste de voz e áudio ficam bloqueados.
- **Caixa de som externa** (coluna): basta pareá-la com o telefone por Bluetooth; a voz sai pelo canal de mídia (ajuste o volume de mídia).
- **Fones de ouvido Bluetooth**: são pareados do mesmo jeito e só você ouve a voz, sem incomodar as quadras vizinhas; servem também para quem marca o placar da beira da quadra. As pulseiras podem continuar conectadas ao mesmo tempo.
- **Dois dispositivos de áudio ao mesmo tempo**: muitos telefones mandam o áudio para dois dispositivos Bluetooth juntos. No Samsung: conecte os dois, abra o painel rápido › **Saída de mídia** e marque os dois. Assim cada jogador pode ter o seu fone, ou a caixa de som toca para a quadra e o fone para quem marca o placar.

### 4.1 Idiomas (app 2.3, firmware 2.2)

Mais › **Idioma e voz** › **Idioma**: Italiano, English, Français, Deutsch, Español, Português (cada idioma aparece escrito no próprio idioma, para que você o encontre mesmo com o app num idioma que não lê). A escolha vale ao mesmo tempo para **telas, voz do árbitro, placar na TV, resumo/compartilhamento e pulseiras**. As datas seguem o idioma (*mercredi 30 septembre 2026*, *Mittwoch, 30. September 2026*…).

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
5. **File › Open** → `firmware/TSM_Band/TSM_Band.ino` do clone ou do ZIP (ou `~/Arduino/TSM_Band/TSM_Band.ino` se você usou o script). A partir do firmware 2.4 a pasta `TSM_Band` contém também `swing_detector.cpp/.h` e `stroke_classifier.cpp/.h`: eles precisam ficar ao lado de `TSM_Band.ino` (copiando só o arquivo `.ino`, a compilação falha).
6. **Tools › Board › M5Stack › M5StickS3**, **Tools › Port** → a porta da pulseira: no Linux `/dev/ttyACM0`, no Windows `COM3`, `COM4`… (a que aparece quando você conecta o cabo).
   *(Sem o pacote M5Stack também serve "ESP32S3 Dev Module": USB CDC On Boot = Enabled, Flash Size = 8MB, Partition = 8M with spiffs.)*
7. **Upload (→)**. Se a porta não aparecer ou o envio falhar: tente outro cabo USB-C (alguns servem só para carregar), depois mantenha o **botão lateral** pressionado por alguns segundos (modo download) e tente de novo; no modo download, no Windows, o número da porta COM pode mudar.
   **Depois do envio**, se a tela continuar preta (a pulseira ficou no modo de programação), pressione **uma vez** o botão lateral: ela reinicia com o programa novo.
8. Ao iniciar, a pulseira mostra o seu nome, ex. **TSM-3FA2** (pode ser mudado pelo app, veja 6.1). Repita para a segunda pulseira.

> **Firmware 2.4** (com o app 2.5): reconhecimento de golpes (6.4); os exemplos de calibração ficam salvos na pulseira. **Firmware 2.3** (com o app 2.4): o bipe de KEY1/KEY2 toca quando o telefone recebeu o toque, os toques feitos durante uma queda curta são enviados assim que ela reconecta, *NAO ENVIADO* se o telefone não confirmar (6); depois de desligada com o cabo conectado, KEY1 ou KEY2 também a ligam de novo; leituras de bateria com falha não chegam mais ao app como 0 %. Com um app mais antigo, uma pulseira 2.3 funciona como antes. **Firmware 2.2.2**: a mensagem de conexão passa para o idioma do app assim que ele chega (4.1); em espanhol *VINCULANDO…/VINCULADA* e em português *PAREADA*, como no app; em francês *MANCHES* na tela dos jogos. **Firmware 2.2.1**: também a contagem regressiva antes de desligar no idioma do app (6). **Firmware 2.2**: os textos da pulseira no idioma do app (4.1). **Firmware 2.1**: a tela de recarga (6.2); os ajustes, *Identificar* e o desligamento pelo app exigem pelo menos o 2.0. Carregue o `TSM_Band.ino` em **ambas** as pulseiras; com um firmware antigo o app avisa no painel de ajustes e o resto continua funcionando.

## 6. Usar as pulseiras

| Botão | Ação |
|---|---|
| **KEY1** (frontal) curto | ponto para quem está usando a pulseira (também inicia a partida na página INÍCIO DA PARTIDA); um bipe confirma (firmware 2.3 + app 2.4: quando o telefone o recebeu, cerca de meio segundo depois) |
| **KEY1** longo (1 s) | mostra a bateria (e mantém a pulseira ligada se ela estiver prestes a se desligar por inatividade) |
| **KEY2** curto | anula o último ponto (também no popup de fim de partida); dois bipes mais graves (com o firmware 2.3, quando o telefone confirma) |
| **KEY2** longo (2 s) | desliga a pulseira (com o cabo conectado mostra *CONTINUA CARREGANDO*: ela carrega mesmo desligada; a partir do firmware 2.3, KEY1 ou KEY2 também a ligam de novo) |
| Botão lateral | um clique liga; clique duplo desliga (função do hardware) |

- Ao ligar, pisca **PAREANDO...** (acesa 0,35 s a cada 2 s, para economizar). O app se conecta sozinho às pulseiras que já conhece assim que é aberto; as novas ele encontra em **Mais › Dispositivos** (ou em **Modo e regras** com o modo *Pulseiras*).
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

O resto da economia: CPU a 80 MHz, tela apagada quando não é necessária, Bluetooth de baixo consumo (o rádio acorda 2-3 vezes por segundo, mas o toque no botão é enviado mesmo assim em até ~120 ms), bipe alimentado só enquanto toca, microfone e 5V desligados, sensor de movimento (IMU) ligado só com o reconhecimento de golpes (6.4).

### 6.1 Ajustes da pulseira

Em **Mais › Dispositivos** ou **Modo e regras** (**Ajustes** embaixo da pulseira de cada jogador) ou durante a partida (toque em **J1**/**J2** no alto, ou no ícone dos controles deslizantes). Eles são salvos **na pulseira** e continuam valendo mesmo depois de desligá-la; a cada mudança a pulseira mostra o nome com *AJUSTES OK*. Com o firmware 2.4 há também a seção **Golpes** (6.4).

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
- **No app**: a linha da pulseira (em *Dispositivos* e em *Modo e regras*) e os ajustes dela mostram *Carregando 78%* ou *Carga completa*; na partida, as etiquetas J1/J2 têm o símbolo ⚡. O registro `files/battery_log.csv` também tem duas colunas a mais (tensão USB, carga completa): útil para verificar em quanto tempo ela carrega de verdade.
- Pulseira e app agora calculam a porcentagem com a **mesma curva** da LiPo (antes a pulseira usava uma reta menos precisa), então mostram o mesmo número.

> A verificar com as pulseiras reais: a recarga não pôde ser testada no hardware. Em especial, com que frequência o carregador indica *carga completa* e quão precisa é a porcentagem durante a carga.

### 6.3 Sem pulseiras: controles Bluetooth (app 2.5)

O jeito mais barato de deixar os jogadores marcarem os pontos: dois **controles Bluetooth de selfie** (os de botão de disparo, muitas vezes vendidos como *AB Shutter3*), que custam muito pouco. O Android os vê como um teclado: nenhum firmware para gravar e nenhuma permissão para o app. Qualquer outro teclado ou botoeira Bluetooth (ou USB) também serve.

1. Ligue o controle e pareie nas **configurações de Bluetooth** do telefone, como um fone de ouvido (*Mais › Dispositivos › Configurações de Bluetooth* abre a tela). Repita com o segundo.
2. *Modo e regras* › modo **Controles** › **Associar** embaixo do Jogador 1 › aperte a tecla do controle dele que vai dar o ponto; depois o mesmo para o Jogador 2. A cada clique de um controle associado a linha dele mostra **Tecla recebida**: assim dá para testar antes de jogar. **Trocar J1 ↔ J2** inverte os dois.
3. **INÍCIO DA PARTIDA**: um clique num controle começa a partida, como o KEY1 das pulseiras. No alto, **J1**/**J2** ficam coloridos quando os controles estão conectados.

| Gesto | Ação |
|---|---|
| um clique | ponto para quem está com o controle (chega meio segundo depois: o app espera para ver se é um clique duplo) |
| clique duplo | desfaz o último ponto (também na janela de fim de partida) |
| segurar 1 s | desfaz o último ponto |
| a outra tecla (controles de duas teclas) | desfaz o último ponto, na hora; apertada menos de meio segundo depois de um clique, cancela esse clique |

- Valem as proteções das pulseiras: dois pontos com menos de 2 segundos de diferença contam uma vez só, dois desfazer seguidos tiram um ponto só, *Partida encerrada* só se confirma no telefone.
- Muitos controles de selfie têm dois botões (*iOS* e *Android*): a tecla apertada ao associar dá o ponto e a outra desfaz. No CAMKIX (um *AB Shutter3*) o botão grande manda AUMENTAR VOLUME e o pequeno ENTER: associe com o botão grande e o pequeno vira “desfazer”. Se um controle manda duas teclas a cada aperto, um aperto continua sendo um clique.
- **A tela precisa ficar acesa com o app na frente** (durante a partida o app a mantém acesa sozinho). Diferente das pulseiras, as teclas de um teclado Bluetooth só chegam ao app visível: com a tela bloqueada ou outro app aberto elas não contam. Em partidas longas, deixe o telefone carregando.
- Se um controle se desconecta durante a partida (desligado, sem bateria, longe demais) o quadro laranja mostra *CONTROLE 1 DESCONECTADO*, e *CONECTADO* quando volta. Muitos controles dormem depois de alguns minutos sem clique: o primeiro clique os desperta e às vezes se perde. Se a voz não anunciar o ponto, aperte de novo.
- Alcance: uns dez metros, como um fone Bluetooth; com o telefone perto da rede, no meio da quadra, os dois lados ficam cobertos. Bateria: em geral uma CR2032, que dura meses.

### 6.4 Reconhecimento de golpes (firmware 2.4, app 2.5)

Com o firmware 2.4 a pulseira reconhece **forehand, backhand, saque e voleio** com o sensor de movimento (IMU) e os envia ao app, que os conta por jogador. O reconhecimento aprende com quem usa a pulseira: antes é preciso uma breve **calibração**, cujos exemplos ficam salvos na pulseira.

1. **Prenda a pulseira no pulso**, sempre do mesmo jeito (mesmo pulso, mesmo sentido). O reconhecimento se baseia na direção dos movimentos: uma pulseira virada em relação à calibração confunde os golpes.
2. Ajustes da pulseira (6.1) › **Golpes** › **Calibração**: toque em **Calibrar** ao lado de *Forehand*. A pulseira mostra *FOREHAND · CALIBRACAO 0/20*.
3. Faça **5-10 forehands** com uma pausa entre eles: a cada golpe o contador sobe, na pulseira e no app (com um bipe, se o volume não estiver no zero). Se um golpe saiu errado, toque em **Desfazer o último**. Depois **Concluir**.
4. Repita para backhand, saque e voleio. A lixeira apaga os exemplos de um golpe, para refazê-los. São mantidos no máximo 20 exemplos por golpe (acima disso o mais antigo é substituído). Bastam 3 exemplos de pelo menos dois golpes (*Pronta: reconhece os golpes calibrados*), mas mais exemplos, e mais variados, dão um reconhecimento melhor.
5. Ative **Reconhecimento de golpes**. Com **Mostrar o golpe na pulseira** o golpe aparece por um instante com a confiança do reconhecimento (ex. *BACKHAND 87%*), só se a tela não estiver mostrando o placar ou uma mensagem.

- Durante a partida, abaixo dos tempos, uma linha mostra os golpes de cada jogador (*J1 F 12 · B 8 · S 5 · V 3*). Eles contam só com a partida iniciada, não no aquecimento nem na calibração.
- O resumo (7) traz os golpes por tipo, a **velocidade do pulso** (média e máxima, em °/s: a força do golpe, não a velocidade da bola) e os **golpes por ponto** (duração das trocas de bola). Se só um jogador tem pulseira com golpes, as trocas contam só os golpes dele.
- Um golpe que não se parece com nenhum exemplo não é mostrado e conta como *não reconhecido*: se acontecer muito, confira como você usa a pulseira ou acrescente exemplos.
- Movimentos lentos (voltar à posição, pegar a bola) não contam: um golpe precisa passar de 400 °/s de rotação do pulso e 2,5 g de aceleração.
- **Bateria**: o sensor gasta cerca de 1 mA a mais, uns vinte minutos de uma carga completa (a estimativa nos ajustes já considera isso). Sem reconhecimento, o sensor fica desligado.
- Nos golpes muito fortes o giroscópio chega ao máximo (2000 °/s): o reconhecimento não é afetado, mas a velocidade mostrada fica um pouco abaixo da real.

### 6.5 Cintas cardíacas (app 2.5)

Frequência cardíaca e calorias de **cintas peitorais Bluetooth** com o serviço cardíaco padrão: Coospo (testada a H6), Magene, Polar, Garmin, Wahoo… Valem em todos os modos, mesmo sem pulseiras.

1. Molhe os eletrodos e coloque a cinta: ela liga sozinha em contato com a pele.
2. *Modo e regras* (ou *Mais › Dispositivos*) › **Frequência cardíaca** › ative **Cintas cardíacas** e escolha a cinta de cada jogador (uma vez só). **Trocar** as inverte.
3. **Idade, peso e sexo** de quem a usa (opcionais): para as calorias (fórmula de Keytel, erro típico ±20%) e, com a idade, para as zonas cardíacas (máximo estimado 208 − 0,7 × idade).

- Sob cada cinta: estado, frequência, **sinal** em dBm (mais perto de 0 = mais forte) e **quedas**, ou seja, as vezes em que a frequência não chegou por mais de 3 segundos, com os segundos perdidos (*2 quedas (14 s)*). Contam a partir do «jogo» da partida; antes, desde a escolha da cinta. Mostram até onde a cinta alcança.
- Na partida uma linha mostra a frequência ao vivo (*J1 · 142 bpm*). No resumo (7): frequência média e máxima, calorias, gráfico no tempo e tempo nas zonas cardíacas. As pausas não contam.
- **Alcance**: teste em casa com a H6 e o telefone parado: conectada até 21 m, mas a −100 dBm, no limite; de costas o sinal cai 5-10 dBm. Em simples o jogador distante fica a 24-30 m do telefone: para não perder dados use o **segundo telefone** (6.6).

### 6.6 Segundo telefone atrás da outra linha de fundo (app 2.5)

Cintas e pulseiras aceitam **um telefone de cada vez** e alcançam alguns metros a menos que o comprimento da quadra. Com um segundo telefone atrás da outra linha de fundo cada jogador sempre tem um por perto: o segundo (**satélite**) capta as cintas e pulseiras dos jogadores do seu lado e faz a ponte com o **principal**, que controla a partida.

1. **Principal**: Mais › **Dispositivos** › **Segundo telefone** › **Usar um segundo telefone**. Na primeira vez pede a permissão *Dispositivos próximos* (a localização até o Android 12). O telefone cria a sua **própria rede, só local**: sem SIM nem dados, e sem precisar do Wi-Fi do clube. Aparece um QR.
2. **Satélite**: Mais › **Dispositivos** › **Segundo telefone** › **Servir de segundo telefone** › escaneie o QR. O Android pede uma vez para conectar à rede do principal (*AndroidShare_…*): toque em **Conectar**. O satélite mantém os dados móveis. Deve mostrar *Conectado ao telefone principal*.
3. O satélite segue sozinho as cintas e pulseiras escolhidas no principal, nos mesmos lados: não é preciso escolher nada. Deixe-o atrás da linha de fundo com o app aberto.

- **Troca de lado**: não é preciso fazer nada. Cada cinta e cada pulseira fica com o telefone a que está conectada enquanto o sinal aguentar; depois o telefone mais próximo a assume (alguns segundos). No principal aparece *Pelo segundo telefone*; durante a passagem uma pulseira pode aparecer desconectada por um instante.
- **Nenhum dado perdido**: frequência, botões e golpes do satélite levam o **ID da partida** e um número sequencial, e o satélite os guarda até o principal confirmar. Se o Wi-Fi entre os telefones cair, a frequência chega depois e preenche os buracos do gráfico; os golpes contam se forem da mesma partida. Um **botão** da pulseira que chega mais de 5 segundos depois não dá o ponto: aperte de novo. O mesmo botão chegado por dois caminhos conta uma vez só.
- Do principal para as pulseiras passam placar, mensagens, *Identificar*, desligamento e configurações. É melhor mudar as configurações com a pulseira conectada ao principal.
- **Placar**: no satélite **Mostrar o placar** abre o placar da partida em tela cheia (como em 8.3), já conectado ao principal.
- Se o principal não conseguir criar a rede (hotspot normal já ligado), usa o Wi-Fi em que está: nesse caso os dois telefones devem estar na mesma rede. Com Android 9 ou anterior o satélite mostra nome e senha da rede, para escolher à mão nas configurações de Wi-Fi.
- **Parar** fecha a conexão e a rede; o satélite volta às suas cintas e pulseiras.

## 7. Como usar o app

1. **Nova partida** (opcional): **Tênis** ou **Padel** (app 2.5: mesmas regras, tempos e anúncios; escolhendo Padel, dupla e golden point (ponto de ouro) são configurados sozinhos, dá para mudar (no lugar do golden point, *Modo e regras* oferece também o *star point* da FIP: no máximo duas vantagens, depois um ponto decisivo), e o resumo diz *Padel*), clube, quadra, simples/duplas, nomes (nas duplas, dois nomes por equipe). **Buscar o endereço da quadra** (desativado por padrão) acrescenta ao resumo o endereço junto com as coordenadas: ele é pedido ao serviço de geocodificação do telefone, que com os serviços do Google envia as coordenadas ao Google. **Avançar**. Com *Padel* aparece também **Torneio Americano ou Mexicano**: de 4 a 16 jogadores e de 1 a 4 quadras, partidas por pontos (16, 24 ou 32: os pontos das duas duplas somados dão o total) ou por tempo. No *Americano* as duplas mudam a cada rodada para que cada um jogue com e contra o maior número de pessoas; no *Mexicano*, a partir da segunda rodada, joga-se pela classificação (1º e 4º contra 2º e 3º na mesma quadra). A cada rodada escreve-se o resultado de cada quadra (por pontos basta um número, o outro se completa) ou conta-se com *Contar ponto a ponto*, com a voz e, por tempo, o cronômetro; com mais jogadores que lugares em quadra, descansa-se em revezamento. **Próxima rodada** começa quando todos os resultados estão lá; a **Classificação** conta os pontos de cada um, depois as vitórias e o saldo de pontos, e é compartilhada como texto.
2. **Modo e regras**:
   - *Árbitro*, *Pulseiras* ou *Controles* (6.3). Com as pulseiras são obrigatórios Bluetooth ligado, permissão de localização e localização ativada: as linhas dos **Requisitos** se atualizam em tempo real (mesmo se você desligar o Bluetooth ou a localização pelo painel de notificações) e ficam vermelhas, com o botão para corrigir.
   - A **busca é automática e contínua** enquanto a página estiver aberta: ligue as pulseiras e elas ocupam sozinhas os lugares livres (primeiro Jogador 1, depois Jogador 2). **Identificar** faz aquela pulseira piscar na cor do jogador (amarelo ou vermelho) com bipes, para você ver logo qual tem na mão; **Trocar J1 ↔ J2** as inverte sem desconectá-las. No menu suspenso você sempre pode escolher outra ou *Nenhuma* (a que for tirada à mão não é recolocada pela busca). Uma pulseira não pode ficar com dois jogadores.
   - **Desligar as pulseiras no fim da partida e ao sair** (ativado por padrão): ao confirmar *Partida encerrada* e com *Sair*, as pulseiras se desligam em vez de esperar a inatividade.
   - No modo árbitro, tocando em **Avançar** sem localização aparece o convite para ativá-la (senão o local não vai constar no resumo).
   - Formato (*3 sets*, *5 sets*, *2 sets + tie-break decisivo a 10*, *pro set* de 8 ou 10 jogos, *só tie-break decisivo a 10*), jogos por set (6, ou 4 em sets curtos com tie-break no 4-4), tie-break nos sets sim/não (não: o set continua até alguém ter 2 jogos de vantagem), *Sem vantagem* (No-Ad). Idioma e voz ficam em *Mais › Idioma e voz* (4.1).
   - **Sorteio**: a moeda gira e indica quem vence; você define quem serve e os lados da quadra **vistos da cadeira do árbitro** (esquema da quadra com **Inverter lados**). Nas duplas, escolha também quem serve primeiro em cada equipe.
3. **INÍCIO DA PARTIDA** pisca: toque no botão, pressione KEY1 de uma pulseira ou aperte um controle. A voz diz *"Primeiro set" · "[nome] ao serviço" · "joguem"* com 1 segundo entre as frases; o **Match Time** começa em "joguem".
4. **Partida**: no alto à esquerda o tempo de partida, à direita a contagem regressiva (**Shot Clock** 25 s, **Changeover Time**, **Set Break Time**; em vermelho nos últimos 5 s). O quadro laranja acende por 5 s para *troca de lado, tie-break, set point, match point, break point…*. Os dois botões quadrados (amarelo = Jogador 1, vermelho = Jogador 2) ficam do lado em que os jogadores estão de verdade e trocam de lugar a cada troca de lado; embaixo de quem serve aparece **On Serve**. Embaixo: *Anular ponto*, *Suspender/Retomar*, áudio (ícone do alto-falante, riscado = desligado), *Nova partida*, **Sair**. No modo pulseiras, embaixo dos tempos ficam **J1**/**J2** com bateria e autonomia: tocando neles, abrem-se os ajustes da pulseira. Um toque duplo em *Anular ponto* tira um ponto só (um segundo toque em menos de 1 s não conta); logo depois de uma troca de página o segundo toque de um toque duplo é ignorado, para não cair num botão da página nova. O **cadeado** embaixo bloqueia a tela (por exemplo com o telefone no bolso ou no banco): os toques não fazem nada, pulseiras e controles sim; para desbloquear, segure o cadeado.
5. No último ponto aparece o popup **Partida encerrada / Anular o último ponto**. "Partida encerrada" só se confirma **no telefone**. Por meio segundo depois de aparecer, os botões dele ignoram toques: um toque duplo no último ponto não anula nada.
6. **Resumo**: vencedor, nomes, placar set a set com os pontos do tie-break, duração, hora de início e de fim, data, clube, quadra, local, formato, pontos e jogos ganhos. Botões **Salvar arquivos** (nome do arquivo + pasta à escolha + formatos .txt/.json/.png e **PDF completo** com resultado, estatísticas da partida e de cada set, histórico e fotos). **Compartilhar** pergunta se envia a imagem com texto (para WhatsApp e redes sociais) ou o PDF completo. No Resumo, **Fotos da partida** guarda até 10 fotos de recordação (da galeria ou tiradas na hora): ficam no telefone, vão para o PDF e passam para o outro telefone com a transferência, nunca para a imagem das redes, **Compartilhar** (imagem 1080×1350 + texto para WhatsApp/Instagram/…), **Nova partida**, **Sair**. Abaixo das fotos, **Notas** aceita um texto livre (até 2000 caracteres): salva sozinho, vai para o PDF, o texto compartilhado e os arquivos .txt e .json, e passa para o outro telefone com a transferência; não vai para a imagem das redes. A partida terminada entra sozinha no **Histórico** (aba **Progresso**): a lista das partidas, para reabrir com o resumo ou excluir, e a aba **Jogadores** com vitórias, derrotas e confronto direto de quem tem o nome escrito em *Nova partida* (maiúsculas e espaços não contam; nas duplas conta cada jogador). **Importar** adiciona as partidas salvas como .json com *Salvar arquivos* em outro telefone: as terminadas vão para o histórico e as não terminadas para as partidas suspensas para retomar. **Transferir** (no alto à direita no Histórico) passa o histórico e as partidas suspensas para outro telefone com um código QR: os dois telefones precisam estar na mesma rede Wi-Fi ou um no hotspot do outro; no que tem as partidas toque em *Enviar*, **escolha as partidas para mandar** (a última já vem marcada; há *Todas*, *Nenhuma*, *Últimos 7 dias* e *De um jogador…*), toque em *Enviar (N)* e deixe aberta a janela com o código, no outro em *Receber* e escaneie o código (precisa da permissão da câmera). Assim dá para continuar em outro telefone até uma partida em andamento: suspenda, transfira e retome. As partidas se somam às que o telefone já tem. **Fotos dos jogadores**: toque no círculo ao lado do nome em *Nova partida* (ou no detalhe do jogador no Histórico) e escolha uma foto; ela é cortada quadrada e encontrada pelo nome. Aparece no Histórico, no resumo e no placar da TV, fica só neste telefone (vai junto na transferência) e não entra na imagem compartilhada. Tocando de novo no círculo você troca ou remove a foto. Se o app fechar enquanto você está no resumo (o Android fechando o app em segundo plano, uma atualização, uma reinicialização do telefone), ele volta quando o app é reaberto; não se você mesmo saiu com *Nova partida*, *Sair* ou tirando o app dos recentes. A pasta padrão é **Documents/Tennis Score Manager** (a partir do Android 10; no Android 8 e 9 a pasta do app): qualquer gerenciador de arquivos a abre e ela fica mesmo desinstalando o app. Salvando duas vezes com o mesmo nome, o segundo vira *nome (1)*; na imagem os textos longos (nomes, endereço) se ajustam à largura.

   O resumo tem três abas. **Resumo**: os dados acima e, com as pulseiras 2.4, golpes, velocidade do pulso e golpes por ponto (6.4). **Estatísticas**, da partida ou de um set: os dois jogadores comparados com barras que saem do centro (colorida a de quem está à frente) para pontos e jogos ganhos no saque e na devolução, break points salvos e convertidos, total de pontos e jogos, pontos seguidos, últimos 10 pontos, match points salvos; com as pulseiras 2.4 também golpes por tipo e velocidade média do pulso. **Ponto a ponto**: set a set, cada jogo com a bola de quem saca, *SAQUE PERDIDO* nas quebras, o placar depois de cada ponto com **BP** (break point), **SP** (set point) e **MP** (match point) e o resultado (*JOGO*, *QUEBRA*, *SET*, *PARTIDA*). Aces, duplas faltas, primeiros saques, winners e erros aparecem se você ativar as **estatísticas detalhadas** em *Modo e regras*: no lugar dos dois botões grandes surgem *Ace* e *Falta* (na segunda vez vira *Dupla falta*) e, para cada jogador, *Winner*, *Erro* (o ponto vai para o outro) e *Ponto*; *Anular ponto* também tira uma falta. É pensado para quem marca da beira da quadra. Depois de *Erro* aparece por 6 segundos *Não forçado / Forçado*; com pulseiras ou controles, depois de cada ponto aparece *Como terminou o ponto?* (Ace, Winner, Erro): responder é opcional e o placar não muda. No Histórico, o detalhe de um jogador mostra a **evolução**: pontos ganhos partida a partida e as médias de aces, duplas faltas, primeiros saques, winners e erros. As estatísticas de cada partida contam também game, set e match points convertidos, games vencidos a zero, games seguidos, sets vencidos depois de estar um break atrás e, no cartão **Tempos**, a duração de cada set e o game mais longo.

   **Revanche** (no fim do resumo): propõe o mesmo dia da semana e a mesma hora, na semana seguinte, e o clube da partida; dá para mudar data, hora e local e acrescentar uma nota. **Mandar o convite** abre o compartilhamento (WhatsApp, SMS, e-mail…) com a mensagem pronta: quem contra quem, quando, onde e como terminou da última vez. **Pôr na agenda** cria o evento no app de agenda, **Preparar a partida** traz de volta para *Nova partida* jogadores, clube, quadra e regras (o torneio não). Sem conta: o convite passa pelos canais que você já usa.

**Sair**: **Sair** (na partida pede confirmação, e também está no resumo) fecha de verdade o app; o mesmo acontece se você o remover dos apps recentes. Ao reabrir, ele começa pela aba **Jogar**.

**Salvamento**: a partida é salva sozinha a cada ponto. Na aba **Jogar** uma partida suspensa se exclui **mantendo pressionado** o seu *Retomar*; todas as suspensas estão em **Progresso › Histórico › Suspensas**, para retomar ou excluir (marcando várias, também juntas). *Suspender* para os tempos; se você sair, se o telefone desligar ou se o app for fechado, a partida fica em **Retomar** (aba **Jogar**, ou *Retomar partida suspensa* na tela INÍCIO DA PARTIDA) e continua com *Retomar*. *Anular ponto* recalcula tudo desde o início, então funciona também depois do fim de um jogo, de um set ou da partida. A lixeira de uma partida suspensa pede confirmação. Se a partida foi iniciada por uma pulseira com a tela bloqueada (o Android não fornece a localização nesse caso), o local é obtido assim que você abrir o app de novo.

### 7.1 Treino com a cinta (app 2.5)

Para treinar com a cinta cardíaca sem partida: **Treinar** › **Treino com a cinta**. Usa-se **uma cinta**, a do Jogador 1, com idade, peso e sexo para calorias e zonas; ela é escolhida ali mesmo (com o interruptor *Cintas cardíacas*, Bluetooth e permissões se faltarem).

1. **Iniciar**: em destaque a frequência ao vivo, embaixo o tempo e a volta atual.
2. **Volta** fecha um trecho do treino e abre outro (como o botão de volta de um cronômetro): um tiro, um exercício, uma volta na pista. **Pausar / Retomar** para o tempo; as pausas não contam.
3. **Terminar**: o app grava **mais um minuto de recuperação** (fique parado) para medir quanto a frequência cai; **Salvar já** pula essa parte.

- **Resumo**: duração, frequência média e máxima, calorias, **VFC (RMSSD)** a partir dos intervalos entre batimentos enviados pela cinta (são precisos uns trinta batimentos limpos; compare com os seus treinos parecidos, não com os dos outros), **recuperação em 1 minuto** (quanto mais cai, melhor), gráfico, zonas cardíacas e, para cada volta, duração, média, máximo e recuperação.
- **CSV** (segundo a segundo, com a volta e o minuto de recuperação) e **PDF** de uma página com gráfico, pizza das zonas e voltas, para compartilhar.
- Os treinos ficam na mesma tela, separados das partidas; podem ser reabertos ou excluídos. A frequência chega também do segundo telefone (6.6) e a gravação continua com a tela apagada.

### 7.2 Progressos (app 2.5)

**Progresso** › **Rating TSM · Progressos**: um documento para ver se você está melhorando, **separado** para treinos e partidas.

- **Treinos**: duração, frequência média e máxima, calorias, VFC, recuperação.
- **Partidas** de um jogador do histórico (escolhido na lista): partidas vencidas, pontos ganhos, aces, duplas faltas, primeiros saques em quadra, winners, erros não forçados. Aces e os outros valores detalhados só existem nas partidas jogadas com estatísticas detalhadas; são totais por partida.
- Para cada item a média da **primeira metade** do período e a da **segunda**: ▲ verde se melhora, ▼ vermelho se piora (menos duplas faltas ou erros é uma melhora), = se fica igual (menos de 5% de diferença) ou se não dá para dizer (frequência). São precisos pelo menos 2 treinos ou partidas.
- **O que comparar**: os **Filtros** escolhem o período (tudo, 30 dias, 3 meses, 1 ano), os **dias da semana** (por exemplo só as quartas-feiras, também vários) e, para as partidas, **torneio**, **clube** e **adversário** do histórico; embaixo, a lista **Para comparar** para marcar (*Todos* / *Nenhum*). Comparação, PDF e impressão usam só os marcados.
- **Rating TSM**: em *Partidas*, acima dos filtros, o rating do jogador (todos começam em 1500) com as partidas contadas, a variação da última e a evolução, e a **Classificação TSM** de todos os jogadores do histórico. É um Elo calculado no telefone: sobe quando você vence, mais contra quem tem rating mais alto e com placar folgado; nas duplas conta a média da dupla; tênis e padel têm classificações separadas. Nas 5 primeiras partidas é *provisório* e se move mais. No resumo de cada partida o cartão **Rating TSM** mostra quanto cada um ganhou ou perdeu; com **Amistoso** a partida fica no histórico mas não conta.
- **PDF** (com a evolução de cada item e a lista completa, em várias páginas se necessário) para compartilhar, ou **Imprimir** com o serviço de impressão do Android (também *Salvar como PDF*).

**Objetivos** (no alto de Progressos): escolha um dado (partidas ganhas, pontos ganhos, primeiros serviços, aces, duplas faltas, erros não forçados, partidas em 30 dias, treinos em 7 dias) e uma meta; a barra enche-se com a média das suas últimas 3, 5 ou 10 partidas. Quando a alcança aparece um aviso em **Jogar**. **O meu ano** (também no separador Progresso): partidas, vitórias, horas em quadra, o rival com o balanço, as partidas mês a mês, a maior série e os treinos, numa imagem para partilhar. Os dois precisam que o app saiba quem é (Mais › Você).

**Calendário** (em Progressos, abaixo de *O meu ano*): o mês com pontos amarelos das partidas e vermelhos dos treinos, e há quantas semanas seguidas joga ou treina; toque num dia para ver o que fez e abrir a partida.

### 7.3 Vídeos: telefone-câmera, melhores momentos e replay (app 2.5)

Um segundo telefone num tripé filma a partida e coloca no vídeo o placar do árbitro.

1. **Árbitro**: Mais › **Dispositivos** › **Segundo telefone** › **Usar um segundo telefone** (QR, como em 6.6).
2. **Câmera**: Mais › **Dispositivos** › **Segundo telefone** › **Servir de câmera**, escaneie o QR e depois **Abrir a câmera** (permissões de câmera e microfone; o telefone precisa gravar em 1080p). Com *Placar conectado* o gráfico do placar está no vídeo (**Gráfico** liga ou desliga); sem árbitro grava só o vídeo.
   - **Gravar / Parar**: o vídeo vai para a galeria (*Filmes/Tennis Score Manager*) com capítulos, um por ponto.
   - **Ao vivo**: transmissão RTMP no YouTube, Twitch, Facebook ou num servidor seu; servidor e chave da transmissão em *Ao vivo: servidor*.
   - **Enquadrar** (assistente de enquadramento): informe a quantos metros atrás da linha de fundo e a que altura você colocou o telefone e em que quadra joga; o app indica a inclinação para baixo que mantém no vídeo as duas linhas de fundo e o jogador distante, e diz se a quadra cabe na largura. Muitas vezes o limite é a largura: da grade (cerca de 6 m) a lente principal corta os cantos próximos, a **grande-angular** não (**Usar a lente…**, ou o botão **Lente**, com a gravação parada). Com **Mostrar o nível** a pré-visualização mostra o horizonte e indicações (*aponte 2° mais para baixo*, *gire no sentido horário*…), em verde quando está certo, com uma vibração quando o telefone está na posição. O nível aparece também na janela **Câmera** do telefone do árbitro. Os sensores não sabem esquerda e direita: confira a olho se a rede está no centro.
   - **Contorno** (contorno da quadra): a pré-visualização mostra as linhas da quadra como a lente as vê, e elas acompanham o telefone quando você o inclina. Mova os controles (**Distância**, **Altura**, **Direção**, **Deslocamento lateral** e, se preciso, **Campo de visão** e **Correção da inclinação**) até coincidirem com as linhas reais: assim se acerta também o centro, e a inclinação sugerida fica precisa. O ajuste é lembrado para a quadra da partida (*Clube · Quadra* do árbitro) e recuperado na próxima vez. Contorno e controles também estão na janela **Câmera** do árbitro. Quanto mais alto o telefone, melhor se vê a metade distante da quadra: a 2,5 m ela fica espremida logo acima da rede.
3. **Câmera no alto, comandada pelo árbitro**: no telefone do árbitro, **Câmera** (ao lado de *Replay* durante a partida, ou *Enquadramento e comandos da câmera* na Mais › **Dispositivos** › **Segundo telefone**, útil para ajustá-la antes de começar) mostra o enquadramento do telefone-câmera como fica no vídeo, com o gráfico, e **Gravar / Parar**, **Ao vivo**, **Gráfico** e o **zoom**. Mostra também a duração da gravação, a bateria e o espaço livre (em laranja quando baixos). A imagem é pequena e aos saltos (cerca de 3 quadros por segundo) e só é enviada enquanto se olha; a chave da transmissão é colocada no telefone-câmera.

- **Vídeo** › **Seus vídeos**: os vídeos gravados, para rever ponto a ponto, game a game ou set a set. **Capítulos do YouTube** prepara os capítulos para colar na descrição. **Importar vídeo** pega um vídeo feito com outra câmera (o arquivo fica onde está): os pontos são marcados assistindo, tocando no fim de cada ponto quem o ganhou e, se quiser, como terminou (*Desfazer o último* tira o último); formato e regras são os de *Modo e regras*.
- **Filtros do treinador** (ícone do filtro): só os pontos de um jogador, ganhos ou perdidos, no saque ou na devolução, no primeiro ou no segundo saque, pela forma como terminaram, em break points ou em set e match points. **Reproduzir** coloca-os em sequência.
- **Melhores momentos**: montagem automática dos melhores pontos (aces, winners, break points, set e match points, trocas de bola longas com as pulseiras) na ordem de prioridade escolhida com as setas, de no máximo 1, 3, 5, 10 ou 15 minutos ou uma **Outra duração** até 90. Se não couber tudo, primeiro os trechos são encurtados, depois ficam de fora os pontos menos importantes. O resultado vai para a galeria.
  - **Estatísticas no fim do vídeo**: 2 ou 3 telas de 10 segundos como nas transmissões dos torneios: saque e break points; pontos, erros e trocas de bola curtas e longas; batimentos, se a cinta foi usada. A partida é a do histórico jogada durante a gravação, ou a dos pontos marcados num vídeo importado. Nas laterais, a foto do jogador; sem foto, um retrato estilizado de homem ou de mulher (escolha **Homem / Mulher** em *Nova partida*) (sem escolha, o do homem; vale em todo o app, também em *Nova partida*, no histórico, no resumo, no PDF e no placar da TV); embaixo, a porcentagem de pontos ganhos. No alto, torneio, categoria e fase (*Mais detalhes* em *Nova partida*), ou clube e quadra.
  - **Música** sob as estatísticas, com fade de entrada e de saída: *Nowhere Land* de Kevin MacLeod (incompetech.com, licença CC BY 4.0: se publicar o vídeo, dê o crédito), ou uma faixa sua com **Escolher uma faixa** (usa-se o começo; use só músicas que você tem o direito de colocar nos seus vídeos).
  - No rodapé de cada tela de estatísticas aparece **Criado com Tennis Score Manager** com o endereço do site.
- **Replay**: no telefone do árbitro, com a câmera conectada, **Replay** pede à câmera o ponto contestado (*Último ponto*, *Ponto anterior* ou *Desde o último ponto marcado*) e o mostra, com pausa e quadro a quadro. **Também no placar** mostra também no placar da TV.

### 7.4 Raquetes: raquete virtual (app 2.5)

**Progresso** (ou **Mais**) › **As minhas raquetes**: cada raquete física tem a sua ficha (duas raquetes do mesmo modelo são duas fichas: cordas, peso e horas mudam).

- **Ficha**: alcunha (A, B…), marca, modelo, peso, equilíbrio, cabeça e padrão de cordas, escritos à mão ou tirados **do catálogo** (dados do fabricante, a corrigir se mediu a sua). *Posta de lado*: deixa de ser proposta, mas fica nas comparações.
- **Encordoamentos**: tipo (monofilamento, multifilamento, sintética, tripa natural, híbrido), marca e modelo (também das cordas comuns), calibre, tensão das verticais e das horizontais, data e notas. *Novo encordoamento* parte do montado. Para cada um a app soma as **horas de jogo** dos jogos e treinos associados e avisa ao passar o **limite de verificação** escolhido (20 horas por omissão): só pelo tempo não consegue saber quanta tensão resta.
- **Antes de jogar**: em *Nova partida*, por baixo da classificação de cada jogador, escolhe-se a sua raquete; no treino, acima da cinta. O jogo (pontos, pancadas das pulseiras, batimento) e o treino são guardados com uma **cópia** da raquete como estava nesse dia, cordas e tensão incluídas: voltar a encordoá-la depois não muda as sessões passadas.
- **No resumo** do jogo (separador *Resumo*) e do treino vê-se a raquete usada, pode associá-la também depois (usa-se o encordoamento montado nesse dia) e avaliar as **sensações** de 1 a 5: controlo, conforto, potência e cansaço.
- **Comparação**: *por raquete* ou *por encordoamento* (mesma raquete com tensões ou cordas diferentes), escolhem-se as configurações A e B. Barras lado a lado para sessões, horas, jogos ganhos, pontos ganhos, primeiros serviços dentro, pontos ganhos com o primeiro e o segundo, ases, duplas faltas, winners e erros não forçados por jogo, velocidade média do pulso e pancadas das pulseiras, batimento médio e sensações médias. O melhor valor fica a negrito, mas a app não escolhe uma vencedora: adversário, piso, condições e forma também contam.

### 7.5 Treino de golpes: saque, forehand, backhand e voleio (app 2.5)

Um telefone no tripé filma quem saca; a pulseira no pulso reconhece o saque e com os botões se diz como foi. Basta um telefone, sem árbitro.

1. **Treinar** › **Treino de golpes**, escolha o golpe (**Saque**, **Forehand**, **Backhand**, **Voleio**) › **Começar** (a pulseira deve estar conectada como em 6.1; sem ela, marque pelos botões da tela).
2. **Onde colocar o telefone** (abre sozinho na primeira vez, depois pelo botão *Onde coloco o telefone?*):
   - **ao lado de quem saca**, do lado do braço que bate (à direita para um destro), a **3-4 m**, na altura da linha de fundo ou meio metro atrás;
   - lente a **1-1,2 m** do chão, telefone **na horizontal** e reto, apontado para quem saca;
   - no enquadramento tem de caber **a pessoa inteira, dos pés à raquete no alto** (cerca de 2,7 m): se a cabeça da raquete sair, afaste o telefone ou passe para a **grande-angular** (botão *Lente*);
   - para ver também para onde vai a bola, como alternativa: 3-4 m **atrás** de quem saca, um pouco de lado, a 1,5-2 m de altura;
   - nunca na frente de quem saca nem na trajetória da bola.
3. Escolha o **lado** (direito/esquerdo), **primeiro ou segundo** e, se quiser, o **alvo** (no T, no corpo, aberto): valem para os saques seguintes.
4. Saque e aperte **KEY1 = dentro** ou **KEY2 = fora**; **KEY1 pressionado** desfaz o último. A voz diz a contagem (*Dentro. 8 de 12*) e a pulseira mostra. Se a pulseira reconheceu o saque, vale esse instante; senão, alguns segundos antes do botão. Por isso, ao começar, o app **liga sozinho o reconhecimento de golpes** na pulseira (firmware 2.4; não precisa calibrar, só conta o instante) e no fim deixa como estava; *Reconhecimento de golpes ligado* na tela confirma. Com um firmware mais antigo o instante é estimado pela tecla.
   - **A tela**: no alto uma linha só (dentro sobre tentativas, o último golpe e três ícones: pulseira, reconhecimento de golpes, memória com o número de clipes e o espaço); as explicações aparecem por alguns segundos no começo e ao tocar em **i**, para deixar o enquadramento livre. A câmera guarda na **memória** os dois últimos minutos e salva **só os golpes marcados** (alguns segundos antes e depois): nada de vídeos longos para cortar.
   - **Pausa** desliga câmera e memória (bateria, calor) enquanto você recolhe as bolas: as teclas da pulseira não marcam nada, KEY1 ou **Retomar** recomeçam. Depois de **5 minutos sem golpes** a pausa entra sozinha (a voz avisa) e aí também recomeça no primeiro golpe reconhecido.
5. De cada saque é salvo um **clipe** de alguns segundos em volta do golpe. **Fim** mostra o resumo: dentro sobre tentativas, por lado, primeiro e segundo, alvo e, se a pulseira reconheceu os saques, a rotação média do pulso nos saques dentro e nos de fora. **Rever todos / só os dentro / só os fora** abre os clipes no replay (pausa, câmera lenta, quadro a quadro).
6. Embaixo de **Começar** ficam as 3 últimas sessões **do golpe escolhido** (os botões dos golpes mostram quantas sessões cada um tem, ex. *Backhand · 4*) e a linha dos **golpes dentro, sessão após sessão**. **Todas as sessões** abre as do golpe escolhido, por mês, com o espaço dos vídeos e se já foram analisadas. Toque numa sessão para o resumo e os clipes; **segure** para selecioná-la (o ícone no alto seleciona todas) e depois **Apagar** (sessão e vídeos) ou **Só os vídeos** (libera espaço: ficam as contagens, as medidas da análise e as imagens dos momentos-chave). **Progresso** mostra a evolução de cada golpe.
   - **Onde ficam os vídeos**: em **Documentos/Tennis Score Manager/Treino de golpes**, com as pastas **Saque**, **Forehand**, **Backhand** e **Voleio** (criadas ao abrir o app) e dentro uma pasta por sessão (data e hora) e uma por golpe, com `video.mp4` (abre e compartilha de qualquer app), a análise e as três imagens. Aparecem em qualquer gerenciador de arquivos e ficam mesmo desinstalando o app (guarde como arquivo: um app reinstalado não recupera as sessões). No Android 8-10 a mesma estrutura fica na pasta do app. Os clipes das sessões de antes da 2.5 vão para lá sozinhos na primeira abertura.
7. **Análise do movimento** (a partir da sessão salva): **Analisar os saques** encontra a posição do corpo nos clipes (modelo MoveNet: na primeira vez o app oferece baixá-lo, 7 MB, da página do projeto no GitHub; depois tudo fica no telefone, sem internet, alguns segundos por saque; pode ser excluído no fim da tela. Não está disponível em aparelhos com processador x86) e mede três momentos: **preparação** (braço do lançamento no alto), **joelhos** mais flexionados e **impacto**. Para cada saque: as três imagens com o esqueleto desenhado e as medidas (joelho mais flexionado, cotovelo no impacto, altura do impacto em relação à altura, braço do lançamento, ombros na preparação, tempo da preparação ao impacto); no alto, **dentro e fora** comparados. São estimativas a partir de um vídeo de lado: servem sobretudo para comparar os seus saques entre si. O corpo é bem reconhecido com a pessoa inteira no enquadramento, de lado e com boa luz; raquete e bola não são medidas.
   Toque num saque para abri-lo: **Ver o vídeo com o esqueleto** abre o clipe no replay com o corpo desenhado por cima, em câmera lenta, com botões para saltar para **Preparação**, **Joelhos** e **Impacto** e outro para tirar ou pôr o esqueleto; dois dedos ampliam vídeo e esqueleto juntos (até 6×), toque duplo para voltar. **Comparar com outro golpe** coloca ao lado o mesmo golpe de outro dia ou da mesma sessão (só os analisados e com vídeo, da mesma pessoa): os dois vídeos com o esqueleto, um em cima do outro (lado a lado com o telefone na horizontal), alinhados no impacto e em loop no mesmo trecho, com velocidade, quadro a quadro e os momentos-chave; **Sobrepor** desenha no primeiro também o esqueleto do segundo (em azul), com os quadris no mesmo ponto e o tronco do mesmo comprimento, para ver onde o movimento muda. As três imagens também se tocam para vê-las em tela cheia e ampliá-las. Com **várias pessoas** no enquadramento o app segue quem saca (quem mais levanta o braço) e cobre as outras; isso aparece embaixo do saque: confira nas imagens. **Refazer a análise** começa de novo (os clipes ficam), útil depois de uma atualização do app.

**Forehand, backhand e voleio** funcionam do mesmo jeito (KEY1 dentro, KEY2 fora, um clipe por golpe, resumo e análise), com estas diferenças:
- **Onde colocar o telefone**: ao lado de quem bate, perpendicular à direção dos golpes, a 4-5 m e fora do corredor, na altura do ponto de impacto; lente a cerca de 1 m; o swing inteiro no enquadramento (o pulso se move uns 2 m para a frente e para trás). Para o voleio: ao lado de quem joga na rede, a 3-4 m, na altura dele. Sempre do mesmo lado durante a sessão, e nunca na trajetória da bola.
- No lugar de lado, primeiro/segundo e alvo escolhe-se a **direção** desejada (**cruzado**, **no centro**, **paralela**) e, para o voleio, se é **de forehand** ou **de backhand**; o resumo conta-os à parte.
- **Análise do movimento**: os três momentos são **preparação** (onde o pulso para no fim da preparação), **impacto** (pulso mais rápido; ali perto o app relê um quadro a cada 33 ms) e **final** (pulso mais alto depois do impacto; no voleio, onde o pulso para). Se houver vários golpes na janela, vale o reconhecido pela pulseira; os pulsos trocados pelo MoveNet (acontece de lado) se corrigem sozinhos. Filme **de lado**: de frente a preparação vai em profundidade e não aparece. As medidas (*—* se o cotovelo ou as pernas não aparecem): joelho mais flexionado, cotovelo no impacto, altura do impacto, quanto **à frente do corpo** se bate, **amplitude da preparação**, altura do final (todas em relação à altura) e o tempo da preparação ao impacto. O vídeo com o esqueleto tem os botões **Preparação**, **Impacto** e **Final**.

### 7.6 Alunos: a parte do treinador (app 2.5)

- **Treinar › Alunos** › **Novo aluno**: nome (o mesmo das partidas: assim foto, histórico e rating aparecem sozinhos) e mão.
- **Quem treina**: em Treinar, acima de *Começar*, escolha o aluno (ou *Nenhum aluno*). A sessão é dele: as últimas sessões, *Todas as sessões*, a evolução em Progresso e os números nos botões dos golpes são só dele, e os clipes vão para **Treino de golpes/<golpe>/<aluno>/<data e hora>**.
- **A ficha do aluno** (toque no nome em *Alunos*):
  - **Golpes**: para saque, forehand, backhand e voleio as sessões, os golpes, a última vez e a linha dos golpes dentro; tocando num golpe abrem-se as sessões dele.
  - **Movimento**: as medidas da análise (6.4 e 7.5) da última sessão analisada ao lado da anterior, para ver o que muda.
  - **Partidas**: vitórias e derrotas, rating TSM e as últimas partidas do histórico com o nome dele.
  - **Notas do treinador**: o que trabalhar, objetivos; salvam-se sozinhas.
  - **Raquete**: na ficha (e em *Editar*) escolhe-se a raquete dele em *As minhas raquetes*: vai com cada sessão como estava naquele dia, cordas e tensão incluídas, e a ficha mostra o encordoamento montado. Uma raquete que não era de ninguém passa a ser dele, e é proposta também nas partidas dele. Se a raquete ainda não existe, **Nova raquete** cria-a dali (com o jogador como dono) e a escolhe.
  - **Relatório para o aluno**: escolha *Últimos 30 dias* ou *Tudo*, depois **PDF completo** (golpes com a porcentagem dentro e a evolução, movimento, de cada golpe um dentro e um fora com as três imagens dos momentos-chave, partidas, rating e suas notas) ou **Imagem para WhatsApp** (uma página só com os números dos golpes e o impacto de um golpe). O compartilhamento abre na hora: WhatsApp, e-mail ou onde quiser. O relatório leva seu nome como treinador (de *Você*) e o clube (de *Clube › Quadras*).
  - **Treinar agora** escolhe o aluno e volta para Treinar, pronto para *Começar*. **Remover aluno** tira-o da lista: as sessões ficam, sem aluno. **Apagar aluno e os dados dele** remove tudo o que o telefone tem dele: sessões com vídeos e análise, notas e foto (a raquete fica, sem dono; as partidas do histórico ficam, porque os adversários também estão nelas).

### 7.7 Jogadores: o banco (app 2.5)

Cada pessoa que joga ou treina é um **jogador** do banco, com um identificador que continua o mesmo em cada telefone: estatísticas, rating e histórico são dessa pessoa mesmo com um homônimo, também depois de passar partidas de um telefone para outro.

- **Nome, sobrenome e apelido**: nome e sobrenome são obrigatórios e identificam a pessoa; o **apelido** (opcional) é como ela é chamada em quadra: partida, placar, voz e estatísticas usam o apelido se houver, senão nome e sobrenome. Os jogadores nascidos do histórico têm um nome só, que vira o apelido: em *Jogadores* aparecem *a completar* e a ficha oferece **Completar nome e sobrenome**.
- **A ficha** (*Novo jogador* ou *Editar*): só **nome e sobrenome** são obrigatórios; o resto é opcional, mas cada dado tem o seu motivo:
  - **Apelido**: é o nome que se vê e se ouve na partida, no placar e na voz ("Rossi" em vez de "Mario Rossi").
  - **Foto** e **homem/mulher**: a foto aparece em Nova partida, no Histórico, no resumo e no placar da TV; sem foto, homem/mulher escolhe o retrato. Homem/mulher também serve para as calorias.
  - **Data de nascimento**: a idade aparece na ficha e serve para as calorias; para os sócios, decide se é menor (avisos ao responsável, consentimentos).
  - **Altura e peso**: com idade e sexo fazem as **calorias da cinta cardíaca** se calcularem sozinhas quando o jogador joga ou, se é você, treina (sem eles o app pede à parte ou não calcula).
  - **Clube de origem**: aparece na ficha; útil para convidados e adversários de outros clubes, para saber quem são.
  - **Mão e backhand** (com uma ou duas mãos): descrevem o jogador na ficha, útil para o treinador; a análise do movimento reconhece sozinha o braço que bate.
  - **Aluno**: coloca-o entre os alunos do treinador (Treinar › Alunos, *Quem treina*), com sessões, movimento e relatório.
  - **Telefone e e-mail**: servem para os **avisos** (WhatsApp, SMS, e-mail: torneios, mensalidades, convocações) mesmo que não seja sócio.
  - **Raquete**: vai com cada sessão e partida como estava naquele dia (cordas e tensão) e comparam-se as estatísticas; se não existe, *Nova raquete* cria-a dali.
  - Ranking, cartões e atestado não ficam aqui: ficam nos *Dados do sócio*, porque servem para torneios e mensalidades.
- **Nova partida**: em quadra vão jogadores do banco de dados. Cada posição tem **Escolher o jogador**, que abre a lista com pesquisa (primeiro sócios e alunos; quem já está em quadra não aparece) e no alto **Jogador novo**, que pede nome, sobrenome e o resto e depois o coloca em quadra; embaixo, os jogadores recentes para tocar. Escolhido, a posição mostra foto e nome com **Trocar** e o **X**; o ranking de um sócio se preenche sozinho. Um nome antigo sem ficha (revanches, partidas velhas) precisa ser escolhido na lista ou criado antes de *Avançar*.
- **Na primeira abertura** o app cria um jogador para cada nome das partidas já jogadas.
- **Mais › Jogadores**: todos, com pesquisa; **Novo jogador** cria um com os dados dele (também um aluno: depois ative *Aluno* na ficha); *homônimo* em laranja onde dois jogadores têm o mesmo nome. A ficha é a dos alunos (7.6) com mais:
  - **Aluno**: coloca (ou tira) entre os alunos.
  - **Juntar com outro jogador**: a mesma pessoa escrita de dois jeitos ("Rossi" e "Mario Rossi") ou criada duas vezes; partidas e sessões passam para ele.
  - **Separar um homônimo** (em *Partidas*): marque as partidas de outra pessoa com o mesmo nome, que vira um jogador novo.
- **Partidas de outro telefone** (transferência ou *Importar*): um jogador que o telefone conhece se liga sozinho; um novo entra com o identificador dele; se tem o nome de um dos seus o app pergunta uma vez **Quem é?** (*Sim, é ele* liga os dois e da próxima vez reconhece; *Não, outra pessoa* adiciona). Partidas de versões antigas, sem jogadores, se ligam pelo nome.
- A **foto** e **homem/mulher** ainda valem para o nome: dois homônimos as compartilham.

### 7.8 Você, e Perfil e dados (app 2.5)

- **Você** é o seu jogador do banco: Progresso e rating partem dele. Na primeira abertura bastam nome e sobrenome (e apelido); depois **Mais › Você** abre a sua ficha. Se ainda não escolheu, *Quem é você?* oferece os jogadores que existem (você já jogou partidas) ou **Sou novo**. Na ficha de outro jogador, **Sou eu** faz dele o seu.
- **Mais › Perfil e dados**:
  - **Criar o backup**: um arquivo ZIP com partidas, jogadores, alunos, sessões de golpes com a análise, raquetes, treinos, torneios, fotos, configurações e gravações da voz; com **Incluir os vídeos** também clipes de golpes, replay e highlights (o arquivo fica grande). Salve onde quiser (Download, um PC, um pen drive): sem nuvem. Não entram o modelo da análise do movimento (é baixado de novo) nem os arquivos que você salvou em Documentos.
  - **Restaurar**: coloca os dados de um backup neste telefone, mesmo novo; substitui tudo o que há (um backup sem vídeos deixa os vídeos que existem). Depois o app fecha: ao abrir de novo você encontra os dados do backup.
  - **Apagar todos os dados**: o app volta a ficar como recém-instalado (duas confirmações; com **Também os vídeos** também clipes, replay e highlights). Os arquivos salvos em Documentos e os vídeos da galeria ficam.

**Cópia automática todos os dias**: sem vídeos, só se algo mudou. As últimas 5 cópias vão para Documentos › Tennis Score Manager › Backup, que fica mesmo que desinstale o app; na lista *Cópias automáticas* cada uma **envia-se** (Drive, e-mail…) ou **restaura-se**. Por segurança guarde uma também fora do telefone: no Drive, no PC ou sincronizando essa pasta.

### 7.9 Papéis: Você, Treinador, Clube (app 2.6)

- Em **Mais › Perfil e dados › Papéis** (e na primeira abertura) você escolhe quem usa o app; pode ter mais de um. Os papéis mudam as abas da primeira tela, **os dados são os mesmos**; com vários papéis, no alto aparece o ativo: toque para passar a outro.
  - **Você**: Jogar, Treinar, Vídeo, Progresso, Mais (o app de sempre).
  - **O que usas** (Mais): fica-se só com o que é preciso. Pulseiras e comandos, cintas cardíacas, treino de pancadas e alunos, vídeo e câmara, progressos e rating, raquetes, padel: o que se desliga não aparece, e os separadores que ficam vazios (Treinar, Vídeo) desaparecem; o histórico fica sempre (sem Progressos passa para Jogar). No início está tudo ligado; na primeira apresentação a app propõe o que manter a partir da resposta a «como vais usá-la». Mais está dividido em grupos: Dispositivos e voz, Dados, Ajuda.
  - **Treinador**: **Alunos** (lista, quem treina e treino de golpes), **Jogar** (partidas entre alunos, torneio entre amigos), Vídeo, Mais.
  - **Clube**: **Hoje** (as reservas do dia, o que há por receber, os sócios por regularizar, os jogos de torneio, as aulas, os encontros e os desafios: toca-se num quadro para lá ir), **Torneios**, **Quadras** (nome do clube e quadras, uma por linha: em Nova partida, embaixo da quadra, você toca e clube e quadra ficam escritos; o placar de TV), **Jogadores**, Mais.
  - **Funções do clube** (no fundo dos separadores do Clube): o clube escolhe o que usa. Básicas: Sócios, Mensalidades e pagamentos, Agenda das quadras e Torneios do clube; adicionais: Equipes e campeonatos, Escola de tênis, Classificação por desafios, Estatísticas das quadras, Servidor do clube e Secretaria no PC. As funções desligadas não aparecem nos menus e os dados ficam; no início também estão ligadas as que já têm dados.
  - O **?** em cima em muitos ecrãs abre o guia logo na secção desse ecrã.
- O placar está em todos os papéis: nada impede você de marcar uma partida.
- **Primeira abertura**: idioma, papéis, o seu nome (nome, sobrenome, apelido), como você usa o app (com *Você*), o clube (com *Clube*), os seus dados com o **lembrete de backup** (a cada 30 dias a primeira tela lembra você; também está em Perfil e dados).

### 7.10 Exercícios guiados (app 2.6)

- Um **exercício** é um golpe e passos em sequência: por exemplo *20 forehands cruzados, depois 20 na paralela*, ou no saque *10 primeiros do lado de iguais no T, depois 10 abertos…*. Há quatro exemplos prontos, um por golpe.
- **Exercícios** (na aba *Alunos* do treinador, ou na escolha embaixo de *Começar*): **Novo exercício**, nome, golpe e passos; para cada passo quantos golpes (− e + de 5 em 5) e, conforme o golpe, lado, primeiro ou segundo e alvo, direção, voleio de forehand ou de backhand (*—* = deixar como está). Toque num exercício para mudar ou apagar.
- Em **Treinar**, embaixo dos golpes, **Exercício** escolhe o que seguir (ou *Livre*). Durante o treino o app ajusta sozinho as escolhas do passo, no alto mostra *Passo 1 de 2 · 7/20*, no fim de um passo a voz diz o próximo (e a pulseira mostra) e no fim *Exercício terminado*. Desfazendo um golpe volta-se ao passo certo. O resumo conta por direção e alvo, e a sessão lembra o nome do exercício.

### 7.11 Sócios do clube (app 2.6)

Com o papel **Clube**, em **Jogadores › Sócios** fica a lista dos inscritos, pensada para o aparelho da secretaria.

- **Novo sócio**: escolha um jogador que já existe (quem jogou partidas ou um aluno) ou crie um novo. Na ficha de um jogador também dá para tocar em **Inscrever como sócio**.
- **Dados do sócio**: telefone e e-mail (servem para os avisos), data de nascimento, sócio desde, cartão do clube; **cartão FITP** com número, tipo (competição, não competição, outro), ano e ranking; **atestado médico** com tipo e vencimento; consentimento ao tratamento de dados e a fotos e vídeos. Para os **menores**, também o responsável e o telefone dele.
- Do atestado se guardam **só o tipo e o vencimento**, não o documento: é um dado de saúde e fica no arquivo do clube.
- Embaixo de cada sócio o app diz o que falta: consentimento de privacidade, cartão FITP do ano, atestado que falta, vencido ou que vence em menos de um mês, dados do responsável, um contato. Em vermelho o que impede de jogar (cartão e atestado), em laranja o resto. **A resolver** mostra só quem tem algo pendente.
- Os dados de sócio ficam no telefone: quando as partidas passam para outro telefone só viajam nome, sobrenome, apelido e mão. **Tirar dos sócios** apaga cartões, atestado e consentimentos; o jogador e as partidas dele ficam.

### 7.12 Mensalidades e pagamentos (app 2.6)

Em **Jogadores › Mensalidades e pagamentos** (papel Clube) fica o registro do clube. O app **registra** os pagamentos, não os recebe: nenhum cartão ou conta vinculado.

- **Nova mensalidade**: nome (ex. *Anuidade 2027*), tipo (anuidade, cartão FITP, curso, torneio, outro), valor e, se precisar, o vencimento. Tocando numa mensalidade, **Cobrar de todos os sócios** a atribui a quem ainda não a tem; embaixo se vê quantos pagaram.
- Na ficha do sócio, **Mensalidades e pagamentos** mostra o que ele deve: *pago*, *a pagar*, *faltam 100,00 €*, em vermelho *atrasado* depois do vencimento. **Nova cobrança** adiciona uma mensalidade do clube ou outro valor (uma aula, um encordoamento).
- Tocando numa cobrança, **Registrar pagamento**: valor (geralmente o que falta: dá para pagar em parcelas), data, método (dinheiro, transferência, cartão, Satispay, outro) e referência. Com **Recibo agora** abre o recibo em PDF para enviar ou imprimir, numerado por ano (*1/2027*, em janeiro volta a 1). É um **documento não fiscal**: para recibos fiscais e contabilidade, siga o seu contador.
- Um pagamento errado se cancela (o número do recibo não é reutilizado); uma cobrança sem pagamentos se exclui, com pagamentos se **cancela**: não é mais devida e os pagamentos ficam no registro.
- No alto: recebido no ano e o que falta receber; **A receber** lista quem deve, da maior dívida para a menor. Também em *Sócios* quem deve algo está entre os *a resolver*.
- **Exportar os pagamentos (CSV)**: todos os pagamentos do ano com data, recibo, nome, descrição, método, referência e valor, para a planilha do contador.
- Ao excluir um jogador somem as cobranças não pagas; os pagamentos feitos ficam com o nome, porque são contabilidade. O registro está no backup de *Perfil e dados*.

### 7.13 Torneios do clube (app 2.6)

Com o papel **Clube**, em **Torneios › Torneios do clube** se gerenciam os torneios **sociais** e os **FITP**: inscrições, taxas, sorteio, chave, grupos e resultados. Nos torneios FITP o app ajuda o clube e o árbitro geral, mas não substitui os sistemas oficiais da Federação.

- **Novo torneio**: nome, social ou FITP, simples ou duplas, categoria, **eliminação direta** ou **grupos + chave** (quantos grupos e quantos passam de cada um), datas, **taxa de inscrição** por jogador (vira uma mensalidade em *Mensalidades e pagamentos*), *na chave só quem pagou*, **ranking máximo** (ex. *4.1*: entram os 4.1 e os abaixo; vazio = open). Nos FITP também o código do torneio e o árbitro geral.
- **Inscrever**: escolha no banco de dados (primeiro os sócios) ou crie um jogador; nas duplas escolha os dois. Com taxa, a inscrição é cobrada de cada um e recebida na ficha dele (também com recibo).
- Embaixo de cada inscrito o app diz se está **em ordem**. **FITP**: cartão de competição do ano, atestado de competição válido no dia do início, ranking dentro do limite. **Social**: atestado válido (quem não é sócio é indicado, mas pode jogar). Para todos: a inscrição paga, se exigida. Em vermelho quem não entra no sorteio.
- **Cabeças de chave**: pelo ranking FITP (o mais forte primeiro); tocando num inscrito dá para escolher à mão. Dois cabeças de chave até 8 posições, depois um a cada quatro.
- **Sorteio**: entram só os inscritos em ordem. Na chave o cabeça 1 vai no topo, o 2 no fim, o 3 e o 4 (depois 5-8) sorteados entre as suas posições; os **byes** vão para os primeiros cabeças, que passam sozinhos. Com grupos: distribuição em serpentina pelo ranking e todos contra todos; terminados os grupos, **Chave final** com os primeiros como cabeças e os segundos na metade oposta ao primeiro do seu grupo. Enquanto não houver resultado, **Refazer o sorteio**.
  - **Editar a chave** (aba *Chave*, também pelo PC): depois do sorteio, toque em um jogador ou *bye*, depois no outro lugar, e os dois trocam; nos grupos, trocam dois inscritos. Quem se inscreve depois do sorteio fica de fora até que, escolhido um lugar, você o coloque nele: quem sai continua inscrito, mas fora da chave. Só muda o que não tem resultado, e os grupos ficam fixos quando a chave final existe. Em torneios FITP, edite só se o árbitro geral permitir: o número de alterações fica registrado.
- **Chave**: os grupos com a classificação (vitórias, saldo de sets e games, depois o confronto direto; em destaque quem passa) e a chave, uma rodada por coluna (rola para o lado). **Partidas**: primeiro as a jogar por horário, depois as jogadas.
- Tocando numa partida: **Jogar com o marcador** abre Nova partida com jogadores, rankings, torneio, rodada e quadra já preenchidos; no fim, o resultado entra sozinho na chave. Ou o resultado à mão (o app entende o vencedor pelo placar), **W.O.** ou **desistência**; depois quadra e hora. Corrigindo um resultado, a rodada seguinte se atualiza.

- **No placar da TV**: com o placar da TV ligado (*Dispositivos*), **Mostrar no placar da TV** no torneio cobre o placar com o torneio, em páginas que se alternam a cada 15 segundos: **chave** (vencedores em amarelo), **grupos** com a classificação (em destaque quem passa) e **ordem de jogos** de hoje (ou as próximas partidas programadas), com quadra, hora e resultados. Cada resultado, também o do marcador, aparece na hora. **Tirar do placar da TV** volta ao placar.

### 7.14 Avisos aos participantes e aos sócios (app 2.6)

O app prepara as mensagens, já escritas e pessoais ("Olá Mario, …"), e abre o app certo: **quem envia é você**, tocando em Enviar. Sem serviços externos nem assinaturas: usam-se WhatsApp, SMS, e-mail e o compartilhamento do telefone.

- **Onde**: no torneio, **Avisar os participantes**: *Inscrição e taxa* (confirmação e taxa paga ou a pagar), *Próxima partida* (a cada um: rodada, adversário, dia, hora e quadra), *Ordem de jogos de um dia*, *Chave* (uma imagem com a chave e os grupos), *Mensagem livre*. Em **Mensalidades e pagamentos**, **Lembrar quem deve** (item por item, com os vencimentos). Em **Sócios**, **Avisar os sócios**: quem tem algo a resolver (atestado, cartão, consentimentos) ou uma mensagem a todos.
- **A todos**: o texto para todos (ordem de jogos, lista de inscritos, chave). **Compartilhar** envia num grupo do WhatsApp, no Telegram, Instagram, Facebook… com a imagem se houver; **E-mail a todos** coloca todos em cópia oculta; **SMS a todos** abre um SMS com todos os números.
- **Um por um**: a mensagem de cada um (toque para ler tudo) e os botões **WhatsApp** (abre a conversa com aquele número e o texto pronto), **SMS** e **E-mail**. A marca lembra quem você já avisou.
- Números sem prefixo valem como italianos (+39). Para os **menores** escreve-se ao telefone do responsável (*Dados do sócio*), com "um aviso sobre Luca". Quem não tem telefone nem e-mail é indicado: adicione na ficha dele.

### 7.15 Quadras e agenda (app 2.6)

Com o papel **Clube**, na aba **Quadras**.

- **As quadras**: crie uma quadra (**Nova quadra**), ordene e toque numa para mudá-la: nome (ao renomear, reservas, períodos e partidas dos torneios acompanham), piso (**saibro, grama, rápida**, outro), **coberta ou descoberta**, **iluminação**, **aquecimento**, os **preços** dela (por hora, com iluminação, adicional de aquecimento por hora; vazios = os gerais) e **uma ou mais fotos** da galeria. **Excluir a quadra** remove também as reservas dela a partir de hoje.
- **Agenda das quadras**: o dia com uma quadra por coluna e uma hora por linha (◀ ▶ e o calendário para mudar de dia). Toque num horário livre para reservar: quadra, dia, hora e duração, tipo (jogo, aula, fechada, outro), jogadores do banco de dados e convidados, nota. O **preço** se calcula sozinho (iluminação a partir da hora definida, aquecimento se pedido) e pode ser alterado; **Cobrar dos jogadores** divide em partes iguais e leva para *Mensalidades e pagamentos*. **Como se paga**: *na quadra* ou *online*; com a reserva cobrada, **Pago: …** marca como paga com um dos meios de pagamento do clube. **Toda semana por N semanas** repete a reserva (as datas com a quadra ocupada são puladas e o app avisa). **Avisar os jogadores** envia quadra, dia, hora e como pagar.
- As **partidas dos torneios** com quadra e horário aparecem sozinhas; o app não deixa reservar por cima de outra reserva ou de uma partida.
- **Não reserváveis**: períodos em que as quadras não se reservam, para **cursos**, **torneios**, **manutenção** ou outro: quadras (ou todas), de um dia a outro, dias da semana, faixa de horário ou o dia todo; para um torneio, nome e datas vêm do torneio. Nos períodos dos cursos entram as aulas deles (tocando o horário abre uma aula já pronta).
- **Estatísticas das quadras** (na agenda): para as últimas 4 semanas, o mês em curso ou o passado, a **ocupação** de cada quadra (horas reservadas sobre as reserváveis: os períodos não reserváveis não contam), as **receitas** (preço das reservas de jogo e aula, pagas ou não), quantas reservas e quantas online, as horas de torneio, um mapa **dia × hora** que mostra quando se joga mais e quem reserva mais.
- **Horários e preços**: abertura e fechamento, duração de um horário (30, 60, 90 minutos), preço por hora geral e com iluminação, a partir de que hora precisa de iluminação, quanto dura uma partida de torneio na agenda.
- **Meios de pagamento** (em *Mensalidades e pagamentos*): quem administra o clube decide. Começa com dinheiro, cartão, transferência e Satispay e dá para adicionar outros (PayPal, Revolut, Bancomat Pay, um link SumUp…): nome, link/IBAN/número e se é pago a distância. Escolhe-se um ao registrar um pagamento (o nome vai nos recibos e no CSV); os a distância, com os dados, vão nos avisos e lembretes. O app não recebe: registra e envia as instruções.

### 7.16 Escola de tênis (app 2.6)

Com o papel **Clube**, em **Jogadores › Escola de tênis**.

- **Novo curso**: nome (ex. *Escolinha Sub-10 · Turma A*), nível, professor (do banco de dados ou escrito), temporada (de… até…), **horários** semanais (dia, hora, duração, quadra; mais de um se precisar), vagas máximas, **mensalidade** por mês ou pela temporada, notas. Nos horários com quadra, **essa quadra fica não reservável** na agenda a temporada toda (as aulas do curso sim); mudando ou excluindo o curso, a agenda se atualiza.
- **Alunos**: adicionados do banco de dados (sócios ou não) até as vagas; de cada um a **presença** (presente em 3 de 4, em laranja abaixo de 75 %) e o que deve. A ficha do jogador mostra os cursos dele.
- **Aulas**: o calendário vem dos horários. Toque numa aula para a **chamada** (quem estava) ou para marcá-la **cancelada** (chuva, feriado…); **Cancelar e avisar os alunos** prepara a mensagem com dia e hora. *Avisar os alunos* envia uma mensagem livre. Aos menores se escreve ao responsável.
- **Mensalidades**: para cada mês da temporada (ou a temporada) **Cobrar dos alunos** atribui a mensalidade a quem ainda não tem; vê-se quantos pagaram e recebe-se como as outras mensalidades, com recibo.

### 7.17 Equipes e campeonatos (app 2.6)

Com o papel **Clube**, em **Torneios › Equipes e campeonatos**.

- **Nova equipe**: nome, campeonato, temporada, **campeonato FITP** (verificam-se cartão e atestado de competição do dia do confronto), formato (quantas simples e duplas por confronto), capitão. No **Elenco** adicionam-se os jogadores na ordem da lista; em vermelho quem não está em ordem para o próximo confronto. O retrospecto (vitórias, derrotas, empates) fica ao lado do nome.
- **Novo confronto**: adversário, dia e hora, em casa ou fora, onde se joga, **ponto de encontro**, notas. Em casa dá para **reservar quadras** por algumas horas: na agenda ficam não reserváveis.
- **Disponibilidade**: *Pedir disponibilidade a todos* envia a mensagem (respondem SIM, NÃO ou TALVEZ); as respostas se marcam com um toque, e *Pedir a quem não respondeu* insiste só com eles.
- **Convocados**: primeiro quem disse sim, depois os talvez; em vermelho quem não está em ordem naquele dia. **Enviar a convocação** escreve a cada um dia, hora, local e ponto de encontro, e prepara para o grupo a lista dos convocados.
- **Partidas**: para cada simples e dupla os nossos (dos convocados) e os deles, depois o placar (do nosso lado: o app entende quem venceu) ou **Jogar com o marcador**, e no fim o resultado volta sozinho. No alto o resultado do confronto (ex. *4-2 vencido*); **Enviar o resultado** prepara partida por partida.

### 7.18 Servidor do clube para as reservas (opcional, app 2.6)

**Opcional**: sem servidor nada muda, reserva-se na secretaria como sempre. Com um pequeno servidor no clube (um Raspberry Pi Zero 2 W ou um PC antigo) os jogadores convidados **reservam as quadras sozinhos** no telemóvel, **também iPhone**, numa página web do clube que se adiciona ao ecrã principal como uma app.

- **O servidor**: arranca com `java -jar tsm-server.jar` (Java 17 ou superior). Ao arrancar mostra o endereço e um **código de emparelhamento** (também no ficheiro *setup-code.txt* da pasta de dados).
- **Emparelhar a secretaria**: *Agenda das quadras › Horários e preços › Servidor do clube*: endereço do servidor (ex. `http://192.168.1.50:8080`) e código, depois **Emparelhar**. A partir daí a agenda (quadras, horários, preços, períodos não reserváveis, reservas da secretaria e jogos dos torneios, sem nomes) vai sozinho para o servidor depois de cada alteração; com a agenda aberta, as reservas online chegam a cada minuto.
- **As regras para os jogadores**, no mesmo diálogo: reservas online abertas ou fechadas, quantos dias antes, reservas por jogador, duração máxima, até quantas horas antes se cancela sozinho, até quantos minutos antes ainda se reserva, que quadras se reservam online e como se paga (este texto é lido pelos jogadores).
- **Convidar um jogador**: na sua ficha, **Convite para reservar online**: um QR e um código que valem uma vez durante 14 dias (ou **Partilhar a ligação**). O jogador lê o QR. No iPhone a página **TSM** guia-o: *Partilhar › Adicionar ao ecrã principal*, depois abre o TSM a partir do ecrã principal e cola o código (o Safari e a app do ecrã principal não partilham dados, por isso o convite usa-se na app instalada). Na ficha vê-se quem reserva online; **Retirar o acesso online** desliga-o.
- **Com a app TSM no Android** o jogador também reserva em *Jogar › Reservar quadra*: lê o QR do convite (ou cola a ligação), depois vê as suas reservas, escolhe o dia e toca numa casa livre; duração, aquecimento e preço antes de confirmar, **Cancelar** enquanto há tempo, **Sair deste clube** para desligar o telemóvel. As regras são as mesmas da página web.
- A página e a app têm **três separadores**: **Reservar** (dias e casas), **Minhas** (reservas, listas de espera, lembretes) e **Parceiros** (Procuro parceiro).
- No app, em **Minhas**, **Convidar** envia a quem joga consigo a quadra, o dia e a hora já escritos (pelo WhatsApp ou onde quiser); depois de uma reserva o app leva-o lá sozinho.
- **As reservas online** entram na agenda com o nome do jogador e a palavra *online*, ao preço da quadra; cobram-se e recebem-se como as outras. Se a secretaria tirar ou mudar uma, fica livre no servidor; se o jogador a cancelar, desaparece da agenda. Os outros jogadores só veem *Ocupado*, nunca nomes.
- **Num Raspberry Pi Zero 2 W** (ou um PC com Debian/Ubuntu): com o *Raspberry Pi Imager* escreve-se no microSD o **Raspberry Pi OS Lite (64-bit)**, com o nome `tsm-circolo`, o Wi-Fi do clube e SSH ligado. Depois copia-se o kit (`tsm-server-kit.tar.gz`) para o Pi, entra-se com `ssh` e corre-se `tar xzf tsm-server-kit.tar.gz && cd tsm-server && sudo ./install.sh`: instala o Java, arranca o servidor a cada ligação e mostra o **endereço** (também `http://tsm-circolo.local:8080`) e o **código de emparelhamento**. Corrido de novo com um kit novo, atualiza o servidor sem mexer nos dados; `sudo ./reset-admin.sh` dá um código novo à secretaria.
- **Num PC com uma pen USB**: a imagem `tsm-server.iso` (Debian live) escreve-se com `sudo ./make-usb.sh /dev/sdX` (no Windows com o **Rufus**, com uma *partição persistente*): a pen fica com uma partição **persistence** onde os dados ficam guardados. Arranca-se o PC pela pen (de preferência com cabo de rede): no ecrã aparecem endereço e código. Sem a partição persistence, o servidor esquece tudo ao desligar (e avisa no ecrã).
- **Cópia dos dados**: está tudo num só ficheiro, `/var/lib/tsm-server/state.json`; convém copiá-lo de vez em quando e antes de reescrever a pen.
- **Lembretes aos jogadores**: o clube escolhe quantas horas antes do jogo chegam (*Lembrete aos jogadores*, 0 = não; normalmente 2). Na página TSM o jogador toca em **Ativar os lembretes** (no iPhone a partir da app TSM do ecrã principal, com iOS 16.4 ou mais recente e o endereço https do clube) e recebe logo uma notificação de teste; a app TSM no Android tem **Lembretes no telemóvel**. Também é avisado se a secretaria retirar ou mudar uma das suas reservas (não se for ele a cancelar).
- **Lista de espera**: ao tocar numa casa **Ocupada** (na página TSM ou em *Reservar quadra* na app) o jogador escolhe **Avisa-me se ficar livre**, para essa quadra ou qualquer quadra à mesma hora (no máximo 3 esperas). Se a reserva for cancelada ou mudada, recebe logo o aviso e na página aparece **Reservar**: fica com ela quem reservar primeiro; se outro for mais rápido, volta a esperar. As esperas desaparecem sozinhas quando a hora passa.
- **Procuro parceiro**: na página TSM e em *Reservar quadra* o jogador publica um anúncio (dia, faixa horária, singulares ou pares, nível, nota; no máximo 3 abertos). Os outros veem-no e tocam em **Conta comigo**; quem o publicou recebe um aviso. Completos (2 jogadores em singulares, 4 em pares) o servidor **reserva sozinho** a primeira quadra livre na faixa, com as regras das reservas online, e avisa todos; se não houver quadra livre diz-o, e reservam eles. Os anúncios passados desaparecem sozinhos.
- **Avisos para a secretaria**: com *Avisar-me das reservas online* (ligado por omissão) o telemóvel da secretaria recebe uma notificação quando um jogador reserva ou cancela, mesmo com a app fechada: mais ou menos a cada 15 minutos, quando há rede. Ao tocá-la abre-se a agenda no dia da reserva. As que a própria secretaria tira não dão notificação.
- **De casa, sem mexer no roteador** (recomendado). Em *Servidor do clube*, com o telefone emparelhado, toque em **Ativar** em *Acesso pela internet sem mexer no roteador*. O servidor instala o Tailscale, e o app pede para abrir o Tailscale para entrar (ou criar uma conta gratuita) e, na primeira vez, para confirmar o Funnel. Quando estiver pronto, **Endereço pela internet** se preenche sozinho (`https://tsm-circolo….ts.net`): HTTPS incluído, sem DuckDNS, e funciona também com CGNAT.
  - A conexão sai do servidor para fora, então o roteador não é tocado e os jogadores não instalam nada. O tráfego passa pelos sistemas do Tailscale, um serviço externo; o Funnel ainda está em beta e tem limites de tráfego, que não devem pesar nas reservas de um clube. O primeiro acesso ao endereço pode levar alguns minutos.
  - O acesso ao Tailscale normalmente expira após 180 dias: para evitar isso, em login.tailscale.com, em *Machines*, escolha *Disable key expiry* para o servidor. Se expirar, o app mostra de novo **Abrir o Tailscale**.
  - **Emparelhar** a secretaria continua possível só pela rede do clube. **Desativar** fecha a conexão. Em servidores já instalados, execute antes `install.sh` de novo com o kit novo.
- **De casa** (internet, em HTTPS: também é preciso para pôr a página no ecrã principal do iPhone). Tudo grátis:
  1. Em **duckdns.org** (entra-se com uma conta GitHub, Google…) cria-se um nome, ex. `tcprova.duckdns.org`, e copia-se o **token**.
  2. No **router do clube** dá-se ao Pi um endereço fixo e reencaminham-se para ele as portas **TCP 80 e 443**.
  3. No Pi, a partir da pasta do kit: `sudo ./setup-https.sh tcprova.duckdns.org --duckdns O-TEU-TOKEN` (a partir da pen: `sudo /opt/tsm-server/kit/setup-https.sh …`). Instala o **Caddy**, que obtém sozinho o certificado **Let's Encrypt** e o renova; a cada 5 minutos diz ao DuckDNS o endereço do clube, caso o fornecedor o mude. Com um domínio próprio escreve-se esse, sem `--duckdns`.
  4. Na app, *Servidor do clube › Endereço pela internet*: `https://tcprova.duckdns.org`. A partir daí os convites usam este endereço, e a secretaria fora do clube chega ao servidor por ele.
  - O **emparelhamento** da secretaria só se faz a partir da rede do clube (com o endereço `http://…:8080`), nunca pela internet.
  - Se um telemóvel em 4G/5G não chega, a linha do clube está provavelmente atrás de **CGNAT** (acontece com muitas linhas sem fios e móveis): o endereço de internet que o router mostra é diferente do que um site *qual é o meu IP* indica. Então pede-se ao fornecedor um **IP público** (muitas vezes grátis), ou usa-se um túnel (Tailscale Funnel, Cloudflare Tunnel: serviços de terceiros) ou um pequeno servidor alugado.
  - `sudo ./setup-https.sh --remove` tira o acesso pela internet.

### 7.19 Classificação por desafios (app 2.6)

Com o papel **Clube**, em **Torneios › Classificação por desafios**.

- **Nova classificação** (mais de uma se quiseres: masculina, feminina, jovens…), depois **Adicionar jogadores**: o primeiro é o número 1; **Ordenar a classificação** muda-os à mão ou tira-os.
- **Novo desafio**: escolhe-se quem desafia e depois quem é desafiado, no máximo *N* lugares acima (**Regras**: quantos lugares, normalmente 3, e em quantos dias tem de ser jogado, normalmente 14). Cada um tem um só desafio aberto. **Avisar** manda aos dois quem desafia quem e até quando.
- **Jogar com o marcador**: o desafiante é o Jogador 1; no fim o resultado volta sozinho. Ou **Resultado** à mão (do lado do desafiante: a app percebe quem ganhou).
- Se **ganha o desafiante fica com o lugar** do desafiado e os que estavam no meio descem um; se ganha o desafiado nada muda. **Desafios jogados** guarda o histórico, com os lugares antes e depois.

### 7.20 Secretaria no PC (app 2.6)

Inserir sócios e pagamentos é mais cómodo com teclado e mouse. **Um computador (Windows, Mac ou Linux) na mesma rede do telefone** abre no navegador uma página servida pelo telefone, sem instalar nada e sem servidor: **os dados ficam no telefone**; o computador vê e altera os dados enquanto está conectado e não guarda cópias. Uma pessoa de cada vez. O *servidor do clube* (7.18) é outra coisa e serve só para as reservas dos jogadores.

- **Ativar**: *Funções do clube* › **Secretaria no PC**, depois a entrada na aba das quadras e **Ativar**. O telefone mostra o endereço (ex. `https://192.168.1.20:8443`) e um código.
- **No computador**: abra o endereço no navegador. O telefone o mostra, e **Compartilhar o endereço** o envia ao computador (WhatsApp Web, e-mail) sem precisar copiar: o link sozinho não dá acesso. Da primeira vez avisa que a conexão não é privada: é normal, porque o certificado foi criado pelo telefone e não por uma autoridade. Escolha *Avançado › Continuar*. Se o aviso voltar sem ter mudado de telefone nem reinstalado o app, não prossiga: compare a impressão digital SHA-256 do navegador com a do telefone.
- **Entrar**: a página mostra um QR. No telefone toque em **Escaneie o QR do computador**, escaneie e toque em **Permitir**, verificando o endereço do computador: o computador entra sozinho. Sem câmera, abra *Entre com o código* no computador e digite o código do telefone.
- **O que se pode fazer**: **Hoje** (a primeira página: quem joga, o que cobrar, sócios por tratar, jogos e aulas do dia), **Sócios** (procurar, adicionar, alterar, *Por tratar*, *Atestado a renovar*, **Imprimir** e **Exportar CSV** da lista que vê), **Importar de CSV** do Excel ou Sheets (primeiro uma pré-visualização; os sócios existentes são completados, sem apagar nada), **Mensalidades e pagamentos** (mensalidades, débitos, pagamentos, **Recibo** em PDF, exportação CSV, impressão), **Agenda** (vista **Dia**, **Semana** ou **Mês**; reservar, alterar, repetir todas as semanas e apagar também toda a série; **Não reserváveis** para fechos e manutenção; impressão), **Torneios** (criar e alterar, inscrever procurando entre os jogadores com os problemas de atestado, cartão e taxa à vista, cabeças de chave, sorteio, chave e grupos com resultados, dia, hora e quadra de cada jogo com aviso da agenda se a quadra estiver ocupada, e **Imprimir** inscritos, chave e programa). **Escola de tênis** (cursos com horários e quadras, alunos, chamada das aulas, aulas canceladas, mensalidades do mês ou da temporada a debitar), **Estatísticas das quadras** (ocupação, receitas, quadra a quadra, dias e horas mais cheios, quem reserva mais, por período, para imprimir). Com **Preparar lembrete** em Mensalidades e pagamentos escolhe a quem escrever entre os débitos que vê: a mensagem abre-se no telefone e envia-a de lá. **Equipes e campeonatos** (plantel, encontros em casa ou fora com quadras reservadas, disponibilidade, convocados, jogos e resultado; os avisos abrem-se no telefone). **Reservas online**, com o servidor do clube (próximas reservas dos jogadores, lista de espera, anúncios de Procuro parceiro a tirar se preciso, regras para os jogadores, **Enviar agora**), **Definições** (nome do clube, horários e preços, quadras com piso, luzes e preços próprios, formas de pagamento). Avisos de WhatsApp, fotos das quadras e marcador ao vivo continuam no app.
- **Cópia da secretaria** (no PC, abaixo de Estatísticas; no telefone em Quadras › Cópia da secretaria): um só arquivo `.tsmclub` com sócios e jogadores, mensalidades e pagamentos, agenda, torneios, escola de tênis, equipes, quadras e definições (não jogos, golpes e vídeos: a cópia completa tem esses). **Baixar para o PC** ou, do telefone, **enviá-la** para onde quiser (Google Drive, e-mail, um pen drive). Com a **cópia automática** o telefone faz uma cópia por dia só se algo mudou e guarda as últimas 14; com um servidor do clube, ele também. **Restaurar** mostra primeiro o que há no arquivo e faz uma cópia de agora, para voltar atrás. O arquivo contém dados pessoais dos sócios: guarde-o num lugar seu.
- **Segurança**: tudo viaja cifrado (HTTPS, TLS 1.2 ou 1.3). São necessários o QR ou o código, de uso único e temporários, e a autorização no telefone; cinco erros mudam o código e bloqueiam as tentativas. Um só computador de cada vez, vinculado ao seu endereço. Responde só à rede local, nunca aos dados móveis. A sessão termina com **Sair**, ao fechar o aba, após 30 minutos parada ou após 12 horas. Sem ninguém conectado desativa-se sozinha após 30 minutos; a notificação lembra que está ligada e tem o botão **Desativar**. A chave do certificado nunca entra nas backups. Em redes públicas ou compartilhadas, use o hotspot do telefone.
- **Conselhos**: mantenha o telefone ligado e carregando, perto do router. Se a página disser que o telefone não responde, verifique se está na mesma rede: algumas redes de convidados isolam os dispositivos; use então o hotspot do telefone.

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

1. Mais › **Dispositivos** › **Placar na TV** › ative **Placar na TV ou monitor**. No Android 13 ou mais recente o app pede a permissão de notificações: ela é necessária para a notificação do serviço que mantém o placar ativo.
2. Aparecem o **endereço** (ex. `192.168.43.1:8080`), o **QR** e quantos placares estão conectados. Se aparecer *Sem rede*, ative o hotspot ou conecte-se ao do outro telefone.
3. **Prévia neste telefone** abre o placar no navegador do próprio telefone.
4. **Aparência do placar**: cor de cada jogador (8 cores), tempo de partida, relógio de serviço e pausas, sets terminados, mensagens (break point, set point, troca de lado…), bola ao lado de quem serve, segmentos apagados visíveis, texto embaixo (vazio = clube e quadra de *Nova partida*). As mudanças chegam na hora ao monitor.
5. Na partida, no alto ao centro, fica **TV · 1** (placares conectados): tocando nele, você vê de novo o endereço e o QR.

Com o placar ativado, um serviço em primeiro plano (notificação *Partida em andamento · placar na TV ativo*) mantém o servidor vivo mesmo com a tela apagada, também no modo árbitro. O placar é **somente leitura**: não é possível mudar nada por ali. Ele continua ativo também no resumo e nas outras páginas enquanto o placar estiver ativado (notificação *Placar na TV ativo*), assim o resultado final fica no monitor mesmo com o telefone bloqueado.

O que ele mostra, além da pontuação: *AGUARDANDO A PARTIDA* antes do início, *PRONTOS PARA JOGAR* na página INÍCIO DA PARTIDA, **TIE-BREAK** / **TIE-BREAK DECISIVO** no lugar de *VS*, *PARTIDA SUSPENSA* piscando, **VENCEDOR [nome]** no fim da partida com todos os sets; as vantagens aparecem como **AD** também nos dígitos de LED. Uma partida vencida no tie-break decisivo termina com 1-0 nos jogos e 2-1 nos sets, com [10-8] entre os sets concluídos; mensagens longas diminuem para caber na tela.

### 8.3 No telefone-placar

1. **Jogar** › **Usar como placar**.
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
| Controles Bluetooth (gestos, associação) | `remote/PressDetector.kt` (+ testes), `remote/RemoteInput.kt` |
| Estatísticas e histórico ponto a ponto do resumo | `data/MatchStats.kt` (+ testes), `ui/screens/MatchStatsPanels.kt` |
| Histórico, jogadores e importação | `data/PlayerStats.kt` (+ testes), `ui/screens/HistoryScreen.kt`, `MatchController.kt` (`importMatches`) |
| Transferência entre telefones (QR) | `transfer/Transfer.kt` (server e `TransferBundle`), `MatchController.kt` (`startSending`, `receiveFrom`), `ui/screens/HistoryScreen.kt` (`SendDialog`) |
| Fotos dos jogadores | `ui/PlayerPhoto.kt`, `data/Storage.kt` (`savePhoto`), `MatchController.kt` (`setPhoto`), `/photo/` em `tv/TvServer.kt` e `scoreboard.html` |
| Torneios Americano e Mexicano | `data/Tournament.kt` (+ testes), `ui/screens/TournamentScreen.kt` |
| Reconhecimento de golpes | `TSM_Band.ino` (seção "colpi"), `swing_detector.*` e `stroke_classifier.*` (copiados do SwingSense); app: `ble/BandProtocol.kt`, `ui/BandSettingsPanel.kt`, `data/Reports.kt` |
| Cintas cardíacas (Bluetooth, frequência, calorias, zonas, quedas) | `ble/HeartRateManager.kt`, `ble/HeartRateProtocol.kt`, `ble/StrapGaps.kt`, `data/HeartStats.kt` (+ testes), `ui/HeartSection.kt` |
| Segundo telefone (protocolo, rede, ponte para cintas e pulseiras) | `relay/RelayProtocol.kt`, `relay/RelayLink.kt` (+ testes), `relay/Hotspot.kt`, `relay/PhoneRelay.kt`, `ui/RelaySection.kt`; em `ble/BleManager.kt` `setRemote`, `relayOut`, `inject*` |
| Treino (gravação, voltas, VFC, recuperação, CSV e PDF) | `workout/WorkoutSession.kt`, `data/Workout.kt` (+ testes), `data/WorkoutReport.kt`, `ui/screens/WorkoutScreen.kt` |
| Progressos (comparação primeira/segunda metade, PDF, impressão) | `data/Progress.kt` (+ testes), `data/ProgressPdf.kt`, `ui/screens/ProgressScreen.kt` |


## 11. Problemas comuns

- **Pulseira não encontrada**: Bluetooth e localização ativados (linhas verdes)? A pulseira está piscando PAREANDO? (Se já estiver conectada a outro telefone, ela não aparece.) Se nesse meio-tempo ela se desligou sozinha (30 s), ligue-a de novo com um clique no botão lateral.
- **A pulseira mostra NAO ENVIADO**: o telefone não confirmou o toque em 8 segundos (conexão perdida ou app fechado). Pressione de novo quando a pulseira tiver reconectado. Caso raro: se o toque tinha chegado e só a confirmação se perdeu, o ponto conta duas vezes; a voz anuncia o placar, corrija com *Anular ponto*.
- **No painel de ajustes aparece "O firmware desta pulseira não tem ajustes"**: essa pulseira ainda tem o sketch antigo; carregue-o de novo (capítulo 5).
- **O Android Studio não reconhece o telefone**: Depuração USB ativada e impressão digital RSA aceita no telefone (3)? No Windows às vezes é preciso o driver do fabricante; no Linux, as regras udev e uma nova sessão (1.1). Ou use a depuração por Wi-Fi (3).
- **O Arduino IDE não mostra a porta da pulseira**: cabo USB-C de dados, e não só de carga; no Linux, grupo `dialout` e nova sessão (1.1); depois, o modo download (5, passo 7).
- **A voz não fala ou lê errado**: volume de mídia, *Audio On*, voz do idioma escolhido instalada (4); tente outro mecanismo ou outra voz em *Áudio e voz*.
- **Tela apagada durante a partida**: no modo pulseiras, um serviço em primeiro plano (notificação "Partida em andamento") mantém ativos Bluetooth, voz e cronômetros; nos modos árbitro e controles a tela fica acesa (com os controles ela precisa ficar: 6.3).
- **O controle não faz nada**: ele está associado ao jogador (*Mais › Dispositivos* › *Controles*, linha com o nome dele)? O app precisa estar aberto na partida, com a tela acesa (6.3). Se a linha diz *Desconectado*, aperte uma tecla para despertá-lo ou pareie de novo nas configurações de Bluetooth.
- **Os golpes são confundidos ou saem como não reconhecidos**: use a pulseira como durante a calibração (mesmo pulso, mesmo sentido); senão refaça a calibração usando-a como na partida (6.4).
- **A pulseira mostra MEMORIA CHEIA**: um exemplo de calibração não pôde ser salvo. Apague os exemplos de um golpe (lixeira) e refaça.
- **Entender por que dois golpes se confundem**: com a pulseira ligada ao computador, abra o monitor serial (115200 baud) e digite `d`: aparecem os exemplos salvos e os últimos 40 golpes, cada um com suas 11 medidas. Os últimos golpes ficam guardados até a pulseira desligar ou reiniciar.
- **Falha no sync do Gradle por causa do JDK**: defina Gradle JDK = jbr-21 (passo 2.6).
- **O telefone-placar não encontra o árbitro**: mesma rede? (um dos dois faz o hotspot, o outro está conectado a ele). *Placar na TV* ativado no telefone do árbitro? Tente o endereço à mão. Alguns hotspots isolam os dispositivos conectados entre si ("isolamento de clientes"): se houver essa opção, desative-a.
- **O navegador do telefone não abre o endereço** com os dados móveis ativados: o Android manda o tráfego pelos dados móveis porque o hotspot não tem internet. Use o app em *Usar como placar* (ele resolve isso sozinho) ou desligue os dados móveis nesse telefone.
- **Monitor preto com o cabo**: esse telefone não tem saída de vídeo pela USB-C (8.1), ou o Samsung DeX foi iniciado: escolha *Espelhamento de tela*.
