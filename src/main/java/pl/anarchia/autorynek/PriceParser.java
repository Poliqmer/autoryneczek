package pl.anarchia.autorynek;

import java.util.List;
import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wyciąganie ceny z linii opisu (lore) przedmiotu oraz parsowanie liczb wpisanych w menu.
 * Klasa nie zależy od Minecrafta, dzięki czemu można ją testować osobno.
 */
public final class PriceParser {
    private PriceParser() {
    }

    /**
     * Szuka ceny w liniach opisu. Wzorzec musi mieć grupę 1 (liczba) i opcjonalnie grupę 2 (sufiks k/m).
     * Zwraca pierwszą rozpoznaną cenę albo pusty wynik (wtedy oferta jest pomijana, a nie kupowana na ślepo).
     */
    public static OptionalDouble findPrice(List<String> lines, Pattern pattern) {
        for (String line : lines) {
            Matcher m = pattern.matcher(line);
            if (m.find()) {
                String suffix = m.groupCount() >= 2 ? m.group(2) : null;
                double v = parseNumber(m.group(1), suffix);
                if (!Double.isNaN(v)) {
                    return OptionalDouble.of(v);
                }
            }
        }
        return OptionalDouble.empty();
    }

    /**
     * Parsuje liczbę w stylu: 1500, 1 500, 1.500, 1,5k, 2m, 12.50.
     * Zwraca NaN, gdy nie da się jednoznacznie sparsować.
     */
    public static double parseNumber(String raw, String suffix) {
        if (raw == null) {
            return Double.NaN;
        }
        String s = raw.replace(' ', ' ').trim();
        while (!s.isEmpty()) {
            char last = s.charAt(s.length() - 1);
            if (last == '.' || last == ',' || last == ' ') {
                s = s.substring(0, s.length() - 1);
            } else {
                break;
            }
        }
        if (s.isEmpty()) {
            return Double.NaN;
        }
        s = s.replace(" ", "");
        boolean hasSuffix = suffix != null && !suffix.isEmpty();
        try {
            if (hasSuffix) {
                double v = Double.parseDouble(s.replace(',', '.'));
                char c = Character.toLowerCase(suffix.charAt(0));
                if (c == 'k') {
                    v *= 1_000d;
                } else if (c == 'm') {
                    v *= 1_000_000d;
                }
                return v;
            }
            if (s.matches("\\d+[.,]\\d{1,2}")) {
                return Double.parseDouble(s.replace(',', '.'));
            }
            return Double.parseDouble(s.replace(".", "").replace(",", ""));
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /** Parsuje tekst wpisany przez użytkownika w menu (np. "1500", "2,5k"). */
    public static OptionalDouble parseUserInput(String text) {
        if (text == null) {
            return OptionalDouble.empty();
        }
        String t = text.trim();
        if (t.isEmpty()) {
            return OptionalDouble.empty();
        }
        String suffix = null;
        char last = Character.toLowerCase(t.charAt(t.length() - 1));
        if (last == 'k' || last == 'm') {
            suffix = String.valueOf(last);
            t = t.substring(0, t.length() - 1);
        }
        double v = parseNumber(t, suffix);
        if (Double.isNaN(v) || v < 0 || Double.isInfinite(v)) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(v);
    }

    /** Ładne formatowanie ceny do wyświetlania. */
    public static String format(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) {
            return String.valueOf((long) v);
        }
        return String.valueOf(v);
    }
}
