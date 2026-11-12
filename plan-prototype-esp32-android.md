# JEIA – Plan du prototype : ESP32 + appli Android (Kotlin Multiplatform)

> **Objectif** : un appui long sur un bouton branché sur l'ESP32 envoie une alerte en Bluetooth à l'appli Android, qui récupère la position GPS et prévient les contacts.
> Le même contrat BLE servira plus tard avec la vraie puce de la bague (DA14531). Seul le firmware sera à réécrire.

---

## 1. Schéma d'ensemble

```
 [Bouton] ──► ESP32-C3 ──BLE──► Appli Android (Kotlin) ──HTTPS──► Backend ──► SMS / push
  GND + D4     firmware C++       foreground service + GPS
                  ▲                       │
                  └──── ACK (écriture) ───┘   la LED clignote 2× = alerte bien partie
```

---

## 2. Matériel à acheter

| Élément | Détail | Prix indicatif |
|---|---|---|
| **ESP32-C3 Super Mini** | Carte avec USB-C intégré (une ESP32 classique ou un autre modèle C3 conviennent aussi) | 3 à 5 € |
| **Bouton poussoir** | Bouton tactile 2 broches | < 1 € |
| 2 fils + plaque d'essai (breadboard) | Pour éviter de souder au début | ~3 € |
| Câble USB-C | Programmation et alimentation | déjà en ta possession |
| **Un téléphone Android** | Android 8 ou plus récent (Android 12+ recommandé) | déjà en ta possession |

Les prix et les dimensions sont indicatifs, à confirmer chez le vendeur.

---

## 3. Câblage du bouton

Un seul bouton, **entre la broche D4 et GND**. Aucune résistance n'est nécessaire : on active la résistance interne (pull-up) dans le code.

```
        ESP32-C3
      ┌──────────┐
      │      GP4 ├──────┐
      │          │    [Bouton]
      │      GND ├──────┘
      └──────────┘
```

- **Repos** : la broche lit `HIGH` (tirée vers le haut par le pull-up).
- **Appui** : la broche lit `LOW` (reliée à GND).
- **« D4 »** : sur les cartes ESP32-C3 Super Mini, les broches sont généralement sérigraphiées par leur numéro de GPIO. Dans le code, on utilise donc `GPIO 4`. Sur une carte ESP32 classique (DevKit), « D4 » correspond aussi à `GPIO 4`.
- **LED intégrée** : la carte a une LED (souvent sur le GPIO 8, à vérifier selon le modèle). Elle remplace le vibreur pendant le prototype.
- Le GPIO 4 de l'ESP32-C3 peut aussi réveiller la puce de la veille profonde, ce qui sera utile plus tard.

---

## 4. Contrat BLE (le même pour l'ESP32, le DA14531 et l'appli)

À conserver dans un fichier unique, partagé entre le firmware et l'appli.

| Élément | Valeur |
|---|---|
| Nom BLE | `JEIA Ring` |
| **Service d'alerte** | UUID `4a450001-7c1d-4a8e-9b3f-2f6d8c1a0b11` (exemple à régénérer avec `uuidgen`) |
| **Alert Event** (Notify) | UUID `4a450002-…` : `uint8 type` (1 = alerte, 2 = annulation) + `uint16 compteur` (petit-boutiste) |
| **Alert Ack** (Write) | UUID `4a450003-…` : `uint16 compteur acquitté` + `uint8 statut` (0 = envoyée, 1 = échec) |
| Batterie | Battery Service standard (0x180F), à ajouter plus tard |

Le **compteur** sert à dédupliquer les alertes après une reconnexion.

---

## 5. Firmware ESP32

### Environnement

| Outil | Choix |
|---|---|
| Langage | C++ (framework Arduino) |
| IDE | Arduino IDE ou **PlatformIO** (VS Code) |
| Carte | `ESP32C3 Dev Module` (activer « USB CDC On Boot » pour voir les messages série) |
| Bibliothèque BLE | **NimBLE-Arduino** (légère, adaptée au BLE) |
| Test sans appli | Appli téléphone **nRF Connect** |

### Logique (à implémenter)

1. **Démarrage** : configurer GPIO 4 en `INPUT_PULLUP`, lancer la publicité BLE (nom + service d'alerte).
2. **Appui détecté** : démarrer un chronomètre, avec un anti-rebond de ~30 ms.
3. **À 1 s** : clignotement court de la LED (« ça arme »).
4. **À 2 s** : incrémenter le compteur, envoyer la notification `Alert Event` (type 1).
5. **Relâchement avant 2 s** : annuler.
6. **Renvoi** de l'événement chaque seconde tant qu'aucun ACK n'est reçu, pendant 5 s au maximum.
7. **ACK reçu** (compteur identique, statut 0) : arrêter les renvois, LED qui clignote 2 fois.
8. **Pas d'ACK après 5 s** : LED allumée 1 s (erreur).
9. **Déconnexion** : relancer la publicité.

### Squelette (non testé, à adapter à la version de NimBLE-Arduino)

```cpp
#include <NimBLEDevice.h>

#define BUTTON_PIN 4
#define LED_PIN    8     // à vérifier selon la carte
#define ARM_MS     1000
#define ALERT_MS   2000
#define RETRY_MS   1000
#define ACK_MAX_MS 5000

static const char* SVC_UUID = "4a450001-7c1d-4a8e-9b3f-2f6d8c1a0b11";
static const char* EVT_UUID = "4a450002-7c1d-4a8e-9b3f-2f6d8c1a0b11";
static const char* ACK_UUID = "4a450003-7c1d-4a8e-9b3f-2f6d8c1a0b11";

NimBLECharacteristic* evtChar;
uint16_t counter = 0;
bool ackOk = true;
unsigned long pressStart = 0, alertStart = 0, lastSend = 0;
bool pressed = false, armed = false, sent = false;

class AckCallbacks : public NimBLECharacteristicCallbacks {
  void onWrite(NimBLECharacteristic* c) {
    std::string v = c->getValue();
    if (v.size() >= 3) {
      uint16_t cnt = (uint8_t)v[0] | ((uint8_t)v[1] << 8);
      uint8_t status = (uint8_t)v[2];
      if (cnt == counter && status == 0) {
        ackOk = true;
        for (int i = 0; i < 2; i++) { digitalWrite(LED_PIN, LOW); delay(100);
                                      digitalWrite(LED_PIN, HIGH); delay(150); }
      }
    }
  }
};

void sendAlert() {
  uint8_t data[3] = { 1, (uint8_t)(counter & 0xFF), (uint8_t)(counter >> 8) };
  evtChar->setValue(data, 3);
  evtChar->notify();
}

void setup() {
  pinMode(BUTTON_PIN, INPUT_PULLUP);
  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, HIGH);          // LED éteinte (souvent active à l'état bas)

  NimBLEDevice::init("JEIA Ring");
  NimBLEServer* server = NimBLEDevice::createServer();
  NimBLEService* svc = server->createService(SVC_UUID);
  evtChar = svc->createCharacteristic(EVT_UUID, NIMBLE_PROPERTY::NOTIFY);
  NimBLECharacteristic* ackChar =
      svc->createCharacteristic(ACK_UUID, NIMBLE_PROPERTY::WRITE);
  ackChar->setCallbacks(new AckCallbacks());
  svc->start();
  NimBLEAdvertising* adv = NimBLEDevice::getAdvertising();
  adv->addServiceUUID(SVC_UUID);
  adv->start();
}

void loop() {
  bool down = (digitalRead(BUTTON_PIN) == LOW);
  unsigned long now = millis();

  if (down && !pressed) { pressed = true; pressStart = now; armed = false; sent = false; }
  if (!down && pressed) { pressed = false; }

  if (pressed && !armed && now - pressStart >= ARM_MS) {
    armed = true;
    digitalWrite(LED_PIN, LOW); delay(60); digitalWrite(LED_PIN, HIGH);
  }
  if (pressed && !sent && now - pressStart >= ALERT_MS) {
    sent = true; counter++; ackOk = false; alertStart = now; lastSend = now;
    sendAlert();
  }
  if (!ackOk && now - lastSend >= RETRY_MS) {
    if (now - alertStart > ACK_MAX_MS) {
      ackOk = true;
      digitalWrite(LED_PIN, LOW); delay(1000); digitalWrite(LED_PIN, HIGH);
    } else { sendAlert(); lastSend = now; }
  }
  delay(10);
}
```

*Ce squelette illustre la logique. Les délais bloquants (`delay`) sont acceptables pour un prototype, mais seront remplacés par une gestion par événements sur la vraie puce. L'appairage chiffré (bonding) n'est pas inclus : à ajouter avant tout usage réel.*

### Test sans l'appli

1. Flasher l'ESP32, ouvrir **nRF Connect** sur le téléphone, scanner, se connecter à « JEIA Ring ».
2. Activer les notifications sur `Alert Event`.
3. Appui de 2 s sur le bouton : l'événement apparaît.
4. Écrire un ACK (`compteur` + `00`) dans `Alert Ack` : la LED clignote 2 fois.

---

## 6. Application Android (Kotlin Multiplatform)

### Stack

| Domaine | Choix |
|---|---|
| Langage | Kotlin |
| Structure | **Kotlin Multiplatform** (`commonMain`, `androidMain`), iOS ajouté plus tard |
| UI | Compose Multiplatform + Material 3 (thème rose clair + glassmorphisme, flou via Haze) |
| Architecture | MVVM + Clean Architecture |
| Asynchrone | Coroutines + Flow |
| Injection | Koin |
| BLE | **Kable** (scan, connexion, notifications, écriture) |
| Réseau | Ktor Client |
| Localisation | FusedLocationProvider (`androidMain`) |
| Tâches différées | WorkManager (file d'attente des alertes hors-ligne) |
| Appairage | Companion Device Manager (phase ultérieure) |

### Structure du projet

```
jeia/
├─ shared/
│   └─ src/
│       ├─ commonMain/   UI, ViewModels, domaine, contrat BLE, RingRepository
│       └─ androidMain/  foreground service, localisation, permissions
├─ androidApp/
└─ backend/              (plus tard)
```

Une interface `RingRepository` avec deux implémentations :
- `FakeRingRepository` : simule l'appui et la connexion depuis un menu développeur, pour avancer sans matériel.
- `BleRingRepository` : le vrai BLE via Kable.

### Permissions Android (Android 12 et plus)

- `BLUETOOTH_SCAN` et `BLUETOOTH_CONNECT`
- `ACCESS_FINE_LOCATION`, puis `ACCESS_BACKGROUND_LOCATION` (demande en deux temps)
- `POST_NOTIFICATIONS`
- `FOREGROUND_SERVICE` et `FOREGROUND_SERVICE_CONNECTED_DEVICE`

Chaque permission est expliquée à l'écran avant d'être demandée.

### Flux d'alerte dans l'appli

1. Réception de la notification `Alert Event` (type 1, compteur).
2. Écran plein écran avec compte à rebours d'annulation de 5 s (configurable).
3. Position GPS haute précision.
4. Envoi HTTPS au backend, avec nouvelles tentatives et file persistante.
5. Écriture de l'ACK dans la caractéristique `Alert Ack` : la LED de l'ESP32 clignote 2 fois.
6. Écran « Alerte en cours » : statut, bouton « Je vais bien ».

---

## 7. Phases de travail et état d'avancement

| Phase | Contenu | Résultat attendu | Statut |
|---|---|---|---|
| **0** | Câbler le bouton, flasher un « bouton → LED » | Le bouton fonctionne | Matériel (ESP32) |
| **1** | Firmware BLE complet, test avec **nRF Connect** | Événement reçu, ACK qui fait clignoter la LED | Firmware C++ |
| **2** | Projet Android, thème, navigation, `FakeRingRepository` | Appli qui tourne sans matériel | ✅ **Terminé** |
| **3** | Scan et connexion BLE avec client Android (`BleRingRepository`) | L'appli voit « JEIA Ring » | ✅ **Terminé** |
| **4** | Réception de l'appui, compte à rebours, écriture de l'ACK | L'appui fait apparaître l'alerte, la LED confirme | ✅ **Terminé** |
| **5** | Foreground service (`JeiaRingService`) et maintien en tâche de fond | L'alerte fonctionne écran verrouillé | ✅ **Terminé** |
| **6** | Position GPS (`LocationProvider`) + SMS d'urgence aux contacts | L'alerte part avec position précise et SMS de secours | ✅ **Terminé** |
| **7** | Cercles famille, carte, vie privée, historique | Version complète | En cours (écran contacts prêt) |

---

## 8. Checklist de test

- [ ] Appui de 3 s : une seule alerte, pas de doublon
- [ ] Appui de moins de 2 s : rien n'est envoyé
- [ ] Appli ouverte, écran verrouillé, appli en arrière-plan : l'alerte arrive
- [ ] Appli fermée par balayage : comportement noté (limite connue)
- [ ] Bluetooth coupé / rallumé : reconnexion automatique
- [ ] Ouverture à 10 m, puis à travers un mur : portée constatée
- [ ] Pas de réseau : l'alerte est mise en file puis envoyée au retour
- [ ] Annulation pendant le compte à rebours : aucun envoi
- [ ] Test sur au moins deux téléphones de marques différentes (Samsung, Xiaomi…)

---

## 9. Points d'attention

- **Optimisations de batterie Android** : Samsung, Xiaomi et d'autres coupent les applications en arrière-plan. Prévoir un écran d'aide pour désactiver l'optimisation.
- **Prototype non sécurisé** : sans appairage chiffré, n'importe quel appareil proche peut se connecter. À corriger avant tout usage réel.
- **Fiabilité** : une alerte qui échoue en silence est grave. La LED de confirmation (ACK) permet de le détecter dès le prototype.
- **Passage au DA14531** : le contrat BLE (§4) et la logique d'alerte (§5) sont conservés. Seul le code du firmware est réécrit avec le SDK Renesas.
