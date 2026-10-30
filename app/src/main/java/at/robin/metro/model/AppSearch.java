package at.robin.metro.model;

import java.text.Normalizer;
import java.util.Locale;

public final class AppSearch {
    private AppSearch() { }
    public static String normalized(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replace("ß", "ss");
    }
    public static boolean matches(String label, String query) {
        return normalized(label).contains(normalized(query).trim());
    }
    public static String section(String label) {
        String value = normalized(label).trim();
        if (value.isEmpty()) return "#";
        int first = value.codePointAt(0);
        return Character.isLetter(first) ? new String(Character.toChars(first)).toUpperCase(Locale.ROOT) : "#";
    }
}
