package pl.anarchia.autorynek;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.OptionalDouble;

/**
 * Menu moda otwierane komendą /autorynek. Wszystkie napisy po polsku.
 */
public class AutoRynekScreen extends Screen {
    private static final int COLUMN_WIDTH = 200;
    private static final int ROW_HEIGHT = 18;
    private static final int LIST_TOP = 154;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFAAAAAA;
    private static final int RED = 0xFFFF5555;
    private static final int GREEN = 0xFF55FF55;

    private final AutoRynekConfig cfg = AutoRynekConfig.get();

    private ButtonWidget toggleButton;
    private TextFieldWidget defaultPriceField;
    private TextFieldWidget nameField;
    private TextFieldWidget priceField;

    // Wartości pól zachowywane przy zmianie rozmiaru okna
    private String nameValue = "";
    private String priceValue = "";
    private boolean zestaw = false;
    private int page = 0;
    private String statusText = "";
    private int statusColor = GRAY;

    public AutoRynekScreen() {
        super(Text.literal("Anarchia AutoRynek"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int left = cx - COLUMN_WIDTH / 2;

        toggleButton = ButtonWidget.builder(toggleLabel(), b -> {
            cfg.enabled = !cfg.enabled;
            AutoRynekConfig.save();
            b.setMessage(toggleLabel());
        }).dimensions(left, 22, COLUMN_WIDTH, 20).build();
        addDrawableChild(toggleButton);

        defaultPriceField = new TextFieldWidget(this.textRenderer, cx + 10, 46, 90, 16, Text.literal("Domyślna maksymalna cena"));
        defaultPriceField.setMaxLength(20);
        defaultPriceField.setText(PriceParser.format(cfg.defaultMaxPrice));
        defaultPriceField.setChangedListener(s -> {
            OptionalDouble v = PriceParser.parseUserInput(s);
            if (v.isPresent()) {
                cfg.defaultMaxPrice = v.getAsDouble();
                AutoRynekConfig.save();
            }
        });
        addDrawableChild(defaultPriceField);

        nameField = new TextFieldWidget(this.textRenderer, left, 78, COLUMN_WIDTH, 16, Text.literal("Nazwa przedmiotu"));
        nameField.setMaxLength(80);
        nameField.setPlaceholder(Text.literal("Nazwa lub fragment nazwy (np. Totem)"));
        nameField.setText(nameValue);
        nameField.setChangedListener(s -> nameValue = s);
        addDrawableChild(nameField);

        priceField = new TextFieldWidget(this.textRenderer, left, 98, 98, 16, Text.literal("Maksymalna cena"));
        priceField.setMaxLength(20);
        priceField.setPlaceholder(Text.literal("Maks. cena"));
        priceField.setText(priceValue);
        priceField.setChangedListener(s -> priceValue = s);
        addDrawableChild(priceField);

        addDrawableChild(ButtonWidget.builder(categoryLabel(), b -> {
            zestaw = !zestaw;
            b.setMessage(categoryLabel());
        }).dimensions(left + 102, 98, 98, 16).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Dodaj do listy"), b -> addEntry())
                .dimensions(left, 118, COLUMN_WIDTH, 18).build());

        // Lista wpisów ze stronicowaniem
        int perPage = rowsPerPage();
        int pages = Math.max(1, (cfg.entries.size() + perPage - 1) / perPage);
        if (page >= pages) {
            page = pages - 1;
        }
        int start = page * perPage;
        for (int i = 0; i < perPage && start + i < cfg.entries.size(); i++) {
            final int index = start + i;
            int y = LIST_TOP + i * ROW_HEIGHT;
            addDrawableChild(ButtonWidget.builder(Text.literal("Usuń"), b -> {
                if (index < cfg.entries.size()) {
                    AutoRynekConfig.Entry removed = cfg.entries.remove(index);
                    AutoRynekConfig.save();
                    setStatus("Usunięto: " + removed.name, GRAY);
                    clearAndInit();
                }
            }).dimensions(left + COLUMN_WIDTH - 44, y, 44, 16).build());
        }

        int bottom = this.height - 24;
        addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> {
            if (page > 0) {
                page--;
                clearAndInit();
            }
        }).dimensions(left, bottom, 20, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> {
            if (page < pages - 1) {
                page++;
                clearAndInit();
            }
        }).dimensions(left + 24, bottom, 20, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Gotowe"), b -> close())
                .dimensions(left + COLUMN_WIDTH - 100, bottom, 100, 20).build());
    }

    private int rowsPerPage() {
        return Math.max(1, (this.height - 24 - 6 - LIST_TOP) / ROW_HEIGHT);
    }

    private Text toggleLabel() {
        return Text.literal("Status: ").append(cfg.enabled
                ? Text.literal("WŁĄCZONY").formatted(Formatting.GREEN)
                : Text.literal("WYŁĄCZONY").formatted(Formatting.RED));
    }

    private Text categoryLabel() {
        return Text.literal(zestaw ? "Typ: Zestaw" : "Typ: Event");
    }

    private void setStatus(String text, int color) {
        statusText = text;
        statusColor = color;
    }

    private void addEntry() {
        String name = nameValue.trim();
        if (name.isEmpty()) {
            setStatus("Wpisz nazwę przedmiotu lub zestawu.", RED);
            return;
        }
        double max = 0;
        if (!priceValue.trim().isEmpty()) {
            OptionalDouble v = PriceParser.parseUserInput(priceValue);
            if (v.isEmpty()) {
                setStatus("Niepoprawna cena. Przykłady: 1500, 2,5k, 1m.", RED);
                return;
            }
            max = v.getAsDouble();
        }
        for (AutoRynekConfig.Entry e : cfg.entries) {
            if (e.name.equalsIgnoreCase(name)) {
                e.maxPrice = max;
                e.category = zestaw ? "ZESTAW" : "EVENT";
                AutoRynekConfig.save();
                setStatus("Zaktualizowano: " + name, GREEN);
                nameValue = "";
                priceValue = "";
                clearAndInit();
                return;
            }
        }
        cfg.entries.add(new AutoRynekConfig.Entry(name, max, zestaw ? "ZESTAW" : "EVENT"));
        AutoRynekConfig.save();
        setStatus("Dodano: " + name, GREEN);
        nameValue = "";
        priceValue = "";
        page = Integer.MAX_VALUE; // przeskocz na ostatnią stronę (init przytnie wartość)
        clearAndInit();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int cx = this.width / 2;
        int left = cx - COLUMN_WIDTH / 2;

        drawCentered(context, this.title, cx, 8, WHITE);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Domyślna maks. cena:"), left, 50, GRAY);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Dodaj przedmiot eventowy / zestaw Anarchia.gg:"), left, 68, GRAY);

        int perPage = rowsPerPage();
        int pages = Math.max(1, (cfg.entries.size() + perPage - 1) / perPage);
        context.drawTextWithShadow(this.textRenderer,
                Text.literal("Lista (" + cfg.entries.size() + "), strona " + (Math.min(page, pages - 1) + 1) + "/" + pages + ":"),
                left, 142, GRAY);

        List<AutoRynekConfig.Entry> entries = cfg.entries;
        int start = Math.min(page, pages - 1) * perPage;
        if (entries.isEmpty()) {
            context.drawTextWithShadow(this.textRenderer, Text.literal("Lista jest pusta."), left, LIST_TOP + 4, GRAY);
        }
        for (int i = 0; i < perPage && start + i < entries.size(); i++) {
            AutoRynekConfig.Entry e = entries.get(start + i);
            String tag = "ZESTAW".equals(e.category) ? "[Z] " : "[E] ";
            String price = e.maxPrice > 0 ? PriceParser.format(e.maxPrice) : "domyślna (" + PriceParser.format(cfg.defaultMaxPrice) + ")";
            String line = tag + e.name + " - maks. " + price;
            String trimmed = this.textRenderer.trimToWidth(line, COLUMN_WIDTH - 50);
            context.drawTextWithShadow(this.textRenderer, Text.literal(trimmed), left, LIST_TOP + i * ROW_HEIGHT + 4, WHITE);
        }

        if (!statusText.isEmpty()) {
            context.drawTextWithShadow(this.textRenderer, Text.literal(statusText), left + 50, this.height - 18, statusColor);
        }
    }

    private void drawCentered(DrawContext context, Text text, int centerX, int y, int color) {
        int w = this.textRenderer.getWidth(text);
        context.drawTextWithShadow(this.textRenderer, text, centerX - w / 2, y, color);
    }

    @Override
    public void removed() {
        AutoRynekConfig.save();
    }
}
