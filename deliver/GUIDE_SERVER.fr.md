# Serveur du club — guide pour le secrétariat

[Italiano](GUIDA_SERVER.md) · [English](GUIDE_SERVER.en.md) · **Français** · [Deutsch](GUIDE_SERVER.de.md) · [Español](GUIDE_SERVER.es.md) · [Português](GUIDE_SERVER.pt.md)

Le serveur du club est facultatif : il ne sert que si vous voulez que les joueurs réservent les terrains eux-mêmes, depuis leur téléphone. Sans serveur l'appli marche tout autant : c'est le secrétariat qui note les réservations.

Il y a trois façons de l'utiliser. Choisissez celle qu'il vous faut :

| Façon | Les joueurs réservent… | Ce qu'il faut |
|---|---|---|
| **A. Seulement au club** | quand ils sont sur le Wi-Fi du club | le PC du serveur allumé |
| **B. Depuis internet avec Tailscale** | de n'importe où | en plus, un compte Tailscale gratuit (10 minutes, une seule fois) |
| **C. Depuis internet par la box** | de n'importe où | en plus, quelqu'un qui sache configurer la box du club |

Notre conseil : commencez par la façon **A**. Si vous voulez les réservations depuis la maison, passez à la **B** : personne ne touche à la box.

---

## 1. Démarrer le serveur (pour toutes les façons)

Il faut un vieux PC (ou un Raspberry Pi préparé par un technicien) et la **clé USB TSM-SERVER**.

1. Branchez le PC au réseau du club, de préférence par câble. Sur un portable sans câble le Wi-Fi convient aussi : la première fois, quand l'écran affiche **No network**, tapez `user`, Entrée, `live`, Entrée, puis `sudo nmtui` (après la connexion, l'écran rappelle toutes les commandes) ; choisissez **Activate a connection**, le réseau du club et tapez son mot de passe. Il reste enregistré sur la clé : les fois suivantes il se connecte tout seul. Pour sortir : **Quit**, puis `exit`.
2. Insérez la clé et allumez le PC. Si Windows démarre, redémarrez et appuyez sur la touche du menu de démarrage (souvent F12, F9 ou Échap : l'écran l'indique à l'allumage) puis choisissez la clé USB.
3. Après environ une minute l'écran affiche :

   ```
   TSM – Server del circolo / Club server
   http://192.168.1.50:8080
   Codice di abbinamento / Pairing code: 123456
   ```

   Vos chiffres seront différents. Notez l'**adresse** (la ligne `http://…`) et le **code**.
4. Laissez le PC allumé. Inutile de se connecter ou de taper quoi que ce soit : le serveur travaille tout seul. Si vous éteignez le PC, les joueurs ne réservent plus jusqu'à ce que vous le rallumiez ; les données restent sur la clé.

Si l'écran affiche **WARNING: no persistence partition**, la clé n'a pas été bien préparée : les données se perdraient à l'extinction. Faites-la refaire.

## 2. Appairer le téléphone du secrétariat (une seule fois)

1. Le téléphone sur le **Wi-Fi du club**, ouvrez TSM et allez dans **Terrains › Serveur du club**.
2. Dans **Adresse du serveur**, tapez l'adresse lue sur l'écran du PC ; dans **Code d'appairage**, le code.
3. Touchez **Appairer**. Le code ne sert qu'une fois : l'écran du PC affiche maintenant **Segreteria abbinata / Office paired**.

Si un jour vous changez de téléphone, il faut un nouveau code : demandez au technicien de se connecter sur le PC du serveur et de taper `sudo tsm-reset-admin`.

## 3. Façon A : réservations au club

Toujours dans **Terrains › Serveur du club** :

1. Activez **Réservations en ligne ouvertes**.
2. Choisissez les **Terrains réservables en ligne** et, si vous voulez, les règles (combien de jours à l'avance, combien de réservations par joueur…).
3. Touchez **Envoyer maintenant** : le planning part vers le serveur.
4. Pour chaque joueur : ouvrez sa fiche et touchez **Invitation à réserver en ligne**. Le joueur scanne le QR avec l'appareil photo et réserve depuis la page du club (sur iPhone : Partager › Sur l'écran d'accueil, et elle s'ouvre comme une appli).

Depuis le secrétariat sur le PC c'est pareil : **Adhérents**, ouvrez le membre et cliquez **Invitation à réserver en ligne**. En plus du QR il y a **Copier le lien**, **Envoyer par WhatsApp** et **Envoyer par e-mail** : le message s'ouvre déjà écrit et c'est vous qui l'envoyez.

Les réservations des joueurs arrivent toutes seules dans le planning du secrétariat, marquées **en ligne**.

## 4. Façon B : depuis internet avec Tailscale

Tailscale est un service qui relie le serveur à internet « de l'intérieur », sans ouvrir de ports sur la box. Il marche aussi avec les connexions sans adresse publique (beaucoup de lignes 4G/5G et sans fil). Il faut un compte gratuit.

**Avant de commencer :** le serveur doit avoir internet (le réseau du club sort sur internet) et le téléphone du secrétariat doit déjà être appairé (chapitre 2).

1. Dans **Terrains › Serveur du club**, dans le cadre **Accès internet sans toucher à la box**, touchez **Activer**.
2. Attendez quelques minutes : la première fois le serveur télécharge Tailscale (**Le serveur prépare la connexion**).
3. **Ouvrir Tailscale** apparaît : touchez-le. La page de Tailscale s'ouvre dans le navigateur :
   - si le compte existe déjà, connectez-vous ;
   - sinon, créez-le : on se connecte avec un compte que vous avez déjà (par exemple Google, Microsoft ou Apple). Mieux vaut le compte du club que celui, personnel, de la personne au secrétariat.
   - Tailscale demande de connecter un appareil nommé **tsm-circolo** : confirmez.
4. Revenez dans TSM. Bientôt apparaît **Dernière étape : ouvrez Tailscale et confirmez l'activation de Funnel**. Touchez à nouveau **Ouvrir Tailscale** et confirmez (Funnel est la fonction qui rend le serveur joignable depuis internet).
5. Revenez dans TSM : **Activé : https://tsm-circolo.….ts.net** apparaît. C'est l'adresse depuis internet, et TSM la met tout seul dans **Adresse depuis internet**. Désormais les invitations marchent aussi depuis la maison.
6. **À faire tout de suite** : tous les six mois Tailscale demande de se reconnecter. Pour l'éviter, sur un ordinateur allez sur **login.tailscale.com**, trouvez **tsm-circolo** dans **Machines**, ouvrez son menu (les trois points) et choisissez **Disable key expiry**.

Pour couper l'accès depuis internet : **Désactiver**. Les réservations au club (façon A) continuent de marcher.

Quand l'accès est actif, l'écran du PC du serveur affiche aussi la ligne **Da internet / From the internet**.

Le forfait gratuit de Tailscale est prévu pour un usage personnel et modeste : avant de l'utiliser pour le club, jetez un œil aux conditions sur leur site.

**Si quelque chose ne va pas**
- **Cela n'a pas fonctionné : vérifiez que le serveur a accès à internet** : le PC du serveur ne sort pas sur internet. Vérifiez le câble ou le Wi-Fi du club et touchez à nouveau **Activer**.
- **Pour l'utiliser, mettez à jour le serveur** : la clé est ancienne, sans Tailscale. Faites-la refaire avec la nouvelle image.
- Si après quelques mois l'adresse depuis internet ne marche plus, ouvrez **Terrains › Serveur du club** : si **Ouvrir Tailscale** réapparaît, reconnectez-vous avec votre compte.

## 5. Façon C : depuis internet par la box

C'est la façon classique : elle ne dépend pas de Tailscale, mais il faut quelqu'un qui sache configurer la box du club, et la ligne doit avoir une adresse publique.

À donner au technicien :
1. Un nom gratuit sur **duckdns.org** (par ex. `tcrossi.duckdns.org`) et son jeton.
2. Sur la box, les ports **80** et **443** redirigés vers le PC du serveur.
3. Sur le PC du serveur, en se connectant avec l'utilisateur `user` et le mot de passe `live` (à changer avec `passwd`) :

   ```
   sudo /opt/tsm-server/kit/setup-https.sh tcrossi.duckdns.org --duckdns JETON
   ```

4. Dans TSM, dans **Adresse depuis internet**, tapez `https://tcrossi.duckdns.org`.

Pour savoir si la ligne a une adresse publique : comparez l'adresse WAN affichée par la page de la box avec celle d'un site du genre « quelle est mon IP ». Si elles diffèrent, ou si celle de la box commence par `100.64`–`100.127`, la façon C ne marche pas : utilisez la **B**.

## 6. Pense-bête pour le secrétariat

- Le PC du serveur reste allumé ; on n'enlève pas la clé.
- De temps en temps, faites une sauvegarde depuis **Terrains › Sauvegarde du secrétariat** : une copie est gardée aussi sur le serveur.
- Pour arrêter les réservations un moment (entretien, fermeture), désactivez **Réservations en ligne ouvertes** : les joueurs voient le planning mais ne réservent pas.
- Un joueur qui ne doit plus réserver : dans sa fiche, **Retirer l'accès en ligne**.
- Une annonce pour tous (terrains fermés, un tournoi, une fête) : **Serveur du club › Tableau du club**, ou **Réservations en ligne** depuis le PC. Elle arrive aux joueurs avec une notification et reste 30 jours. Depuis **Discussions des annonces** vous pouvez retirer les messages déplacés des discussions de Je cherche un partenaire.
