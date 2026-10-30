## 3.1 Cómo está organizada la app

En el **primer arranque** la app hace tres preguntas: el idioma, cómo la vas a usar (llevar el marcador, jugar con las pulseras, entrenar, grabar partidos) y tu nombre; se puede saltar y repetir desde **Más › Presentación de la app**. Abajo hay siempre **cinco pestañas**:

- **Jugar**: **Nuevo partido**, el partido suspendido para reanudar, la **revancha** del último, el **torneo entre amigos** (Americano, Mexicano) y **Usar este teléfono como** marcador o cámara. Arriba el estado de los dispositivos (pulseras, bandas, cámara, TV): un toque para conectarlos.
- **Entrenar**: entrenamiento con la banda de pulso y **entrenamiento de golpes** (saque, derecha, revés, volea) con las sesiones guardadas.
- **Vídeo**: **Grabar con este teléfono**, **Tus vídeos** (capítulos, mejores jugadas, filtros, importar) y el teléfono-cámara.
- **Progresos**: **Rating TSM · Progresos** (con tu nombre muestra enseguida tu rating), **Historial**, **Mis raquetas**.
- **Más**: **Tú** (tu nombre), **Dispositivos** (pulseras, mandos, bandas, segundo teléfono, marcador en TV), **Idioma y voz**, **Ayuda** (esta guía, con búsqueda y referencias que se tocan), raquetas, transferir partidos, presentación, versión y licencia.

**Nuevo partido** son dos pasos: **quién juega** (individual o dobles, los nombres, con los jugadores recientes para tocar, y el deporte; club, pista y torneo en *Más detalles*) y **cómo llevas el marcador y las reglas**. Todo es opcional: se puede seguir sin nombres. Desde cada pantalla **Atrás** vuelve a la pestaña de la que se venía.

## 4. La voz (funciona sin internet)

- Cada canto lo lee **en una sola frase** la síntesis de voz del teléfono, con una voz instalada: sin internet y con una entonación natural. Los nombres de los jugadores forman parte de la frase.
- Más › **Idioma y voz** › **Audio y voz**:
  - **Motor de síntesis de voz**: *Predeterminado del teléfono* (en los Samsung es Samsung TTS) o el motor que elijas, por ejemplo *Servicios de voz de Google*. En nuestras pruebas, hechas en italiano, Google sonaba más natural.
  - **Voz**: *Automática (la mejor sin conexión)* o una voz concreta (Google identifica sus voces con un código; las marcadas *en línea* necesitan internet).
  - **Probar voz**: lee una secuencia de cantos de ejemplo con los nombres introducidos. Mientras lee, el botón pasa a ser **Detener la prueba**: tócalo otra vez para interrumpirla (también se detiene sola al salir de la página). La síntesis de voz no se puede pausar a mitad de frase, así que el botón la detiene; si lo vuelves a pulsar, empieza desde el principio.
- Si falta la voz del idioma elegido: **Ajustes › Administración general › Idioma › Texto a voz** (o *Salida de texto a voz*) → descarga la voz de ese idioma para el motor elegido (la app muestra también el botón **Instalar voz**). Si la síntesis de voz no arranca en absoluto, la app lo indica con un mensaje aparte y el botón **Abrir ajustes** (ajustes de síntesis de voz de Android).
- **Pronunciación**: algunos motores leen mal palabras del tenis (en italiano, "primo set" salía como "primo settembre" y "tie-break" como "time break"). La app las corrige sola; las correcciones se han verificado transcribiendo el audio real de Samsung y de Google.
- **Grabaciones personalizadas** (la voz más natural de todas: la tuya o la de un juez): en la carpeta `Android/data/io.github.steve_linux.tsm/files/voice/` está `LEGGIMI.txt` (el archivo «léeme»; las explicaciones del principio están en el idioma de la app y se reescriben al cambiarlo) con la lista de las 85 claves y de sus frases. Graba los archivos con esos nombres (`score_1_0.mp3` = «quince cero»…), ponlos en un ZIP con una carpeta por idioma (`it/`, `en/`, `fr/`, `de/`, `es/`, `pt/`) y usa **Importar ZIP**. `LEGGIMI.txt` tiene una columna por idioma separada por tabuladores, así que también se abre bien como hoja de cálculo. Las grabaciones siempre tienen prioridad sobre la síntesis; los nombres los sigue leyendo la síntesis. En el ZIP cuenta la carpeta que contiene el archivo (también dentro de otras carpetas, p. ej. `voice/es/`); los archivos fuera de una carpeta de idioma van al idioma actual y las carpetas `tts/` se ignoran, así que también se puede comprimir la carpeta `voice/` de la app. Un ZIP dañado o incompleto no cambia nada (*ZIP ilegible o incompleto*). **Quitar grabaciones** pide confirmación. Si un archivo no se puede reproducir, esa frase la dice la síntesis de voz y el archivo no se vuelve a usar hasta reiniciar la app.
- **Usar archivos de audio pregenerados** (opcional): con **Generar archivos** la app crea una sola vez los 85 archivos con la voz elegida (incluso una voz *en línea*, si en ese momento hay internet) y después los usa en lugar de la síntesis continua. Suena más «a trozos», pero sirve para llevarse sin conexión una voz en línea. Los archivos anteriores se mantienen hasta que los nuevos estén listos (si la generación no termina: *Generación no completada*); mientras genera, idioma, motor, voz, prueba de voz y audio quedan bloqueados.
- **Altavoz externo**: basta con emparejarlo por Bluetooth con el teléfono; la voz sale por el canal multimedia (ajusta el volumen multimedia).
- **Auriculares Bluetooth**: se emparejan igual y la voz solo la oyes tú, sin molestar a las pistas de al lado; también sirven a quien lleva el marcador desde fuera de la pista. Las pulseras pueden seguir conectadas a la vez.
- **Dos dispositivos de audio a la vez**: muchos teléfonos envían el audio a dos dispositivos Bluetooth al mismo tiempo. En Samsung: conecta los dos, abre el panel rápido › **Salida multimedia** y marca los dos. Así cada jugador puede tener sus auriculares, o el altavoz suena para la pista y los auriculares para quien lleva el marcador.

### 4.1 Idiomas

Más › **Idioma y voz** › **Idioma**: Italiano, English, Français, Deutsch, Español, Português (cada idioma aparece escrito en su propia lengua, así lo encuentras aunque la app esté en un idioma que no entiendas). La elección se aplica a la vez a **pantallas, voz del juez, marcador de TV, resumen/compartir y pulseras**. Las fechas siguen el idioma (*mercredi 30 septembre 2026*, *Mittwoch, 30. September 2026*…).

Los cantos no son traducciones palabra por palabra: siguen los textos oficiales para jueces de silla de cada federación, es decir, FFT *L'arbitrage en 255 questions* e ITF en francés, los materiales de DTB/BTV y Swiss Tennis, RFET *Deberes y procedimientos*, FPT *Deveres e Procedimentos* (el único guion portugués publicado). El inglés sigue a la ITF. En inglés, el cero de los sets al final del partido se dice *love* («six love, six four»), como en los puntos.

| | Italiano | English | Français | Deutsch | Español | Português |
|---|---|---|---|---|---|---|
| Inicio | primo set · Rossi al servizio · gioco | first set · Rossi to serve · play | première manche · au service Rossi · jouez | erster Satz · Aufschlag Rossi · spielen | primer set · al servicio Rossi · jueguen | primeiro set · Rossi ao serviço · joguem |
| 15-0 · 15-15 | quindici zero · quindici pari | fifteen love · fifteen all | quinze-zéro · quinze A | fünfzehn null · fünfzehn beide | quince cero · quince iguales | quinze-zero · quinze iguais |
| 40-40 · ventaja | parità · vantaggio Rossi | deuce · advantage Rossi | égalité · avantage Rossi | Einstand · Vorteil Rossi | iguales · ventaja Rossi | iguais · vantagem Rossi |
| Fin del juego | gioco Rossi, Rossi conduce tre giochi a due | game Rossi, Rossi leads three games to two | jeu Rossi, Rossi mène trois jeux à deux | Spiel Rossi, Rossi führt drei zu zwei | juego Rossi, Rossi gana tres juegos a dos | jogo Rossi, Rossi vence por três jogos a dois |
| Juegos iguales | due giochi pari | two games all | deux jeux partout | zwei beide | dos juegos iguales | dois jogos iguais |
| Tie-break | tre a uno Rossi · sei pari | three one Rossi · six all | trois un Rossi · six partout | drei zu eins Rossi · sechs beide | tres uno Rossi · seis iguales | três a um Rossi · seis iguais |
| Set | un set a zero · un set pari | one set to love · one set all | une manche à zéro · une manche partout | eins zu null in Sätzen · ein Satz beide | un set a cero · un set iguales | um set a zero · sets iguais |
| Fin del partido | gioco, set, partita Rossi | game, set and match Rossi | jeu, set et match Rossi | Spiel, Satz und Sieg Rossi | juego, set y partido Rossi | jogo, set e partida Rossi |
| Cambio de lado | cambio campo | change ends | changement de côté | Seitenwechsel | cambio de lado | troca de lado |

Particularidades:
- En francés y en español el nombre va **después** de "au service"/"al servicio", en alemán después de "Aufschlag". El francés llama al set **manche** (salvo en "jeu, set et match") y al tie-break **jeu décisif**.
- **10-6 … 10-9** en el súper tie-break: en francés, español y portugués se dice "dix **à** huit", "diez **a** ocho", "dez **a** oito", porque "dix huit" / "diez ocho" / "dez oito" se oyen como *dieciocho*.
- **Portugués**: una sola opción, con la interfaz y la voz preferida de Brasil (si falta la voz brasileña se usa la portuguesa) y los cantos del guion de la FPT, con palabras válidas en los dos países ("jogo" y no "game", "partida"). "Um set a um" pasó a ser "sets iguais": en singular, *set* se pronuncia como *sete* y parecía 7-1.
- **Pronunciación**: todas las frases de todos los idiomas se han hecho leer a las voces de Google y se han transcrito con whisper. Correcciones añadidas: en alemán, "Tie-Break" se hace leer "Taibreak", con una coma delante (si no, sale "Teilbreg" o "bei Detailbreak"); en francés, la "à" suelta de los archivos pregenerados se hace leer "a" (si no, sale "a accent grave"). **Samsung TTS todavía no se ha probado en los nuevos idiomas.**
- **Pulseras** (firmware 2.2): la app envía por sí sola el idioma en cada conexión y cuando lo cambias; la pulsera lo guarda y lo usa también cuando no está conectada (búsqueda del teléfono, carga, apagado), sin tildes porque la fuente es ASCII (*EN CHARGE*, *LAEDT*, *CARGANDO*…). Con un firmware 2.1 o anterior, los mensajes que envía la app salen traducidos, pero los propios de la pulsera siguen en italiano. Una pulsera recién programada arranca en italiano; desde el firmware 2.2.2 su mensaje de conexión se vuelve a escribir en el idioma de la app en cuanto esta se lo envía, más o menos un segundo después de conectarse.

## 6. Usar las pulseras

| Botón | Acción |
|---|---|
| **KEY1** (frontal) corto | punto para quien lleva la pulsera (también inicia el partido desde la página INICIO DEL PARTIDO); un pitido lo confirma (firmware 2.3 + app 2.4: cuando el teléfono lo ha recibido, más o menos medio segundo después) |
| **KEY1** largo (1 s) | muestra la batería (y mantiene encendida la pulsera si está a punto de apagarse por inactividad) |
| **KEY2** corto | anula el último punto (también desde la ventana de fin de partido); dos pitidos más graves (con el firmware 2.3, al confirmarlo el teléfono) |
| **KEY2** largo (2 s) | apaga la pulsera (con el cable conectado muestra *SIGUE CARGANDO*: se carga también apagada; desde el firmware 2.3 también KEY1 o KEY2 la vuelven a encender) |
| Botón lateral | un clic enciende; doble clic apaga (función del hardware) |

- Al encenderla parpadea **VINCULANDO...** (encendida 0,35 s cada 2 s, para ahorrar). La app se conecta sola a las pulseras que ya conoce en cuanto se abre; las nuevas las encuentra en **Más › Dispositivos** (o en **Modo y reglas** con el modo *Pulseras*).
- Conectada: **VINCULADA** durante 3 segundos con dos pitidos, luego **VINCULADA A** + el nombre del jugador.
- En cada punto la pantalla se enciende con el tanteo del juego en grande (a la izquierda el tuyo, a la derecha el del rival; la pelota verde indica quién saca) y luego se apaga. Al final de cada juego muestra juegos y sets. Si el teléfono no confirma una pulsación en 8 segundos (conexión perdida), aparece **NO ENVIADO** en rojo con dos pitidos graves: vuelve a pulsar cuando se haya reconectado. Una pulsación hecha durante un corte breve, en cambio, no se pierde: se envía en cuanto la pulsera se reconecta, y el pitido suena en ese momento.

**Apagado automático** (los tiempos se cambian desde la app, 6.1):

| Situación | Qué hace la pulsera | Predeterminado |
|---|---|---|
| Encendida, pero ningún teléfono se conecta | parpadea VINCULANDO y luego se apaga | **30 s** |
| Teléfono perdido (apagado, fuera de alcance, Bluetooth desactivado, app cerrada de golpe) | parpadea RECONECTANDO y se vuelve a conectar sola en cuanto puede; si no, se apaga | 3 min |
| Conectada pero inactiva (ningún punto, ningún mensaje) | 30 s antes avisa con **INACTIVO · MANTEN PULSADO KEY1** y un pitido, luego se apaga | 30 min |
| Partido terminado (confirmado en el teléfono) o **Salir** en la app | muestra FIN DEL PARTIDO / APP CERRADA y se apaga enseguida | activado (se puede desactivar, 7.2) |
| Batería agotada (por debajo de 3,30 V en dos lecturas seguidas) | muestra BATERIA AGOTADA y se apaga, para no quedarse encendida a medias | siempre |

- En los últimos 10 segundos antes de apagarse sin teléfono muestra **APAGANDO · 8s - PULSA UN BOTON** con un pitido: cualquier botón aplaza el apagado y la cuenta vuelve a empezar.
- Cuando se apaga sola se lo comunica al teléfono: durante el partido el recuadro naranja muestra *PULSERA 1 APAGADA (SIN USO)*, *(BATERÍA AGOTADA)*… Después del apagado al final del partido, el teléfono deja de buscarla continuamente y se reconecta solo cuando la vuelves a encender.


El resto del ahorro: CPU a 80 MHz, pantalla apagada cuando no hace falta, Bluetooth de bajo consumo (la radio se despierta 2-3 veces por segundo; aun así, el botón responde en ~120 ms), zumbador activo solo durante los pitidos, micrófono y 5V apagados, sensor de movimiento (IMU) encendido solo con el reconocimiento de golpes (6.4).

### 6.1 Ajustes de la pulsera

Desde **Más › Dispositivos** o **Modo y reglas** (**Ajustes** debajo de la pulsera de cada jugador) o durante el partido (toca **J1**/**J2** arriba, o el icono de los controles deslizantes). Se guardan **en la pulsera** y se conservan aunque se apague; con cada cambio la pulsera muestra su nombre con *AJUSTES OK*. Con el firmware 2.4 también está la sección **Golpes** (6.4).

| Ajuste | Valores | Predeterminado |
|---|---|---|
| Nombre | hasta 12 caracteres (p. ej. el nombre del jugador o "J1") | TSM-xxxx |
| Brillo de la pantalla | 5-100 % | 20 % |
| Marcador visible tras cada punto | No, 2, 3, 5, 8 s (el resumen de final de juego dura 2 s más) | 3 s |
| Volumen del pitido | Silencio-100 % | 50 % |
| Pantalla girada | para llevarla en la otra muñeca | no |
| Apagado automático: al encenderla, si no se conecta ningún teléfono | 15 s - 5 min | 30 s |
| Apagado automático: si pierde la conexión con el teléfono | 1-10 min | 3 min |
| Apagado automático: si sigue conectada pero sin uso | 10-60 min | 30 min |

Debajo aparece la **estimación de autonomía** (con la carga completa y con la carga actual) con el consumo desglosado: cambia mientras mueves los controles deslizantes, antes incluso de confirmar. Después, **Identificar**, **Apagar** y **Copiar a la otra pulsera** (mismos ajustes; el nombre sigue siendo el suyo). **Apagar** pide confirmación, también durante el partido.

### 6.2 Carga

Conecta el cable USB-C: la pulsera emite un pitido y muestra la **pantalla de carga** durante 30 segundos; luego la pantalla se apaga y cada 10 segundos se vuelve a encender 1,5 s (un vistazo, como el piloto de un cargador). **Cualquier botón** la vuelve a encender otros 30 segundos. Si la pulsera estaba apagada, enciéndela con un clic en el botón lateral para verla (la carga se produce igualmente, también con la pulsera apagada).

```
 TSM-3FA2   USB 5.01V             ← nombre y tensión del cable
 ┌──────────┐
 │██████ ⚡  │▌   78%              ← icono de batería y porcentaje de carga
 └──────────┘
      CARGANDO                    ← o bien CARGA COMPLETA / ALIMENTADO POR USB
 4.12V  HACE 42 MIN  FIN ~25 MIN  ← tensión de la batería, tiempo en carga, fin estimado
```

- **Porcentaje**: durante la carga la tensión medida es más alta que la real (≈0,1 V); la pulsera la corrige, nunca hace bajar el porcentaje y solo llega al **100 %** cuando el cargador indica que ha terminado. Con la carga completa, el texto pasa a **CARGA COMPLETA** con el tiempo empleado (*CARGADA EN 1H 25*) y la pantalla se queda apagada (nada de destellos por la noche).
- **Fin estimado**: el chip de alimentación (PM1) no mide la corriente de carga, así que el tiempo que falta se calcula a partir de cuánto ha subido la carga en los últimos 10 minutos: aparece a los 10 minutos, redondeado a 5, y es una estimación.
- **ALIMENTADO POR USB**: el cable está conectado pero la batería no se carga (y no está llena): cable o cargador insuficientes, o batería desconectada.
- **Con el cable no se apaga sola** (ni por falta de teléfono, ni por inactividad, ni por *batería agotada*); se puede apagar con KEY2 largo. Al desconectar el cable muestra **USB DESCONECTADO · BATERIA 97%** y a partir de ahí vuelven a contar los tiempos normales de apagado (6).
- **Conectada al teléfono** (p. ej. con una batería externa durante el partido) el tanteo tiene prioridad: solo un mensaje breve *CARGANDO 78%*, y KEY1 largo muestra *CARGA COMPLETA* o *CARGANDO*.
- **En la app**: la fila de la pulsera (en *Dispositivos* y en *Modo y reglas*) y sus ajustes muestran *Cargando 78 %* o *Carga completa*; durante el partido, las etiquetas J1/J2 llevan el símbolo ⚡. El registro `files/battery_log.csv` también tiene dos columnas más (tensión USB, carga completa): útil para comprobar cuánto tarda realmente en cargarse.
- La pulsera y la app calculan ahora el porcentaje con la **misma curva** de la batería LiPo (antes la pulsera usaba una recta menos precisa), así que muestran el mismo número.

> Pendiente de comprobar con las pulseras reales: la carga no se ha podido probar en el hardware. En particular, con qué frecuencia indica el cargador *carga completa* y qué precisión tiene el porcentaje durante la carga.

### 6.3 Sin pulseras: mandos Bluetooth

El sistema más económico para que los jugadores se anoten los puntos: dos **mandos Bluetooth para selfies** (los del botón de disparo, a menudo vendidos como *AB Shutter3*), de 1 a 3 € cada uno. Android los ve como un teclado: sin firmware que cargar y sin permisos para la app. También sirve cualquier otra botonera o teclado Bluetooth (o USB).

1. Enciende el mando y emparéjalo en los **ajustes de Bluetooth** del teléfono, como unos auriculares (*Más › Dispositivos › Ajustes de Bluetooth* los abre). Repite con el segundo.
2. *Modo y reglas* › modo **Mandos** › **Asignar** debajo del Jugador 1 › pulsa la tecla de su mando que dará el punto; luego lo mismo para el Jugador 2. Con cada clic de un mando asignado su fila muestra **Tecla recibida**: así se prueba antes de jugar. **Intercambiar J1 ↔ J2** los invierte.
3. **INICIO DEL PARTIDO**: un clic en un mando empieza el partido, como KEY1 de las pulseras. Arriba, **J1**/**J2** se colorean cuando los mandos están conectados.

| Gesto | Acción |
|---|---|
| un clic | punto para quien tiene el mando (llega medio segundo después: la app espera a ver si es un doble clic) |
| doble clic | deshace el último punto (también desde la ventana de fin de partido) |
| mantener pulsado 1 s | deshace el último punto |
| la otra tecla (mandos de dos teclas) | deshace el último punto, enseguida; pulsada menos de medio segundo después de un clic, anula ese clic |

- Valen las protecciones de las pulseras: dos puntos con menos de 2 segundos de diferencia cuentan una sola vez, dos deshacer seguidos quitan un solo punto, *Partido terminado* solo se confirma desde el teléfono.
- Muchos mandos para selfies tienen dos botones (*iOS* y *Android*): la tecla pulsada al asignarlo da el punto y la otra deshace. En el CAMKIX (un *AB Shutter3*) el botón grande envía SUBIR VOLUMEN y el pequeño INTRO: asígnalo con el botón grande y el pequeño pasa a ser «deshacer». Si un mando envía dos teclas en cada pulsación, una pulsación sigue siendo un clic.
- **La pantalla debe seguir encendida con la app delante** (durante el partido la app la mantiene encendida sola). A diferencia de las pulseras, las teclas de un teclado Bluetooth solo llegan a la app visible: con la pantalla bloqueada u otra app abierta no cuentan. En partidos largos, deja el teléfono cargando.
- Si un mando se desconecta durante el partido (apagado, sin pila, demasiado lejos) el recuadro naranja muestra *MANDO 1 DESCONECTADO*, y *CONECTADO* cuando vuelve. Muchos mandos se duermen tras unos minutos sin clics: el primer clic los despierta y a veces se pierde. Si la voz no canta el punto, pulsa otra vez.
- Alcance: unos diez metros, como unos auriculares Bluetooth; con el teléfono junto a la red, en el centro de la pista, se cubren los dos lados. Pila: normalmente una CR2032, que dura meses.

### 6.4 Reconocimiento de golpes

Con el firmware 2.4 la pulsera reconoce **derecha, revés, saque y volea** con su sensor de movimiento (IMU) y los envía a la app, que los cuenta por jugador. El reconocimiento aprende de quien lleva la pulsera: primero hace falta una breve **calibración**, cuyos ejemplos se quedan guardados en la pulsera.

1. **Sujeta la pulsera a la muñeca**, siempre igual (misma muñeca, mismo sentido). El reconocimiento se basa en la dirección de los movimientos: una pulsera girada respecto a la calibración confunde los golpes.
2. Ajustes de la pulsera (6.1) › **Golpes** › **Calibración**: toca **Calibrar** junto a *Derecha*. La pulsera muestra *DERECHA · CALIBRACION 0/20*.
3. Haz **5-10 derechas** con una pausa entre una y otra: con cada golpe sube el contador, en la pulsera y en la app (con un pitido si el volumen no está a cero). Si un golpe ha salido mal, toca **Deshacer el último**. Luego **Terminar**.
4. Repite para revés, saque y volea. La papelera borra los ejemplos de un golpe para repetirlos. Se guardan como máximo 20 ejemplos por golpe (a partir de ahí se sustituye el más antiguo). Bastan 3 ejemplos de al menos dos golpes (*Lista: reconoce los golpes calibrados*), pero más ejemplos y más variados dan un reconocimiento mejor.
5. Activa **Reconocimiento de golpes**. Con **Mostrar el golpe en la pulsera** el golpe aparece un momento con la seguridad del reconocimiento (p. ej. *REVES 87%*), solo si la pantalla no está mostrando el marcador o un mensaje.

- Durante el partido, bajo los tiempos, una fila muestra los golpes de cada jugador (*J1 D 12 · R 8 · S 5 · V 3*). Cuentan solo con el partido empezado, no en el calentamiento ni en la calibración.
- El resumen (7) incluye los golpes por tipo, la **velocidad de muñeca** (media y máxima, en °/s: la fuerza del golpe, no la velocidad de la pelota) y los **golpes por punto** (duración de los intercambios). Si solo un jugador tiene pulsera con golpes, los intercambios cuentan solo los suyos.
- Un golpe que no se parece a ningún ejemplo no se muestra y cuenta como *no reconocido*: si pasa a menudo, revisa cómo llevas la pulsera o añade ejemplos.
- Los movimientos lentos (recolocarse, recoger la pelota) no cuentan: un golpe tiene que superar 400 °/s de giro de muñeca y 2,5 g de aceleración.
- **Batería**: el sensor gasta unos 1 mA más, unos veinte minutos de una carga completa (la estimación de los ajustes lo tiene en cuenta). Sin reconocimiento, el sensor queda apagado.
- En los golpes muy fuertes el giroscopio llega a su máximo (2000 °/s): el reconocimiento no se resiente, pero la velocidad indicada sale algo por debajo de la real.

### 6.5 Bandas de pecho

Frecuencia cardíaca y calorías desde **bandas de pecho Bluetooth** con el servicio cardíaco estándar: Coospo (probada la H6), Magene, Polar, Garmin, Wahoo… Valen en todos los modos, incluso sin pulseras.

1. Moja los electrodos y ponte la banda: se enciende sola al contacto con la piel.
2. *Modo y reglas* (o *Más › Dispositivos*) › **Frecuencia cardíaca** › activa **Bandas de pecho** y elige la banda de cada jugador (una sola vez). **Intercambiar** las invierte.
3. **Edad, peso y sexo** de quien la lleva (opcionales): para las calorías (fórmula de Keytel, error típico ±20 %) y, con la edad, para las zonas cardíacas (máximo estimado 208 − 0,7 × edad).

- Bajo cada banda: estado, pulso, **señal** en dBm (más cerca de 0 = más fuerte) y **cortes**, es decir, las veces que el pulso no llegó durante más de 3 segundos, con los segundos perdidos (*2 cortes (14 s)*). Cuentan desde el «juego» del partido; antes, desde que se eligió la banda. Sirven para saber hasta dónde llega la banda.
- En el partido una fila muestra el pulso en directo (*J1 · 142 bpm*). En el resumen (7): pulso medio y máximo, calorías, gráfico en el tiempo y tiempo en las zonas cardíacas. Las pausas no cuentan.
- **Alcance**: prueba en casa con la H6 y el teléfono quieto: conectada hasta 21 m, pero a −100 dBm, al límite; de espaldas la señal baja 5-10 dBm. En individuales el jugador lejano está a 24-30 m del teléfono: para no perder datos usa el **segundo teléfono** (6.6).

### 6.6 Segundo teléfono detrás de la otra línea de fondo

Bandas y pulseras aceptan **un teléfono a la vez** y llegan a unos metros menos que el largo de la pista. Con un segundo teléfono detrás de la otra línea de fondo cada jugador tiene siempre uno cerca: el segundo (**satélite**) capta las bandas y pulseras de los jugadores de su lado y hace de puente con el **principal**, que lleva el partido.

1. **Principal**: Más › **Dispositivos** › **Segundo teléfono** › **Usar un segundo teléfono**. La primera vez pide el permiso *Dispositivos cercanos* (la ubicación hasta Android 12). El teléfono crea su **propia red, solo local**: sin SIM ni datos, y sin necesidad del Wi-Fi del club. Aparece un QR.
2. **Satélite**: Más › **Dispositivos** › **Segundo teléfono** › **Hacer de segundo teléfono** › escanea el QR. Android pide una vez conectarse a la red del principal (*AndroidShare_…*): toca **Conectar**. El satélite mantiene sus datos móviles. Debe decir *Conectado al teléfono principal*.
3. El satélite sigue solo las bandas y pulseras elegidas en el principal, en los mismos lados: no hay que elegir nada. Déjalo detrás de la línea de fondo con la app abierta.

- **Cambio de lado**: no hay que hacer nada. Cada banda y cada pulsera sigue con el teléfono al que está conectada mientras la señal aguante; luego la toma el más cercano (unos segundos). En el principal aparece *Desde el segundo teléfono*; durante el paso una pulsera puede aparecer desconectada un momento.
- **Sin datos perdidos**: pulso, teclas y golpes del satélite llevan el **ID del partido** y un número correlativo, y el satélite los guarda hasta que el principal los confirma. Si el Wi-Fi entre los teléfonos se corta, el pulso llega después y rellena los huecos del gráfico; los golpes cuentan si son del mismo partido. Una **tecla** de la pulsera que llega más de 5 segundos tarde no da el punto: pulsa otra vez. La misma tecla llegada por dos caminos cuenta una sola vez.
- Del principal a las pulseras pasan marcador, mensajes, *Identificar*, apagado y ajustes. Conviene cambiar los ajustes con la pulsera conectada al principal.
- **Marcador**: en el satélite **Mostrar el marcador** abre el marcador del partido a pantalla completa (como en 8.3), ya conectado al principal.
- Si el principal no puede crear la red (punto de acceso normal ya activo), usa el Wi-Fi en el que está: entonces los dos teléfonos deben estar en la misma red. Con Android 9 o anterior el satélite muestra nombre y contraseña de la red, para elegirla a mano en los ajustes de Wi-Fi.
- **Detener** cierra la conexión y la red; el satélite vuelve a sus bandas y pulseras.

## 7. Cómo se usa la app

1. **Nuevo partido** (opcional): **Tenis** o **Pádel** (app 2.5: mismas reglas, tiempos y cantos; al elegir Pádel se ponen solos el dobles y el punto de oro, que se pueden cambiar (en lugar del punto de oro, *Modo y reglas* ofrece también el *Star Point* de la FIP: como máximo dos ventajas y luego un punto decisivo), y el resumen dice *Pádel*), club, pista, individual/dobles, nombres (en dobles, dos nombres por pareja). **Buscar la dirección de la pista** (desactivado por defecto) añade al resumen la dirección junto a las coordenadas: se la pide al servicio de geocodificación del teléfono, que con los servicios de Google envía las coordenadas a Google. **Siguiente**. Con *Pádel* aparece también **Torneo Americano o Mexicano**: de 4 a 16 jugadores y de 1 a 4 pistas, partidos a puntos (16, 24 o 32: los puntos de las dos parejas suman el total) o a tiempo. En el *Americano* las parejas cambian en cada ronda para que cada uno juegue con y contra el mayor número de personas; en el *Mexicano*, desde la segunda ronda, se juega por clasificación (1.º y 4.º contra 2.º y 3.º en la misma pista). En cada ronda se escribe el resultado de cada pista (a puntos basta un número, el otro se completa) o se cuenta con *Contar punto a punto*, con la voz y, a tiempo, el cronómetro; con más jugadores que plazas en pista se descansa por turnos. **Ronda siguiente** empieza cuando están todos los resultados; la **Clasificación** cuenta los puntos de cada uno, luego las victorias y la diferencia de puntos, y se comparte como texto.
2. **Modo y reglas**:
   - *Juez*, *Pulseras* o *Mandos* (6.3). Con las pulseras son obligatorios el Bluetooth activado, el permiso de ubicación y la ubicación activada: las filas de **Requisitos** se actualizan en tiempo real (aunque desactives el Bluetooth o la ubicación desde los ajustes rápidos) y se ponen en rojo, con el botón para arreglarlo.
   - La **búsqueda es automática y continua** mientras la página está abierta: enciende las pulseras y ocupan solas los puestos libres (primero Jugador 1, luego Jugador 2). **Identificar** hace parpadear esa pulsera en el color del jugador (amarillo o rojo) con unos pitidos, así ves enseguida cuál tienes en la mano; **Intercambiar J1 ↔ J2** las invierte sin desconectarlas. En el menú desplegable siempre puedes elegir otra o *Ninguna* (la que quitas a mano no la vuelve a poner la búsqueda). Una pulsera no puede estar asignada a dos jugadores.
   - **Apagar las pulseras al final del partido y al salir** (activado de serie): al confirmar *Partido terminado* y con *Salir*, las pulseras se apagan en lugar de esperar a la inactividad.
   - En modo juez, si pulsas **Siguiente** sin ubicación aparece la invitación a activarla (si no, el lugar no aparecerá en el resumen).
   - Formato (*3 sets*, *5 sets*, *2 sets + súper tie-break a 10*, *pro set* a 8 o 10 juegos, *solo súper tie-break a 10*), juegos por set (6, o 4 en sets cortos con tie-break en 4-4), tie-break en los sets sí/no (no: el set sigue hasta que alguien saque 2 juegos de ventaja), *Sin ventaja (punto decisivo)*. El idioma y la voz están en *Más › Idioma y voz* (4.1).
   - **Sorteo**: la moneda gira e indica quién gana; indicas quién saca y los lados de la pista **vistos desde la silla del juez** (esquema de la pista con **Cambiar lados**). En dobles eliges también quién saca primero en cada pareja.
3. **INICIO DEL PARTIDO** parpadea: pulsa el botón, KEY1 de una pulsera o un mando. La voz dice *«Primer set» · «al servicio [nombre]» · «jueguen»* con 1 segundo entre frases; el **Match Time** empieza con «jueguen».
4. **Partido**: arriba a la izquierda, el tiempo de partido; a la derecha, la cuenta atrás (**Shot Clock** 25 s, **Changeover Time**, **Set Break Time**; en rojo en los últimos 5 s). El recuadro naranja se enciende 5 s para *cambio de lado, tie-break, bola de set, bola de partido, bola de break…*. Los dos botones cuadrados (amarillo = Jugador 1, rojo = Jugador 2) están del lado en que se encuentran realmente los jugadores y se intercambian en cada cambio de lado; debajo de quien saca aparece **On Serve**. Abajo: *Anular punto*, *Suspender/Reanudar*, audio (icono del altavoz, tachado = apagado), *Nuevo partido*, **Salir**. En modo pulseras, debajo de los tiempos están **J1**/**J2** con batería y autonomía: al tocarlos se abren los ajustes de la pulsera. Un doble toque en *Anular punto* quita un solo punto (un segundo toque en menos de 1 s no cuenta); justo después de un cambio de página se ignora el segundo toque de un doble toque, para que no caiga en un botón de la página nueva. El **candado** de abajo bloquea la pantalla (por ejemplo con el teléfono en el bolsillo o en el banco): los toques no hacen nada, las pulseras y los mandos sí; se desbloquea manteniendo pulsado el candado.
5. En el último punto aparece la ventana **Partido terminado / Anular el último punto**. «Partido terminado» se confirma **solo desde el teléfono**. Durante medio segundo después de aparecer, sus botones ignoran los toques: un doble toque en el último punto no anula nada.
6. **Resumen**: ganador, nombres, resultado set por set con los puntos del tie-break, duración, hora de inicio y de fin, fecha, club, pista, lugar, formato, puntos y juegos ganados. Botones **Guardar archivos** (nombre de archivo + carpeta a elegir + formatos .txt/.json/.png y **PDF completo** con resultado, estadísticas del partido y de cada set, historial y fotos). **Compartir** pregunta si enviar la imagen con texto (para WhatsApp y las redes) o el PDF completo. En el Resumen, **Fotos del partido** guarda hasta 10 fotos de recuerdo (de la galería o hechas en el momento): se quedan en el teléfono, van al PDF y pasan al otro teléfono con la transferencia, nunca a la imagen para las redes, **Compartir** (imagen de 1080×1350 + texto para WhatsApp/Instagram/…), **Nuevo partido**, **Salir**. Debajo de las fotos, **Notas** admite un texto libre (hasta 2000 caracteres): se guarda solo, va al PDF, al texto compartido y a los archivos .txt y .json, y pasa al otro teléfono con la transferencia; no va a la imagen para las redes. El partido terminado entra solo en el **Historial** (pestaña **Progresos**): la lista de partidos, para volver a abrirlos con su resumen o eliminarlos, y la pestaña **Jugadores** con victorias, derrotas y cara a cara de quien tiene el nombre escrito en *Nuevo partido* (mayúsculas y espacios no cuentan; en dobles cuenta cada jugador). **Importar** añade los partidos guardados como .json con *Guardar archivos* en otro teléfono: los terminados van al historial y los no terminados a los partidos suspendidos para reanudar. **Transferir** (arriba a la derecha en el Historial) pasa el historial y los partidos suspendidos a otro teléfono con un código QR: los dos teléfonos deben estar en la misma red Wi-Fi o uno en el punto de acceso del otro; en el que los tiene toca *Enviar* y deja abierta la ventana con el código, en el otro *Recibir* y escanea el código (hace falta el permiso de la cámara). Así también puedes seguir un partido en curso en otro teléfono: suspéndelo, transfiérelo y reanúdalo. Los partidos se suman a los que el teléfono ya tiene. **Fotos de los jugadores**: toca el círculo junto al nombre en *Nuevo partido* (o en el detalle del jugador en el Historial) y elige una foto; se recorta cuadrada y se encuentra por el nombre. Aparece en el Historial, en el resumen y en el marcador de la TV, se queda solo en este teléfono (pasa al otro con la transferencia) y no va en la imagen compartida. Tocando otra vez el círculo la cambias o la quitas. Si la app se cierra mientras estás en el resumen (Android que la cierra en segundo plano, una actualización, un reinicio del teléfono), lo vuelves a encontrar al abrirla; no si lo has dejado tú con *Nuevo partido*, *Salir* o quitando la app de las recientes. La carpeta predeterminada es **Documents/Tennis Score Manager** (desde Android 10; en Android 8 y 9 la carpeta de la app): se abre con cualquier gestor de archivos y se queda aunque desinstales la app. Al guardar dos veces con el mismo nombre, el segundo pasa a ser *nombre (1)*; en la imagen los textos largos (nombres, dirección) se ajustan al ancho.

   El resumen tiene tres pestañas. **Resumen**: los datos de arriba y, con las pulseras 2.4, golpes, velocidad de muñeca y golpes por punto (6.4). **Estadísticas**, del partido o de un set: los dos jugadores comparados con barras que salen del centro (coloreada la de quien va por delante) para puntos y juegos ganados con el saque y al resto, bolas de break salvadas y ganadas, total de puntos y juegos, puntos seguidos, últimos 10 puntos, bolas de partido salvadas; con las pulseras 2.4 también golpes por tipo y velocidad media de muñeca. **Punto a punto**: set por set, cada juego con la pelota de quien saca, *SAQUE PERDIDO* en las roturas, el marcador tras cada punto con **BB** (bola de break), **BS** (bola de set) y **BP** (bola de partido) y el desenlace (*JUEGO*, *ROTURA*, *SET*, *PARTIDO*). Aces, dobles faltas, primeros saques, golpes ganadores y errores aparecen si activas las **estadísticas detalladas** en *Modo y reglas*: en lugar de los dos botones grandes salen *Ace* y *Falta* (la segunda vez pasa a *Doble falta*) y, para cada jugador, *Ganador*, *Error* (el punto va al otro) y *Punto*; *Anular punto* también quita una falta. Está pensado para quien anota desde fuera de la pista. Después de *Error* aparece 6 segundos *No forzado / Forzado*; con pulseras o mandos, después de cada punto aparece *¿Cómo terminó el punto?* (Ace, Ganador, Error): responder es opcional y el marcador no cambia. En el Historial, el detalle de un jugador muestra la **evolución**: puntos ganados partido a partido y las medias de aces, dobles faltas, primeros saques, ganadores y errores. Las estadísticas de cada partido cuentan también bolas de juego, de set y de partido convertidas, juegos en blanco, juegos seguidos, sets ganados tras ir un break abajo y, en la tarjeta **Tiempos**, la duración de cada set y el juego más largo.

   **Revancha** (al final del resumen): propone el mismo día de la semana y la misma hora, la semana siguiente, y el club del partido; se cambian fecha, hora y lugar y se añade una nota. **Mandar la invitación** abre el menú de compartir (WhatsApp, SMS, correo…) con el mensaje listo: quién contra quién, cuándo, dónde y cómo acabó la última vez. **Añadir al calendario** crea el evento en la app del calendario, **Preparar el partido** devuelve a *Nuevo partido* jugadores, club, pista y reglas (el torneo no). Sin cuentas: la invitación pasa por los canales que ya usas.

**Para salir**: **Salir** (durante el partido pide confirmación, y también está en el resumen) cierra la app de verdad; lo mismo ocurre si la quitas de las aplicaciones recientes. Al volver a abrirla empieza desde la pestaña **Jugar**.

**Guardado**: el partido se guarda solo en cada punto. *Suspender* detiene los tiempos; si sales, si el teléfono se apaga o si la app se cierra, el partido aparece en **Reanudar** (pestaña **Jugar**, o *Reanudar partido suspendido* en la pantalla INICIO DEL PARTIDO) y continúa con *Reanudar*. *Anular punto* recalcula todo desde el principio, así que funciona también después del final de un juego, de un set o del partido. La papelera de un partido suspendido pide confirmación. Si el partido se inició desde una pulsera con la pantalla bloqueada (Android no da entonces la ubicación), el lugar se toma en cuanto vuelves a abrir la app.

### 7.1 Entrenamiento con la banda

Para entrenar con la banda de pecho sin partido: **Entrenar** › **Entrenamiento con la banda**. Se usa **una banda**, la del Jugador 1, con edad, peso y sexo para calorías y zonas; se elige ahí mismo (con el interruptor *Bandas de pecho*, Bluetooth y permisos si faltan).

1. **Iniciar**: en grande el pulso en directo, debajo el tiempo y la vuelta en curso.
2. **Vuelta** cierra un tramo del entrenamiento y abre otro (como el botón de vuelta de un cronómetro): un sprint, un ejercicio, una vuelta a la pista. **Pausa / Reanudar** detiene el tiempo; las pausas no cuentan.
3. **Terminar**: la app registra **un minuto más de recuperación** (quédate quieto) para medir cuánto baja el pulso; **Guardar ya** lo salta.

- **Resumen**: duración, pulso medio y máximo, calorías, **VFC (RMSSD)** a partir de los intervalos entre latidos que manda la banda (hacen falta unos treinta latidos limpios; compárala con tus entrenamientos parecidos, no con los de otros), **recuperación en 1 minuto** (cuanto más baja, mejor), gráfico, zonas cardíacas y, por cada vuelta, duración, media, máximo y recuperación.
- **CSV** (segundo a segundo, con la vuelta y el minuto de recuperación) y **PDF** de una página con gráfico, tarta de zonas y vueltas, para compartir.
- Los entrenamientos quedan en la misma pantalla, aparte de los partidos; se reabren o se eliminan. El pulso llega también desde el segundo teléfono (6.6) y la grabación sigue con la pantalla apagada.

### 7.2 Progresos

**Progresos** › **Rating TSM · Progresos**: un documento para ver si mejoras, **separado** para entrenamientos y partidos.

- **Entrenamientos**: duración, pulso medio y máximo, calorías, VFC, recuperación.
- **Partidos** de un jugador del historial (elegido de la lista): partidos ganados, puntos ganados, aces, dobles faltas, primeros saques dentro, ganadores, errores no forzados. Los aces y los demás valores detallados solo existen en los partidos jugados con estadísticas detalladas; son totales por partido.
- Para cada dato la media de la **primera mitad** del periodo y la de la **segunda**: ▲ verde si mejora, ▼ rojo si empeora (menos dobles faltas o errores es una mejora), = si se mantiene (menos del 5 % de diferencia) o si no se puede decir (pulso). Hacen falta al menos 2 entrenamientos o partidos.
- **Qué comparar**: los **Filtros** eligen el periodo (todo, 30 días, 3 meses, 1 año), los **días de la semana** (por ejemplo solo los miércoles, también varios) y, para los partidos, **torneo**, **club** y **rival** del historial; debajo, la lista **Para comparar** para marcar (*Todos* / *Ninguno*). Comparación, PDF e impresión usan solo los marcados.
- **Rating TSM**: en *Partidos*, encima de los filtros, el rating del jugador (todos empiezan en 1500) con los partidos contados, la variación del último y la evolución, y la **Clasificación TSM** de todos los jugadores del historial. Es un Elo calculado en el teléfono: sube cuando ganas, más contra quien tiene un rating más alto y con un resultado claro; en dobles cuenta la media de la pareja; tenis y pádel tienen clasificaciones separadas. En los 5 primeros partidos es *provisional* y se mueve más. En el resumen de cada partido la tarjeta **Rating TSM** muestra cuánto ganó o perdió cada uno; con **Amistoso** el partido sigue en el historial pero no cuenta.
- **PDF** (con la evolución de cada dato y la lista completa, en varias páginas si hace falta) para compartir, o **Imprimir** con el servicio de impresión de Android (también *Guardar como PDF*).

### 7.3 Vídeos: teléfono-cámara, mejores jugadas y repetición

Un segundo teléfono en un trípode graba el partido y pone en el vídeo el marcador del árbitro.

1. **Árbitro**: Más › **Dispositivos** › **Segundo teléfono** › **Usar un segundo teléfono** (QR, como en 6.6).
2. **Cámara**: Más › **Dispositivos** › **Segundo teléfono** › **Hacer de cámara**, escanea el QR y luego **Abrir la cámara** (permisos de cámara y micrófono; el teléfono debe grabar en 1080p). Con *Marcador conectado* el gráfico del marcador está en el vídeo (**Gráfico** lo enciende o lo apaga); sin árbitro graba solo el vídeo.
   - **Grabar / Parar**: el vídeo va a la galería (*Películas/Tennis Score Manager*) con capítulos, uno por punto.
   - **Directo**: emisión RTMP en YouTube, Twitch, Facebook o un servidor tuyo; servidor y clave de la emisión en *Directo: servidor*.
   - **Encuadrar** (asistente de encuadre): escribe a cuántos metros detrás de la línea de fondo y a qué altura has puesto el teléfono y en qué pista juegas; la app da la inclinación hacia abajo que mantiene en el vídeo las dos líneas de fondo y al jugador lejano, y dice si la pista cabe a lo ancho. A menudo el límite es el ancho: desde la valla (unos 6 m) el objetivo principal corta las esquinas cercanas, el **gran angular** no (**Usar el objetivo…**, o el botón **Objetivo**, con la grabación parada). Con **Mostrar el nivel** la vista previa muestra el horizonte e indicaciones (*apunta 2° más abajo*, *gira en sentido horario*…), en verde cuando está bien, con una vibración cuando el teléfono está en posición. El nivel también se ve en la ventana **Cámara** del teléfono del árbitro. Los sensores no saben izquierda y derecha: comprueba a ojo que la red esté en el centro.
   - **Silueta** (silueta de la pista): la vista previa muestra las líneas de la pista como las ve el objetivo, y siguen al teléfono cuando lo inclinas. Mueve los controles (**Distancia**, **Altura**, **Dirección**, **Desplazamiento lateral** y, si hace falta, **Campo de visión** y **Corrección de inclinación**) hasta que coincidan con las reales: así se ajusta también el centrado, y la inclinación aconsejada se vuelve precisa. El ajuste se recuerda para la pista del partido (*Club · Pista* del árbitro) y se recupera la próxima vez. Silueta y controles también están en la ventana **Cámara** del árbitro. Cuanto más alto esté el teléfono, mejor se ve la mitad lejana de la pista: desde 2,5 m queda aplastada justo encima de la red.
3. **Cámara en alto, manejada por el árbitro**: en el teléfono del árbitro, **Cámara** (junto a *Repetición* durante el partido, o *Encuadre y mandos de la cámara* en la Más › **Dispositivos** › **Segundo teléfono**, útil para colocarla antes de empezar) muestra el encuadre del teléfono-cámara tal como queda en el vídeo, con el gráfico, y **Grabar / Parar**, **Directo**, **Gráfico** y el **zoom**. También se ven la duración de la grabación, la batería y el espacio libre (en naranja si son bajos). La imagen es pequeña y a saltos (unos 3 fotogramas por segundo) y solo se envía mientras se mira; la clave del directo se pone en el teléfono-cámara.

- **Vídeo** › **Tus vídeos**: los vídeos grabados, para verlos punto a punto, juego a juego o set a set. **Capítulos de YouTube** prepara los capítulos para pegar en la descripción. **Importar vídeo** toma un vídeo hecho con otra cámara (el archivo se queda donde está): los puntos se marcan mirándolo, tocando al final de cada punto quién lo ganó y, si quieres, cómo terminó (*Deshacer el último* quita el último); formato y reglas son los de *Modo y reglas*.
- **Filtros de entrenador** (icono del filtro): solo los puntos de un jugador, ganados o perdidos, al saque o al resto, con el primer o el segundo saque, según cómo terminaron, en bolas de break o en bolas de set y de partido. **Reproducir** los pone en fila.
- **Mejores jugadas**: montaje automático de los mejores puntos (aces, golpes ganadores, bolas de break, de set y de partido, peloteos largos con las pulseras) en el orden de prioridad elegido con las flechas, de como máximo 1, 3, 5, 10 o 15 minutos u **Otra duración** de hasta 90. Si no cabe todo, primero se acortan los trozos y luego se quedan fuera los puntos menos importantes. El resultado va a la galería.
  - **Estadísticas al final del vídeo**: 2 o 3 pantallas de 10 segundos como en las retransmisiones de los torneos: saque y bolas de break; puntos, errores y peloteos cortos y largos; pulso, si se llevaba la banda. El partido es el del historial jugado mientras se grababa, o el de los puntos marcados en un vídeo importado. A los lados, la foto del jugador; sin foto, un retrato estilizado de hombre o de mujer (elección **Hombre / Mujer** en *Nuevo partido*) (sin elección, el del hombre; vale en toda la app, también en *Nuevo partido*, el historial, el resumen, el PDF y el marcador de la TV); debajo, el porcentaje de puntos ganados. Arriba, torneo, categoría y ronda (*Más detalles* en *Nuevo partido*), o club y pista.
  - **Música** bajo las estadísticas, con fundido de entrada y de salida: *Nowhere Land* de Kevin MacLeod (incompetech.com, licencia CC BY 4.0: si publicas el vídeo, cítalo), o una pista tuya con **Elegir una pista** (se usa el principio; usa solo música que tengas derecho a poner en tus vídeos).
  - Al pie de cada pantalla de estadísticas aparece **Creado con Tennis Score Manager** con la dirección del sitio.
- **Repetición**: en el teléfono del árbitro, con la cámara conectada, **Repetición** pide a la cámara el punto dudoso (*Último punto*, *Punto anterior* o *Desde el último punto marcado*) y lo muestra, con pausa y fotograma a fotograma. **También en el marcador** lo enseña también en el marcador de la TV.

### 7.4 Raquetas: raqueta virtual

**Progresos** (o **Más**) › **Mis raquetas**: cada raqueta física tiene su ficha (dos raquetas del mismo modelo son dos fichas: cuerdas, peso y horas cambian).

- **Ficha**: apodo (A, B…), marca, modelo, peso, balance, cabeza y patrón de cordaje, escritos a mano o tomados **del catálogo** (datos del fabricante, para corregir si has medido la tuya). *Retirada*: ya no se propone, pero sigue en las comparaciones.
- **Encordados**: tipo (monofilamento, multifilamento, sintética, tripa natural, híbrido), marca y modelo (también de las cuerdas comunes), calibre, tensión de verticales y horizontales, fecha y notas. *Nuevo encordado* parte del montado. Para cada uno la app suma las **horas de juego** de los partidos y entrenamientos asociados y avisa al pasar el **umbral de revisión** elegido (20 horas por defecto): solo con el tiempo no puede saber cuánta tensión queda.
- **Antes de jugar**: en *Nuevo partido*, bajo la clasificación de cada jugador, se elige su raqueta; en el entrenamiento, encima de la banda. El partido (puntos, golpes de las pulseras, pulso) y el entrenamiento se guardan con una **copia** de la raqueta tal como estaba ese día, cuerdas y tensión incluidas: volver a encordarla después no cambia las sesiones pasadas.
- **En el resumen** del partido (pestaña *Resumen*) y del entrenamiento se ve la raqueta usada, se puede asociar también después (se toma el encordado montado ese día) y se valoran las **sensaciones** de 1 a 5: control, confort, potencia y cansancio.
- **Comparación**: *por raqueta* o *por encordado* (misma raqueta con tensiones o cuerdas distintas), se eligen las configuraciones A y B. Barras lado a lado para sesiones, horas, partidos ganados, puntos ganados, primeros saques dentro, puntos ganados con el primero y el segundo, aces, dobles faltas, golpes ganadores y errores no forzados por partido, velocidad media de la muñeca y golpes de las pulseras, pulso medio y sensaciones medias. El mejor valor va en negrita, pero la app no elige ganadora: también cuentan rival, superficie, condiciones y forma.

### 7.5 Entrenamiento de golpes: saque, derecha, revés y volea

Un teléfono en un trípode graba a quien saca; la pulsera en la muñeca reconoce el saque y con los botones se dice cómo ha ido. Basta un teléfono, sin árbitro.

1. **Entrenar** › **Entrenamiento de golpes**, elige el golpe (**Saque**, **Derecha**, **Revés**, **Volea**) › **Empezar** (la pulsera debe estar conectada como en 6.1; sin ella, se marca con los botones de la pantalla).
2. **Dónde poner el teléfono** (la primera vez se abre solo, luego con el botón *¿Dónde pongo el teléfono?*):
   - **al lado de quien saca**, en el lado del brazo que golpea (a la derecha para un diestro), a **3-4 m**, a la altura de la línea de fondo o medio metro detrás;
   - objetivo a **1-1,2 m** del suelo, teléfono **en horizontal** y recto, apuntando a quien saca;
   - en el encuadre debe entrar **toda la persona, de los pies a la raqueta arriba** (unos 2,7 m): si la cabeza de la raqueta se sale, aleja el teléfono o cambia al **gran angular** (botón *Objetivo*);
   - para ver también adónde va la pelota, como alternativa: 3-4 m **detrás** de quien saca, un poco de lado, a 1,5-2 m de altura;
   - nunca delante de quien saca ni en la trayectoria de la pelota.
3. Elige el **lado** (derecho/izquierdo), **primero o segundo** y, si quieres, el **objetivo** (a la T, al cuerpo, abierto): valen para los saques siguientes.
4. Saca y pulsa **KEY1 = dentro** o **KEY2 = fuera**; **KEY1 mantenido** anula el último. La voz dice el recuento (*Dentro. 8 de 12*) y la pulsera lo muestra. Si la pulsera reconoció el saque, se usa ese instante; si no, unos segundos antes de pulsar. Por eso al empezar la app **activa sola el reconocimiento de golpes** en la pulsera (firmware 2.4; no hace falta calibrar, solo cuenta el instante) y al final lo deja como estaba; *Reconocimiento de golpes activado* en pantalla lo confirma. Con un firmware más antiguo el instante se estima por la tecla.
   - **La pantalla**: arriba una sola línea (dentro sobre intentos, el último golpe y tres iconos: pulsera, reconocimiento de golpes, memoria con el número de clips y el espacio); las explicaciones aparecen unos segundos al principio y al tocar **i**, para dejar libre el encuadre. La cámara guarda en **memoria** los dos últimos minutos y graba **solo los golpes marcados** (unos segundos antes y después): nada de vídeos largos que recortar.
   - **Pausa** apaga cámara y memoria (batería, calor) mientras recoges las bolas: las teclas de la pulsera no marcan nada, KEY1 o **Seguir** vuelven a empezar. Tras **5 minutos sin golpes** se pone en pausa sola (lo dice la voz) y entonces también sigue con el primer golpe reconocido.
5. De cada saque se guarda un **clip** de unos segundos alrededor del golpe. **Fin** muestra el resumen: dentro sobre intentos, por lado, primero y segundo, objetivo y, si la pulsera reconoció los saques, la rotación media de la muñeca en los saques dentro y en los de fuera. **Ver todos / solo los buenos / solo los fuera** abre los clips en la repetición (pausa, cámara lenta, fotograma a fotograma).
6. Debajo de **Empezar** están las 3 últimas sesiones **del golpe elegido** (los botones de los golpes muestran cuántas sesiones tiene cada uno, p. ej. *Revés · 4*) y la línea de **golpes dentro, sesión tras sesión**. **Todas las sesiones** abre las del golpe elegido, por mes, con el espacio de los vídeos y si ya están analizadas. Toca una sesión para el resumen y los clips; **mantenla pulsada** para seleccionarla (el icono de arriba las selecciona todas) y luego **Borrar** (sesión y vídeos) o **Solo los vídeos** (libera espacio: quedan los recuentos, las medidas del análisis y las imágenes de los momentos clave). **Progresos** muestra la evolución de cada golpe.
   - **Dónde están los vídeos**: en **Documentos/Tennis Score Manager/Entrenamiento de golpes**, con las carpetas **Saque**, **Derecha**, **Revés** y **Volea** (creadas al arrancar) y dentro una carpeta por sesión (fecha y hora) y una por golpe, con `video.mp4` (se abre y se comparte desde cualquier app), el análisis y las tres imágenes. Se ven con cualquier gestor de archivos y se quedan aunque desinstales la app (guárdalos como archivo: una app reinstalada no recupera sus sesiones). En Android 8-10 la misma estructura está en la carpeta de la app. Los clips de las sesiones anteriores a la 2.5 se mueven allí solos en el primer arranque.
7. **Análisis del movimiento** (desde la sesión guardada): **Analizar los saques** encuentra la posición del cuerpo en los clips (modelo MoveNet: la primera vez la app ofrece descargarlo, 7 MB, desde la página del proyecto en GitHub; después todo se queda en el teléfono, sin internet, unos segundos por saque; se puede eliminar al final de la pantalla. No está disponible en dispositivos con procesador x86) y mide tres momentos: **armado** (brazo del lanzamiento arriba), **rodillas** más flexionadas e **impacto**. Para cada saque: las tres imágenes con el esqueleto dibujado y las medidas (rodilla más flexionada, codo en el impacto, altura del impacto respecto a la estatura, brazo del lanzamiento, hombros en el armado, tiempo del armado al impacto); arriba, **dentro y fuera** comparados. Son estimaciones a partir de un vídeo de lado: sirven sobre todo para comparar tus saques entre sí. El cuerpo se reconoce bien con toda la persona en el encuadre, de lado y con buena luz; raqueta y pelota no se miden.
   Toca un saque para abrirlo: **Ver el vídeo con el esqueleto** abre el clip en la repetición con el cuerpo dibujado encima, a cámara lenta, con botones para saltar a **Armado**, **Rodillas** e **Impacto** y otro para quitar o poner el esqueleto; dos dedos amplían vídeo y esqueleto juntos (hasta 6×), doble toque para volver. Las tres imágenes también se tocan para verlas a pantalla completa y ampliarlas. Con **varias personas** en el encuadre la app sigue a quien saca (quien más levanta el brazo) y tapa a las demás; lo indica debajo del saque: compruébalo en las imágenes. **Repetir el análisis** empieza de nuevo (los clips se quedan), útil tras una actualización de la app.

**Derecha, revés y volea** funcionan igual (KEY1 dentro, KEY2 fuera, un clip por golpe, resumen y análisis), con estas diferencias:
- **Dónde poner el teléfono**: al lado de quien golpea, perpendicular a la dirección de los golpes, a 4-5 m y fuera del pasillo, a la altura del punto de impacto; objetivo a 1 m; todo el swing en el encuadre (la muñeca se mueve unos 2 m adelante y atrás). Para la volea: al lado de quien juega en la red, a 3-4 m, a su altura. Siempre del mismo lado toda la sesión, y nunca en la trayectoria de la pelota.
- En lugar de lado, primero/segundo y objetivo se elige la **dirección** deseada (**cruzado**, **al centro**, **paralelo**) y, para la volea, si es **de derecha** o **de revés**; el resumen los cuenta por separado.
- **Análisis del movimiento**: los tres momentos son **preparación** (donde la muñeca se para al final del armado), **impacto** (muñeca más rápida; alrededor la app vuelve a leer un fotograma cada 33 ms) y **final** (muñeca más alta tras el impacto; en la volea, donde la muñeca se para). Si hay varios golpes en la ventana, cuenta el que reconoció la pulsera; las muñecas intercambiadas por MoveNet (pasa de lado) se corrigen solas. Graba **de lado**: de frente el armado va en profundidad y no se ve. Las medidas (*—* si no se ven el codo o las piernas): rodilla más flexionada, codo en el impacto, altura del impacto, cuánto **delante del cuerpo** se golpea, **amplitud de la preparación**, altura del final (todas respecto a la estatura) y el tiempo de la preparación al impacto. El vídeo con el esqueleto tiene los botones **Preparación**, **Impacto** y **Final**.

### 7.6 Alumnos: la parte del entrenador

- **Entrenar › Alumnos** › **Nuevo alumno**: nombre (el mismo de los partidos: así foto, historial y rating aparecen solos) y mano.
- **Quién entrena**: en Entrenar, encima de *Empezar*, elige al alumno (o *Ningún alumno*). La sesión es suya: las últimas sesiones, *Todas las sesiones*, la evolución en Progresos y los números de los botones de los golpes son solo suyos, y los clips van a **Entrenamiento de golpes/<golpe>/<alumno>/<fecha y hora>**.
- **La ficha del alumno** (toca el nombre en *Alumnos*):
  - **Golpes**: para saque, derecha, revés y volea las sesiones, los golpes, la última vez y la línea de golpes dentro; al tocar un golpe se abren sus sesiones.
  - **Movimiento**: las medidas del análisis (6.4 y 7.5) de la última sesión analizada junto a la anterior, para ver qué cambia.
  - **Partidos**: victorias y derrotas, rating TSM y los últimos partidos del historial con su nombre.
  - **Notas del entrenador**: qué trabajar, objetivos; se guardan solas.
  - **Raqueta**: en la ficha (y en *Editar*) se elige su raqueta entre *Mis raquetas*: va con cada sesión tal como estaba ese día, cuerdas y tensión incluidas, y la ficha muestra el encordado montado. Una raqueta que no era de nadie pasa a ser suya, y se propone también en sus partidos.
  - **Entrenar ahora** lo elige y vuelve a Entrenar, listo para *Empezar*. **Quitar alumno** lo quita de la lista: sus sesiones se quedan, sin alumno. **Borrar alumno y sus datos** en cambio quita todo lo que el teléfono tiene de él: sesiones con vídeos y análisis, notas y foto (la raqueta se queda, sin dueño; los partidos del historial se quedan, porque también están los rivales).

## 8. Marcador en un televisor o monitor

El teléfono del juez hace de **pequeño servidor** en la red Wi-Fi: el marcador es una página web de estilo LED (cifras de 7 segmentos, amarillo contra rojo, juegos y sets en el centro, sets terminados y tiempo de partido abajo a la izquierda, **SAQUE: 25 SEG** abajo a la derecha) que se actualiza sola en cada punto. El monitor no tiene que ser «smart» y no hace falta la red del club: basta con el **punto de acceso** (hotspot) de uno de los dos teléfonos.

### 8.1 Las opciones posibles

| Cómo llega al monitor | Qué hace falta | Ventajas | Inconvenientes |
|---|---|---|---|
| **Segundo teléfono con salida de vídeo** + cable USB-C/HDMI, app TSM en *Usar como marcador* | un teléfono que saque vídeo por el USB-C (DisplayPort Alt Mode) | sin internet; el marcador ocupa todo el monitor en 16:9 y el teléfono queda libre | muchos teléfonos **no** sacan vídeo: en general sí los Galaxy S/Note/Tab S (con DeX: elige *Duplicar pantalla* o desactiva el inicio automático de DeX), y no casi todos los Galaxy A. Busca «DisplayPort» / «salida de vídeo» en la ficha técnica |
| **Chromecast** (o Google TV Streamer) en el monitor + cualquier teléfono con TSM en *Usar como marcador* y **Enviar** (*Enviar pantalla* hasta Android 14, Smart View en los Samsung) | un Chromecast configurado una vez con Google Home en la red del punto de acceso | sirve cualquier teléfono, sin cables largos | el Chromecast necesita **internet** (datos móviles en el punto de acceso); retraso de aproximadamente 1 s; se envía la pantalla del teléfono (en horizontal) |
| **Navegador** en cualquier aparato conectado al monitor (portátil, tableta, TV box, Fire TV Stick…) | escanear el QR o escribir la dirección | no hay que instalar ninguna app | la pantalla se apaga sola si no la configuras; hay que tocar **PANTALLA COMPLETA** cada vez que se abre |

**Por qué no por Bluetooth**: el teléfono del juez ya mantiene las dos pulseras por Bluetooth, donde cuentan los tiempos de los botones; el Wi-Fi va aparte, es más rápido y llega más lejos. **Por qué no directamente del teléfono del juez al Chromecast** (sin segundo teléfono): se puede hacer, pero hace falta una app «receptora» registrada en Google (Google Cast Developer Console, 5 $ de pago único) y publicada en un sitio https; es un posible paso futuro.

**Consejos de red**:
- El marcador envía poquísimos datos (un mensaje en cada punto y cada 5 segundos) y no consume tráfico de internet.
- Si el teléfono del juez hace de punto de acceso, en los ajustes del punto de acceso elige la banda de **5 GHz** si la hay: el Bluetooth de las pulseras trabaja a 2,4 GHz y así no se interfieren.
- Con el Chromecast conviene que el punto de acceso lo haga el teléfono del juez con los **datos móviles activados**; el teléfono-marcador y el Chromecast se conectan a ese punto de acceso.
- Sin Chromecast también vale al revés (punto de acceso en el teléfono-marcador, como en la idea original): la app del juez se mantiene conectada al Wi-Fi aunque no tenga internet.

### 8.2 En el teléfono del juez

1. Más › **Dispositivos** › **Marcador en TV** › activa **Marcador en TV o monitor**. En Android 13 y posteriores la app pide el permiso de notificaciones: hace falta para la notificación del servicio que mantiene activo el marcador.
2. Aparecen la **dirección** (p. ej. `192.168.43.1:8080`), el **QR** y cuántos marcadores hay conectados. Si pone *Sin red*, activa el punto de acceso o conéctate al del otro teléfono.
3. **Vista previa en este teléfono** abre el marcador en el navegador del propio teléfono.
4. **Aspecto del marcador**: color de cada jugador (8 colores), tiempo de partido, reloj de saque y descansos, sets terminados, mensajes (bola de break, bola de set, cambio de lado…), pelota junto a quien saca, segmentos apagados visibles, texto inferior (vacío = club y pista de *Nuevo partido*). Los cambios llegan al monitor al instante.
5. Durante el partido, arriba en el centro aparece **TV · 1** (marcadores conectados): al tocarlo se vuelven a ver la dirección y el QR.

Con el marcador activado, un servicio en primer plano (notificación *Partido en curso · marcador de TV activo*) mantiene vivo el servidor aunque la pantalla esté apagada, también en modo juez. El marcador es **de solo lectura**: desde él no se puede cambiar nada. Sigue activo también en el resumen y en las demás páginas mientras el marcador esté activado (notificación *Marcador de TV activo*), así el resultado final se queda en el monitor aunque el teléfono esté bloqueado.

Qué muestra, además del tanteo: *ESPERANDO EL PARTIDO* antes de empezar, *LISTOS PARA JUGAR* en la página INICIO DEL PARTIDO, **TIE-BREAK** / **SÚPER TIE-BREAK** en lugar de *VS*, *PARTIDO SUSPENDIDO* parpadeando, **GANADOR [nombre]** al final del partido con todos los sets; las ventajas se leen **AD** también en las cifras LED. Un partido ganado en el súper tie-break termina con 1-0 en juegos y 2-1 en sets, con [10-8] entre los sets terminados; los mensajes largos se achican para caber en la pantalla.

### 8.3 En el teléfono-marcador

1. **Jugar** › **Usar como marcador**.
2. El teléfono **busca solo** el teléfono del juez (anuncio en la red y escaneo del punto de acceso, unos segundos) y recuerda la última dirección. Si no lo encuentra: revisa el punto de acceso y *Marcador en TV*, luego **Buscar de nuevo**, o escribe la dirección que muestra el juez y pulsa **Conectar**. Lo encuentra aunque en este teléfono también esté activado *Marcador en TV o monitor*; si el Wi-Fi se conecta más tarde, vuelve a buscar solo.
3. El marcador pasa a pantalla completa, en horizontal, con la pantalla siempre encendida.
   - **Con el cable HDMI**: el marcador va al monitor en su formato; el teléfono muestra *El marcador está en el monitor externo* con el brillo al mínimo (**Mostrar también aquí** para verlo también en el teléfono). Al desconectar el cable vuelve al teléfono.
   - **Con el Chromecast**: abre los ajustes rápidos › **Enviar** (o **Enviar pantalla**) / **Smart View** › elige el Chromecast.
4. Si el teléfono del juez desaparece (fuera de alcance, app cerrada) aparece *CONEXIÓN PERDIDA - RECONECTANDO...* y a los 20 segundos lo vuelve a buscar solo, aunque haya cambiado de dirección. También tras un corte del punto de acceso se reconecta solo en cuanto vuelve la red. Nunca pasa al teléfono de otra pista: para cambiar de pista, sal de *Usar como marcador* y vuelve a buscar.
5. Para salir: **atrás dos veces**.

**Desde un navegador** (portátil, TV box): escanea el QR o escribe la dirección y toca **PANTALLA COMPLETA** (aparece al mover el ratón o al tocar la pantalla). Configura el apagado de la pantalla en *nunca*: en una página http el navegador no puede mantenerla encendida por sí solo. Tras un corte la página se reconecta sola; demasiados marcadores abiertos ya no bloquean uno nuevo (se cierra el más antiguo).


## 9. Reglas aplicadas (ITF) y decisiones acordadas

- **Juego**: 0-15-30-40, iguales, ventaja, juego. **Sin ventaja** (No-Ad): con 40-40, punto decisivo («iguales, punto decisivo»).
- **Set**: 6 juegos con 2 de diferencia (7-5); con **6-6, tie-break**.
- **Tie-break**: a 7 con 2 de diferencia; quien tiene el turno saca el 1.er punto y luego 2 puntos cada uno; cambio de lado **cada 6 puntos** y al final; quien sacó primero en el tie-break **resta** en el primer juego del set siguiente.
- **Match tie-break** (formato de 2 sets): con 1-1 se juega a 10 puntos con 2 de diferencia.
- **Cambio de lado** (regla 10 de la ITF): después del 1.º, 3.º, 5.º… juego de cada set. Al final de un set solo se cambia si el set ha tenido un número impar de juegos (6-3, 7-6); si no (6-4), se cambia después del primer juego del set siguiente. El tie-break cuenta como un juego.
- **Tiempos**: shot clock de 25 s entre puntos; changeover de 90 s; set break de 120 s al final de cada set. Además, según lo pedido (no es regla ITF): **30 s** para cambiarse de lado después del 1.er juego de cada set, en cada cambio de lado del tie-break y con 6-6; al terminar cualquier pausa arranca el shot clock.
- **Dobles**: rotación del saque A1-B1-A2-B2 durante todo el set, también en el tie-break; al principio de cada set la app pide el orden (se puede cambiar, como permite el reglamento).
- **Cantos**: el tanteo se canta desde quien saca («quince cero», «cero cuarenta», «quince iguales», «iguales», «ventaja Rossi»); al final de cada juego, «juego Rossi, Rossi gana tres juegos a dos» / «dos juegos iguales» + «cambio de lado» cuando toca cambiar; con 6-6, «juego Rossi, seis juegos iguales, tie-break»; en el tie-break se canta desde quien va ganando («tres uno Rossi», «seis iguales»); al final del set, «juego Rossi, Rossi gana un set a cero» / «un set iguales»; al final del partido, «juego, set y partido Rossi, seis cuatro, tres seis, siete cinco»; «corrección» + tanteo cuando se anula un punto.

**Decisiones tomadas respecto a la petición original, para seguir a la ITF:**
1. **Con 6-6 no se cambia de lado** (son 12 juegos, número par): la app hace la pausa de 30 s y dice «tie-break», pero no dice «cambio de lado» ni intercambia los botones. El primer cambio llega tras 6 puntos del tie-break.
2. El cambio al final del set depende del número de juegos del set (ver arriba); no se hace siempre.
3. Con 1-1, en el formato con súper tie-break, la voz añade «súper tie-break».

## 11. Problemas frecuentes

- **No se encuentra la pulsera**: ¿Bluetooth y ubicación activados (filas en verde)? ¿La pulsera está parpadeando VINCULANDO? (Si ya está conectada a otro teléfono, no se ve.) Si mientras tanto se ha apagado sola (30 s), vuelve a encenderla con un clic en el botón lateral.
- **La pulsera muestra NO ENVIADO**: el teléfono no ha confirmado la pulsación en 8 segundos (conexión perdida o app cerrada). Vuelve a pulsar cuando la pulsera se haya reconectado. Caso raro: si la pulsación había llegado y solo se perdió la confirmación, el punto cuenta dos veces; la voz canta el tanteo, corrígelo con *Anular punto*.
- **En el panel de ajustes aparece «El firmware de esta pulsera no tiene ajustes»**: esa pulsera todavía tiene el sketch antiguo; vuelve a cargarlo (capítulo 5).
- **La voz no habla o lee mal**: volumen multimedia, *Audio On*, voz del idioma elegido instalada (4); prueba otro motor u otra voz en *Audio y voz*.
- **Pantalla apagada durante el partido**: en modo pulseras, un servicio en primer plano (notificación «Partido en curso») mantiene activos el Bluetooth, la voz y los cronómetros; en modo juez y mandos, la pantalla se queda encendida (con los mandos debe hacerlo: 6.3).
- **El mando no hace nada**: ¿está asignado al jugador (*Más › Dispositivos* › *Mandos*, fila con su nombre)? La app tiene que estar abierta en el partido, con la pantalla encendida (6.3). Si la fila dice *No conectado*, pulsa una tecla para despertarlo o vuelve a emparejarlo en los ajustes de Bluetooth.
- **Los golpes se confunden o salen como no reconocidos**: lleva la pulsera como durante la calibración (misma muñeca, mismo sentido); si no, repite la calibración llevándola como en el partido (6.4).
- **La pulsera muestra MEMORIA LLENA**: no se ha podido guardar un ejemplo de calibración. Borra los ejemplos de un golpe (papelera) y repítelos.
- **El teléfono-marcador no encuentra al juez**: ¿están en la misma red? (uno de los dos hace de punto de acceso y el otro está conectado a él). ¿*Marcador en TV* está activado en el teléfono del juez? Prueba a escribir la dirección a mano. Algunos puntos de acceso aíslan entre sí los dispositivos conectados («aislamiento de clientes»): si es así, desactívalo.
- **El navegador del teléfono no abre la dirección** con los datos móviles activados: Android envía el tráfico por los datos móviles porque el punto de acceso no tiene internet. Usa la app en *Usar como marcador* (lo gestiona sola) o desactiva los datos móviles en ese teléfono.
- **Monitor negro con el cable**: ese teléfono no tiene salida de vídeo por el USB-C (8.1), o se ha iniciado Samsung DeX: elige *Duplicar pantalla*.
