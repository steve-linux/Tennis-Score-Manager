# TSM Arena 3D

Sorgente della grafica 3D di TSM Arena (three.js). La pagina del gioco è `app/src/main/assets/arena.html`;
questo file la disegna in WebGL in due stili (3D semplice low-poly, 3D realistica) e si compila in
`app/src/main/assets/arena/arena3d.js`, un file solo servito dal telefono alla TV (niente internet).

```bash
cd TennisScoreManager/arena3d
npm ci          # three.js ed esbuild, versioni fissate in package-lock.json
sh build.sh     # rigenera app/src/main/assets/arena/arena3d.js
```

- Anteprima: servire `app/src/main/assets/` con un server statico qualsiasi che risponda a `/arena` con `arena.html`
  e a `/arena/<file>` con `arena/<file>`, poi aprire `/arena?demo=1&gfx=2` (`gfx=1` semplice, `gfx=0` 2D, `two=1` in due).
- `app/src/main/assets/arena/male.glb`: "3D Male Base Mesh" di orange-juice-games, CC0
  (https://github.com/BoQsc/Godot-Male-Base-Mesh). Le animazioni da tennis sono procedurali (`POSES` in `arena3d.js`).
- `character.js`: il giocatore della grafica realistica. Il corpo CC0 (~1200 vertici) si arrotonda con due passaggi di
  suddivisione di Loop; maglietta, pantaloncini, scarpe e capelli sono gusci separati con bordi netti (maschera per
  vertice), materiali fisici (pelle con sheen e pori, tessuto a maglia), occhi, sopracciglia, dita chiuse sul manico;
  in `arena3d.js` la testa segue la palla. Le texture sono generate in codice.
- three.js è MIT (licenza in fondo al file compilato).
