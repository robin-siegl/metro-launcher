package at.robin.metro;

import android.app.Activity;
import android.app.Dialog;
import android.app.role.RoleManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.provider.Settings;
import android.util.AtomicFile;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.window.OnBackInvokedDispatcher;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import at.robin.metro.data.AppCatalog;
import at.robin.metro.data.LauncherState;
import at.robin.metro.data.TileNotifications;
import at.robin.metro.model.Tile;
import at.robin.metro.model.TileLayout;
import at.robin.metro.ui.AppListView;
import at.robin.metro.ui.MetroPager;
import at.robin.metro.ui.MetroUi;
import at.robin.metro.ui.SettingsPanel;
import at.robin.metro.ui.TileBoard;
import at.robin.metro.ui.WallpaperCrop;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.IntConsumer;

public final class MainActivity extends Activity {
    private static final int PICK_PHOTO = 40, PICK_FONT = 41, EXPORT = 42, IMPORT = 43, HOME = 44;
    public LauncherState state;
    public AppCatalog catalog;
    public FrameLayout root;
    public MetroPager pager;
    public ScrollView startScroll;
    public TileBoard board, editingBoard;
    public Bitmap wallpaper;
    private AppListView apps;
    private View panel, editBar;
    private Typeface customFont;
    private boolean ready, receiversRegistered;
    private int restorePage;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() { if (board != null) board.redraw(); handler.postDelayed(this, 60_000 - System.currentTimeMillis() % 60_000); }
    };
    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { reloadApps(); }
    };
    private final BroadcastReceiver liveTiles = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { if (board != null) board.redraw(); }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); state = LauncherState.load(this); catalog = new AppCatalog(this);
        restorePage = saved == null ? 0 : saved.getInt("page", 0);
        root = new FrameLayout(this); root.setBackgroundColor(state.surface()); setContentView(root); configureWindow();
        TextView loading = MetroUi.text(this, "Start", 54); loading.setGravity(Gravity.CENTER);
        root.addView(loading, new FrameLayout.LayoutParams(-1, -1));
        if (Build.VERSION.SDK_INT >= 33) getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::handleBack);
        IntentFilter filter = new IntentFilter(); filter.addAction(Intent.ACTION_PACKAGE_ADDED); filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED); filter.addAction(Intent.ACTION_PACKAGE_REPLACED); filter.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(packages, filter, Context.RECEIVER_NOT_EXPORTED);
            registerReceiver(liveTiles, new IntentFilter(TileNotifications.CHANGED), Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerLegacyReceivers(filter);
        }
        receiversRegistered = true;
        reloadApps();
    }
    // Android 26–32 have no export flags. Android 33+ uses explicit NOT_EXPORTED above.
    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerLegacyReceivers(IntentFilter filter) {
        registerReceiver(packages, filter); registerReceiver(liveTiles, new IntentFilter(TileNotifications.CHANGED));
    }
    private void configureWindow() {
        Window window = getWindow(); window.setStatusBarColor(state.surface()); window.setNavigationBarColor(state.surface());
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        if (state.light) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        window.getDecorView().setSystemUiVisibility(flags);
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                android.graphics.Insets ime = insets.getInsets(WindowInsets.Type.ime());
                root.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            } else root.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        }); root.requestApplyInsets();
    }
    private void reloadApps() {
        worker.execute(() -> {
            AppCatalog next = new AppCatalog(this); next.reload();
            Bitmap image = null; Typeface font = null;
            if (!ready) {
                if (!state.background.isEmpty()) image = BitmapFactory.decodeFile(new File(getFilesDir(), "background.jpg").getPath());
                File fontFile = new File(getFilesDir(), "custom-font.ttf");
                if (fontFile.isFile()) try { font = Typeface.createFromFile(fontFile); } catch (RuntimeException ignored) { }
            }
            Bitmap loadedImage = image; Typeface loadedFont = font;
            runOnUiThread(() -> {
                if (isDestroyed()) return; catalog = next;
                if (!ready) {
                    wallpaper = loadedImage; customFont = loadedFont;
                    if (!state.initialized) createInitialTiles();
                    ready = true; buildHome();
                    if (!state.onboardingDone) showWelcome();
                } else { board.sync(); apps.refresh(); }
            });
        });
    }
    private void createInitialTiles() {
        String[] kinds = {"phone", "message", "mail", "browser", "camera", "photos", "people", "store"};
        for (int i = 0; i < kinds.length; i++) {
            String key = catalog.first(kinds[i]); if (key == null) continue;
            Tile tile = new Tile(key); tile.size = i == 5 ? Tile.Size.WIDE : Tile.Size.MEDIUM;
            TileLayout.append(state.tiles, tile, state.columns());
        }
        Tile calendar = new Tile("@calendar"); calendar.size = Tile.Size.WIDE; TileLayout.append(state.tiles, calendar, state.columns());
        Tile clock = new Tile("@clock"); TileLayout.append(state.tiles, clock, state.columns());
        Tile settings = new Tile("@settings"); TileLayout.append(state.tiles, settings, state.columns());
        state.initialized = true; persist();
    }
    private void buildHome() {
        endEditing(); root.removeAllViews(); panel = null;
        root.setBackgroundColor(state.surface()); configureWindow();
        pager = new MetroPager(this); pager.animations = state.animations;
        startScroll = new ScrollView(this); startScroll.setVerticalScrollBarEnabled(false); startScroll.setClipToPadding(false);
        LinearLayout start = MetroUi.vertical(this);
        start.setPadding(MetroUi.dp(this, 20), MetroUi.dp(this, 24), MetroUi.dp(this, 20), MetroUi.dp(this, 90));
        board = new TileBoard(this, state.tiles, null); start.addView(board, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout footer = new LinearLayout(this); footer.setGravity(Gravity.CENTER_VERTICAL);
        TextView settings = MetroUi.text(this, "•••", 26); settings.setContentDescription("start+theme settings");
        settings.setGravity(Gravity.CENTER_VERTICAL); settings.setMinHeight(MetroUi.dp(this, 64)); settings.setOnClickListener(v -> showSettings());
        footer.addView(settings, new LinearLayout.LayoutParams(0, -2, 1));
        View all = new View(this) {
            private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(android.graphics.Canvas canvas) {
                float radius = MetroUi.dp(MainActivity.this, 15), x = getWidth() - radius - MetroUi.dp(MainActivity.this, 2), y = getHeight() / 2f;
                paint.setStyle(android.graphics.Paint.Style.STROKE); paint.setStrokeWidth(MetroUi.dp(MainActivity.this, 1.5f)); paint.setColor(state.foreground());
                canvas.drawCircle(x, y, radius, paint);
                at.robin.metro.ui.MetroGlyph.draw(canvas, "arrow", x - radius * .63f, y - radius * .63f, radius * 1.26f, state.foreground());
            }
        };
        all.setFocusable(true); all.setContentDescription("Open all apps"); all.setOnClickListener(v -> pager.setPage(1, true));
        footer.addView(all, new LinearLayout.LayoutParams(MetroUi.dp(this, 56), MetroUi.dp(this, 64)));
        start.addView(footer, new LinearLayout.LayoutParams(-1, -2));
        startScroll.addView(start); startScroll.setOnScrollChangeListener((v, x, y, oldX, oldY) -> board.redraw());
        pager.addView(startScroll); apps = new AppListView(this); pager.addView(apps);
        pager.listener = page -> { if (page == 0) hideKeyboard(); }; root.addView(pager, new FrameLayout.LayoutParams(-1, -1));
        pager.post(() -> pager.setPage(restorePage, false));
    }
    public void persist() {
        try { state.save(this); } catch (IOException error) { toast("Could not save your layout. Check available storage."); }
    }
    public Typeface font() { return customFont == null ? MetroUi.defaultFont(this) : customFont; }
    public boolean hasCustomFont() { return customFont != null; }
    public void applyTheme() {
        int settingsY = panel instanceof SettingsPanel ? ((SettingsPanel) panel).getScrollY() : 0;
        int y = startScroll.getScrollY(); restorePage = pager.page(); persist(); buildHome();
        startScroll.post(() -> startScroll.scrollTo(0, y)); showSettings();
        SettingsPanel settings = (SettingsPanel) panel; settings.post(() -> settings.scrollTo(0, settingsY));
    }
    public void beginEditing(TileBoard active) {
        if (editingBoard != null && editingBoard != active) editingBoard.select(null);
        editingBoard = active; pager.locked = true;
    }
    public void editingChanged(TileBoard active) {
        if (active.selected == null && editingBoard == active) { editingBoard = null; pager.locked = false; }
        if (editBar != null) root.removeView(editBar); editBar = null;
        if (editingBoard == null || editingBoard.selected == null) return;
        Tile tile = editingBoard.selected; TileBoard source = editingBoard;
        LinearLayout bar = new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL); bar.setBackgroundColor(state.surface());
        addBarButton(bar, "customize", () -> customize(tile, source));
        if (source.owner != null) addBarButton(bar, "move out", () -> source.moveOut(tile));
        addBarButton(bar, "done", this::endEditing);
        editBar = bar; root.addView(bar, new FrameLayout.LayoutParams(-1, MetroUi.dp(this, 56), Gravity.BOTTOM));
    }
    private void addBarButton(LinearLayout bar, String text, Runnable action) {
        TextView button = MetroUi.text(this, text, 18); button.setGravity(Gravity.CENTER); button.setMinHeight(MetroUi.dp(this, 48));
        button.setOnClickListener(v -> action.run()); bar.addView(button, new LinearLayout.LayoutParams(0, -1, 1));
    }
    public void endEditing() {
        if (editingBoard != null) editingBoard.select(null);
        if (editBar != null && root != null) root.removeView(editBar); editBar = null; editingBoard = null;
        if (pager != null) pager.locked = false;
    }
    public void launch(String key) {
        hideKeyboard();
        if ("@settings".equals(key)) { showSettings(); return; }
        if ("@clock".equals(key)) { safeStart(new Intent(AlarmClock.ACTION_SHOW_ALARMS)); return; }
        if ("@calendar".equals(key)) {
            safeStart(new Intent(Intent.ACTION_VIEW, Uri.withAppendedPath(CalendarContract.CONTENT_URI, "time/" + System.currentTimeMillis()))); return;
        }
        ComponentName component = ComponentName.unflattenFromString(key);
        if (component == null) { toast("This app is unavailable"); return; }
        safeStart(Intent.makeMainActivity(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
    }
    private void safeStart(Intent intent) {
        try { startActivity(intent); } catch (android.content.ActivityNotFoundException | SecurityException error) { toast("No app is available for this action"); }
    }
    public void appMenu(AppCatalog.Entry entry) {
        List<String> labels = new ArrayList<>(); labels.add(isPinned(entry.key) ? "already pinned to start" : "pin to start");
        if (!entry.key.startsWith("@")) { labels.add("app info"); labels.add("uninstall"); }
        choose(entry.label, labels.toArray(new String[0]), index -> {
            if (index == 0) pin(entry.key);
            else if (index == 1) safeStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + AppCatalog.packageOf(entry.key))));
            else safeStart(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + AppCatalog.packageOf(entry.key))));
        });
    }
    private boolean isPinned(String key) {
        for (Tile tile : state.tiles) {
            if (key.equals(tile.app)) return true;
            for (Tile child : tile.children) if (key.equals(child.app)) return true;
        } return false;
    }
    public void pin(String key) {
        if (isPinned(key)) { toast("Already pinned to Start"); return; }
        if (tileCount() >= TileLayout.MAX_TILES) { toast("Start is full"); return; }
        Tile tile = new Tile(key); TileLayout.append(state.tiles, tile, state.columns()); persist(); board.sync();
        apps.closeSearch(); pager.setPage(0, true);
        board.post(() -> startScroll.smoothScrollTo(0, Math.max(0, board.getTop() + Math.round(tile.row * board.getWidth() / (float) state.columns()) - MetroUi.dp(this, 24))));
    }
    private int tileCount() { int count = state.tiles.size(); for (Tile tile : state.tiles) count += tile.children.size(); return count; }
    public void customize(Tile tile, TileBoard source) {
        String[] options = {"rename", "tile colour", tile.photo ? "use solid tile" : "use Start photo", tile.live ? "turn live tile off" : "turn live tile on",
            "small", "medium", "wide", "unpin from start"};
        choose(source.label(tile), options, index -> {
            switch (index) {
                case 0: rename(tile, () -> { source.sync(); persist(); }); return;
                case 1:
                    String[] names = new String[LauncherState.COLOR_NAMES.length + 1]; names[0] = "use accent colour";
                    System.arraycopy(LauncherState.COLOR_NAMES, 0, names, 1, LauncherState.COLOR_NAMES.length);
                    choose("tile colour", names, color -> { tile.color = color == 0 ? 0 : LauncherState.COLORS[color - 1]; persist(); source.sync(); }); return;
                case 2: tile.photo = !tile.photo; break;
                case 3: tile.live = !tile.live; break;
                case 4: case 5: case 6:
                    TileLayout.resize(source.tiles, tile, Tile.Size.values()[index - 4], state.columns()); break;
                default: source.unpin(tile); return;
            }
            persist(); source.sync();
        });
    }
    public void rename(Tile tile, Runnable after) {
        Dialog dialog = dialog(); LinearLayout body = dialogBody(tile.isFolder() ? "name folder" : "rename tile");
        EditText input = new EditText(this); input.setText(tile.label); input.setSingleLine(); input.setTextSize(24);
        input.setTextColor(state.foreground()); input.setHintTextColor(0xff888888); input.setHint(tile.isFolder() ? "folder" : catalog.label(tile.app));
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(100)});
        input.setTypeface(font()); body.addView(input); MetroUi.space(body, MetroUi.dp(this, 18));
        body.addView(MetroUi.button(this, "✓  done", () -> { tile.label = input.getText().toString().trim(); persist(); dialog.dismiss(); after.run(); }));
        dialog.setContentView(body); showDialog(dialog); input.requestFocus(); dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }
    public void showSettings() { endEditing(); showPanel(new SettingsPanel(this)); }
    public void showPanel(View next) {
        if (apps != null) apps.closeJump(); hideKeyboard(); if (panel != null) root.removeView(panel);
        panel = next; root.addView(next, new FrameLayout.LayoutParams(-1, -1));
    }
    public void closePanel() { hideKeyboard(); if (panel != null) root.removeView(panel); panel = null; }
    private Dialog dialog() { Dialog dialog = new Dialog(this); dialog.requestWindowFeature(Window.FEATURE_NO_TITLE); return dialog; }
    private LinearLayout dialogBody(String title) {
        LinearLayout body = MetroUi.vertical(this); body.setBackgroundColor(state.surface()); int p = MetroUi.dp(this, 22); body.setPadding(p, p, p, p);
        TextView heading = MetroUi.text(this, title, 30); body.addView(heading); MetroUi.space(body, MetroUi.dp(this, 20)); return body;
    }
    private void showDialog(Dialog dialog) {
        dialog.show(); Window window = dialog.getWindow();
        window.setBackgroundDrawableResource(android.R.color.transparent); window.setLayout(-1, -2); window.setGravity(Gravity.BOTTOM);
    }
    public void choose(String title, String[] choices, IntConsumer chosen) {
        Dialog dialog = dialog(); LinearLayout body = dialogBody(title);
        for (int i = 0; i < choices.length; i++) {
            final int index = i; TextView item = MetroUi.text(this, choices[i], 23);
            item.setMinHeight(MetroUi.dp(this, 52)); item.setGravity(Gravity.CENTER_VERTICAL); item.setFocusable(true);
            item.setOnClickListener(v -> { dialog.dismiss(); chosen.accept(index); }); body.addView(item);
        }
        ScrollView scroll = new ScrollView(this); scroll.addView(body); dialog.setContentView(scroll); showDialog(dialog);
        dialog.getWindow().setLayout(-1, Math.min(MetroUi.dp(this, 100 + choices.length * 54), Math.round(getResources().getDisplayMetrics().heightPixels * .82f)));
    }
    public void confirm(String title, String message, Runnable action) {
        Dialog dialog = dialog(); LinearLayout body = dialogBody(title); TextView text = MetroUi.text(this, message, 19); body.addView(text);
        MetroUi.space(body, MetroUi.dp(this, 22)); body.addView(MetroUi.button(this, "continue", () -> { dialog.dismiss(); action.run(); }));
        MetroUi.space(body, MetroUi.dp(this, 10)); body.addView(MetroUi.button(this, "cancel", dialog::dismiss)); dialog.setContentView(body); showDialog(dialog);
    }
    public void showHelp() {
        LinearLayout body = dialogBody("make Start yours");
        String[] help = {"Swipe left for all apps. Tap a letter to jump through the alphabet. Tap the search icon to search.",
            "Hold an app in the list, then choose pin to start.", "Hold a tile to arrange it. Drag to move it. The bottom-right arrow cycles small, medium and wide; the top-right × unpins it.",
            "In 8.1, hold a dragged tile over the centre of another tile until you feel a second vibration, then release to create a folder.",
            "Tap a folder to expand it. Tap its name to rename it. Hold a tile inside to resize, rearrange or move it out.",
            "Tap customize while editing for tile colours, names and live-tile controls. Tap a gap or done to finish.",
            "Find start+theme in the app list or tap ••• beneath the tiles. Android keeps control of the lock screen, notification shade and system navigation."};
        for (String text : help) { TextView view = MetroUi.text(this, text, 18); view.setLineSpacing(MetroUi.dp(this, 3), 1); body.addView(view); MetroUi.space(body, MetroUi.dp(this, 20)); }
        body.addView(MetroUi.button(this, "←  back", this::showSettings)); ScrollView scroll = new ScrollView(this); scroll.addView(body); showPanel(scroll);
    }
    private void showWelcome() {
        LinearLayout body = dialogBody("welcome back\nto Start.");
        TextView text = MetroUi.text(this, "Your apps. Your tiles.\n\nSwipe left for your app list. Hold a tile to move or resize it. Make it yours in start+theme.", 23);
        text.setLineSpacing(MetroUi.dp(this, 5), 1); body.addView(text); MetroUi.space(body, MetroUi.dp(this, 40));
        body.addView(MetroUi.button(this, "set as Home app", this::requestHome)); MetroUi.space(body, MetroUi.dp(this, 12));
        body.addView(MetroUi.button(this, "explore first", () -> { state.onboardingDone = true; persist(); closePanel(); }));
        body.setGravity(Gravity.CENTER_VERTICAL); ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.addView(body); showPanel(scroll);
    }
    public boolean isDefaultHome() {
        if (Build.VERSION.SDK_INT >= 29) {
            RoleManager roles = getSystemService(RoleManager.class);
            if (roles != null) return roles.isRoleHeld(RoleManager.ROLE_HOME);
        }
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo home = getPackageManager().resolveActivity(intent, 0);
        return home != null && home.activityInfo != null && getPackageName().equals(home.activityInfo.packageName);
    }
    public void requestHome() {
        if (isDefaultHome()) {
            if (!state.onboardingDone) { state.onboardingDone = true; persist(); closePanel(); }
            toast("Metro is already your Home app"); return;
        }
        if (Build.VERSION.SDK_INT >= 29) {
            RoleManager roles = getSystemService(RoleManager.class);
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) {
                try { startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME), HOME); return; }
                catch (android.content.ActivityNotFoundException ignored) { }
            }
        }
        try { startActivityForResult(new Intent(Settings.ACTION_HOME_SETTINGS), HOME); }
        catch (android.content.ActivityNotFoundException error) { androidSettings(); }
    }
    public void notificationAccess() {
        safeStart(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
    }
    public void androidSettings() { safeStart(new Intent(Settings.ACTION_SETTINGS)); }
    public void choosePhoto() { pick(PICK_PHOTO, "image/*"); }
    public void chooseFont() { pick(PICK_FONT, "*/*"); }
    private void pick(int code, String type) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType(type).addCategory(Intent.CATEGORY_OPENABLE);
        try { startActivityForResult(intent, code); } catch (android.content.ActivityNotFoundException error) { toast("No file picker is available"); }
    }
    public void exportLayout() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, "metro-start.json");
        try { startActivityForResult(intent, EXPORT); } catch (android.content.ActivityNotFoundException error) { toast("No file picker is available"); }
    }
    public void importLayout() { pick(IMPORT, "*/*"); }
    public void removeFont() {
        File file = new File(getFilesDir(), "custom-font.ttf"); if (file.isFile() && !file.delete()) { toast("Could not remove font"); return; }
        customFont = null; applyTheme();
    }
    public void applyWallpaper(Bitmap bitmap) {
        worker.execute(() -> {
            AtomicFile file = new AtomicFile(new File(getFilesDir(), "background.jpg")); FileOutputStream output = null;
            try {
                output = file.startWrite(); if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)) throw new IOException("Image encoding failed");
                file.finishWrite(output);
                runOnUiThread(() -> { if (!isDestroyed()) { wallpaper = bitmap; state.background = "background.jpg"; applyTheme(); } });
            } catch (IOException error) { if (output != null) file.failWrite(output); runOnUiThread(() -> toast("Could not save photo")); }
        });
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == HOME) {
            if (isDefaultHome()) { state.onboardingDone = true; persist(); closePanel(); toast("Metro is your Home app"); }
            return;
        }
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (request == PICK_PHOTO) {
            worker.execute(() -> {
                try {
                    Bitmap image = decodePhoto(uri);
                    runOnUiThread(() -> { if (!isDestroyed()) showPanel(new WallpaperCrop(this, image)); });
                } catch (IOException | RuntimeException | OutOfMemoryError error) { runOnUiThread(() -> toast("Could not open this photo")); }
            });
        } else if (request == PICK_FONT) {
            worker.execute(() -> {
                File pending = new File(getCacheDir(), "font-pending.ttf");
                try {
                    byte[] bytes = read(uri, 10_000_000);
                    try (FileOutputStream output = new FileOutputStream(pending)) { output.write(bytes); }
                    Typeface next = Typeface.createFromFile(pending);
                    AtomicFile fontFile = new AtomicFile(new File(getFilesDir(), "custom-font.ttf")); FileOutputStream out = null;
                    try { out = fontFile.startWrite(); out.write(bytes); fontFile.finishWrite(out); }
                    catch (IOException error) { if (out != null) fontFile.failWrite(out); throw error; }
                    runOnUiThread(() -> { if (!isDestroyed()) { customFont = next; applyTheme(); } });
                } catch (IOException | RuntimeException error) { runOnUiThread(() -> toast("Choose a valid TTF or OTF font file")); }
                finally { if (pending.exists()) pending.delete(); }
            });
        } else if (request == EXPORT) {
            byte[] json = state.json().getBytes(StandardCharsets.UTF_8);
            worker.execute(() -> {
                try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                    if (output == null) throw new IOException("No output stream"); output.write(json);
                    runOnUiThread(() -> toast("Layout exported"));
                } catch (IOException | RuntimeException error) { runOnUiThread(() -> toast("Could not export layout")); }
            });
        } else if (request == IMPORT) {
            worker.execute(() -> {
                try {
                    LauncherState imported = LauncherState.parse(new String(read(uri, 2_000_000), StandardCharsets.UTF_8));
                    runOnUiThread(() -> {
                        if (isDestroyed()) return;
                        confirm("replace your layout?", "Your current pins and theme will be replaced by this backup. Export first if you want to keep them.", () -> {
                            imported.onboardingDone = true; imported.initialized = true; state = imported;
                            if (wallpaper == null) state.background = ""; restorePage = 0; persist(); buildHome(); showSettings();
                        });
                    });
                } catch (IOException | org.json.JSONException | RuntimeException error) { runOnUiThread(() -> toast("This is not a valid Metro layout backup")); }
            });
        }
    }
    private byte[] read(Uri uri, int limit) throws IOException {
        try (InputStream input = getContentResolver().openInputStream(uri); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) throw new IOException("No input stream");
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) { if (output.size() + count > limit) throw new IOException("File too large"); output.write(buffer, 0, count); }
            return output.toByteArray();
        }
    }
    // Framework EXIF is only used for the Android 8/8.1 fallback; newer versions use ImageDecoder.
    @android.annotation.SuppressLint("ExifInterface")
    private Bitmap decodePhoto(Uri uri) throws IOException {
        if (Build.VERSION.SDK_INT >= 28) {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(), uri), (decoder, info, source) -> {
                int max = Math.max(info.getSize().getWidth(), info.getSize().getHeight());
                float factor = Math.min(1f, 2048f / max);
                decoder.setTargetSize(Math.max(1, Math.round(info.getSize().getWidth() * factor)), Math.max(1, Math.round(info.getSize().getHeight() * factor)));
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            });
        }
        BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
        try (InputStream input = getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(input, null, options); }
        options.inSampleSize = 1; while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 2048) options.inSampleSize *= 2;
        options.inJustDecodeBounds = false; Bitmap bitmap;
        try (InputStream input = getContentResolver().openInputStream(uri)) { bitmap = BitmapFactory.decodeStream(input, null, options); }
        if (bitmap == null) throw new IOException("Invalid photo");
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input != null) {
                int orientation = new android.media.ExifInterface(input).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL);
                Matrix matrix = new Matrix();
                switch (orientation) {
                    case android.media.ExifInterface.ORIENTATION_ROTATE_90: matrix.postRotate(90); break;
                    case android.media.ExifInterface.ORIENTATION_ROTATE_180: matrix.postRotate(180); break;
                    case android.media.ExifInterface.ORIENTATION_ROTATE_270: matrix.postRotate(270); break;
                    case android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL: matrix.postScale(-1, 1); break;
                    case android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL: matrix.postScale(1, -1); break;
                    case android.media.ExifInterface.ORIENTATION_TRANSPOSE: matrix.postRotate(90); matrix.postScale(-1, 1); break;
                    case android.media.ExifInterface.ORIENTATION_TRANSVERSE: matrix.postRotate(270); matrix.postScale(-1, 1); break;
                    default: break;
                }
                if (!matrix.isIdentity()) bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            }
        } catch (IOException unsupportedMetadata) { /* A valid PNG or other image may have no supported EXIF container. */ }
        return bitmap;
    }
    public void hideKeyboard() { if (root != null) getSystemService(InputMethodManager.class).hideSoftInputFromWindow(root.getWindowToken(), 0); }
    public void toast(String text) { if (!isDestroyed()) Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }
    private void handleBack() {
        if (apps != null && apps.closeJump()) return;
        if (panel != null) { if (!(panel instanceof SettingsPanel) && state.onboardingDone) showSettings(); else closePanel(); return; }
        if (editingBoard != null) { endEditing(); return; }
        if (board != null && board.closeFolder()) return;
        if (apps != null && apps.closeSearch()) return;
        if (pager != null && pager.page() == 1) { pager.setPage(0, true); return; }
        if (startScroll != null) startScroll.smoothScrollTo(0, 0);
    }
    // API 33+ is registered with the platform OnBackInvokedDispatcher in onCreate.
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() { if (Build.VERSION.SDK_INT < 33) handleBack(); }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent);
        if (ready) { closePanel(); apps.closeJump(); apps.closeSearch(); endEditing(); board.closeFolder(); pager.setPage(0, true); startScroll.smoothScrollTo(0, 0); }
    }
    @Override public void onConfigurationChanged(Configuration configuration) { super.onConfigurationChanged(configuration); root.requestLayout(); if (board != null) board.requestLayout(); }
    @Override protected void onSaveInstanceState(Bundle saved) { super.onSaveInstanceState(saved); saved.putInt("page", pager == null ? 0 : pager.page()); }
    @Override protected void onResume() { super.onResume(); handler.removeCallbacks(tick); handler.post(tick); if (ready) reloadApps(); }
    @Override protected void onPause() { super.onPause(); handler.removeCallbacks(tick); if (ready) persist(); }
    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null); worker.shutdown();
        if (receiversRegistered) { unregisterReceiver(packages); unregisterReceiver(liveTiles); }
        super.onDestroy();
    }
}
