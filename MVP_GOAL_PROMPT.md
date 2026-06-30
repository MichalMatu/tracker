# BlueEye Field MVP Goal Prompt

## Operator Decision

Nie rob pelnej aplikacji komercyjnej. Zrob dzisiaj kod w ksztalcie Field MVP: build gotowy do instalacji, diagnostyka widoczna, alerty spojne, ekran mniej zaszumiony, eksport sesji uzyteczny do pierwszych testow terenowych.

Zgoda na sprint 2-3h dotyczy tylko tego zakresu. Dobry prompt nie usuwa ograniczen Androida i nie zastepuje testu na telefonie. Po tym sprincie MVP ma byc gotowe do pierwszych sesji terenowych, nie do sprzedazy w Google Play.

## Master Goal

Doprowadz projekt BlueEye Tracker do "Field MVP build":

- aplikacja buduje sie i przechodzi minimalny quality gate,
- UI nie jest zasypane przypadkowymi termometrami/smart-home/sensorami,
- scanner pokazuje, czy realnie zbiera BLE/Classic po odblokowaniu i po zablokowaniu telefonu,
- alerty watchlist/follow-me/public-safety-like ida przez jedna polityke,
- ustawienia wibracji, dzwieku i heads-up sa przewidywalne,
- Settings ma test alertu i diagnostyke blokad systemowych,
- eksport sesji zawiera dane potrzebne do kalibracji,
- Sony headphones sa traktowane jako smoke test widocznosci/klasyfikacji audio, nie jako tracker,
- aplikacja nie obiecuje wykrycia osoby, intencji ani kazdego trackera.

## Hard Scope

Zostaje w MVP:

- watchlist return alerts,
- passive BLE/Classic observation,
- Follow-Me score jako ostrozna heurystyka,
- public-safety-like evidence jako klasyfikacja sygnalu, nie claim obecnosci sluzb,
- Apple/Google/Samsung/Tile/Chipolo/Fast Pair/audio/generic beacon evidence,
- Sony/audio recognition,
- evidence timeline/export,
- active GATT only jako jawny opt-in.

Wyrzuc albo wylacz z produkcyjnego bindingu MVP:

- termometry,
- wilgotnosciomierze,
- wagi,
- hydrometry,
- smart-home czujniki,
- BBQ/kitchen/environmental parsers,
- inne dekodery, ktore daja "sensor data" bez wartosci dla watchlist/follow-me/evidence MVP.

Nie zostawiaj ukrytej kompatybilnosci. Jezeli parser jest usuwany z MVP, usun jego Hilt binding, testy zalezne od produkcyjnego uzycia i UI copy, ktore sugeruje wsparcie. Pliki parserow mozna usunac, jezeli nie sa potrzebne do testow/fuzz ani wspolnych modeli. Jezeli bezpieczniej jest najpierw tylko odpiac bindingi, zrob to jawnie i opisz w commit message.

## Architecture Rules

- Pracuj na `main`.
- Nie tworz branchy.
- Kotlin only.
- Compose only.
- No XML layouts, no Fragments, no LiveData, no RxJava.
- Domain methods return `Result<T>`.
- UI state przez `StateFlow`.
- Bluetooth Android SDK nie wycieka do UI.
- Nie dodawaj nowych parserow przed pierwszymi sesjami terenowymi.
- Usuwaj stare galezie i fallbacki po migracji callerow.

## Execution Protocol

Po kazdym subgoal:

1. Zmien checklisty w tym pliku z `[ ]` na `[x]` tylko dla wykonanych punktow.
2. Uruchom minimalne testy dotknietych modulow.
3. Jezeli zmiana dotyka runtime/build, uruchom `./gradlew :app:assembleDebug`.
4. Uruchom `git diff --check`.
5. Zrob commit.
6. Wypchnij `main`.
7. Nie przechodz dalej, jezeli obecny subgoal nie jest zielony albo nie ma jawnego blokera.

Standard quality gate:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew qualityCheck
./gradlew :app:assembleDebug
git diff --check
```

Jezeli JDK 17 nie istnieje, napraw toolchain jako pierwszy commit albo zapisz blocker. Nie zgaduj wyniku testow.

## Definition Of Done For Today

Field MVP jest gotowe, gdy:

- debug APK buduje sie lokalnie,
- Settings/Radar pokazuje status skanera i alertow,
- da sie wyslac test alert,
- UI/eksport odroznia brak danych od zablokowanego alertu,
- noisy sensor parsers nie zasmiecaja widoku,
- watchlist/follow-me/public-safety-like alerty respektuja jedna polityke,
- eksport sesji zawiera diagnostyke skanera, decyzje alertowe, evidence, RSSI, movement/baseline i active probe state,
- README albo docs maja krotka instrukcje pierwszej sesji terenowej.

## Subgoal 0: Make The Repo Executable

Cel: zanim zmienisz produkt, projekt musi byc testowalny.

- [ ] Zweryfikuj lokalne JDK 17.
- [ ] Uruchom `./gradlew qualityCheck`.
- [ ] Uruchom `./gradlew :app:assembleDebug`.
- [ ] Jesli build pada, napraw tylko blokery toolchain/build.
- [ ] Nie zmieniaj logiki BLE/alertow w tym subgoal.

Acceptance:

- Build/test dzialaja albo blocker jest udokumentowany w tym pliku.

Commit:

```text
Stabilize Field MVP quality gate
```

## Subgoal 1: Cut MVP Noise From Decoder Pipeline

Cel: aplikacja ma przestac wygladac jak przypadkowy skaner termometrow.

- [ ] Zmapuj Hilt bindings dekoderow w `app/src/main/java/io/blueeye/core/di`.
- [ ] Zostaw w produkcyjnym setcie tylko dekodery potrzebne do MVP: tracker/beacon/audio/public-safety/vendor identity.
- [ ] Odetnij smart-home/environmental/thermometer/humidity/scale/hydrometer parsers od produkcyjnego `Set<BleBeaconDecoder>`.
- [ ] Usun albo zdezaktywuj UI copy pokazujace sensor-first workflow.
- [ ] Zachowaj neutralne raw evidence tam, gdzie przydaje sie do diagnostyki.
- [ ] Dodaj/zmien test, ktory potwierdza, ze MVP decoder set nie zawiera sensor-noise decoders.
- [ ] Nie dodawaj nowych parserow.

Acceptance:

- Radar/Details nie sa zasypywane sensorami niezwiazanymi z celem aplikacji.
- Zostaja tylko dekodery, ktore pomagaja watchlist/follow-me/audio/public-safety/evidence.

Commit:

```text
Trim decoder pipeline to Field MVP scope
```

## Subgoal 2: Add Scanner Runtime Diagnostics

Cel: uzytkownik i developer maja widziec, czy telefon faktycznie zbiera dane.

- [ ] Dodaj domenowy model diagnostyki skanera: scan state, start time, last BLE result, last Classic result, BLE results/min, Classic results/min, dropped queue events, last scan error.
- [ ] Aktualizuj diagnostyke z `BleScanner`, `BleScanSource`, `ClassicScanSource` i `ScannerService`.
- [ ] Wystaw diagnostyke przez domenowy `ScannerRuntimeController` albo rownowazny Flow/StateFlow kontrakt.
- [ ] Pokaz diagnostyke w Settings lub Radar w zwartym panelu.
- [ ] Dodaj testy mapperow/formatterow diagnostyki.
- [ ] Dodaj diagnostyke do eksportu sesji.

Acceptance:

- Po lock screen da sie sprawdzic, czy skaner nadal dostaje wyniki.
- Eksport pokazuje, kiedy ostatni raz widziano BLE/Classic.

Commit:

```text
Add scanner runtime diagnostics
```

## Subgoal 3: Separate Live Scan From Background Watchlist Scan

Cel: usunac falszywe zalozenie, ze broad unfiltered scan callback jest niezawodny po zablokowaniu telefonu.

- [ ] Opisz w kodzie/docs praktyczna konsekwencje Android BLE screen-off restrictions.
- [ ] Zostaw broad scan jako live/foreground observation mode.
- [ ] Dodaj background/watchlist scan path oparty o najlepsze dostepne filtry: MAC, service UUID, manufacturer data albo inne stabilne clues.
- [ ] Jezeli entry nie ma filtra, oznacz je jako `backgroundReliabilityLimited`.
- [ ] Jezeli uzywasz `PendingIntent` scan path, dodaj BroadcastReceiver i mapper intent -> scan event.
- [ ] Dodaj testy dla wyboru scan strategy.
- [ ] Nie polegaj na samym wake lock jako rozwiazaniu produktu.

Acceptance:

- Watchlist ma osobna, bardziej realistyczna sciezke na lock screen.
- Unknown broad detection jest jawnie ograniczona.

Commit:

```text
Separate live scan from background watchlist scan
```

## Subgoal 4: Unify Alert Policy And Dispatch

Cel: ustawienia wibracji/popupow/dzwieku maja dzialac dla wszystkich alertow tak samo.

- [ ] Dodaj domenowy model kategorii alertow: `WATCHLIST_RETURN`, `FOLLOW_ME`, `PUBLIC_SAFETY_SIGNAL`, `TEST`.
- [ ] Dodaj jeden model ustawien: enabled, tray notification, heads-up, sound, vibration.
- [ ] Zastap rozproszone czytanie `WatchlistPreferences` w alert path jednym repo/contractem ustawien alertow.
- [ ] Dodaj jeden `AlertDispatcher` odpowiedzialny za notification, sound i vibration.
- [ ] Zmigruj `TrackerAlertService` i `TacticalAlertService` albo rozbij je tak, zeby nie omijaly dispatcher policy.
- [ ] Usun stare galezie/fallbacki po migracji.
- [ ] Dodaj testy: category off, vibration off, heads-up off, sound off, notification permission missing, cooldown.

Acceptance:

- Jeden test potwierdza, ze wylaczenie vibration blokuje wibracje w kazdej kategorii.
- Watchlist return nie omija globalnej polityki alertow.
- Heads-up off nie kasuje przypadkiem calego systemu alertow.

Commit:

```text
Unify alert policy and dispatch
```

## Subgoal 5: Add Alert Delivery Diagnostics And Test Alert

Cel: uzytkownik ma umiec sprawdzic alert przed spacerem.

- [ ] Dodaj w Settings przycisk "Test alert".
- [ ] Pokaz status `POST_NOTIFICATIONS`.
- [ ] Pokaz status kanalow: importance, enabled/blocked, sound, vibration.
- [ ] Pokaz ostatni wynik dispatchu: posted, blocked by policy, blocked by permission, blocked by channel.
- [ ] Ustaw notification category/visibility/priority/importance zgodnie z Android notifications.
- [ ] Nie rob fake popupow zamiast system notification.
- [ ] Dodaj testy diagnostyki alert delivery.

Acceptance:

- Na odblokowanym i zablokowanym ekranie da sie recznie sprawdzic alert.
- UI mowi, dlaczego alert nie doszedl.

Commit:

```text
Add alert delivery diagnostics and test alert
```

## Subgoal 6: Make Session Export Calibration-Ready

Cel: pierwsze sesje terenowe maja dawac dane, nie tylko logcat.

- [ ] Eksport zawiera scanner diagnostics.
- [ ] Eksport zawiera alert decisions i alert delivery result.
- [ ] Eksport zawiera evidence list, RSSI samples, movement state, baseline state, active probe state.
- [ ] Dodaj pole scenario/notatke sesji: home baseline, walk no tracker, walk with watchlist device, city, car/transit.
- [ ] Dodaj session readiness summary.
- [ ] Zweryfikuj schema version i testy JSON mapperow.

Acceptance:

- Po eksporcie da sie odpowiedziec: czy telefon widzial urzadzenie, czy policy alertow je zablokowala, czy Android zablokowal powiadomienie, dlaczego score wzrosl.

Commit:

```text
Make session export calibration-ready
```

## Subgoal 7: Sony Audio Smoke Path

Cel: sluchawki Sony maja byc testem widocznosci Bluetooth i klasyfikacji audio, nie ryzyka.

- [ ] Zweryfikuj sciezki: BLE Fast Pair, Classic discovery, SDP UUID, name classifier, vendor strategy.
- [ ] Dodaj/napraw evidence dla Sony/audio, jezeli brakuje uzasadnienia w UI/export.
- [ ] Jesli sluchawki nie sa zaobserwowane, pokaz `not observed`, nie zgaduj.
- [ ] Nie podbijaj Follow-Me score tylko dlatego, ze to sluchawki.
- [ ] Dodaj test dla Sony/audio evidence albo popraw istniejacy.

Acceptance:

- Aplikacja pokazuje jedno z trzech: Sony/audio observed, generic audio observed, not observed.

Commit:

```text
Harden Sony audio evidence path
```

## Subgoal 8: First Field Session Readiness Doc

Cel: po buildzie user wie dokladnie, co zrobic na telefonie.

- [ ] Dodaj krotki plik `FIELD_SESSION_CHECKLIST.md` w root albo docs.
- [ ] Checklist zawiera: install APK, grant permissions, disable battery restrictions if needed, test alert unlocked, test alert locked, home baseline, walk no tracker, walk with watchlist device, export.
- [ ] Opisz, czego nie zmieniac przed zebraniem eksportow: progi scoringu i nowe parsery.
- [ ] Podaj minimalny zestaw danych, ktory trzeba przyniesc z telefonu.

Acceptance:

- Projekt jest gotowy do pierwszych realnych sesji bez dodatkowych decyzji produktowych.

Commit:

```text
Add first field session checklist
```

## Stop Conditions

Przerwij sprint i wpisz blocker, jezeli:

- JDK/build nie dziala i nie da sie go naprawic lokalnie,
- alert test jest blokowany przez systemowy permission/channel, ktorego aplikacja nie moze sama obejsc,
- broad scan nie daje wynikow po screen-off i nie ma stabilnych watchlist filtrow dla danego urzadzenia,
- zmiana wymagalaby powrotu do legacy Android UI/API,
- testy pokazuja, ze Follow-Me score miesza baseline z ruchem.

## Final Decision After Sprint

Po Subgoal 8 decyzja:

- GO: instaluj debug APK i rob pierwsze sesje.
- PIVOT: jesli broad unknown detection jest slabe, pozycjonuj app jako BLE evidence/watchlist tool.
- STOP: jesli watchlist alert i test alert nie dzialaja na realnym telefonie po lock screen.
