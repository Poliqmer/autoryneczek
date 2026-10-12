package pl.anarchia.autorynek;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Ustawienia moda zapisywane w pliku config/anarchia_autorynek.json.
 * Zapis następuje przy każdej zmianie w menu oraz przy zamykaniu gry.
 */
public final class AutoRynekConfig {
    public static final String DEFAULT_PRICE_REGEX =
            "(?i)(?:cena|koszt|price)[^0-9]{0,8}([0-9](?:[0-9 .,\\u00a0]*[0-9])?)\\s*([km](?!\\p{L}))?";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static AutoRynekConfig instance = new AutoRynekConfig();

    /** Pojedynczy przedmiot eventowy lub zestaw do kupowania. */
    public static final class Entry {
        /** Fragment nazwy przedmiotu (bez kolorów i polskich znaków nie ma znaczenia) albo "id:minecraft:totem_of_undying". */
        public String name = "";
        /** Maksymalna cena; 0 = użyj domyślnej ceny maksymalnej. */
        public double maxPrice = 0;
        /** "EVENT" albo "ZESTAW" – tylko etykieta w menu. */
        public String category = "EVENT";

        public Entry() {
        }

        public Entry(String name, double maxPrice, String category) {
            this.name = name;
            this.maxPrice = maxPrice;
            this.category = category;
        }
    }

    // --- Ustawienia widoczne w menu ---
    public boolean enabled = false;
    public double defaultMaxPrice = 1000;
    public List<Entry> entries = new ArrayList<>();

    // --- Ustawienia zaawansowane (edytowalne w pliku JSON) ---
    /** Komenda otwierająca rynek (bez ukośnika). */
    public String marketCommand = "rynek";
    /** Czy mod sam otwiera rynek co refreshIntervalTicks. */
    public boolean autoOpenMarket = true;
    /** Co ile ticków (20 = 1 s) mod otwiera rynek. */
    public int refreshIntervalTicks = 200;
    /** Fragment tytułu okna rynku (bez wielkości liter i polskich znaków). */
    public String marketTitleContains = "rynek";
    /** Ile ticków odczekać po otwarciu okna / zmianie strony, zanim zacznie skanowanie. */
    public int settleTicks = 10;
    /** Przerwa po kliknięciu oferty. */
    public int afterPurchaseCooldownTicks = 40;
    /** Ta sama oferta (nazwa + cena) nie będzie klikana ponownie przez tyle ticków. */
    public int retryBlockTicks = 600;
    /** Maks. liczba prób zakupu w jednej sesji gry; 0 = bez limitu. Po osiągnięciu mod się wyłącza. */
    public int maxPurchasesPerSession = 0;
    /** Ile stron rynku przejrzeć (tylko gdy mod sam otworzył rynek i znajdzie przycisk następnej strony). */
    public int maxPages = 3;
    /** "PICKUP" (zwykłe kliknięcie) albo "QUICK_MOVE" (shift+klik). */
    public String clickMode = "PICKUP";
    /** Regex ceny w opisie przedmiotu: grupa 1 = liczba, grupa 2 = opcjonalny sufiks k/m. */
    public String priceRegex = DEFAULT_PRICE_REGEX;
    /** Czy dopasowywać też do linii opisu (lore), a nie tylko do nazwy. */
    public boolean matchLore = false;
    public List<String> nextPageKeywords = new ArrayList<>(Arrays.asList("nastepna", "nastepn", "dalej", "next", "→", ">>"));
    public List<String> confirmTitleKeywords = new ArrayList<>(Arrays.asList("potwierdz", "zakup", "kupno", "confirm"));
    public List<String> confirmButtonKeywords = new ArrayList<>(Arrays.asList("potwierdz", "kup", "tak", "zaakceptuj", "confirm"));

    private transient Pattern cachedPattern;
    private transient String cachedPatternSource;

    public static AutoRynekConfig get() {
        return instance;
    }

    public static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("anarchia_autorynek.json");
    }

    public static synchronized void load() {
        Path p = path();
        if (Files.exists(p)) {
            try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                AutoRynekConfig loaded = GSON.fromJson(r, AutoRynekConfig.class);
                if (loaded != null) {
                    instance = loaded;
                }
            } catch (Exception e) {
                AutoRynekClient.LOGGER.error("Nie udało się wczytać konfiguracji, używam ustawień domyślnych", e);
                // uszkodzony plik zachowujemy jako kopię, żeby go nie nadpisać bez śladu
                try {
                    Files.move(p, p.resolveSibling("anarchia_autorynek.json.uszkodzony"), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) {
                    // nic więcej nie zrobimy
                }
                instance = new AutoRynekConfig();
            }
        }
        instance.sanitize();
        save();
    }

    public static synchronized void save() {
        Path p = path();
        Path tmp = p.resolveSibling("anarchia_autorynek.json.tmp");
        try {
            Files.createDirectories(p.getParent());
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(instance, w);
            }
            try {
                Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailed) {
                Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            AutoRynekClient.LOGGER.error("Nie udało się zapisać konfiguracji", e);
        }
    }

    private void sanitize() {
        if (entries == null) entries = new ArrayList<>();
        entries.removeIf(e -> e == null || e.name == null || e.name.isBlank());
        for (Entry e : entries) {
            if (e.category == null) e.category = "EVENT";
            if (e.maxPrice < 0 || Double.isNaN(e.maxPrice)) e.maxPrice = 0;
        }
        if (defaultMaxPrice < 0 || Double.isNaN(defaultMaxPrice)) defaultMaxPrice = 0;
        if (marketCommand == null || marketCommand.isBlank()) marketCommand = "rynek";
        if (marketCommand.startsWith("/")) marketCommand = marketCommand.substring(1);
        if (marketTitleContains == null) marketTitleContains = "rynek";
        refreshIntervalTicks = Math.max(40, refreshIntervalTicks);
        settleTicks = Math.max(2, settleTicks);
        afterPurchaseCooldownTicks = Math.max(5, afterPurchaseCooldownTicks);
        retryBlockTicks = Math.max(0, retryBlockTicks);
        maxPurchasesPerSession = Math.max(0, maxPurchasesPerSession);
        maxPages = Math.max(1, Math.min(20, maxPages));
        if (clickMode == null) clickMode = "PICKUP";
        if (priceRegex == null || priceRegex.isBlank()) priceRegex = DEFAULT_PRICE_REGEX;
        if (nextPageKeywords == null) nextPageKeywords = new ArrayList<>();
        if (confirmTitleKeywords == null) confirmTitleKeywords = new ArrayList<>();
        if (confirmButtonKeywords == null) confirmButtonKeywords = new ArrayList<>();
    }

    /** Skompilowany regex ceny; przy błędnym wyrażeniu wraca do domyślnego. */
    public Pattern pricePattern() {
        if (cachedPattern == null || !priceRegex.equals(cachedPatternSource)) {
            try {
                cachedPattern = Pattern.compile(priceRegex);
            } catch (PatternSyntaxException e) {
                AutoRynekClient.LOGGER.warn("Błędny regex ceny w konfiguracji, używam domyślnego: {}", e.getMessage());
                cachedPattern = Pattern.compile(DEFAULT_PRICE_REGEX);
            }
            cachedPatternSource = priceRegex;
        }
        return cachedPattern;
    }
}
