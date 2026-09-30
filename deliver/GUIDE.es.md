# Tennis Score Manager — guía paso a paso

[Italiano](GUIDA.md) · [English](GUIDE.en.md) · [Français](GUIDE.fr.md) · [Deutsch](GUIDE.de.md) · **Español** · [Português](GUIDE.pt.md)

App Android (Kotlin + Jetpack Compose) para llevar el tanteo del tenis según las reglas ITF, con los cantos del juez de silla por voz **en seis idiomas** (italiano, inglés, francés, alemán, español y portugués: capítulo 4.1), dos pulseras **M5StickS3** conectadas por Bluetooth LE y un **marcador LED en un televisor o monitor** (capítulo 8).

## 0. Qué hay en el repositorio

| Ruta | Para qué sirve |
|---|---|
| `TennisScoreManager/` | El proyecto de Android Studio. |
| `firmware/TSM_Band/TSM_Band.ino` | El firmware de la pulsera. |
| `deliver/installa_tsm.sh` | Alternativa a clonar el repositorio: crea **todo** el proyecto Android y el sketch solo con bloques `cat << 'TSM_EOF'` (el jar del Gradle wrapper va en base64). |
| `deliver/GUIDE.es.md` | Esta guía (en español); el original en italiano es `deliver/GUIDA.md` y las demás traducciones son `deliver/GUIDE.en.md`, `.fr`, `.de`, `.pt`. |

Versiones usadas y verificadas: app **2.3** · firmware **2.2.1** · Gradle 8.14.3 · Android Gradle Plugin 8.13.2 · Kotlin 2.2.21 · Compose BOM 2025.12.00 · compileSdk/targetSdk 36 · minSdk 26 (Android 8.0). Firmware: M5Unified ≥ 0.2.12, NimBLE-Arduino ≥ 2.1, core ESP32 3.x.

---

## 1. Preparar el ordenador y descargar el proyecto

Puedes usar **Windows 10/11**, **Ubuntu** (22.04 o posterior) o **Fedora**; en otras distribuciones Linux los pasos son los de Ubuntu o los de Fedora, con su gestor de paquetes. En los comandos, `~` es tu carpeta personal: en Linux `/home/<usuario>`, en Windows `C:\Users\<usuario>`.

### 1.1 Programas que hay que instalar

| | Windows 10/11 (PowerShell) | Ubuntu | Fedora |
|---|---|---|---|
| **Git** (descargar y actualizar el proyecto) | `winget install --id Git.Git -e` | `sudo apt install git` | `sudo dnf install git` |
| **Android Studio** (la app) | `winget install --id Google.AndroidStudio -e` | `sudo snap install android-studio --classic` | archivo `.tar.gz` de developer.android.com/studio, descomprimido por ejemplo en `~/android-studio` |
| **Arduino IDE 2** (las pulseras) | `winget install --id ArduinoSA.IDE.stable -e` | Flatpak `cc.arduino.IDE2` (ver abajo) | `flatpak install flathub cc.arduino.IDE2` |
| **Teléfono conectado por cable** | driver USB del fabricante, si hace falta (ver abajo) | `sudo apt install android-sdk-platform-tools-common` | `sudo dnf install android-tools` |
| **Puerto serie de la pulsera** | nada que hacer | `sudo usermod -aG dialout $USER` | `sudo usermod -aG dialout $USER` |

- **Windows**: `winget` ya viene incluido en Windows 10/11 actualizados y se usa desde *PowerShell* o *Terminal*. También se pueden descargar los instaladores normales de git-scm.com, developer.android.com/studio y arduino.cc/en/software.
- **Ubuntu, Arduino IDE**: si todavía no tienes Flatpak: `sudo apt install flatpak`, luego `flatpak remote-add --if-not-exists flathub https://dl.flathub.org/repo/flathub.flatpakrepo` y `flatpak install flathub cc.arduino.IDE2`; cierra la sesión y vuelve a entrar para que aparezca en el menú. La AppImage de arduino.cc también funciona, pero en las Ubuntu recientes necesita paquetes y opciones adicionales: Flatpak es más sencillo.
- **Linux, Android Studio desde el archivo**: se inicia con `bin/studio.sh` dentro de la carpeta descomprimida (p. ej. `~/android-studio/bin/studio.sh`). En Ubuntu también sirve el snap; en Fedora existe además el Flatpak `com.google.AndroidStudio`, pero el archivo oficial da menos problemas con el teléfono conectado.
- **Linux, grupo `dialout`** (puerto serie) y paquetes para el teléfono (reglas udev): después de instalarlos **cierra la sesión y vuelve a entrar** (o reinicia); si no, el puerto y el teléfono siguen sin estar accesibles.
- **Windows, driver del teléfono**: muchos teléfonos funcionan a la primera. Si Android Studio no ve el teléfono, instala el driver del fabricante: *Samsung Android USB Driver* (del sitio Samsung Developer) para los Samsung, *Google USB Driver* (Android Studio › **Tools › SDK Manager › SDK Tools**) para los Pixel. La pulsera no necesita driver: aparece como puerto `COM3`, `COM4`…
- La primera vez que se abre, Android Studio lanza un asistente: elige **Standard** y deja que descargue el SDK de Android (hace falta internet, varios GB).

### 1.2 Descargar el proyecto

El repositorio de GitHub es **privado**: hace falta una cuenta de GitHub a la que el propietario haya dado acceso.

**A. Con git (recomendado: después se actualiza con `git pull`)**

Linux (Ubuntu, Fedora), en la terminal:

```bash
mkdir -p ~/AndroidStudioProjects
cd ~/AndroidStudioProjects
git clone https://github.com/steve-linux/Tennis-Score-Manager.git
```

Windows, en PowerShell:

```powershell
mkdir -Force $HOME\AndroidStudioProjects
cd $HOME\AndroidStudioProjects
git clone https://github.com/steve-linux/Tennis-Score-Manager.git
```

- **Acceso**: en Windows, git abre por sí solo la ventana de inicio de sesión de GitHub. En Linux, GitHub no acepta la contraseña en `git clone`: lo más sencillo es GitHub CLI (`sudo apt install gh` o `sudo dnf install gh`), luego `gh auth login` y, en la carpeta `~/AndroidStudioProjects`, `gh repo clone steve-linux/Tennis-Score-Manager`.
- El repositorio queda en `~/AndroidStudioProjects/Tennis-Score-Manager`. El proyecto que hay que abrir en Android Studio es su subcarpeta **`TennisScoreManager`**; el sketch de la pulsera está en `firmware/TSM_Band/`.
- Para actualizar: `git pull` dentro de `Tennis-Score-Manager`.

**B. Sin git**: en la página de GitHub del proyecto (con la sesión iniciada) **Code › Download ZIP**; luego descomprime el ZIP, por ejemplo en `~/AndroidStudioProjects`. Para una versión nueva se vuelve a descargar el ZIP.

**C. Con el script** `deliver/installa_tsm.sh`, que contiene todo el proyecto en un único archivo de texto (bloques `cat << 'TSM_EOF'`). Se ejecuta en Linux o, en Windows, en *Git Bash* (viene con Git):

```bash
bash installa_tsm.sh
```

- El proyecto va a `~/AndroidStudioProjects/TennisScoreManager` y el sketch a `~/Arduino/TSM_Band/TSM_Band.ino`.
- Si la carpeta del proyecto ya existe, se **mueve** a `TennisScoreManager.backup-AAAAMMDD-hhmmss` (los layouts XML y las clases antiguas harían fallar la nueva compilación).
- Para usar otras carpetas: `bash installa_tsm.sh /ruta/proyecto /ruta/sketch`.

## 2. Abrir y compilar en Android Studio

1. Inicia Android Studio: en Windows desde el menú Inicio, en Ubuntu (snap) desde el menú de aplicaciones, desde el archivo con `bin/studio.sh` (1.1).
2. **File › Open** (u **Open** en la ventana de bienvenida) → elige la carpeta `TennisScoreManager` (dentro del clon o del ZIP, o la que ha creado el script) → **Trust Project**.
3. Espera a que termine la sincronización de Gradle (la primera vez descarga Gradle, el plugin de Android y las bibliotecas: solo en este momento hace falta internet).
4. Si aparece *"Failed to find target android-36"*, haz clic en el enlace **Install missing platform** (o **Tools › SDK Manager › SDK Platforms › Android 16 (API 36)**).
5. Si Android Studio te propone el **AGP Upgrade Assistant**, puedes ignorarlo: estas versiones se han compilado y probado tal cual.
6. JDK de Gradle: **Settings › Build, Execution, Deployment › Build Tools › Gradle › Gradle JDK** = *jbr-21* (el incluido, que es el predeterminado).
7. **Build › Make Project**: debe terminar con *BUILD SUCCESSFUL*.
8. Opcional: las pruebas (74: reglas, voz, idiomas, batería y carga, ajustes de las pulseras, marcador de TV) se lanzan con clic derecho en `app/src/test` › **Run Tests**.

## 3. Instalar la app en el teléfono

1. En el teléfono: **Ajustes › Información del teléfono** → toca 7 veces *Número de compilación* → activa **Opciones para desarrolladores › Depuración por USB**.
2. Conecta el cable, acepta la huella digital de la clave RSA, elige el teléfono arriba y pulsa **▶ Run**.
   - Si el ordenador no ve el teléfono: en Windows, el driver USB del fabricante; en Linux, el paquete con las reglas udev (1.1); después vuelve a conectar el cable. Alternativa sin cable ni driver: **Device Manager › Pair devices using Wi-Fi** (depuración inalámbrica, Android 11+, ordenador y teléfono en la misma red).
3. Otra opción: **Build › Build App Bundle(s) / APK(s) › Build APK(s)**, copia el APK al teléfono e instálalo (hay que permitir la instalación de aplicaciones desconocidas).

## 4. La voz (funciona sin internet)

- Cada canto lo lee **en una sola frase** la síntesis de voz del teléfono, con una voz instalada: sin internet y con una entonación natural. Los nombres de los jugadores forman parte de la frase.
- Página 2 › **Audio y voz**:
  - **Motor de síntesis de voz**: *Predeterminado del teléfono* (en los Samsung es Samsung TTS) o el motor que elijas, por ejemplo *Servicios de voz de Google*. En nuestras pruebas, hechas en italiano, Google sonaba más natural.
  - **Voz**: *Automática (la mejor sin conexión)* o una voz concreta (Google identifica sus voces con un código; las marcadas *en línea* necesitan internet).
  - **Probar voz**: lee una secuencia de cantos de ejemplo con los nombres introducidos. Mientras lee, el botón pasa a ser **Detener la prueba**: tócalo otra vez para interrumpirla (también se detiene sola al salir de la página). La síntesis de voz no se puede pausar a mitad de frase, así que el botón la detiene; si lo vuelves a pulsar, empieza desde el principio.
- Si falta la voz del idioma elegido: **Ajustes › Administración general › Idioma › Texto a voz** (o *Salida de texto a voz*) → descarga la voz de ese idioma para el motor elegido (la app muestra también el botón **Instalar voz**).
- **Pronunciación**: algunos motores leen mal palabras del tenis (en italiano, "primo set" salía como "primo settembre" y "tie-break" como "time break"). La app las corrige sola (`voice/Pronunciation.kt`); las correcciones se han verificado transcribiendo el audio real de Samsung y de Google.
- **Grabaciones personalizadas** (la voz más natural de todas: la tuya o la de un juez): en la carpeta `Android/data/com.tennis.scoremanager/files/voice/` está `LEGGIMI.txt` (el archivo «léeme») con la lista de las 84 claves y de sus frases. Graba los archivos con esos nombres (`score_1_0.mp3` = «quince cero»…), ponlos en un ZIP con una carpeta por idioma (`it/`, `en/`, `fr/`, `de/`, `es/`, `pt/`) y usa **Importar ZIP**. `LEGGIMI.txt` tiene una columna por idioma separada por tabuladores, así que también se abre bien como hoja de cálculo. Las grabaciones siempre tienen prioridad sobre la síntesis; los nombres los sigue leyendo la síntesis.
- **Usar archivos de audio pregenerados** (opcional): con **Generar archivos** la app crea una sola vez los 84 archivos con la voz elegida (incluso una voz *en línea*, si en ese momento hay internet) y después los usa en lugar de la síntesis continua. Suena más «a trozos», pero sirve para llevarse sin conexión una voz en línea.
- **Altavoz externo**: basta con emparejarlo por Bluetooth con el teléfono; la voz sale por el canal multimedia (ajusta el volumen multimedia).

### 4.1 Idiomas (app 2.3, firmware 2.2)

Página 2 › **Idioma**: Italiano, English, Français, Deutsch, Español, Português (cada idioma aparece escrito en su propia lengua, así lo encuentras aunque la app esté en un idioma que no entiendas). La elección se aplica a la vez a **pantallas, voz del juez, marcador de TV, resumen/compartir y pulseras**. Las fechas siguen el idioma (*mercredi 30 septembre 2026*, *Mittwoch, 30. September 2026*…).

Los cantos no son traducciones palabra por palabra: siguen los textos oficiales para jueces de silla de cada federación, es decir, FFT *L'arbitrage en 255 questions* e ITF en francés, los materiales de DTB/BTV y Swiss Tennis, RFET *Deberes y procedimientos*, FPT *Deveres e Procedimentos* (el único guion portugués publicado). El inglés sigue a la ITF.

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
- **Pronunciación** (`voice/Pronunciation.kt`): todas las frases de todos los idiomas se han hecho leer a las voces de Google y se han transcrito con whisper. Correcciones añadidas: en alemán, "Tie-Break" se hace leer "Taibreak", con una coma delante (si no, sale "Teilbreg" o "bei Detailbreak"); en francés, la "à" suelta de los archivos pregenerados se hace leer "a" (si no, sale "a accent grave"). **Samsung TTS todavía no se ha probado en los nuevos idiomas.**
- **Pulseras** (firmware 2.2): la app envía por sí sola el idioma en cada conexión y cuando lo cambias; la pulsera lo guarda y lo usa también cuando no está conectada (búsqueda del teléfono, carga, apagado), sin tildes porque la fuente es ASCII (*EN CHARGE*, *LAEDT*, *CARGANDO*…). Con un firmware 2.1 o anterior, los mensajes que envía la app salen traducidos, pero los propios de la pulsera siguen en italiano.

## 5. Firmware de las pulseras (Arduino IDE)

1. Instala Arduino IDE 2 (1.1). En Linux también hace falta el permiso sobre el puerto serie (grupo `dialout`, 1.1), después de cerrar la sesión y volver a entrar.
2. **File › Preferences › Additional boards manager URLs**:
   `https://static-cdn.m5stack.com/resource/arduino/package_m5stack_index.json`
3. **Boards Manager** → busca **M5Stack** → instala la versión **≥ 3.2.5**.
4. **Library Manager** → instala **M5Unified** (acepta "Install all" para M5GFX) y **NimBLE-Arduino** de *h2zero* (≥ 2.1).
5. **File › Open** → `firmware/TSM_Band/TSM_Band.ino` del clon o del ZIP (o `~/Arduino/TSM_Band/TSM_Band.ino` si has usado el script).
6. **Tools › Board › M5Stack › M5StickS3**, **Tools › Port** → el puerto de la pulsera: en Linux `/dev/ttyACM0`, en Windows `COM3`, `COM4`… (el que aparece al conectar el cable).
   *(Sin el paquete M5Stack también sirve "ESP32S3 Dev Module": USB CDC On Boot = Enabled, Flash Size = 8MB, Partition = 8M with spiffs.)*
7. **Upload (→)**. Si el puerto no aparece o la carga falla: prueba con otro cable USB-C (algunos solo sirven para cargar), luego mantén pulsado el **botón lateral** (modo de descarga) y vuelve a intentarlo; en modo de descarga, en Windows, el número del puerto COM puede cambiar.
   **Después de la carga**, si la pantalla se queda negra (la pulsera se ha quedado en modo de programación), pulsa **una vez** el botón lateral: arranca con el programa nuevo.
8. Al arrancar, la pulsera muestra su nombre, p. ej. **TSM-3FA2** (se puede cambiar desde la app, ver 6.1). Repite el proceso con la segunda pulsera.

> **Firmware 2.2.1**: también la cuenta atrás antes del apagado en el idioma de la app (6). **Firmware 2.2**: los textos de la pulsera en el idioma de la app (4.1). **Firmware 2.1**: la pantalla de carga (6.2); los ajustes, *Identificar* y el apagado desde la app requieren como mínimo la 2.0. Carga `TSM_Band.ino` en **las dos** pulseras; con un firmware antiguo la app lo indica en el panel de ajustes y todo lo demás sigue funcionando.

## 6. Usar las pulseras

| Botón | Acción |
|---|---|
| **KEY1** (frontal) corto | punto para quien lleva la pulsera (también inicia el partido desde la página INICIO DEL PARTIDO); un pitido lo confirma |
| **KEY1** largo (1 s) | muestra la batería (y mantiene encendida la pulsera si está a punto de apagarse por inactividad) |
| **KEY2** corto | anula el último punto (también desde la ventana de fin de partido); dos pitidos más graves |
| **KEY2** largo (2 s) | apaga la pulsera (con el cable conectado muestra *SIGUE CARGANDO*: se carga también apagada) |
| Botón lateral | un clic enciende; doble clic apaga (función del hardware) |

- Al encenderla parpadea **EMPAREJANDO...** (encendida 0,35 s cada 2 s, para ahorrar). La app se conecta sola a las pulseras que ya conoce en cuanto se abre; las nuevas las encuentra en la página 2.
- Conectada: **EMPAREJADO** durante 3 segundos con dos pitidos, luego **VINCULADA A** + el nombre del jugador.
- En cada punto la pantalla se enciende con el tanteo del juego en grande (a la izquierda el tuyo, a la derecha el del rival; la pelota verde indica quién saca) y luego se apaga. Al final de cada juego muestra juegos y sets.

**Apagado automático** (los tiempos se cambian desde la app, 6.1):

| Situación | Qué hace la pulsera | Predeterminado |
|---|---|---|
| Encendida, pero ningún teléfono se conecta | parpadea EMPAREJANDO y luego se apaga | **30 s** |
| Teléfono perdido (apagado, fuera de alcance, Bluetooth desactivado, app cerrada de golpe) | parpadea RECONECTANDO y se vuelve a conectar sola en cuanto puede; si no, se apaga | 3 min |
| Conectada pero inactiva (ningún punto, ningún mensaje) | 30 s antes avisa con **INACTIVO · MANTEN PULSADO KEY1** y un pitido, luego se apaga | 30 min |
| Partido terminado (confirmado en el teléfono) o **Salir** en la app | muestra FIN DEL PARTIDO / APP CERRADA y se apaga enseguida | activado (se puede desactivar, 7.2) |
| Batería agotada (por debajo de 3,30 V en dos lecturas seguidas) | muestra BATERIA AGOTADA y se apaga, para no quedarse encendida a medias | siempre |

- En los últimos 10 segundos antes de apagarse sin teléfono muestra **APAGANDO · 8s - PULSA UN BOTON** con un pitido: cualquier botón aplaza el apagado y la cuenta vuelve a empezar.
- Cuando se apaga sola se lo comunica al teléfono: durante el partido el recuadro naranja muestra *PULSERA 1 APAGADA (SIN USO)*, *(BATERÍA AGOTADA)*…

**Duración de la batería**: lo que más consume es la placa ESP32-S3 con el Bluetooth conectado (unos 35 mA): el core de Arduino está compilado sin el ahorro de energía profundo (light sleep) cuando el Bluetooth está activo, así que no se puede bajar mucho de ahí. La pantalla, el brillo y los pitidos añaden pocos mA. Con la batería de 250 mAh la estimación es de **unas 7 horas con la carga completa**, más que cualquier partido al mejor de tres sets. El panel de ajustes muestra la estimación en tiempo real y, tras 20 minutos de uso, la corrige con el consumo **medido** en esa pulsera. El registro completo está en `files/battery_log.csv` de la app.

El resto del ahorro: CPU a 80 MHz, pantalla apagada cuando no hace falta, Bluetooth de bajo consumo (la radio se despierta 2-3 veces por segundo; aun así, el botón responde en ~120 ms), zumbador activo solo durante los pitidos, micrófono/IMU/5V apagados.

### 6.1 Ajustes de la pulsera

Desde la página 2 (**Ajustes** debajo de la pulsera de cada jugador) o durante el partido (toca **J1**/**J2** arriba, o el icono de los controles deslizantes). Se guardan **en la pulsera** y se conservan aunque se apague; con cada cambio la pulsera muestra su nombre con *AJUSTES OK*.

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

Debajo aparece la **estimación de autonomía** (con la carga completa y con la carga actual) con el consumo desglosado: cambia mientras mueves los controles deslizantes, antes incluso de confirmar. Después, **Identificar**, **Apagar** y **Copiar a la otra pulsera** (mismos ajustes; el nombre sigue siendo el suyo).

### 6.2 Carga (firmware 2.1)

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
- **En la app**: la página 2 y los ajustes de la pulsera muestran *Cargando 78 %* o *Carga completa*; durante el partido, las etiquetas J1/J2 llevan el símbolo ⚡. El registro `files/battery_log.csv` también tiene dos columnas más (tensión USB, carga completa): útil para comprobar cuánto tarda realmente en cargarse.
- La pulsera y la app calculan ahora el porcentaje con la **misma curva** de la batería LiPo (antes la pulsera usaba una recta menos precisa), así que muestran el mismo número.

> Pendiente de comprobar con las pulseras reales: la carga no se ha podido probar en el hardware. En particular, con qué frecuencia indica el cargador *carga completa* y qué precisión tiene el porcentaje durante la carga.

## 7. Cómo se usa la app

1. **Nuevo partido** (opcional): club, pista, individual/dobles, nombres (en dobles, dos nombres por pareja). **Siguiente**.
2. **Modo y reglas**:
   - *Juez* o *Pulseras*. Con las pulseras son obligatorios el Bluetooth activado, el permiso de ubicación y la ubicación activada: las filas de **Requisitos** se actualizan en tiempo real (aunque desactives el Bluetooth o la ubicación desde los ajustes rápidos) y se ponen en rojo, con el botón para arreglarlo.
   - La **búsqueda es automática y continua** mientras la página está abierta: enciende las pulseras y ocupan solas los puestos libres (primero Jugador 1, luego Jugador 2). **Identificar** hace parpadear esa pulsera en el color del jugador (amarillo o rojo) con unos pitidos, así ves enseguida cuál tienes en la mano; **Intercambiar J1 ↔ J2** las invierte sin desconectarlas. En el menú desplegable siempre puedes elegir otra o *Ninguna* (la que quitas a mano no la vuelve a poner la búsqueda). Una pulsera no puede estar asignada a dos jugadores.
   - **Apagar las pulseras al final del partido y al salir** (activado de serie): al confirmar *Partido terminado* y con *Salir*, las pulseras se apagan en lugar de esperar a la inactividad.
   - En modo juez, si pulsas **Siguiente** sin ubicación aparece la invitación a activarla (si no, el lugar no aparecerá en el resumen).
   - Idioma (seis idiomas, 4.1), voz sí/no, formato (*3 sets · tie-break a 7* o *2 sets + súper tie-break a 10*), *Sin ventaja (punto decisivo)*.
   - **Sorteo**: la moneda gira e indica quién gana; indicas quién saca y los lados de la pista **vistos desde la silla del juez** (esquema de la pista con **Cambiar lados**). En dobles eliges también quién saca primero en cada pareja.
3. **INICIO DEL PARTIDO** parpadea: pulsa el botón o KEY1 de una pulsera. La voz dice *«Primer set» · «al servicio [nombre]» · «jueguen»* con 2 segundos entre frases; el **Match Time** empieza con «jueguen».
4. **Partido**: arriba a la izquierda, el tiempo de partido; a la derecha, la cuenta atrás (**Shot Clock** 25 s, **Changeover Time**, **Set Break Time**; en rojo en los últimos 5 s). El recuadro naranja se enciende 5 s para *cambio de lado, tie-break, bola de set, bola de partido, bola de break…*. Los dos botones cuadrados (amarillo = Jugador 1, rojo = Jugador 2) están del lado en que se encuentran realmente los jugadores y se intercambian en cada cambio de lado; debajo de quien saca aparece **On Serve**. Abajo: *Anular punto*, *Suspender/Reanudar*, audio (icono del altavoz, tachado = apagado), *Nuevo partido*, **Salir**. En modo pulseras, debajo de los tiempos están **J1**/**J2** con batería y autonomía: al tocarlos se abren los ajustes de la pulsera.
5. En el último punto aparece la ventana **Partido terminado / Anular el último punto**. «Partido terminado» se confirma **solo desde el teléfono**.
6. **Resumen**: ganador, nombres, resultado set por set con los puntos del tie-break, duración, hora de inicio y de fin, fecha, club, pista, lugar, formato, puntos y juegos ganados. Botones **Guardar en el historial** (nombre de archivo + carpeta a elegir + formatos .txt/.json/.png), **Compartir** (imagen de 1080×1350 + texto para WhatsApp/Instagram/…), **Nuevo partido**, **Salir**.

**Para salir**: **Salir** (durante el partido pide confirmación, y también está en el resumen) cierra la app de verdad; lo mismo ocurre si la quitas de las aplicaciones recientes. Al volver a abrirla empieza desde la primera página.

**Guardado**: el partido se guarda solo en cada punto. *Suspender* detiene los tiempos; si sales, si el teléfono se apaga o si la app se cierra, el partido aparece en **Reanudar partido suspendido** (página 3) y continúa con *Reanudar*. *Anular punto* recalcula todo desde el principio, así que funciona también después del final de un juego, de un set o del partido.

## 8. Marcador en un televisor o monitor

El teléfono del juez hace de **pequeño servidor** en la red Wi-Fi: el marcador es una página web de estilo LED (cifras de 7 segmentos, amarillo contra rojo, juegos y sets en el centro, sets terminados y tiempo de partido abajo a la izquierda, **SAQUE: 25 SEC** abajo a la derecha) que se actualiza sola en cada punto. El monitor no tiene que ser «smart» y no hace falta la red del club: basta con el **punto de acceso** (hotspot) de uno de los dos teléfonos.

### 8.1 Las opciones posibles

| Cómo llega al monitor | Qué hace falta | Ventajas | Inconvenientes |
|---|---|---|---|
| **Segundo teléfono con salida de vídeo** + cable USB-C/HDMI, app TSM en *Usar como marcador* | un teléfono que saque vídeo por el USB-C (DisplayPort Alt Mode) | sin internet; el marcador ocupa todo el monitor en 16:9 y el teléfono queda libre | muchos teléfonos **no** sacan vídeo: en general sí los Galaxy S/Note/Tab S (con DeX: elige *Duplicar pantalla* o desactiva el inicio automático de DeX), y no casi todos los Galaxy A. Busca «DisplayPort» / «salida de vídeo» en la ficha técnica |
| **Chromecast** (o Google TV Streamer) en el monitor + cualquier teléfono con TSM en *Usar como marcador* y **Enviar pantalla** (Smart View en los Samsung) | un Chromecast configurado una vez con Google Home en la red del punto de acceso | sirve cualquier teléfono, sin cables largos | el Chromecast necesita **internet** (datos móviles en el punto de acceso); retraso de aproximadamente 1 s; se envía la pantalla del teléfono (en horizontal) |
| **Navegador** en cualquier aparato conectado al monitor (portátil, tableta, TV box, Fire TV Stick…) | escanear el QR o escribir la dirección | no hay que instalar ninguna app | la pantalla se apaga sola si no la configuras; hay que tocar **PANTALLA COMPLETA** cada vez que se abre |

**Por qué no por Bluetooth**: el teléfono del juez ya mantiene las dos pulseras por Bluetooth, donde cuentan los tiempos de los botones; el Wi-Fi va aparte, es más rápido y llega más lejos. **Por qué no directamente del teléfono del juez al Chromecast** (sin segundo teléfono): se puede hacer, pero hace falta una app «receptora» registrada en Google (Google Cast Developer Console, 5 $ de pago único) y publicada en un sitio https; es un posible paso futuro.

**Consejos de red**:
- El marcador envía poquísimos datos (un mensaje en cada punto y cada 5 segundos) y no consume tráfico de internet.
- Si el teléfono del juez hace de punto de acceso, en los ajustes del punto de acceso elige la banda de **5 GHz** si la hay: el Bluetooth de las pulseras trabaja a 2,4 GHz y así no se interfieren.
- Con el Chromecast conviene que el punto de acceso lo haga el teléfono del juez con los **datos móviles activados**; el teléfono-marcador y el Chromecast se conectan a ese punto de acceso.
- Sin Chromecast también vale al revés (punto de acceso en el teléfono-marcador, como en la idea original): la app del juez se mantiene conectada al Wi-Fi aunque no tenga internet.

### 8.2 En el teléfono del juez

1. Página 2 › **Marcador en TV** › activa **Marcador en TV o monitor**.
2. Aparecen la **dirección** (p. ej. `192.168.43.1:8080`), el **QR** y cuántos marcadores hay conectados. Si pone *Sin red*, activa el punto de acceso o conéctate al del otro teléfono.
3. **Vista previa en este teléfono** abre el marcador en el navegador del propio teléfono.
4. **Aspecto del marcador**: color de cada jugador (8 colores), tiempo de partido, reloj de saque y descansos, sets terminados, mensajes (bola de break, bola de set, cambio de lado…), pelota junto a quien saca, segmentos apagados visibles, texto inferior (vacío = club y pista de la página 1). Los cambios llegan al monitor al instante.
5. Durante el partido, arriba en el centro aparece **TV · 1** (marcadores conectados): al tocarlo se vuelven a ver la dirección y el QR.

Con el marcador activado, un servicio en primer plano (notificación *Partido en curso · marcador de TV activo*) mantiene vivo el servidor aunque la pantalla esté apagada, también en modo juez. El marcador es **de solo lectura**: desde él no se puede cambiar nada.

Qué muestra, además del tanteo: *ESPERANDO EL PARTIDO* antes de empezar, *LISTOS PARA JUGAR* en la página INICIO DEL PARTIDO, **TIE-BREAK** / **SÚPER TIE-BREAK** en lugar de *VS*, *PARTIDO SUSPENDIDO* parpadeando, **GANADOR [nombre]** al final del partido con todos los sets; las ventajas se leen **AD** también en las cifras LED.

### 8.3 En el teléfono-marcador

1. Página 1 › abajo del todo, **Usar como marcador**.
2. El teléfono **busca solo** el teléfono del juez (anuncio en la red y escaneo del punto de acceso, unos segundos) y recuerda la última dirección. Si no lo encuentra: revisa el punto de acceso y *Marcador en TV*, luego **Buscar de nuevo**, o escribe la dirección que muestra el juez y pulsa **Conectar**.
3. El marcador pasa a pantalla completa, en horizontal, con la pantalla siempre encendida.
   - **Con el cable HDMI**: el marcador va al monitor en su formato; el teléfono muestra *El marcador está en el monitor externo* con el brillo al mínimo (**Mostrar también aquí** para verlo también en el teléfono). Al desconectar el cable vuelve al teléfono.
   - **Con el Chromecast**: abre los ajustes rápidos › **Enviar pantalla** / **Smart View** › elige el Chromecast.
4. Si el teléfono del juez desaparece (fuera de alcance, app cerrada) aparece *CONEXIÓN PERDIDA - RECONECTANDO...* y a los 20 segundos lo vuelve a buscar solo, aunque haya cambiado de dirección.
5. Para salir: **atrás dos veces**.

**Desde un navegador** (portátil, TV box): escanea el QR o escribe la dirección y toca **PANTALLA COMPLETA** (aparece al mover el ratón o al tocar la pantalla). Configura el apagado de la pantalla en *nunca*: en una página http el navegador no puede mantenerla encendida por sí solo.

**Vista previa sin teléfonos**: `TennisScoreManager/app/src/main/assets/scoreboard.html?demo=1` en un navegador (también `&state=ad`, `tb`, `end`, `idle`, `doubles`) muestra el marcador con datos de prueba.

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
3. Al final de un set ganado sin tie-break la app usa la misma fórmula que con tie-break («juego Rossi, Rossi gana un set a cero»). Muchos jueces prefieren anunciar juntos el juego y el set, seguidos del nombre y del resultado del set: se puede cambiar fácilmente en `Calls.kt`.
4. Con 1-1, en el formato con súper tie-break, la voz añade «súper tie-break».

## 10. Dónde meter mano

| Qué | Archivo |
|---|---|
| Reglas del tanteo | `model/ScoreEngine.kt` (+ pruebas en `app/src/test`) |
| Frases y cantos de voz | `voice/Calls.kt` (construcción), `voice/CallWords.kt` (palabras y orden de cada idioma) |
| Correcciones de pronunciación | `voice/Pronunciation.kt` |
| Tiempos (25/90/120/30 s), mensajes, desarrollo del partido | `MatchController.kt` |
| Textos de la app | `ui/Strings.kt` (italiano, inglés), `ui/StringsFr.kt`, `StringsDe.kt`, `StringsEs.kt`, `StringsPt.kt` |
| Añadir un idioma | una entrada en `model/Rules.kt` (`Lang`), un `ui/StringsXx.kt`, un `XxWords` en `voice/CallWords.kt`, una fila en `TXT[]` de `TSM_Band.ino`: las pruebas `LanguagesTest` y `StringsTest` indican qué falta |
| Protocolo Bluetooth (UUID, mensajes, ajustes) | `ble/BandProtocol.kt` y al principio de `TSM_Band.ino` |
| Estimación de la autonomía | `ble/BatteryModel.kt` |
| Panel de ajustes de la pulsera | `ui/BandSettingsPanel.kt` |
| Interfaz gráfica | `ui/screens/*.kt`, colores en `ui/Theme.kt` |
| Marcador de TV: página y aspecto | `app/src/main/assets/scoreboard.html` |
| Marcador de TV: datos enviados, servidor, ajustes | `tv/TvModels.kt`, `tv/TvServer.kt`, `ui/TvSection.kt` |
| Teléfono usado como marcador (búsqueda, monitor externo) | `tv/DisplayActivity.kt`, `tv/ScoreboardFinder.kt` |
| Pantalla de carga de la pulsera | `TSM_Band.ino` (`pollPower`, `drawCharge`) |


## 11. Problemas frecuentes

- **No se encuentra la pulsera**: ¿Bluetooth y ubicación activados (filas en verde)? ¿La pulsera está parpadeando EMPAREJANDO? (Si ya está conectada a otro teléfono, no se ve.) Si mientras tanto se ha apagado sola (30 s), vuelve a encenderla con un clic en el botón lateral.
- **En el panel de ajustes aparece «El firmware de esta pulsera no tiene ajustes»**: esa pulsera todavía tiene el sketch antiguo; vuelve a cargarlo (capítulo 5).
- **Android Studio no ve el teléfono**: ¿depuración por USB activada y huella RSA aceptada en el teléfono (3)? En Windows a veces hace falta el driver del fabricante; en Linux, las reglas udev y una nueva sesión (1.1). O bien la depuración inalámbrica (3).
- **Arduino IDE no muestra el puerto de la pulsera**: cable USB-C de datos y no solo de carga; en Linux, grupo `dialout` y nueva sesión (1.1); después, el modo de descarga (5, punto 7).
- **La voz no habla o lee mal**: volumen multimedia, *Audio On*, voz del idioma elegido instalada (4); prueba otro motor u otra voz en *Audio y voz*.
- **Pantalla apagada durante el partido**: en modo pulseras, un servicio en primer plano (notificación «Partido en curso») mantiene activos el Bluetooth, la voz y los cronómetros; en modo juez, la pantalla se queda encendida.
- **Falla la sincronización de Gradle por el JDK**: configura Gradle JDK = jbr-21 (punto 2.6).
- **El teléfono-marcador no encuentra al juez**: ¿están en la misma red? (uno de los dos hace de punto de acceso y el otro está conectado a él). ¿*Marcador en TV* está activado en el teléfono del juez? Prueba a escribir la dirección a mano. Algunos puntos de acceso aíslan entre sí los dispositivos conectados («aislamiento de clientes»): si es así, desactívalo.
- **El navegador del teléfono no abre la dirección** con los datos móviles activados: Android envía el tráfico por los datos móviles porque el punto de acceso no tiene internet. Usa la app en *Usar como marcador* (lo gestiona sola) o desactiva los datos móviles en ese teléfono.
- **Monitor negro con el cable**: ese teléfono no tiene salida de vídeo por el USB-C (8.1), o se ha iniciado Samsung DeX: elige *Duplicar pantalla*.
