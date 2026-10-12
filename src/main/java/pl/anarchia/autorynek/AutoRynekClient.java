package pl.anarchia.autorynek;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoRynekClient implements ClientModInitializer {
    public static final String MOD_ID = "anarchia_autorynek";
    public static final Logger LOGGER = LoggerFactory.getLogger("AutoRynek");

    private static volatile boolean openMenuRequested = false;
    private final MarketScanner scanner = new MarketScanner();

    @Override
    public void onInitializeClient() {
        AutoRynekConfig.load();

        ClientCommandRegistrationCallback.EVENT.register(this::registerCommands);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openMenuRequested) {
                openMenuRequested = false;
                client.setScreen(new AutoRynekScreen());
            }
            scanner.tick(client);
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> AutoRynekConfig.save());

        LOGGER.info("Anarchia AutoRynek załadowany. Menu: /autorynek");
    }

    private void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
        dispatcher.register(ClientCommandManager.literal("autorynek")
                .executes(ctx -> {
                    // Menu otwieramy w następnym ticku, bo po wykonaniu komendy czat sam zamyka swój ekran.
                    openMenuRequested = true;
                    return 1;
                })
                .then(ClientCommandManager.literal("on").executes(ctx -> {
                    AutoRynekConfig.get().enabled = true;
                    AutoRynekConfig.save();
                    ctx.getSource().sendFeedback(Text.literal("[AutoRynek] Włączono.").formatted(Formatting.GREEN));
                    return 1;
                }))
                .then(ClientCommandManager.literal("off").executes(ctx -> {
                    AutoRynekConfig.get().enabled = false;
                    AutoRynekConfig.save();
                    ctx.getSource().sendFeedback(Text.literal("[AutoRynek] Wyłączono.").formatted(Formatting.RED));
                    return 1;
                }))
                .then(ClientCommandManager.literal("status").executes(ctx -> {
                    AutoRynekConfig cfg = AutoRynekConfig.get();
                    ctx.getSource().sendFeedback(Text.literal("[AutoRynek] Status: " + (cfg.enabled ? "WŁĄCZONY" : "WYŁĄCZONY")
                            + ", pozycji na liście: " + cfg.entries.size()
                            + ", domyślna maks. cena: " + PriceParser.format(cfg.defaultMaxPrice)
                            + ", prób zakupu w tej sesji: " + scanner.purchaseAttempts()).formatted(Formatting.YELLOW));
                    return 1;
                }))
                .then(ClientCommandManager.literal("diagnoza").executes(ctx -> {
                    scanner.requestDiagnostic();
                    ctx.getSource().sendFeedback(Text.literal("[AutoRynek] Diagnoza włączona: otwórz teraz rynek serwera"
                            + " (np. /" + AutoRynekConfig.get().marketCommand + "). Zrzut okna trafi do czatu i logu gry.")
                            .formatted(Formatting.YELLOW));
                    return 1;
                }))
                .then(ClientCommandManager.literal("pomoc").executes(ctx -> {
                    ctx.getSource().sendFeedback(Text.literal("[AutoRynek] Komendy: /autorynek (menu), /autorynek on, /autorynek off,"
                            + " /autorynek status, /autorynek diagnoza").formatted(Formatting.YELLOW));
                    return 1;
                })));
    }
}
