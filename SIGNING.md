# BatteryRing signing

BatteryRing usa una chiave di firma persistente per tutte le build release pubblicate. La chiave privata **non è conservata nel repository**.

## Come funziona

GitHub Actions riceve il keystore tramite secret, lo ricostruisce solo nel filesystem temporaneo del runner, firma l'APK e poi lo elimina.

I secret richiesti sono:

- `BATTERYRING_KEYSTORE_BASE64`
- `BATTERYRING_STORE_PASSWORD`
- `BATTERYRING_KEY_ALIAS`
- `BATTERYRING_KEY_PASSWORD`

Il workflow firma soltanto la build **release**.

## Sicurezza

Nel repository sono esclusi tramite `.gitignore`:

- `*.jks`
- `*.keystore`
- `*.p12`
- `keystore.properties`
- copie `*.base64`

Il workflow:

- usa un token GitHub Actions con permesso `contents: read`;
- non esegue la firma su eventi `pull_request`;
- ricostruisce il keystore esclusivamente in `$RUNNER_TEMP`;
- imposta permessi file `600`;
- elimina il keystore temporaneo con uno step `if: always()`.

## Backup

Conserva una copia offline del keystore originale in un luogo sicuro. Se la chiave viene persa, non sarà possibile distribuire APK aggiornabili sopra le versioni già installate con quella firma.

## Build locale

Per una release firmata in locale puoi usare un file `keystore.properties` non tracciato da Git oppure le stesse variabili d'ambiente usate dalla CI.

Esempio:

```bash
gradle :app:assembleRelease
```

## Ulteriore hardening consigliato

Per proteggere ulteriormente la chiave puoi spostare i quattro secret in un **GitHub Environment** dedicato, ad esempio `release`, e applicare regole di protezione/approvazione all'Environment. Il workflow potrà poi usare quell'Environment senza cambiare la firma dell'app.
