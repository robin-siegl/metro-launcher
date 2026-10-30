package at.robin.metro.data;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import at.robin.metro.model.AppSearch;
import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class AppCatalog {
    public static final class Entry {
        public final String key, label, section;
        public final Drawable icon;
        public final String glyph;
        Entry(String key, String label, Drawable icon, String glyph) {
            this.key = key; this.label = label; this.icon = icon; this.glyph = glyph;
            this.section = AppSearch.section(label);
        }
    }
    private final Context context;
    public final List<Entry> entries = new ArrayList<>();
    public final Map<String, Entry> byKey = new HashMap<>();
    public AppCatalog(Context context) { this.context = context; }

    public void reload() {
        PackageManager manager = context.getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> found = manager.queryIntentActivities(query, 0);
        entries.clear(); byKey.clear(); Set<String> seen = new HashSet<>();
        for (ResolveInfo resolved : found) {
            ActivityInfo activity = resolved.activityInfo;
            if (!activity.exported || activity.packageName.equals(context.getPackageName())) continue;
            String key = new ComponentName(activity.packageName, activity.name).flattenToString();
            if (!seen.add(key)) continue;
            try {
                String label = resolved.loadLabel(manager).toString();
                entries.add(new Entry(key, label, resolved.loadIcon(manager), glyph(activity.packageName, label)));
            } catch (RuntimeException ignored) { /* An app can disappear while the list is loading. */ }
        }
        entries.add(new Entry("@settings", "start+theme", null, "settings"));
        entries.add(new Entry("@clock", "Clock tile", null, "clock"));
        entries.add(new Entry("@calendar", "Calendar tile", null, "calendar"));
        Collator collator = Collator.getInstance(); collator.setStrength(Collator.PRIMARY);
        entries.sort((left, right) -> {
            int section = collator.compare(left.section, right.section);
            return section != 0 ? section : collator.compare(left.label, right.label);
        });
        for (Entry entry : entries) byKey.put(entry.key, entry);
    }

    public Entry get(String key) { return byKey.get(key); }
    public String label(String key) { Entry entry = get(key); return entry == null ? "Unavailable app" : entry.label; }
    public List<Entry> search(String query) {
        List<Entry> result = new ArrayList<>();
        for (Entry entry : entries) if (AppSearch.matches(entry.label, query)) result.add(entry);
        return result;
    }
    public String first(String kind) {
        for (Entry entry : entries) if (kind.equals(entry.glyph) && !entry.key.startsWith("@")) return entry.key;
        return null;
    }
    public static String packageOf(String key) {
        ComponentName component = ComponentName.unflattenFromString(key);
        return component == null ? "" : component.getPackageName();
    }

    private static String glyph(String packageName, String label) {
        String value = (packageName + " " + label).toLowerCase(Locale.ROOT);
        if (value.contains("dialer") || value.contains("contacts.activities.dialtacts")) return "phone";
        if (value.contains("messaging") || value.contains("sms") || value.contains("messages")) return "message";
        if (value.contains("chrome") || value.contains("browser") || value.contains("firefox")) return "browser";
        if (value.contains("camera")) return "camera";
        if (value.contains("gmail") || value.contains("email") || value.contains("outlook")) return "mail";
        if (value.contains("photos") || value.contains("gallery")) return "photos";
        if (value.contains("calendar")) return "calendar";
        if (value.contains("deskclock") || label.equalsIgnoreCase("clock")) return "clock";
        if (value.contains("vending") || value.contains("play store")) return "store";
        if (value.contains("contacts")) return "people";
        if (value.contains("settings")) return "settings";
        if (value.contains("music") || value.contains("spotify")) return "music";
        return "";
    }
}
