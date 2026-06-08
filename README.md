# WiseVPN — APK Android avec VPN Gate

Application Android VPN utilisant les serveurs publics VPN Gate.
Développée par W Design pour Wise IPTV.

---

## Architecture

```
WiseVPN/
├── app/src/main/java/com/wdesign/wisevpn/
│   ├── MainActivity.java        ← UI : liste serveurs + état connexion
│   ├── ServerModel.java         ← Modèle données serveur VPN Gate
│   ├── VpnGateApi.java          ← Scraper API CSV vpngate.net
│   ├── OvpnConfigWriter.java    ← Décode base64 → fichier .ovpn
│   ├── WiseVpnService.java      ← VpnService Android (foreground)
│   ├── OpenVpnBridge.java       ← Wrapper ics-openvpn (stub → prod)
│   └── ServerAdapter.java       ← RecyclerView adapter
├── app/src/main/res/
│   ├── layout/activity_main.xml
│   ├── layout/item_server.xml
│   └── values/{colors,strings,themes}.xml
└── app/build.gradle
```

---

## Fonctionnement

1. **Démarrage** : l'app appelle `https://www.vpngate.net/api/iphone/`
2. **Parse CSV** : extraction IP, ping, vitesse, config .ovpn base64
3. **Tri** : serveurs classés par ping croissant (meilleur en haut)
4. **Connexion** :
   - `OvpnConfigWriter` décode la config base64 → fichier `.ovpn`
   - Android demande permission VPN (dialog système)
   - `WiseVpnService` démarre en foreground
   - `OpenVpnBridge` tente le tunnel via ics-openvpn
   - Fallback : tunnel tun Android minimal si ics-openvpn absent

---

## Compilation (Android Studio)

### Étape 1 : Ouvrir le projet
```
File → Open → dossier WiseVPN/
```

### Étape 2 : Compiler en mode DEMO (sans ics-openvpn)
```
Build → Make Project
Run → Run 'app'
```
✅ Fonctionne immédiatement. Le VPN crée une interface tun
   mais ne route pas réellement via VPN Gate.

---

## Intégration ics-openvpn (VRAI tunnel OpenVPN)

### Option A — AAR pré-compilé (recommandé)

1. Télécharger depuis :
   https://github.com/schwabe/ics-openvpn/releases

2. Copier `ics-openvpn-release.aar` dans `app/libs/`

3. Dans `app/build.gradle`, décommenter :
   ```groovy
   implementation(name: 'ics-openvpn-release', ext: 'aar')
   ```
   Et ajouter dans android{} :
   ```groovy
   repositories { flatDir { dirs 'libs' } }
   ```

4. Dans `OpenVpnBridge.java`, décommenter le bloc ics-openvpn
   et supprimer la ligne stub.

### Option B — Compiler depuis les sources

```bash
git clone https://github.com/schwabe/ics-openvpn.git
# Copier le module 'main' comme ':icsopenvpn' dans ce projet
# Dans settings.gradle : include ':app', ':icsopenvpn'
# Dans app/build.gradle : implementation project(':icsopenvpn')
```

---

## Intégration dans Wise IPTV Player

Une fois WiseVpnService actif, **PlayerEngine n'a rien à modifier**.
Tout le trafic Android passe automatiquement par le tunnel tun0.

Pour vérifier depuis l'app IPTV :
```java
// Vérifier si VPN actif avant lecture
ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(CONNECTIVITY_SERVICE);
Network activeNet = cm.getActiveNetwork();
NetworkCapabilities caps = cm.getNetworkCapabilities(activeNet);
boolean vpnActive = caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
```

---

## Limitations VPN Gate

| Aspect | Détail |
|--------|--------|
| Stabilité | Serveurs volontaires, peuvent disparaître |
| Vitesse | Variable (10 Mbps – 700 Mbps) |
| Logs | 2 semaines selon politique serveur |
| Fiabilité | Suffisant pour IPTV, pas pour usage intensif |

Pour production stable → utiliser WireGuard sur Oracle Free Tier.

---

## Permissions Manifest

| Permission | Raison |
|-----------|--------|
| `INTERNET` | Scraper VPN Gate + connexion VPN |
| `FOREGROUND_SERVICE` | Service VPN en background |
| `BIND_VPN_SERVICE` | Obligatoire pour VpnService Android |
| `usesCleartextTraffic` | Certains serveurs VPN Gate sur HTTP |
