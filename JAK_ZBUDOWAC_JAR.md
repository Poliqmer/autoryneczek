# Jak zbudować JAR na GitHubie

1. Zaloguj się na GitHub i utwórz nowe repozytorium.
2. Wgraj **zawartość folderu `anarchia-autorynek`**, a nie sam ZIP jako pojedynczy plik. Na stronie repozytorium użyj `Add file` → `Upload files`.
3. Zatwierdź wgrane pliki (`Commit changes`).
4. Otwórz zakładkę `Actions` i wybierz workflow `Build`.
5. Kliknij `Run workflow`, jeśli przycisk jest dostępny. Workflow uruchamia się też automatycznie po `push`.
6. Poczekaj na zielony status `Success`.
7. Na stronie ukończonego uruchomienia znajdź `Artifacts` i pobierz `anarchia-autorynek`.
8. Rozpakuj artefakt i skopiuj właściwy plik `.jar` do `%appdata%\\.minecraft\\mods`.

W Minecraft Launcherze uruchom profil Fabric dla **Minecraft 1.21.11**. W folderze `mods` musi być również kompatybilny Fabric API.

## Ważne

- Projekt jest skonfigurowany pod Minecraft **1.21.11**, Java **21**, Fabric Loader **0.19.5** i Fabric API **0.141.6+1.21.11**.
- Kod nie został skompilowany w tym środowisku, więc sukces workflow jest potrzebny do potwierdzenia, że kompiluje się bez błędów.
- Mod wykonuje automatyczne kliknięcia na rynku. Sprawdź regulamin serwera przed użyciem — serwer może tego zabraniać.
