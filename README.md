# BatteryRing

BatteryRing mostra un arco colorato attorno alla fotocamera frontale. La lunghezza dell'arco è continua e direttamente proporzionale alla percentuale di batteria: 100% = 360°, 50% = 180°, 1% = 3,6°.

## Funzioni

- Overlay `TYPE_ACCESSIBILITY_OVERLAY`, pensato per essere visibile anche sopra una status bar opaca.
- Nessun permesso `SYSTEM_ALERT_WINDOW`.
- Il servizio di accessibilità non legge il contenuto delle finestre (`canRetrieveWindowContent=false`) e ignora gli eventi ricevuti.
- L'overlay è `NOT_TOUCHABLE` e `NOT_FOCUSABLE`: non intercetta tocchi né focus.
- Aggiornamento della batteria tramite `ACTION_BATTERY_CHANGED`, senza polling continuo.
- Posizione X/Y, diametro, spessore e colore configurabili in tempo reale.
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
7. Scegli il colore con i preset o inserendo `#RRGGBB`.

L'anello resta attivo quando la Activity viene chiusa, finché il servizio di accessibilità rimane abilitato e l'interruttore **Indicatore attivo** è acceso.

## Build con GitHub Actions

Il workflow `.github/workflows/android-build.yml` compila automaticamente l'APK debug. Dopo un push su GitHub:

1. apri la scheda **Actions**;
2. scegli **Android build**;
3. apri il job completato;
4. scarica l'artifact **BatteryRing-debug**.

L'APK si trova anche localmente in `app/build/outputs/apk/debug/app-debug.apk` dopo `gradle :app:assembleDebug`.

## Build locale

Requisiti:

- JDK 17
- Android SDK Platform 37
- Android Build Tools 36.0.0
- Gradle 9.6.0

Esegui:

```bash
gradle :app:assembleDebug
```

## Nota sugli store

L'uso di un `AccessibilityService` per una funzione non strettamente assistiva può richiedere dichiarazioni, disclosure e conformità alle policy dello store. Per installazione personale/sideload questo non influisce sul funzionamento tecnico dell'app.

## Limitazioni

La gestione della sovrapposizione alle finestre di sistema può avere differenze tra firmware OEM. `TYPE_ACCESSIBILITY_OVERLAY` è la strada pubblica più adatta per questo caso, ma alcuni produttori possono introdurre comportamenti propri. La calibrazione manuale serve anche a compensare queste differenze.
