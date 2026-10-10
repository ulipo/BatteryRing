# BatteryRing

BatteryRing è una piccola app Android che visualizza il livello della batteria direttamente attorno al foro della fotocamera frontale.

L'indicatore è un settore circolare pieno: 100% = cerchio completo, 50% = mezzo cerchio. Colore di riempimento, bordo, diametro, posizione, origine e senso di rotazione sono configurabili.

## Funzioni principali

- indicatore sempre visibile sopra la barra di stato tramite `TYPE_ACCESSIBILITY_OVERLAY`;
- settore proporzionale in modo continuo alla percentuale di batteria;
- senso di rotazione selezionabile: orario o antiorario;
- colore di riempimento e bordo configurabili con color picker;
- spessore bordo configurabile, con `0` = nessun bordo;
- calibrazione fine di posizione e diametro;
- rilevamento automatico del `DisplayCutout` quando disponibile;
- gestione corretta di portrait e landscape;
- interfaccia Material 3 con palette Nord;
- nessun polling continuo della batteria;
- overlay compatto e sospensione a schermo spento per ridurre i consumi.

## Primo avvio

1. Installa e apri BatteryRing.
2. Tocca **Apri impostazioni Accessibilità**.
3. Abilita **BatteryRing overlay**.
4. Torna nell'app e attiva **Indicatore attivo**.
5. Usa **Rileva automaticamente il foro** oppure regola manualmente posizione e diametro.

Il servizio di accessibilità viene usato esclusivamente per mostrare l'overlay sopra la UI di sistema. BatteryRing non legge il contenuto delle altre app e l'overlay non intercetta i tocchi.

## Build

Il workflow GitHub Actions compila e firma solamente la build **release**.

Build locale:

```bash
gradle :app:assembleRelease
```

La configurazione della firma persistente è documentata in [`SIGNING.md`](SIGNING.md).

## Vibe-coding

BatteryRing è stato sviluppato in modalità **vibe-coding**, attraverso un processo iterativo di progettazione, generazione del codice, test su dispositivo reale e correzione dei problemi emersi durante l'uso.

Il codice va quindi considerato come un progetto sperimentale e pragmatico: è consigliabile verificarlo e testarlo sul proprio dispositivo prima di affidargli comportamenti critici.

## Compatibilità

Il comportamento degli overlay e del `DisplayCutout` può variare tra firmware e produttori Android. Per questo è disponibile anche la calibrazione manuale.

## Licenza

BatteryRing è distribuito secondo la **GNU General Public License v3.0 o successiva** (`GPL-3.0-or-later`). Vedi [`LICENSE`](LICENSE).
