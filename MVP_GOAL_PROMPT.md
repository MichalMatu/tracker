# BlueEye MVP Goal Prompt

## Master Goal

Doprowadz projekt BlueEye Tracker do pierwszej wersji terenowej MVP, ktora ma realna wartosc uzytkowa:

- niezawodnie pokazuje, czy telefon faktycznie zbiera sygnaly BLE/Classic w czasie sesji,
- uczciwie rozroznia ograniczenia Androida, broad scan, filtered/watchlist scan i aktywne zbieranie,
- ma spojny system alertow: watchlist, follow-me i public-safety-like signals,
- pozwala uruchomic pierwsze sesje terenowe z eksportem danych i kalibracja progow,
- nie obiecuje wykrycia osoby, intencji ani kazdego urzadzenia sledzacego.

Pracuj bez branchy, bez XML layoutow, bez Fragments, bez LiveData, bez RxJava. Uzywaj Kotlin, Jetpack Compose, Hilt, Flow/StateFlow, Result<T>, Navigation Compose i istniejacej architektury feature-first. Wszystkie zmiany rob na `main`.

## Execution Rules

Po kazdym podgolu:

1. Uruchom minimalne testy dla dotknietych modulow.
2. Jesli podgol dotyka build/runtime, uruchom tez `./gradlew :app:assembleDebug`.
3. Zaktualizuj checklisty w tym pliku: zmien `[ ]` na `[x]` tylko dla faktycznie wykonanych punktow.
4. Zrob commit z konkretnym komunikatem.
5. Wypchnij `main`.
6. Nie przechodz do nastepnego podgolu, jesli obecny nie ma zielonej weryfikacji albo jawnie opisanego blokera.

Minimalna weryfikacja lokalna:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew qualityCheck
./gradlew :app:assembleDebug
git diff --check
```

Jezeli JDK 17 nie istnieje lokalnie, pierwszy podgol polega na naprawie toolchainu albo udokumentowaniu dokladnej blokady. Nie zgaduj wynikow testow.

## Non-Negotiable Product Line

Aplikacja nie ma byc "magiczny wykrywacz sledzenia". MVP ma byc narzedziem dowodowym:

- "co telefon zaobserwowal",
- "kiedy to zaobserwowal",
- "dlaczego tak sklasyfikowal",
- "jak pewna jest klasyfikacja",
- "czy alert byl technicznie mozliwy i faktycznie wyslany".

Jesli Android ogranicza skanowanie po zablokowaniu ekranu, UI i eksport maja to pokazac zamiast ukrywac.

## Subgoal 0: Toolchain And Quality Gate

Cel: projekt musi dac sie budowac i testowac lokalnie przed zmianami produktowymi.

- [ ] Zweryfikuj JDK 17 i ustaw `JAVA_HOME` zgodnie z `docs/QUALITY_GATE.md`.
- [ ] Uruchom `./gradlew qualityCheck`.
- [ ] Uruchom `./gradlew :app:assembleDebug`.
- [ ] Jesli quality gate pada przez istniejace problemy, zapisz dokladna liste awarii i napraw tylko blokery potrzebne do dalszej pracy.
- [ ] Nie zmieniaj logiki BLE/alertow w tym podgolu.

Acceptance:

- `qualityCheck` i `assembleDebug` przechodza albo blocker jest jednoznacznie opisany w commit message i w tym pliku.

Suggested commit:

```text
Stabilize local quality gate
```

## Subgoal 1: Scanner Runtime Diagnostics For Field Sessions

Cel: przed poprawianiem detekcji aplikacja ma mierzyc, czy skaner w ogole dziala w realnych warunkach.

- [ ] Dodaj domenowy model diagnostyki skanera, np. runtime state, last BLE result time, last Classic result time, BLE results/min, Classic results/min, dropped queue events, scan start time, screen/lock related state if available.
- [ ] Przekazuj diagnostyke z `BleScanner`, `BleScanSource`, `ClassicScanSource` i `ScannerService` do domenowego API przez Flow/StateFlow.
- [ ] Pokaz diagnostyke w Settings albo Radar jako sekcje developersko-terenowa, bez marketingowego tekstu.
- [ ] Dodaj eksport diagnostyki do sesji JSON, z timestampami i licznikami.
- [ ] Dodaj testy jednostkowe dla formatowania/mapperow diagnostyki.

Acceptance:

- Uzytkownik widzi, czy po zablokowaniu telefonu dalej pojawiaja sie wyniki.
- Eksport sesji pozwala porownac: ekran wlaczony, ekran zablokowany, 5/15/30 minut.
- Brak claimow, ze background broad scan jest niezawodny.

Suggested commit:

```text
Add scanner runtime diagnostics for field sessions
```

## Subgoal 2: Background Scanning Strategy

Cel: naprawic problem "powiadomienia nie dzialaja po zablokowaniu" u zrodla, czyli rozroznic brak alertu od braku danych.

- [ ] Sprawdz oficjalne ograniczenia Androida dla BLE background scanning i zapisz w komentarzu/README tylko praktyczna konsekwencje, bez dlugiej dokumentacji.
- [ ] Nie udawaj, ze unfiltered `startScan(..., ScanCallback)` bedzie niezawodny po screen-off.
- [ ] Dla broad discovery zostaw tryb foreground/live scan i opomiaruj jego skutecznosc.
- [ ] Dla watchlist/background alerts dodaj filtered scan path dla znanych fingerprintow/MAC/service/manufacturer clues tam, gdzie da sie stworzyc sensowny `ScanFilter`.
- [ ] Jesli uzywasz `PendingIntent` scan path, dodaj osobny odbiornik i testowalna warstwe mappera intent -> scan event.
- [ ] Jesli nie da sie stworzyc filtra dla danego watchlist entry, pokaz w UI/export "background reliability limited".
- [ ] Nie dodawaj agresywnego wake-lock/probing obejscia jako glownego rozwiazania.

Acceptance:

- Watchlist ma najlepsza dostepna sciezke dla lock screen.
- Unknown broad detection pozostaje opisana jako ograniczona przez Androida.
- Diagnostyka pokazuje, czy problemem byl brak scan result, decyzja alert policy, permission, czy notification channel.

Suggested commit:

```text
Separate live and background watchlist scanning paths
```

## Subgoal 3: Unified Alert Settings And Dispatcher

Cel: przelaczniki wibracji, dzwieku i popupow maja dzialac przewidywalnie dla wszystkich typow alertow.

- [ ] Zaprojektuj jeden domenowy model ustawien alertow dla kategorii: watchlist return, follow-me, public-safety-like.
- [ ] Kazda kategoria ma jawne ustawienia: enabled, notification/tray, heads-up, sound, vibration.
- [ ] Zastap rozproszone uzycie `WatchlistPreferences` w alert path jednym `AlertSettingsRepository` albo rownowaznym kontraktem domenowym.
- [ ] Zastap rozproszone wywolania notyfikacji/wibracji jednym `AlertDispatcher`.
- [ ] Usun stare wewnetrzne galezie/fallbacki po migracji aktualnych callerow. Nie zostawiaj redundantnej kompatybilnosci API.
- [ ] Dodaj testy dla kazdej kombinacji: master off, category off, vibration off, heads-up off, notification permission missing.
- [ ] Dodaj w Settings proste sterowanie wszystkimi kategoriami.

Acceptance:

- Wylaczenie wibracji znaczy: zadna kategoria, ktora korzysta z tego ustawienia, nie wibruje.
- Wylaczenie heads-up nie kasuje przypadkiem calej historii ani nie wplywa na dzwiek/wibracje, chyba ze UI jawnie tak mowi.
- Watchlist return nie omija globalnej polityki alertow.

Suggested commit:

```text
Unify alert settings and dispatch policy
```

## Subgoal 4: Notification Reliability Diagnostics And Test Alert

Cel: uzytkownik ma wiedziec, czy alert moze pojawic sie na zablokowanym telefonie.

- [ ] Dodaj "Test alert" w Settings dla kazdej kategorii lub jeden test z wyborem kategorii.
- [ ] Pokaz status: `POST_NOTIFICATIONS`, notification channel importance, heads-up enabled, sound enabled, vibration enabled.
- [ ] Jesli permission/channel blokuje alert, pokaz konkretny stan w UI.
- [ ] Dodaj lock-screen-safe notification ustawienia tam, gdzie to ma sens: priority/importance, visibility, category.
- [ ] Nie uzywaj fake popupow jako substytutu notyfikacji systemowej.
- [ ] Dodaj testy dla `AlertContentFormatter`, `AlertDispatcher` i diagnostyki kanalow.

Acceptance:

- Da sie recznie wyslac test alert i zobaczyc, czy system go blokuje.
- Eksport albo diagnostyka odroznia "alert policy blocked" od "Android notification blocked".

Suggested commit:

```text
Add alert delivery diagnostics and test alert
```

## Subgoal 5: Field Session Export Contract

Cel: pierwsze sesje terenowe maja produkowac dane, z ktorych da sie podjac decyzje o progach i detekcji.

- [ ] Upewnij sie, ze eksport zawiera: scanner diagnostics, alert decisions, notification delivery result, scan counts, evidence, RSSI samples, movement/baseline state, active probe state.
- [ ] Dodaj `sessionScenario` albo notatke sesji: home baseline, walk without tracker, walk with known device, city, car/transit.
- [ ] Dodaj czytelny "session readiness" status: czy sesja ma wystarczajaco danych do kalibracji.
- [ ] Dodaj testy JSON mapperow dla nowych pol.
- [ ] Zachowaj schemat eksportu jako jawnie wersjonowany.

Acceptance:

- Po jednej sesji terenowej da sie odpowiedziec: ile danych zebrano, czy screen-off przerwal skan, czy alert mial szanse dojsc, dlaczego score wzrosl.

Suggested commit:

```text
Extend session export for field calibration
```

## Subgoal 6: Sony Headphones Recognition Smoke Path

Cel: nie traktowac sluchawek Sony jako trackerow, ale uzyc ich jako realnego testu rozpoznawania i widocznosci Bluetooth.

- [ ] Dodaj reczny scenariusz testowy w aplikacji/eksportach: Sony headphones nearby/pairing/connected/off.
- [ ] Zweryfikuj BLE Fast Pair, Classic discovery, SDP UUID i name-based classification path.
- [ ] Jesli sluchawki nie sa widoczne, UI/export ma pokazac "not observed", a nie bledna klasyfikacje.
- [ ] Dodaj testy dla istniejacego Sony/Fast Pair/classic evidence mapping, tylko tam gdzie brakuje pokrycia.
- [ ] Nie podbijaj follow-me score dla zwyklych sluchawek bez niezaleznego patternu ruchu.

Acceptance:

- Aplikacja potrafi wyjasnic jedno z trzech: wykryto jako Sony/audio, wykryto tylko generic audio, albo telefon nie zaobserwowal urzadzenia.

Suggested commit:

```text
Harden Sony audio recognition evidence
```

## Subgoal 7: First Field Session Checklist

Cel: przygotowac projekt do pierwszych realnych sesji, bez dalszego kodowania parserow.

- [ ] Zbuduj debug APK.
- [ ] Zainstaluj na telefonie.
- [ ] Wykonaj test alertu na odblokowanym i zablokowanym ekranie.
- [ ] Wykonaj 10-min home baseline.
- [ ] Wykonaj 10-min spacer bez znanego trackera.
- [ ] Wykonaj 10-min spacer z wlasnym znanym urzadzeniem/watchlist item.
- [ ] Wyeksportuj kazda sesje.
- [ ] Oznacz false positives i known safe.
- [ ] Nie zmieniaj progow scoringu przed przejrzeniem eksportow.

Acceptance:

- Sa minimum trzy eksporty z realnego telefonu.
- Jest lista konkretnych false positives/false negatives.
- Nastepny etap to kalibracja progow na danych, nie zgadywanie.

Suggested commit:

```text
Document first field session checklist
```

## Stop Conditions

Przerwij i napisz blocker, jesli:

- build/test nie startuje przez toolchain,
- Android permission/channel blokuje alert i nie da sie tego naprawic kodem,
- broad scan nie daje wynikow po screen-off mimo foreground service,
- zmiana wymaga odejscia od Compose/Clean Architecture/Hilt/Flow,
- dane terenowe pokazuja, ze Follow-Me score nie odroznia baseline od ruchu.

## Commercial Decision Gate

Po wykonaniu Subgoal 7 podejmij decyzje:

- GO: watchlist alerts sa niezawodne, eksport jest uzyteczny, diagnostyka wyjasnia ograniczenia.
- PIVOT: broad unknown tracker detection jest za slabe, ale app ma wartosc jako BLE evidence/session tool.
- STOP: alerty/watchlist nie dzialaja na realnym telefonie po lock screen i nie ma platformowej sciezki obejscia.

