# Servidor del club — guía para la secretaría

[Italiano](GUIDA_SERVER.md) · [English](GUIDE_SERVER.en.md) · [Français](GUIDE_SERVER.fr.md) · [Deutsch](GUIDE_SERVER.de.md) · **Español** · [Português](GUIDE_SERVER.pt.md)

El servidor del club es opcional: solo hace falta si queréis que los jugadores reserven las pistas solos, desde su teléfono. Sin servidor la app funciona igual: las reservas las anota la secretaría.

Hay tres maneras de usarlo. Elegid la que os haga falta:

| Manera | Los jugadores reservan… | Qué hace falta |
|---|---|---|
| **A. Solo en el club** | cuando están en el Wi-Fi del club | el PC del servidor encendido |
| **B. Desde internet con Tailscale** | desde cualquier sitio | además, una cuenta gratuita de Tailscale (10 minutos, una sola vez) |
| **C. Desde internet con el router** | desde cualquier sitio | además, alguien que sepa configurar el router del club |

El consejo: empezad por la manera **A**. Si queréis reservas desde casa, pasad a la **B**: no hay que tocar el router.

---

## 1. Encender el servidor (para todas las maneras)

Hace falta un PC viejo (o una Raspberry Pi preparada por un técnico) y el **pendrive TSM-SERVER**.

1. Conectad el PC a la red del club, mejor con cable. En un portátil sin cable también vale el Wi-Fi: la primera vez, cuando la pantalla diga **No network**, escribid `user`, Intro, `live`, Intro, y luego `sudo nmtui` (tras entrar, la pantalla recuerda todos los comandos); elegid **Activate a connection**, la red del club y escribid su contraseña. Queda guardada en el pendrive: las veces siguientes se conecta solo. Para salir: **Quit**, y luego `exit`.
2. Meted el pendrive y encended el PC. Si arranca Windows, reiniciad, pulsad la tecla del menú de arranque (normalmente F12, F9 o Esc: la pantalla lo dice al encender) y elegid el pendrive USB.
3. Al cabo de un minuto la pantalla muestra:

   ```
   TSM – Server del circolo / Club server
   http://192.168.1.50:8080
   Codice di abbinamento / Pairing code: 123456
   ```

   Vuestros números serán otros. Apuntad la **dirección** (la línea `http://…`) y el **código**.
4. Dejad el PC encendido. No hace falta entrar ni escribir nada: el servidor trabaja solo. Si apagáis el PC, los jugadores no reservan hasta que lo volváis a encender; los datos se quedan en el pendrive.

Si la pantalla dice **WARNING: no persistence partition**, el pendrive no se preparó bien: los datos se perderían al apagar. Hacedlo rehacer.

## 2. Emparejar el teléfono de la secretaría (una sola vez)

1. Con el teléfono en el **Wi-Fi del club**, abrid TSM e id a **Pistas › Servidor del club**.
2. En **Dirección del servidor** escribid la dirección de la pantalla del PC; en **Código de emparejamiento**, el código.
3. Tocad **Emparejar**. El código vale una sola vez: la pantalla del PC dice ahora **Segreteria abbinata / Office paired**.

Si un día cambiáis de teléfono, hace falta un código nuevo: pedid al técnico que entre en el PC del servidor y escriba `sudo tsm-reset-admin`.

## 3. Manera A: reservas en el club

Siempre en **Pistas › Servidor del club**:

1. Activad **Reservas en línea abiertas**.
2. Elegid las **Pistas reservables en línea** y, si queréis, las reglas (cuántos días antes, cuántas reservas por jugador…).
3. Tocad **Enviar ahora**: el planning llega al servidor.
4. Para cada jugador: abrid su ficha y tocad **Invitación para reservar en línea**. El jugador escanea el QR con la cámara y reserva desde la página del club (en iPhone: Compartir › Añadir a pantalla de inicio, y se abre como una app).

Desde la secretaría en el PC es igual: **Socios**, abrid el socio y haced clic en **Invitación para reservar en línea**. Además del QR están **Copiar el enlace**, **Enviar por WhatsApp** y **Enviar por correo**: el mensaje se abre ya escrito y lo enviáis vosotros.

Las reservas de los jugadores llegan solas al planning de la secretaría, marcadas **en línea**.

## 4. Manera B: desde internet con Tailscale

Tailscale es un servicio que conecta el servidor a internet «desde dentro», sin abrir puertos en el router. Funciona también con conexiones sin dirección pública (muchas líneas 4G/5G e inalámbricas). Hace falta una cuenta gratuita.

**Antes:** el servidor debe tener internet (la red del club sale a internet) y el teléfono de la secretaría ya debe estar emparejado (capítulo 2).

1. En **Pistas › Servidor del club**, en el recuadro **Acceso desde internet sin tocar el router**, tocad **Activar**.
2. Esperad unos minutos: la primera vez el servidor descarga Tailscale (**El servidor está preparando la conexión**).
3. Aparece **Abrir Tailscale**: tocadlo. Se abre la página de Tailscale en el navegador:
   - si ya tenéis cuenta, entrad;
   - si no, cread una: se entra con una cuenta que ya tengáis (por ejemplo Google, Microsoft o Apple). Mejor la cuenta del club que la personal de quien lleva la secretaría.
   - Tailscale pide conectar un dispositivo llamado **tsm-circolo**: confirmad.
4. Volved a TSM. Enseguida aparece **Último paso: abre Tailscale y confirma la activación de Funnel**. Tocad otra vez **Abrir Tailscale** y confirmad (Funnel es la función que hace que el servidor se alcance desde internet).
5. Volved a TSM: aparece **Activo: https://tsm-circolo.….ts.net**. Esa es la dirección desde internet, y TSM la pone sola en **Dirección desde internet**. Desde ahora las invitaciones funcionan también desde casa.
6. **Una cosa que hacer enseguida**: cada seis meses Tailscale pide volver a entrar. Para evitarlo, en un ordenador id a **login.tailscale.com**, buscad **tsm-circolo** en **Machines**, abrid su menú (los tres puntos) y elegid **Disable key expiry**.

Para apagar el acceso desde internet: **Desactivar**. Las reservas en el club (manera A) siguen funcionando.

Mientras el acceso está activo, la pantalla del PC del servidor muestra también la línea **Da internet / From the internet**.

El plan gratuito de Tailscale está pensado para un uso personal y pequeño: antes de usarlo para el club, mirad las condiciones en su web.

**Si algo va mal**
- **No ha funcionado: comprueba que el servidor tenga internet**: el PC del servidor no sale a internet. Revisad el cable o el Wi-Fi del club y tocad otra vez **Activar**.
- **Para usarlo, actualiza el servidor**: el pendrive es viejo, sin Tailscale. Hacedlo rehacer con la imagen nueva.
- Si al cabo de meses la dirección desde internet deja de funcionar, abrid **Pistas › Servidor del club**: si vuelve a aparecer **Abrir Tailscale**, entrad otra vez con vuestra cuenta.

## 5. Manera C: desde internet con el router

Es la manera clásica: no depende de Tailscale, pero hace falta alguien que sepa configurar el router del club, y la línea debe tener una dirección pública.

Para el técnico:
1. Un nombre gratuito en **duckdns.org** (p. ej. `tcrossi.duckdns.org`) y su token.
2. En el router, los puertos **80** y **443** redirigidos al PC del servidor.
3. En el PC del servidor, entrando con el usuario `user` y la contraseña `live` (que hay que cambiar con `passwd`):

   ```
   sudo /opt/tsm-server/kit/setup-https.sh tcrossi.duckdns.org --duckdns TOKEN
   ```

4. En TSM, en **Dirección desde internet**, escribid `https://tcrossi.duckdns.org`.

Para saber si la línea tiene dirección pública: comparad la dirección WAN de la página del router con la de una web tipo «cuál es mi IP». Si son distintas, o la del router empieza por `100.64`–`100.127`, la manera C no funciona: usad la **B**.

## 6. Recordatorios para la secretaría

- El PC del servidor se queda encendido; el pendrive no se quita.
- De vez en cuando haced una copia desde **Pistas › Copia de la secretaría**: una copia queda también en el servidor.
- Para parar las reservas un tiempo (mantenimiento, cierre), desactivad **Reservas en línea abiertas**: los jugadores ven el planning pero no reservan.
- Un jugador que ya no debe reservar: en su ficha, **Quitar el acceso en línea**.
