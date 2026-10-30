package at.robin.metro.data;

import android.content.Context;
import android.util.AtomicFile;
import at.robin.metro.model.Tile;
import at.robin.metro.model.TileLayout;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class LauncherState {
    public static final int[] COLORS = {
        0xffa4c400, 0xff60a917, 0xff008a00, 0xff00aba9, 0xff1ba1e2,
        0xff0050ef, 0xff6a00ff, 0xffaa00ff, 0xfff472d0, 0xffd80073,
        0xffa20025, 0xffe51400, 0xfffa6800, 0xfff0a30a, 0xffe3c800,
        0xff825a2c, 0xff6d8764, 0xff647687, 0xff76608a, 0xff87794e
    };
    public static final String[] COLOR_NAMES = {
        "lime", "green", "emerald", "teal", "cyan", "cobalt", "indigo", "violet", "pink", "magenta",
        "crimson", "red", "orange", "amber", "yellow", "brown", "olive", "steel", "mauve", "taupe"
    };
    public final List<Tile> tiles = new ArrayList<>();
    public boolean initialized;
    public boolean onboardingDone;
    public boolean light;
    public boolean moreTiles;
    public boolean phone8;
    public boolean monochrome;
    public boolean animations = true;
    public boolean notificationPreview;
    public int accentIndex = 4;
    public String background = "";
    public int columns() { return moreTiles ? 6 : 4; }
    public int accent() { return COLORS[Math.max(0, Math.min(accentIndex, COLORS.length - 1))]; }
    public int foreground() { return light ? 0xff000000 : 0xffffffff; }
    public int surface() { return light ? 0xffffffff : 0xff000000; }

    public static LauncherState load(Context context) {
        try {
            byte[] bytes = file(context).readFully();
            return parse(new String(bytes, StandardCharsets.UTF_8));
        } catch (IOException | JSONException | IllegalArgumentException error) {
            return new LauncherState();
        }
    }

    public void save(Context context) throws IOException {
        AtomicFile file = file(context);
        FileOutputStream output = null;
        try {
            output = file.startWrite();
            output.write(json().getBytes(StandardCharsets.UTF_8));
            file.finishWrite(output);
        } catch (IOException error) {
            if (output != null) file.failWrite(output);
            throw error;
        }
    }

    public String json() {
        try {
            JSONObject root = new JSONObject();
            root.put("format", "metro-launcher"); root.put("version", 1);
            root.put("initialized", initialized); root.put("onboardingDone", onboardingDone);
            root.put("light", light); root.put("moreTiles", moreTiles); root.put("phone8", phone8);
            root.put("monochrome", monochrome); root.put("animations", animations);
            root.put("notificationPreview", notificationPreview); root.put("accentIndex", accentIndex);
            root.put("background", background);
            JSONArray array = new JSONArray();
            for (Tile tile : tiles) array.put(encode(tile));
            root.put("tiles", array);
            return root.toString(2);
        } catch (JSONException impossible) { throw new IllegalStateException(impossible); }
    }

    public static LauncherState parse(String text) throws JSONException {
        if (text.length() > 2_000_000) throw new JSONException("Backup is too large");
        JSONObject root = new JSONObject(text);
        if (!"metro-launcher".equals(root.optString("format")) || root.optInt("version") != 1)
            throw new JSONException("This is not a Metro Launcher v1 backup");
        LauncherState state = new LauncherState();
        state.initialized = root.optBoolean("initialized", true);
        state.onboardingDone = root.optBoolean("onboardingDone", true);
        state.light = root.optBoolean("light"); state.moreTiles = root.optBoolean("moreTiles");
        state.phone8 = root.optBoolean("phone8"); state.monochrome = root.optBoolean("monochrome");
        state.animations = root.optBoolean("animations", true);
        state.notificationPreview = root.optBoolean("notificationPreview");
        state.accentIndex = Math.max(0, Math.min(root.optInt("accentIndex", 4), COLORS.length - 1));
        // Only the launcher's own image is ever opened; imported paths cannot escape app storage.
        state.background = "background.jpg".equals(root.optString("background")) ? "background.jpg" : "";
        JSONArray tiles = root.getJSONArray("tiles");
        if (tiles.length() > TileLayout.MAX_TILES) throw new JSONException("Too many tiles");
        Set<String> ids = new HashSet<>();
        Set<String> apps = new HashSet<>();
        for (int i = 0; i < tiles.length(); i++) {
            Tile tile = decode(tiles.getJSONObject(i), ids, apps, 0, state.columns());
            if (!tile.isFolder() || !tile.children.isEmpty()) state.tiles.add(tile);
        }
        if (ids.size() > TileLayout.MAX_TILES) throw new JSONException("Too many tiles");
        TileLayout.normalize(state.tiles, state.columns());
        return state;
    }

    private static JSONObject encode(Tile tile) throws JSONException {
        JSONObject value = new JSONObject();
        value.put("id", tile.id); value.put("app", tile.app); value.put("label", tile.label);
        value.put("size", tile.size.name()); value.put("column", tile.column); value.put("row", tile.row);
        value.put("color", tile.color); value.put("photo", tile.photo); value.put("live", tile.live);
        JSONArray children = new JSONArray();
        for (Tile child : tile.children) children.put(encode(child));
        value.put("children", children);
        return value;
    }

    private static Tile decode(JSONObject value, Set<String> ids, Set<String> apps, int depth, int columns) throws JSONException {
        String id = value.getString("id"), app = value.getString("app");
        if (id.length() > 100 || app.length() > 500 || !ids.add(id)) throw new JSONException("Invalid tile identity");
        boolean folder = "@folder".equals(app);
        if (folder && depth > 0) throw new JSONException("Folders cannot contain folders");
        if (!folder && !apps.add(app)) throw new JSONException("An app is pinned more than once");
        Tile tile = new Tile(id, app);
        tile.label = value.optString("label");
        if (tile.label.length() > 100) throw new JSONException("Tile label is too long");
        try { tile.size = Tile.Size.valueOf(value.optString("size", "MEDIUM")); }
        catch (IllegalArgumentException error) { throw new JSONException("Unknown tile size"); }
        tile.column = Math.max(0, value.optInt("column"));
        tile.row = Math.max(0, Math.min(value.optInt("row"), TileLayout.MAX_ROW));
        tile.color = value.optInt("color"); if (tile.color != 0) tile.color |= 0xff000000;
        tile.photo = value.optBoolean("photo", true); tile.live = value.optBoolean("live", true);
        JSONArray children = value.optJSONArray("children");
        if (!folder && children != null && children.length() != 0) throw new JSONException("Invalid app tile");
        if (children != null) {
            if (children.length() > 100) throw new JSONException("Folder is too large");
            for (int i = 0; i < children.length(); i++) tile.children.add(decode(children.getJSONObject(i), ids, apps, depth + 1, columns));
        }
        TileLayout.normalize(tile.children, columns);
        return tile;
    }

    private static AtomicFile file(Context context) { return new AtomicFile(new File(context.getFilesDir(), "start.json")); }
}
