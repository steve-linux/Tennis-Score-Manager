# Club server — guide for the club office

[Italiano](GUIDA_SERVER.md) · **English** · [Français](GUIDE_SERVER.fr.md) · [Deutsch](GUIDE_SERVER.de.md) · [Español](GUIDE_SERVER.es.md) · [Português](GUIDE_SERVER.pt.md)

The club server is optional: you need it only if you want players to book courts by themselves, from their own phone. Without a server the app works just the same: the office records the bookings.

There are three ways to use it. Pick the one you need:

| Way | Players book… | What you need |
|---|---|---|
| **A. Only at the club** | when they are on the club Wi-Fi | the server PC switched on |
| **B. From the internet with Tailscale** | from anywhere | also a free Tailscale account (10 minutes, once) |
| **C. From the internet through the router** | from anywhere | also someone who can configure the club router |

Our advice: start with way **A**. If you want bookings from home, move to **B**: nobody has to touch the router.

---

## 1. Starting the server (for every way)

You need an old PC (or a Raspberry Pi prepared by a technician) and the **TSM-SERVER USB stick**.

1. Connect the PC to the club network, preferably with a cable.
2. Plug in the stick and switch the PC on. If Windows starts, restart and press the boot-menu key (usually F12, F9 or Esc: the screen says which one at power-on) and choose the USB stick.
3. After about a minute the screen shows:

   ```
   TSM – Server del circolo / Club server
   http://192.168.1.50:8080
   Codice di abbinamento / Pairing code: 123456
   ```

   Your numbers will be different. Write down the **address** (the `http://…` line) and the **code**.
4. Leave the PC on. You do not need to log in or type anything: the server runs by itself. If you switch the PC off, players cannot book until you switch it on again; the data stays on the stick.

If the screen says **WARNING: no persistence partition**, the stick was not prepared properly: the data would be lost at shutdown. Have it redone.

## 2. Pairing the office phone (once)

1. With the phone on the **club Wi-Fi**, open TSM and go to **Courts › Club server**.
2. In **Server address** type the address shown on the PC screen, in **Pairing code** the code.
3. Tap **Pair**. The code works only once: the PC screen now says **Office paired**.

If one day you change phone, you need a new code: ask the technician to log in on the server PC and type `sudo tsm-reset-admin`.

## 3. Way A: bookings at the club

Still in **Courts › Club server**:

1. Turn on **Online booking open**.
2. Choose the **Courts bookable online** and, if you like, the rules (how many days ahead, how many bookings per player…).
3. Tap **Send now**: the schedule goes to the server.
4. For each player: open their card and tap **Invite to book online**. The player scans the QR code with the camera and books from the club page (on iPhone: Share › Add to Home Screen, and it works like an app).

The players' bookings reach the office schedule by themselves, marked **online**.

## 4. Way B: from the internet with Tailscale

Tailscale is a service that connects the server to the internet "from the inside", without opening ports on the router. It also works with connections that have no public address (many fixed-wireless and 4G/5G lines). You need a free account.

**Before you start:** the server must have internet access (the club network reaches the internet) and the office phone must already be paired (chapter 2).

1. In **Courts › Club server**, in the **Internet access without touching the router** box, tap **Turn on**.
2. Wait a few minutes: the first time the server downloads Tailscale (**The server is preparing the connection**).
3. **Open Tailscale** appears: tap it. The Tailscale page opens in the browser:
   - if you already have an account, sign in;
   - if not, create one: you sign in with an account you already have (for example Google, Microsoft or Apple). Better to use the club's account, not the personal one of whoever runs the office.
   - Tailscale asks you to connect a device called **tsm-circolo**: confirm.
4. Go back to TSM. Shortly **Last step: open Tailscale and confirm turning on Funnel** appears. Tap **Open Tailscale** again and confirm (Funnel is the feature that makes the server reachable from the internet).
5. Go back to TSM: **On: https://tsm-circolo.….ts.net** appears. That is the address from the internet, and TSM puts it into **Address from the internet** by itself. From now on invitations work from home too.
6. **One thing to do straight away**: every six months Tailscale asks you to sign in again. To avoid it, on a computer go to **login.tailscale.com**, find **tsm-circolo** under **Machines**, open its menu (the three dots) and choose **Disable key expiry**.

To switch internet access off: **Turn off**. Bookings at the club (way A) keep working.

While internet access is on, the server PC screen also shows the line **Da internet / From the internet**.

Tailscale's free plan is meant for personal and small use: before using it for the club, have a look at the terms on their website.

**If something goes wrong**
- **It did not work: check that the server has internet access**: the server PC cannot reach the internet. Check the cable or the club Wi-Fi and tap **Turn on** again.
- **To use this, update the server**: the stick is old, without Tailscale. Have it redone with the new image.
- If after some months the internet address stops working, open **Courts › Club server**: if **Open Tailscale** appears again, sign in with your account.

## 5. Way C: from the internet through the router

This is the classic way: it does not depend on Tailscale, but you need someone who can configure the club router, and the line must have a public address.

To hand to the technician:
1. A free name on **duckdns.org** (e.g. `tcrossi.duckdns.org`) and its token.
2. On the router, ports **80** and **443** forwarded to the server PC.
3. On the server PC, logging in with user `user` and password `live` (to be changed with `passwd`):

   ```
   sudo /opt/tsm-server/kit/setup-https.sh tcrossi.duckdns.org --duckdns TOKEN
   ```

4. In TSM, in **Address from the internet**, type `https://tcrossi.duckdns.org`.

To find out whether the line has a public address: compare the WAN address shown on the router's page with the one shown by a "what is my IP" website. If they differ, or the router's one starts with `100.64`–`100.127`, way C does not work: use **B**.

## 6. Reminders for the office

- The server PC stays on; do not remove the stick.
- Every now and then make a backup from **Courts › Office backup**: a copy is kept on the server too.
- To stop bookings for a while (maintenance, closure), switch off **Online booking open**: players see the schedule but cannot book.
- A player who should no longer book: on their card, **Remove online access**.
