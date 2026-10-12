# Anarchia AutoRynek (Fabric, Minecraft Java 1.21.11)

Kliencki mod w Javie. Komenda `/autorynek` otwiera menu po polsku, w którym włączasz lub wyłączasz mod,
dodajesz przedmioty eventowe i zestawy oraz ustawiasz maksymalną cenę zakupu.
Ustawienia zapisują się w `config/anarchia_autorynek.json`.

## Wersje (zweryfikowane dla 1.21.11)

| Element | Wersja |
|---|---|
| Minecraft | 1.21.11 |
| Java (JDK) | 21 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.141.6+1.21.11 |
| Mapowania | Yarn 1.21.11+build.6 |
| Fabric Loom | 1.17-SNAPSHOT (`net.fabricmc.fabric-loom-remap`) |
| Gradle | 9.5.1 |

## Budowanie JAR-a na Windows

W tym ZIP-ie nie ma kompletnego Gradle Wrappera (`gradlew.bat` i `gradle-wrapper.jar`), więc nie uruchamiaj `gradlew.bat` — tego pliku tutaj nie ma.

**Najprostsza metoda bez instalowania Gradle lokalnie: GitHub Actions**

1. Utwórz na GitHubie nowe repozytorium i wgraj do niego zawartość tego folderu projektu (`build.gradle`, `settings.gradle`, `gradle.properties`, `src`, `.github` itd.).
2. Otwórz zakładkę **Actions** w repozytorium.
3. Wybierz workflow **Build** i kliknij **Run workflow**. Jeśli workflow nie pojawia się, najpierw zatwierdź pliki w gałęzi domyślnej.
4. Po zakończeniu zadania otwórz jego stronę i w sekcji **Artifacts** pobierz `anarchia-autorynek`.
5. Rozpakuj pobrany artefakt. W środku będzie JAR moda. Nie wybieraj pliku `-sources.jar`, jeśli występuje.

Workflow używa JDK 21 i Gradle 9.5.1. Pierwsze budowanie wymaga internetu i może potrwać kilka minut.

**Budowanie lokalnie (jeśli masz Gradle 9.5.1):**

W katalogu projektu otwórz CMD i uruchom `gradle build`. Gotowy plik powinien trafić do `build\\libs\\anarchia-autorynek-1.0.0.jar`.

Nie mogłem potwierdzić kompilacji w tym środowisku, dlatego traktuj JAR z workflow jako gotowy dopiero wtedy, gdy zadanie zakończy się statusem **Success**.

## Instalacja

1. Zainstaluj Fabric Loader dla 1.21.11.
2. Wrzuć do folderu `mods`: Fabric API `0.141.6+1.21.11` (lub nowsze dla 1.21.11) oraz `anarchia-autorynek-1.0.0.jar`.

## Komendy

- `/autorynek` – menu
- `/autorynek on` / `/autorynek off` – włącz / wyłącz
- `/autorynek status` – stan i liczba prób zakupu w tej sesji
- `/autorynek diagnoza` – po wpisaniu otwórz rynek serwera; mod wypisze tytuł okna, nazwy i opisy przedmiotów oraz rozpoznane ceny (do czatu i `logs/latest.log`)

## Jak działa kupowanie (i czego mod nie wie)

Mod działa na zwykłym oknie skrzyni (GenericContainerScreen), które większość serwerów używa jako rynek:

1. Co `refreshIntervalTicks` (domyślnie 10 s) sam wpisuje komendę rynku (`marketCommand`, domyślnie `rynek`).
2. Gdy otworzy się okno z tytułem zawierającym `marketTitleContains`, czyta nazwy i opisy przedmiotów.
3. Jeśli nazwa pasuje do pozycji z listy, a w opisie jest rozpoznana cena nieprzekraczająca limitu, klika ofertę.
4. Jeśli pojawi się okno potwierdzenia (tytuł z `confirmTitleKeywords`), klika przycisk z `confirmButtonKeywords`.
5. Oferty bez rozpoznanej ceny są pomijane, nigdy kupowane na ślepo.

Mod nie zna układu rynku Anarchia.gg. Jeśli tytuł okna, położenie ceny w opisie albo przycisk potwierdzenia
wyglądają inaczej, trzeba to dopasować w pliku konfiguracji (najłatwiej po `/autorynek diagnoza`).
Mod nie omija żadnych zabezpieczeń serwera. Sprawdź regulamin serwera, bo automatyzacja zakupów może być na nim niedozwolona.

## Ustawienia zaawansowane (plik JSON)

`marketCommand`, `autoOpenMarket`, `refreshIntervalTicks`, `marketTitleContains`, `settleTicks`,
`afterPurchaseCooldownTicks`, `retryBlockTicks`, `maxPurchasesPerSession` (0 = bez limitu; po osiągnięciu mod się wyłącza),
`maxPages`, `clickMode` (`PICKUP` lub `QUICK_MOVE`), `priceRegex` (grupa 1 = liczba, grupa 2 = sufiks k/m),
`matchLore`, `nextPageKeywords`, `confirmTitleKeywords`, `confirmButtonKeywords`.

W polu „nazwa" w menu możesz wpisać fragment nazwy (wielkość liter i polskie znaki nie mają znaczenia)
albo `id:minecraft:totem_of_undying`.
