package pl.anarchia.autorynek;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.regex.Pattern;

/**
 * Logika sprawdzania rynku. Działa wyłącznie na standardowym oknie skrzyni (GenericContainerScreen):
 * czyta nazwy i opisy przedmiotów, wyciąga cenę z opisu i klika ofertę, jeśli pasuje do listy i mieści się w limicie.
 *
 * Mod NIE zna układu rynku konkretnego serwera - to, czy zakup zadziała, zależy od tego, czy rynek serwera
 * jest zwykłym oknem skrzyni z ceną w opisie przedmiotu. Do dopasowania służy plik konfiguracji i /autorynek diagnoza.
 */
public final class MarketScanner {
    private static final class Candidate {
        Slot slot;
        String displayName;
        double price;
        double limit;
        String key;
    }

    private long tick = 0;
    private int sinceRefresh = 0;
    private int lastSyncId = -1;
    private long nextActionAt = 0;
    private int pagesScanned = 0;
    private int emptyChecks = 0;
    private boolean finished = false;
    private long modOpenedAt = Long.MIN_VALUE / 2;
    private long lastBuyAttemptTick = Long.MIN_VALUE / 2;
    private int purchases = 0;
    private boolean diagnosticRequested = false;
    private final Map<String, Long> attempts = new HashMap<>();

    public void requestDiagnostic() {
        diagnosticRequested = true;
    }

    public int purchaseAttempts() {
        return purchases;
    }

    private void resetSession() {
        lastSyncId = -1;
        finished = false;
        sinceRefresh = 0;
        attempts.clear();
    }

    public void tick(MinecraftClient client) {
        tick++;
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || client.interactionManager == null) {
            resetSession();
            return;
        }
        AutoRynekConfig cfg = AutoRynekConfig.get();
        Screen screen = client.currentScreen;

        if (screen instanceof GenericContainerScreen gcs) {
            GenericContainerScreenHandler handler = gcs.getScreenHandler();
            if (handler.syncId != lastSyncId) {
                lastSyncId = handler.syncId;
                pagesScanned = 0;
                emptyChecks = 0;
                finished = false;
                nextActionAt = tick + cfg.settleTicks;
            }
            if (tick < nextActionAt) {
                return;
            }
            if (diagnosticRequested) {
                diagnosticRequested = false;
                dumpDiagnostics(client, gcs, cfg);
            }
            if (cfg.enabled) {
                handleContainer(client, player, gcs, handler, cfg);
            }
            return;
        }

        lastSyncId = -1;
        finished = false;
        if (screen != null) {
            return; // czat, menu moda itp. - nie przeszkadzamy
        }
        if (!cfg.enabled || !cfg.autoOpenMarket) {
            sinceRefresh = 0;
            return;
        }
        sinceRefresh++;
        if (sinceRefresh >= cfg.refreshIntervalTicks) {
            sinceRefresh = 0;
            if (player.networkHandler != null) {
                player.networkHandler.sendChatCommand(cfg.marketCommand);
                modOpenedAt = tick;
            }
        }
    }

    private void handleContainer(MinecraftClient client, ClientPlayerEntity player, GenericContainerScreen gcs,
                                 GenericContainerScreenHandler handler, AutoRynekConfig cfg) {
        String title = norm(gcs.getTitle().getString());
        int containerSlots = handler.getRows() * 9;
        boolean openedByMod = tick - modOpenedAt <= 200;

        // 1) Okno potwierdzenia - klikamy tylko tuż po próbie zakupu zainicjowanej przez mod.
        if (containsAny(title, cfg.confirmTitleKeywords)) {
            if (tick - lastBuyAttemptTick <= 200) {
                Slot confirm = findByName(handler, containerSlots, cfg.confirmButtonKeywords);
                if (confirm != null) {
                    click(client, handler, confirm, cfg);
                    lastBuyAttemptTick = Long.MIN_VALUE / 2; // jedno potwierdzenie na jedną próbę zakupu
                    nextActionAt = tick + cfg.afterPurchaseCooldownTicks;
                }
            }
            return;
        }

        // 2) Okno rynku
        if (!title.contains(norm(cfg.marketTitleContains)) || finished) {
            return;
        }

        boolean anyItem = false;
        for (int i = 0; i < containerSlots; i++) {
            if (!handler.slots.get(i).getStack().isEmpty()) {
                anyItem = true;
                break;
            }
        }
        if (!anyItem) {
            // zawartość mogła jeszcze nie dotrzeć z serwera
            emptyChecks++;
            if (emptyChecks > 20) {
                finishScan(player, openedByMod);
            } else {
                nextActionAt = tick + 5;
            }
            return;
        }

        attempts.entrySet().removeIf(e -> tick - e.getValue() > cfg.retryBlockTicks);

        Candidate best = findBestOffer(handler, containerSlots, cfg);
        if (best != null) {
            if (cfg.maxPurchasesPerSession > 0 && purchases >= cfg.maxPurchasesPerSession) {
                cfg.enabled = false;
                AutoRynekConfig.save();
                chat(player, "Osiągnięto limit prób zakupu w tej sesji (" + cfg.maxPurchasesPerSession
                        + "). AutoRynek został wyłączony.", Formatting.RED);
                if (openedByMod) {
                    player.closeHandledScreen();
                }
                return;
            }
            attempts.put(best.key, tick);
            lastBuyAttemptTick = tick;
            purchases++;
            click(client, handler, best.slot, cfg);
            chat(player, "Próba zakupu: " + best.displayName + " za " + PriceParser.format(best.price)
                    + " (limit " + PriceParser.format(best.limit) + ").", Formatting.GREEN);
            nextActionAt = tick + cfg.afterPurchaseCooldownTicks;
            return;
        }

        // Nic do kupienia na tej stronie
        if (openedByMod) {
            if (pagesScanned + 1 < cfg.maxPages) {
                Slot next = findByName(handler, containerSlots, cfg.nextPageKeywords);
                if (next != null) {
                    pagesScanned++;
                    click(client, handler, next, cfg);
                    nextActionAt = tick + cfg.settleTicks;
                    return;
                }
            }
            finishScan(player, true);
        } else {
            nextActionAt = tick + 20; // ręcznie otwarty rynek - sprawdzamy ponownie za chwilę
        }
    }

    private void finishScan(ClientPlayerEntity player, boolean closeScreen) {
        finished = true;
        if (closeScreen) {
            modOpenedAt = Long.MIN_VALUE / 2;
            player.closeHandledScreen();
        }
    }

    private Candidate findBestOffer(GenericContainerScreenHandler handler, int containerSlots, AutoRynekConfig cfg) {
        if (cfg.entries.isEmpty()) {
            return null;
        }
        Pattern pricePattern = cfg.pricePattern();
        Candidate best = null;
        for (int i = 0; i < containerSlots; i++) {
            Slot slot = handler.slots.get(i);
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) {
                continue;
            }
            String rawName = stack.getName().getString();
            String name = norm(rawName);
            List<String> lore = loreLines(stack);
            AutoRynekConfig.Entry entry = matchEntry(cfg, stack, name, lore);
            if (entry == null) {
                continue;
            }
            OptionalDouble price = PriceParser.findPrice(lore, pricePattern);
            if (price.isEmpty()) {
                continue; // brak rozpoznanej ceny = nie kupujemy
            }
            double limit = entry.maxPrice > 0 ? entry.maxPrice : cfg.defaultMaxPrice;
            if (price.getAsDouble() > limit) {
                continue;
            }
            String key = name + "|" + price.getAsDouble();
            if (attempts.containsKey(key)) {
                continue;
            }
            if (best == null || price.getAsDouble() < best.price) {
                best = new Candidate();
                best.slot = slot;
                best.displayName = rawName.replaceAll("§.", "");
                best.price = price.getAsDouble();
                best.limit = limit;
                best.key = key;
            }
        }
        return best;
    }

    private static AutoRynekConfig.Entry matchEntry(AutoRynekConfig cfg, ItemStack stack, String name, List<String> lore) {
        for (AutoRynekConfig.Entry e : cfg.entries) {
            String key = norm(e.name);
            if (key.isEmpty()) {
                continue;
            }
            if (key.startsWith("id:")) {
                String wanted = key.substring(3).trim();
                String id = Registries.ITEM.getId(stack.getItem()).toString().toLowerCase(Locale.ROOT);
                if (!wanted.isEmpty() && id.contains(wanted)) {
                    return e;
                }
                continue;
            }
            if (name.contains(key)) {
                return e;
            }
            if (cfg.matchLore) {
                for (String l : lore) {
                    if (norm(l).contains(key)) {
                        return e;
                    }
                }
            }
        }
        return null;
    }

    private static List<String> loreLines(ItemStack stack) {
        List<String> out = new ArrayList<>();
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore != null) {
            for (Text t : lore.lines()) {
                out.add(t.getString());
            }
        }
        return out;
    }

    private static Slot findByName(GenericContainerScreenHandler handler, int containerSlots, List<String> keywords) {
        for (int i = 0; i < containerSlots; i++) {
            Slot slot = handler.slots.get(i);
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) {
                continue;
            }
            if (containsAny(norm(stack.getName().getString()), keywords)) {
                return slot;
            }
        }
        return null;
    }

    private static boolean containsAny(String normalizedText, List<String> keywords) {
        for (String k : keywords) {
            String nk = norm(k);
            if (!nk.isEmpty() && normalizedText.contains(nk)) {
                return true;
            }
        }
        return false;
    }

    private static void click(MinecraftClient client, GenericContainerScreenHandler handler, Slot slot, AutoRynekConfig cfg) {
        SlotActionType type = "QUICK_MOVE".equalsIgnoreCase(cfg.clickMode) ? SlotActionType.QUICK_MOVE : SlotActionType.PICKUP;
        client.interactionManager.clickSlot(handler.syncId, slot.id, 0, type, client.player);
    }

    /** Normalizacja tekstu: bez kodów kolorów, bez polskich znaków, małe litery. */
    static String norm(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replaceAll("§.", "");
        t = Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        t = t.replace('ł', 'l').replace('Ł', 'L');
        return t.toLowerCase(Locale.ROOT).trim();
    }

    private static void chat(ClientPlayerEntity player, String message, Formatting color) {
        player.sendMessage(Text.literal("[AutoRynek] " + message).formatted(color), false);
    }

    /** Zrzut zawartości aktualnie otwartego okna skrzyni do logu - pomaga dopasować konfigurację do serwera. */
    private void dumpDiagnostics(MinecraftClient client, GenericContainerScreen gcs, AutoRynekConfig cfg) {
        GenericContainerScreenHandler handler = gcs.getScreenHandler();
        int containerSlots = handler.getRows() * 9;
        Pattern p = cfg.pricePattern();
        String title = gcs.getTitle().getString();
        AutoRynekClient.LOGGER.info("[diagnoza] tytuł okna='{}', sloty skrzyni={}", title, containerSlots);
        int nonEmpty = 0;
        int withPrice = 0;
        for (int i = 0; i < containerSlots; i++) {
            ItemStack st = handler.slots.get(i).getStack();
            if (st.isEmpty()) {
                continue;
            }
            nonEmpty++;
            List<String> lore = loreLines(st);
            OptionalDouble price = PriceParser.findPrice(lore, p);
            if (price.isPresent()) {
                withPrice++;
            }
            AutoRynekClient.LOGGER.info("[diagnoza] slot {}: nazwa='{}' x{} | opis={} | cena={}",
                    i, st.getName().getString(), st.getCount(), lore,
                    price.isPresent() ? PriceParser.format(price.getAsDouble()) : "nie rozpoznano");
        }
        if (client.player != null) {
            chat(client.player, "Diagnoza: tytuł okna \"" + title + "\", niepustych slotów: " + nonEmpty
                    + ", z rozpoznaną ceną: " + withPrice + ". Szczegóły w logu gry (logs/latest.log).", Formatting.YELLOW);
            boolean titleOk = norm(title).contains(norm(cfg.marketTitleContains));
            chat(client.player, titleOk
                            ? "Tytuł pasuje do ustawienia marketTitleContains."
                            : "Tytuł NIE zawiera \"" + cfg.marketTitleContains + "\" - popraw marketTitleContains w pliku konfiguracji.",
                    titleOk ? Formatting.GREEN : Formatting.RED);
        }
    }
}
