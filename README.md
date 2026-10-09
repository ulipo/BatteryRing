# BatteryRing

BatteryRing mostra un settore circolare colorato sopra il foro della fotocamera frontale. L'ampiezza del settore è continua e direttamente proporzionale alla percentuale di batteria: 100% = 360°, 50% = 180°, 1% = 3,6°. Il cerchio può avere un bordo di colore e spessore configurabili; con spessore 0 il bordo è disattivato.

## Funzioni

- Overlay `TYPE_ACCESSIBILITY_OVERLAY`, pensato per essere visibile anche sopra una status bar opaca.
- Nessun permesso `SYSTEM_ALERT_WINDOW`.
- Il servizio di accessibilità non legge il contenuto delle finestre (`canRetrieveWindowContent=false`) e ignora gli eventi ricevuti.
- L'overlay è `NOT_TOUCHABLE` e `NOT_FOCUSABLE`: non intercetta tocchi né focus.
- Aggiornamento della batteria tramite `ACTION_BATTERY_CHANGED`, senza polling continuo.
- Posizione X/Y, diametro, colore di riempimento, colore/spessore del bordo e origine del settore configurabili in tempo reale.
- Interfaccia Material 3 con palette Nord dark.
- Rilevamento automatico del `DisplayCutout` quando il dispositivo lo espone.
- Persistenza delle impostazioni con `SharedPreferences`.
- Nessuna notifica permanente: il ciclo di vita dell'overlay è gestito dal servizio di accessibilità.

## Primo avvio

1. Installa e apri BatteryRing.
2. Tocca **Apri impostazioni Accessibilità**.
3. Abilita **BatteryRing overlay**.
4. Torna nell'app.
5. Prova **Rileva automaticamente il foro**.
6. Se necessario, correggi X/Y, diametro e spessore con gli slider.
7. Scegli il colore con il color picker HSV oppure con i preset rapidi.

L'indicatore resta attivo quando la Activity viene chiusa, finché il servizio di accessibilità rimane abilitato e l'interruttore **Indicatore attivo** è acceso.

## Build con GitHub Actions

Il workflow `.github/workflows/android-build.yml` compila e firma soltanto l'APK **release**. Dopo un push su GitHub:

1. apri la scheda **Actions**;
2. scegli **Android build**;
3. apri il job completato;
4. scarica l'artifact `BatteryRing-<build>`.

L'APK prodotto è `BatteryRing-<build>-release.apk`. La firma persistente è descritta in `SIGNING.md`.

## Build locale

Requisiti:

- JDK 17
- Android SDK Platform 36
- Android Build Tools 36.0.0
- Gradle 9.6.0

Esegui:

```bash
gradle :app:assembleRelease
```

## Nota sugli store

L'uso di un `AccessibilityService` per una funzione non strettamente assistiva può richiedere dichiarazioni, disclosure e conformità alle policy dello store. Per installazione personale/sideload questo non influisce sul funzionamento tecnico dell'app.

## Limitazioni

La gestione della sovrapposizione alle finestre di sistema può avere differenze tra firmware OEM. `TYPE_ACCESSIBILITY_OVERLAY` è la strada pubblica più adatta per questo caso, ma alcuni produttori possono introdurre comportamenti propri. La calibrazione manuale serve anche a compensare queste differenze.

## Changes in 1.1

- Battery arc now runs counter-clockwise.
- Arc origin is configurable from 0° to 359° (0° = 12 o'clock).
- X/Y calibration uses 0.25 dp steps with fine-adjustment buttons.
- Vertical calibration is limited to the upper 120 dp of the screen.
- White is available as a preset color.
- Automatic punch-hole detection is performed from the accessibility overlay itself, so detection and drawing share the same coordinate system. Diagnostic cutout values are shown in the app.
- GitHub Actions firma la build release con un certificato BatteryRing persistente. Vedi `SIGNING.md`.

## Changes in 1.2

### Screen rotation

The camera centre is stored in the display's natural coordinate system. The overlay transforms that point for `ROTATION_0`, `ROTATION_90`, `ROTATION_180` and `ROTATION_270`, so a portrait calibration follows the same physical punch-hole when the device rotates. The accessibility overlay is recreated on configuration changes to pick up the new full-screen bounds and cutout geometry.

## Licenza

BatteryRing è distribuito secondo la **GNU General Public License v3.0 o successiva** (`GPL-3.0-or-later`). Vedi `LICENSE`.
