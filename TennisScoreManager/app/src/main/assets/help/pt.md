## 3.1 Como o app está organizado

Na **primeira vez** o app faz três perguntas: o idioma, como você vai usá-lo (marcar o placar, jogar com as pulseiras, treinar, filmar partidas) e o seu nome; dá para pular e refazer em **Mais › Apresentação do app**. Embaixo há sempre **cinco abas**:

- **Jogar**: **Nova partida**, a partida suspensa para retomar, a **revanche** da última, o **torneio entre amigos** (Americano, Mexicano) e **Usar este telefone como** placar ou câmera. No alto o estado dos dispositivos (pulseiras, cintas, câmera, TV): um toque para conectá-los.
- **Treinar**: treino com a cinta cardíaca e **treino de golpes** (saque, forehand, backhand, voleio) com as sessões salvas.
- **Vídeo**: **Gravar com este telefone**, **Seus vídeos** (capítulos, melhores momentos, filtros, importar) e o telefone-câmera.
- **Progresso**: **Rating TSM · Progressos** (com o seu nome mostra na hora o seu rating), **Histórico**, **As minhas raquetes**.
- **Mais**: **Você** (o seu nome), **Dispositivos** (pulseiras, controles, cintas, segundo telefone, placar na TV), **Idioma e voz**, **Ajuda** (este guia, com pesquisa e referências para tocar), raquetes, transferir partidas, apresentação, versão e licença.

**Nova partida** são dois passos: **quem joga** (simples ou duplas, os nomes, com os jogadores recentes para tocar, e o esporte; clube, quadra e torneio em *Mais detalhes*) e **como você marca o placar e as regras**. Tudo é opcional: dá para seguir mesmo sem nomes. De cada tela **Voltar** retorna à aba de onde se veio.

## 4. A voz (funciona sem internet)

- Cada anúncio é lido **numa única frase** pela síntese de voz do telefone, com uma voz instalada: sem internet e com prosódia natural. Os nomes dos jogadores entram na frase.
- Mais › **Idioma e voz** › **Áudio e voz**:
  - **Mecanismo de síntese de voz**: *Padrão do telefone* (nos Samsung é o Samsung TTS) ou um mecanismo à sua escolha, por exemplo *Serviços de voz do Google*. Nos testes, feitos em italiano, o Google soou mais natural.
  - **Voz**: *Automática (melhor offline)* ou uma voz específica (o Google lista as vozes por código, como *Voz* seguido de três letras; as marcadas como *online* precisam de internet).
  - **Testar voz**: lê uma sequência de anúncios de exemplo com os nomes digitados. Enquanto lê, o botão vira **Parar o teste**: toque de novo para interrompê-lo (ele também para sozinho quando você sai da página). A síntese de voz não pode ser pausada no meio de uma frase, por isso o botão a interrompe; tocando de novo, ela recomeça do início.
- Se faltar a voz do idioma escolhido: **Configurações › Gerenciamento geral › Idioma › Conversão de texto em voz** (ou *Saída de conversão de texto em voz*) → baixe a voz desse idioma para o mecanismo escolhido (o app também mostra o botão **Instalar voz**). Se a síntese de voz nem chega a iniciar, o app avisa com uma mensagem própria e o botão **Abrir configurações** (configurações de síntese de voz do Android).
- **Pronúncia**: alguns mecanismos erram palavras do tênis (em italiano, "primo set" lido como "primo settembre", "tie-break" lido como "time break"). O app as corrige sozinho; as correções foram verificadas transcrevendo o áudio real do Samsung e do Google.
- **Gravações personalizadas** (a voz mais natural de todas: a sua ou a de um árbitro): na pasta `Android/data/io.github.steve_linux.tsm/files/voice/` há o `LEGGIMI.txt` ("leia-me"; as explicações do início ficam no idioma do app e são reescritas quando você o muda) com a lista das 85 chaves e das frases. Grave os arquivos com esses nomes (`score_1_0.mp3` = "quinze-zero"…), coloque-os num ZIP com uma pasta por idioma (`it/`, `en/`, `fr/`, `de/`, `es/`, `pt/`) e use **Importar ZIP**. O `LEGGIMI.txt` tem uma coluna por idioma, separadas por tabulações, então também abre bem como planilha. As gravações sempre têm prioridade sobre a síntese; os nomes continuam sendo lidos pela síntese. No ZIP vale a pasta que contém o arquivo (também dentro de outras pastas, ex. `voice/pt/`); arquivos fora de uma pasta de idioma vão para o idioma atual e as pastas `tts/` são ignoradas, então dá para compactar também a pasta `voice/` do app. Um ZIP danificado ou incompleto não muda nada (*ZIP ilegível ou incompleto*). **Remover gravações** pede confirmação. Se um arquivo não puder ser reproduzido, a frase é dita pela síntese de voz e o arquivo não é mais usado até o app reiniciar.
- **Usar arquivos de áudio pré-gerados** (opcional): com **Gerar arquivos** o app cria uma vez os 85 arquivos com a voz escolhida (até mesmo uma voz *online*, se naquele momento houver internet) e depois os usa no lugar da síntese contínua. Soa mais "picotado", mas é útil para levar offline uma voz online. Os arquivos anteriores ficam até os novos estarem prontos (se a geração não chegar ao fim: *Geração não concluída*); durante a geração, idioma, mecanismo, voz, teste de voz e áudio ficam bloqueados.
- **Caixa de som externa** (coluna): basta pareá-la com o telefone por Bluetooth; a voz sai pelo canal de mídia (ajuste o volume de mídia).
- **Fones de ouvido Bluetooth**: são pareados do mesmo jeito e só você ouve a voz, sem incomodar as quadras vizinhas; servem também para quem marca o placar da beira da quadra. As pulseiras podem continuar conectadas ao mesmo tempo.
- **Dois dispositivos de áudio ao mesmo tempo**: muitos telefones mandam o áudio para dois dispositivos Bluetooth juntos. No Samsung: conecte os dois, abra o painel rápido › **Saída de mídia** e marque os dois. Assim cada jogador pode ter o seu fone, ou a caixa de som toca para a quadra e o fone para quem marca o placar.

### 4.1 Idiomas

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
- **Pronúncia**: todas as frases de todos os idiomas foram lidas pelas vozes do Google e transcritas com o whisper. Correções acrescentadas: em alemão "Tie-Break" é lido como "Taibreak", com uma vírgula antes (senão sai "Teilbreg" ou "bei Detailbreak"); em francês o "à" isolado dos arquivos pré-gerados é lido como "a" (senão sai "a accent grave"). **O Samsung TTS nos novos idiomas ainda não foi testado.**
- **Pulseiras** (firmware 2.2): o app envia sozinho o idioma a cada conexão e quando você o muda; a pulseira o salva e o usa também quando está desconectada (busca do telefone, recarga, desligamento), sem acentos porque a fonte é ASCII (*EN CHARGE*, *LAEDT*, *CARGANDO*…). Com um firmware 2.1 ou anterior, as mensagens enviadas pelo app saem traduzidas, mas as mensagens internas da pulseira continuam em italiano. Uma pulseira recém-programada começa em italiano; a partir do firmware 2.2.2, a mensagem de conexão é reescrita no idioma do app assim que ele o envia, cerca de um segundo depois de conectar.

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

### 6.2 Recarga

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

### 6.3 Sem pulseiras: controles Bluetooth

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

### 6.4 Reconhecimento de golpes

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

### 6.5 Cintas cardíacas

Frequência cardíaca e calorias de **cintas peitorais Bluetooth** com o serviço cardíaco padrão: Coospo (testada a H6), Magene, Polar, Garmin, Wahoo… Valem em todos os modos, mesmo sem pulseiras.

1. Molhe os eletrodos e coloque a cinta: ela liga sozinha em contato com a pele.
2. *Modo e regras* (ou *Mais › Dispositivos*) › **Frequência cardíaca** › ative **Cintas cardíacas** e escolha a cinta de cada jogador (uma vez só). **Trocar** as inverte.
3. **Idade, peso e sexo** de quem a usa (opcionais): para as calorias (fórmula de Keytel, erro típico ±20%) e, com a idade, para as zonas cardíacas (máximo estimado 208 − 0,7 × idade).

- Sob cada cinta: estado, frequência, **sinal** em dBm (mais perto de 0 = mais forte) e **quedas**, ou seja, as vezes em que a frequência não chegou por mais de 3 segundos, com os segundos perdidos (*2 quedas (14 s)*). Contam a partir do «jogo» da partida; antes, desde a escolha da cinta. Mostram até onde a cinta alcança.
- Na partida uma linha mostra a frequência ao vivo (*J1 · 142 bpm*). No resumo (7): frequência média e máxima, calorias, gráfico no tempo e tempo nas zonas cardíacas. As pausas não contam.
- **Alcance**: teste em casa com a H6 e o telefone parado: conectada até 21 m, mas a −100 dBm, no limite; de costas o sinal cai 5-10 dBm. Em simples o jogador distante fica a 24-30 m do telefone: para não perder dados use o **segundo telefone** (6.6).

### 6.6 Segundo telefone atrás da outra linha de fundo

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
6. **Resumo**: vencedor, nomes, placar set a set com os pontos do tie-break, duração, hora de início e de fim, data, clube, quadra, local, formato, pontos e jogos ganhos. Botões **Salvar arquivos** (nome do arquivo + pasta à escolha + formatos .txt/.json/.png e **PDF completo** com resultado, estatísticas da partida e de cada set, histórico e fotos). **Compartilhar** pergunta se envia a imagem com texto (para WhatsApp e redes sociais) ou o PDF completo. No Resumo, **Fotos da partida** guarda até 10 fotos de recordação (da galeria ou tiradas na hora): ficam no telefone, vão para o PDF e passam para o outro telefone com a transferência, nunca para a imagem das redes, **Compartilhar** (imagem 1080×1350 + texto para WhatsApp/Instagram/…), **Nova partida**, **Sair**. Abaixo das fotos, **Notas** aceita um texto livre (até 2000 caracteres): salva sozinho, vai para o PDF, o texto compartilhado e os arquivos .txt e .json, e passa para o outro telefone com a transferência; não vai para a imagem das redes. A partida terminada entra sozinha no **Histórico** (aba **Progresso**): a lista das partidas, para reabrir com o resumo ou excluir, e a aba **Jogadores** com vitórias, derrotas e confronto direto de quem tem o nome escrito em *Nova partida* (maiúsculas e espaços não contam; nas duplas conta cada jogador). **Importar** adiciona as partidas salvas como .json com *Salvar arquivos* em outro telefone: as terminadas vão para o histórico e as não terminadas para as partidas suspensas para retomar. **Transferir** (no alto à direita no Histórico) passa o histórico e as partidas suspensas para outro telefone com um código QR: os dois telefones precisam estar na mesma rede Wi-Fi ou um no hotspot do outro; no que tem as partidas toque em *Enviar* e deixe aberta a janela com o código, no outro em *Receber* e escaneie o código (precisa da permissão da câmera). Assim dá para continuar em outro telefone até uma partida em andamento: suspenda, transfira e retome. As partidas se somam às que o telefone já tem. **Fotos dos jogadores**: toque no círculo ao lado do nome em *Nova partida* (ou no detalhe do jogador no Histórico) e escolha uma foto; ela é cortada quadrada e encontrada pelo nome. Aparece no Histórico, no resumo e no placar da TV, fica só neste telefone (vai junto na transferência) e não entra na imagem compartilhada. Tocando de novo no círculo você troca ou remove a foto. Se o app fechar enquanto você está no resumo (o Android fechando o app em segundo plano, uma atualização, uma reinicialização do telefone), ele volta quando o app é reaberto; não se você mesmo saiu com *Nova partida*, *Sair* ou tirando o app dos recentes. A pasta padrão é **Documents/Tennis Score Manager** (a partir do Android 10; no Android 8 e 9 a pasta do app): qualquer gerenciador de arquivos a abre e ela fica mesmo desinstalando o app. Salvando duas vezes com o mesmo nome, o segundo vira *nome (1)*; na imagem os textos longos (nomes, endereço) se ajustam à largura.

   O resumo tem três abas. **Resumo**: os dados acima e, com as pulseiras 2.4, golpes, velocidade do pulso e golpes por ponto (6.4). **Estatísticas**, da partida ou de um set: os dois jogadores comparados com barras que saem do centro (colorida a de quem está à frente) para pontos e jogos ganhos no saque e na devolução, break points salvos e convertidos, total de pontos e jogos, pontos seguidos, últimos 10 pontos, match points salvos; com as pulseiras 2.4 também golpes por tipo e velocidade média do pulso. **Ponto a ponto**: set a set, cada jogo com a bola de quem saca, *SAQUE PERDIDO* nas quebras, o placar depois de cada ponto com **BP** (break point), **SP** (set point) e **MP** (match point) e o resultado (*JOGO*, *QUEBRA*, *SET*, *PARTIDA*). Aces, duplas faltas, primeiros saques, winners e erros aparecem se você ativar as **estatísticas detalhadas** em *Modo e regras*: no lugar dos dois botões grandes surgem *Ace* e *Falta* (na segunda vez vira *Dupla falta*) e, para cada jogador, *Winner*, *Erro* (o ponto vai para o outro) e *Ponto*; *Anular ponto* também tira uma falta. É pensado para quem marca da beira da quadra. Depois de *Erro* aparece por 6 segundos *Não forçado / Forçado*; com pulseiras ou controles, depois de cada ponto aparece *Como terminou o ponto?* (Ace, Winner, Erro): responder é opcional e o placar não muda. No Histórico, o detalhe de um jogador mostra a **evolução**: pontos ganhos partida a partida e as médias de aces, duplas faltas, primeiros saques, winners e erros. As estatísticas de cada partida contam também game, set e match points convertidos, games vencidos a zero, games seguidos, sets vencidos depois de estar um break atrás e, no cartão **Tempos**, a duração de cada set e o game mais longo.

   **Revanche** (no fim do resumo): propõe o mesmo dia da semana e a mesma hora, na semana seguinte, e o clube da partida; dá para mudar data, hora e local e acrescentar uma nota. **Mandar o convite** abre o compartilhamento (WhatsApp, SMS, e-mail…) com a mensagem pronta: quem contra quem, quando, onde e como terminou da última vez. **Pôr na agenda** cria o evento no app de agenda, **Preparar a partida** traz de volta para *Nova partida* jogadores, clube, quadra e regras (o torneio não). Sem conta: o convite passa pelos canais que você já usa.

**Sair**: **Sair** (na partida pede confirmação, e também está no resumo) fecha de verdade o app; o mesmo acontece se você o remover dos apps recentes. Ao reabrir, ele começa pela aba **Jogar**.

**Salvamento**: a partida é salva sozinha a cada ponto. *Suspender* para os tempos; se você sair, se o telefone desligar ou se o app for fechado, a partida fica em **Retomar** (aba **Jogar**, ou *Retomar partida suspensa* na tela INÍCIO DA PARTIDA) e continua com *Retomar*. *Anular ponto* recalcula tudo desde o início, então funciona também depois do fim de um jogo, de um set ou da partida. A lixeira de uma partida suspensa pede confirmação. Se a partida foi iniciada por uma pulseira com a tela bloqueada (o Android não fornece a localização nesse caso), o local é obtido assim que você abrir o app de novo.

### 7.1 Treino com a cinta

Para treinar com a cinta cardíaca sem partida: **Treinar** › **Treino com a cinta**. Usa-se **uma cinta**, a do Jogador 1, com idade, peso e sexo para calorias e zonas; ela é escolhida ali mesmo (com o interruptor *Cintas cardíacas*, Bluetooth e permissões se faltarem).

1. **Iniciar**: em destaque a frequência ao vivo, embaixo o tempo e a volta atual.
2. **Volta** fecha um trecho do treino e abre outro (como o botão de volta de um cronômetro): um tiro, um exercício, uma volta na pista. **Pausar / Retomar** para o tempo; as pausas não contam.
3. **Terminar**: o app grava **mais um minuto de recuperação** (fique parado) para medir quanto a frequência cai; **Salvar já** pula essa parte.

- **Resumo**: duração, frequência média e máxima, calorias, **VFC (RMSSD)** a partir dos intervalos entre batimentos enviados pela cinta (são precisos uns trinta batimentos limpos; compare com os seus treinos parecidos, não com os dos outros), **recuperação em 1 minuto** (quanto mais cai, melhor), gráfico, zonas cardíacas e, para cada volta, duração, média, máximo e recuperação.
- **CSV** (segundo a segundo, com a volta e o minuto de recuperação) e **PDF** de uma página com gráfico, pizza das zonas e voltas, para compartilhar.
- Os treinos ficam na mesma tela, separados das partidas; podem ser reabertos ou excluídos. A frequência chega também do segundo telefone (6.6) e a gravação continua com a tela apagada.

### 7.2 Progressos

**Progresso** › **Rating TSM · Progressos**: um documento para ver se você está melhorando, **separado** para treinos e partidas.

- **Treinos**: duração, frequência média e máxima, calorias, VFC, recuperação.
- **Partidas** de um jogador do histórico (escolhido na lista): partidas vencidas, pontos ganhos, aces, duplas faltas, primeiros saques em quadra, winners, erros não forçados. Aces e os outros valores detalhados só existem nas partidas jogadas com estatísticas detalhadas; são totais por partida.
- Para cada item a média da **primeira metade** do período e a da **segunda**: ▲ verde se melhora, ▼ vermelho se piora (menos duplas faltas ou erros é uma melhora), = se fica igual (menos de 5% de diferença) ou se não dá para dizer (frequência). São precisos pelo menos 2 treinos ou partidas.
- **O que comparar**: os **Filtros** escolhem o período (tudo, 30 dias, 3 meses, 1 ano), os **dias da semana** (por exemplo só as quartas-feiras, também vários) e, para as partidas, **torneio**, **clube** e **adversário** do histórico; embaixo, a lista **Para comparar** para marcar (*Todos* / *Nenhum*). Comparação, PDF e impressão usam só os marcados.
- **Rating TSM**: em *Partidas*, acima dos filtros, o rating do jogador (todos começam em 1500) com as partidas contadas, a variação da última e a evolução, e a **Classificação TSM** de todos os jogadores do histórico. É um Elo calculado no telefone: sobe quando você vence, mais contra quem tem rating mais alto e com placar folgado; nas duplas conta a média da dupla; tênis e padel têm classificações separadas. Nas 5 primeiras partidas é *provisório* e se move mais. No resumo de cada partida o cartão **Rating TSM** mostra quanto cada um ganhou ou perdeu; com **Amistoso** a partida fica no histórico mas não conta.
- **PDF** (com a evolução de cada item e a lista completa, em várias páginas se necessário) para compartilhar, ou **Imprimir** com o serviço de impressão do Android (também *Salvar como PDF*).

### 7.3 Vídeos: telefone-câmera, melhores momentos e replay

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

### 7.4 Raquetes: raquete virtual

**Progresso** (ou **Mais**) › **As minhas raquetes**: cada raquete física tem a sua ficha (duas raquetes do mesmo modelo são duas fichas: cordas, peso e horas mudam).

- **Ficha**: alcunha (A, B…), marca, modelo, peso, equilíbrio, cabeça e padrão de cordas, escritos à mão ou tirados **do catálogo** (dados do fabricante, a corrigir se mediu a sua). *Posta de lado*: deixa de ser proposta, mas fica nas comparações.
- **Encordoamentos**: tipo (monofilamento, multifilamento, sintética, tripa natural, híbrido), marca e modelo (também das cordas comuns), calibre, tensão das verticais e das horizontais, data e notas. *Novo encordoamento* parte do montado. Para cada um a app soma as **horas de jogo** dos jogos e treinos associados e avisa ao passar o **limite de verificação** escolhido (20 horas por omissão): só pelo tempo não consegue saber quanta tensão resta.
- **Antes de jogar**: em *Nova partida*, por baixo da classificação de cada jogador, escolhe-se a sua raquete; no treino, acima da cinta. O jogo (pontos, pancadas das pulseiras, batimento) e o treino são guardados com uma **cópia** da raquete como estava nesse dia, cordas e tensão incluídas: voltar a encordoá-la depois não muda as sessões passadas.
- **No resumo** do jogo (separador *Resumo*) e do treino vê-se a raquete usada, pode associá-la também depois (usa-se o encordoamento montado nesse dia) e avaliar as **sensações** de 1 a 5: controlo, conforto, potência e cansaço.
- **Comparação**: *por raquete* ou *por encordoamento* (mesma raquete com tensões ou cordas diferentes), escolhem-se as configurações A e B. Barras lado a lado para sessões, horas, jogos ganhos, pontos ganhos, primeiros serviços dentro, pontos ganhos com o primeiro e o segundo, ases, duplas faltas, winners e erros não forçados por jogo, velocidade média do pulso e pancadas das pulseiras, batimento médio e sensações médias. O melhor valor fica a negrito, mas a app não escolhe uma vencedora: adversário, piso, condições e forma também contam.

### 7.5 Treino de golpes: saque, forehand, backhand e voleio

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
5. De cada saque é salvo um **clipe** de alguns segundos em volta do golpe. **Fim** mostra o resumo: dentro sobre tentativas, por lado, primeiro e segundo, alvo e, se a pulseira reconheceu os saques, a rotação média do pulso nos saques dentro e nos de fora. **Rever todos / só os dentro / só os fora** abre os clipes no replay (pausa, câmera lenta, quadro a quadro).
6. Embaixo de **Começar** ficam as 3 últimas sessões **do golpe escolhido** (os botões dos golpes mostram quantas sessões cada um tem, ex. *Backhand · 4*) e a linha dos **golpes dentro, sessão após sessão**. **Todas as sessões** abre as do golpe escolhido, por mês, com o espaço dos vídeos e se já foram analisadas. Toque numa sessão para o resumo e os clipes; **segure** para selecioná-la (o ícone no alto seleciona todas) e depois **Apagar** (sessão e vídeos) ou **Só os vídeos** (libera espaço: ficam as contagens, as medidas da análise e as imagens dos momentos-chave). **Progresso** mostra a evolução de cada golpe.
   - **Onde ficam os vídeos**: em **Documentos/Tennis Score Manager/Treino de golpes**, com as pastas **Saque**, **Forehand**, **Backhand** e **Voleio** (criadas ao abrir o app) e dentro uma pasta por sessão (data e hora) e uma por golpe, com `video.mp4` (abre e compartilha de qualquer app), a análise e as três imagens. Aparecem em qualquer gerenciador de arquivos e ficam mesmo desinstalando o app (guarde como arquivo: um app reinstalado não recupera as sessões). No Android 8-10 a mesma estrutura fica na pasta do app. Os clipes das sessões de antes da 2.5 vão para lá sozinhos na primeira abertura.
7. **Análise do movimento** (a partir da sessão salva): **Analisar os saques** encontra a posição do corpo nos clipes (modelo MoveNet: na primeira vez o app oferece baixá-lo, 7 MB, da página do projeto no GitHub; depois tudo fica no telefone, sem internet, alguns segundos por saque; pode ser excluído no fim da tela. Não está disponível em aparelhos com processador x86) e mede três momentos: **preparação** (braço do lançamento no alto), **joelhos** mais flexionados e **impacto**. Para cada saque: as três imagens com o esqueleto desenhado e as medidas (joelho mais flexionado, cotovelo no impacto, altura do impacto em relação à altura, braço do lançamento, ombros na preparação, tempo da preparação ao impacto); no alto, **dentro e fora** comparados. São estimativas a partir de um vídeo de lado: servem sobretudo para comparar os seus saques entre si. O corpo é bem reconhecido com a pessoa inteira no enquadramento, de lado e com boa luz; raquete e bola não são medidas.
   Toque num saque para abri-lo: **Ver o vídeo com o esqueleto** abre o clipe no replay com o corpo desenhado por cima, em câmera lenta, com botões para saltar para **Preparação**, **Joelhos** e **Impacto** e outro para tirar ou pôr o esqueleto; dois dedos ampliam vídeo e esqueleto juntos (até 6×), toque duplo para voltar. As três imagens também se tocam para vê-las em tela cheia e ampliá-las. Com **várias pessoas** no enquadramento o app segue quem saca (quem mais levanta o braço) e cobre as outras; isso aparece embaixo do saque: confira nas imagens. **Refazer a análise** começa de novo (os clipes ficam), útil depois de uma atualização do app.

**Forehand, backhand e voleio** funcionam do mesmo jeito (KEY1 dentro, KEY2 fora, um clipe por golpe, resumo e análise), com estas diferenças:
- **Onde colocar o telefone**: ao lado de quem bate, perpendicular à direção dos golpes, a 4-5 m e fora do corredor, na altura do ponto de impacto; lente a cerca de 1 m; o swing inteiro no enquadramento (o pulso se move uns 2 m para a frente e para trás). Para o voleio: ao lado de quem joga na rede, a 3-4 m, na altura dele. Sempre do mesmo lado durante a sessão, e nunca na trajetória da bola.
- No lugar de lado, primeiro/segundo e alvo escolhe-se a **direção** desejada (**cruzado**, **no centro**, **paralela**) e, para o voleio, se é **de forehand** ou **de backhand**; o resumo conta-os à parte.
- **Análise do movimento**: os três momentos são **preparação** (onde o pulso para no fim da preparação), **impacto** (pulso mais rápido; ali perto o app relê um quadro a cada 33 ms) e **final** (pulso mais alto depois do impacto; no voleio, onde o pulso para). Se houver vários golpes na janela, vale o reconhecido pela pulseira; os pulsos trocados pelo MoveNet (acontece de lado) se corrigem sozinhos. Filme **de lado**: de frente a preparação vai em profundidade e não aparece. As medidas (*—* se o cotovelo ou as pernas não aparecem): joelho mais flexionado, cotovelo no impacto, altura do impacto, quanto **à frente do corpo** se bate, **amplitude da preparação**, altura do final (todas em relação à altura) e o tempo da preparação ao impacto. O vídeo com o esqueleto tem os botões **Preparação**, **Impacto** e **Final**.

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
3. No 1-1, no formato com tie-break decisivo, a voz acrescenta "tie-break decisivo".

## 11. Problemas comuns

- **Pulseira não encontrada**: Bluetooth e localização ativados (linhas verdes)? A pulseira está piscando PAREANDO? (Se já estiver conectada a outro telefone, ela não aparece.) Se nesse meio-tempo ela se desligou sozinha (30 s), ligue-a de novo com um clique no botão lateral.
- **A pulseira mostra NAO ENVIADO**: o telefone não confirmou o toque em 8 segundos (conexão perdida ou app fechado). Pressione de novo quando a pulseira tiver reconectado. Caso raro: se o toque tinha chegado e só a confirmação se perdeu, o ponto conta duas vezes; a voz anuncia o placar, corrija com *Anular ponto*.
- **No painel de ajustes aparece "O firmware desta pulseira não tem ajustes"**: essa pulseira ainda tem o sketch antigo; carregue-o de novo (capítulo 5).
- **A voz não fala ou lê errado**: volume de mídia, *Audio On*, voz do idioma escolhido instalada (4); tente outro mecanismo ou outra voz em *Áudio e voz*.
- **Tela apagada durante a partida**: no modo pulseiras, um serviço em primeiro plano (notificação "Partida em andamento") mantém ativos Bluetooth, voz e cronômetros; nos modos árbitro e controles a tela fica acesa (com os controles ela precisa ficar: 6.3).
- **O controle não faz nada**: ele está associado ao jogador (*Mais › Dispositivos* › *Controles*, linha com o nome dele)? O app precisa estar aberto na partida, com a tela acesa (6.3). Se a linha diz *Desconectado*, aperte uma tecla para despertá-lo ou pareie de novo nas configurações de Bluetooth.
- **Os golpes são confundidos ou saem como não reconhecidos**: use a pulseira como durante a calibração (mesmo pulso, mesmo sentido); senão refaça a calibração usando-a como na partida (6.4).
- **A pulseira mostra MEMORIA CHEIA**: um exemplo de calibração não pôde ser salvo. Apague os exemplos de um golpe (lixeira) e refaça.
- **O telefone-placar não encontra o árbitro**: mesma rede? (um dos dois faz o hotspot, o outro está conectado a ele). *Placar na TV* ativado no telefone do árbitro? Tente o endereço à mão. Alguns hotspots isolam os dispositivos conectados entre si ("isolamento de clientes"): se houver essa opção, desative-a.
- **O navegador do telefone não abre o endereço** com os dados móveis ativados: o Android manda o tráfego pelos dados móveis porque o hotspot não tem internet. Use o app em *Usar como placar* (ele resolve isso sozinho) ou desligue os dados móveis nesse telefone.
- **Monitor preto com o cabo**: esse telefone não tem saída de vídeo pela USB-C (8.1), ou o Samsung DeX foi iniciado: escolha *Espelhamento de tela*.
