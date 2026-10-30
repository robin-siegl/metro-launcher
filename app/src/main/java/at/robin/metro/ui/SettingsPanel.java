package at.robin.metro.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import at.robin.metro.MainActivity;
import at.robin.metro.data.LauncherState;
import at.robin.metro.model.TileLayout;

// Constructed with its controller in Java; never inflated from XML.
@android.annotation.SuppressLint("ViewConstructor")
public final class SettingsPanel extends ScrollView {
    private final MainActivity host;
    private final LinearLayout body;
    public SettingsPanel(MainActivity host) {
        super(host); this.host = host; setBackgroundColor(host.state.surface()); setFillViewport(true);
        body = MetroUi.vertical(host); int padding = MetroUi.dp(host, 22);
        body.setPadding(padding, MetroUi.dp(host, 20), padding, MetroUi.dp(host, 48)); addView(body);
        TextView back = MetroUi.text(host, "←  SETTINGS", 17); back.setPadding(0, 0, 0, MetroUi.dp(host, 14));
        back.setOnClickListener(v -> host.closePanel()); body.addView(back);
        body.addView(MetroUi.text(host, "start+theme", 44)); gap(30);
        label("Background");
        button(host.state.light ? "light" : "dark", () -> host.choose("background", new String[]{"dark", "light"}, index -> {
            host.state.light = index == 1; host.applyTheme();
        })); gap(26);
        label("Accent colour");
        TextView accent = MetroUi.button(host, LauncherState.COLOR_NAMES[host.state.accentIndex], this::colors);
        accent.setTextColor(0xffffffff); accent.setBackgroundColor(host.state.accent()); body.addView(accent); gap(28);
        label("Experience");
        button(host.state.phone8 ? "Windows Phone 8" : "Windows Phone 8.1", () -> host.choose("experience",
            new String[]{"Windows Phone 8", "Windows Phone 8.1"}, index -> {
                host.state.phone8 = index == 0; host.applyTheme();
            }));
        detail("8 uses solid tiles. 8.1 adds photo tiles and drag-to-folder."); gap(28);
        if (!host.state.phone8) {
            label("Start background");
            if (!host.state.background.isEmpty()) {
                View preview = new View(host) {
                    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                    private final android.graphics.Rect bounds = new android.graphics.Rect();
                    @Override protected void onDraw(Canvas canvas) {
                        if (host.wallpaper == null) return;
                        int gap = MetroUi.dp(host, 6), w = (getWidth() - gap * 3) / 4, h = getHeight();
                        for (int i = 0; i < 4; i++) {
                            canvas.save(); canvas.clipRect(i * (w + gap), 0, i * (w + gap) + w, h);
                            bounds.set(0, 0, getWidth(), h); canvas.drawBitmap(host.wallpaper, null, bounds, paint); canvas.restore();
                        }
                    }
                };
                body.addView(preview, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, MetroUi.dp(host, 130))); gap(12);
            }
            button("choose photo", host::choosePhoto); gap(10);
            if (!host.state.background.isEmpty()) button("remove photo", () -> { host.state.background = ""; host.wallpaper = null; host.applyTheme(); });
            detail("The photo shows through tiles. Gaps stay the background colour."); gap(28);
        }
        toggle("Show more Tiles", host.state.moreTiles, () -> {
            host.state.moreTiles = !host.state.moreTiles; TileLayout.reflow(host.state.tiles, host.state.columns());
            for (at.robin.metro.model.Tile tile : host.state.tiles) if (tile.isFolder()) TileLayout.reflow(tile.children, host.state.columns());
            host.applyTheme();
        });
        detail("Switch between two and three columns of medium tiles."); gap(24);
        toggle("Animations", host.state.animations, () -> { host.state.animations = !host.state.animations; host.applyTheme(); }); gap(24);
        toggle("Monochrome app icons", host.state.monochrome, () -> { host.state.monochrome = !host.state.monochrome; host.applyTheme(); });
        detail("Common phone apps use Metro symbols. Other apps keep their own icons unless enabled."); gap(28);
        label("Live tiles");
        button("notification access", host::notificationAccess);
        detail("Optional. Active notifications supply counts. Clock and date tiles work without access."); gap(16);
        toggle("Notification text on wide tiles", host.state.notificationPreview, () -> {
            host.state.notificationPreview = !host.state.notificationPreview; host.applyTheme();
        }); detail("Only enable this if you want notification titles and messages visible on Start."); gap(28);
        label("Font"); button(host.hasCustomFont() ? "change custom font" : "choose font file", host::chooseFont);
        detail("Uses Selawik Light by default. You can also supply your own TTF or OTF font.");
        if (host.hasCustomFont()) { gap(10); button("use default font", host::removeFont); } gap(28);
        label("Home app"); button(host.isDefaultHome() ? "Metro is your Home app" : "set as default Home", host::requestHome); gap(28);
        label("Your layout"); button("export layout", host::exportLayout); gap(10); button("import layout", host::importLayout);
        detail("Includes tile positions, folders, labels and theme. Photo and font files are chosen separately."); gap(28);
        button("how to use", host::showHelp); gap(10);
        button("Android settings", host::androidSettings); gap(32);
        detail("Metro Launcher 0.1.0\nAn independent Windows Phone inspired Android home.\nNo ads, analytics, accounts or network permission.");
    }
    private void label(String text) { TextView view = MetroUi.text(host, text, 19); body.addView(view); gap(9); }
    private void detail(String text) {
        gap(8); TextView view = MetroUi.text(host, text, 15); view.setTextColor(host.state.light ? 0xff666666 : 0xff999999);
        view.setLineSpacing(MetroUi.dp(host, 3), 1); body.addView(view);
    }
    private void button(String text, Runnable action) { body.addView(MetroUi.button(host, text, action)); }
    private void gap(int height) { MetroUi.space(body, MetroUi.dp(host, height)); }
    private void toggle(String title, boolean checked, Runnable action) {
        LinearLayout row = new LinearLayout(host); row.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = MetroUi.text(host, title, 20); row.addView(label, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        View toggle = new View(host) {
            final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas canvas) {
                float d = getResources().getDisplayMetrics().density;
                paint.setColor(host.state.foreground()); paint.setStrokeWidth(2 * d); paint.setStyle(Paint.Style.STROKE);
                canvas.drawRect(2 * d, 10 * d, 62 * d, 36 * d, paint); paint.setStyle(Paint.Style.FILL);
                paint.setColor(checked ? host.state.accent() : host.state.surface());
                canvas.drawRect(6 * d, 14 * d, 58 * d, 32 * d, paint);
                paint.setColor(host.state.foreground()); float left = (checked ? 43 : 2) * d;
                canvas.drawRect(left, 7 * d, left + 19 * d, 39 * d, paint);
            }
            @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(info); info.setClassName("android.widget.Switch"); info.setCheckable(true); info.setChecked(checked);
            }
        };
        toggle.setContentDescription(title + (checked ? ", on" : ", off")); toggle.setFocusable(true); toggle.setOnClickListener(v -> action.run());
        row.addView(toggle, new LinearLayout.LayoutParams(MetroUi.dp(host, 66), MetroUi.dp(host, 48)));
        label.setOnClickListener(v -> action.run()); body.addView(row);
    }
    private void colors() {
        LinearLayout panel = MetroUi.vertical(host); panel.setBackgroundColor(host.state.surface());
        panel.setPadding(MetroUi.dp(host, 20), MetroUi.dp(host, 24), MetroUi.dp(host, 20), MetroUi.dp(host, 30));
        TextView back = MetroUi.text(host, "←  start+theme", 18); back.setOnClickListener(v -> host.showSettings()); panel.addView(back);
        MetroUi.space(panel, MetroUi.dp(host, 20)); panel.addView(MetroUi.text(host, "accent colour", 40)); MetroUi.space(panel, MetroUi.dp(host, 28));
        GridLayout grid = new GridLayout(host); grid.setColumnCount(4);
        int cell = (host.root.getWidth() - MetroUi.dp(host, 40)) / 4;
        for (int i = 0; i < LauncherState.COLORS.length; i++) {
            final int index = i; TextView swatch = MetroUi.text(host, LauncherState.COLOR_NAMES[i], 12);
            swatch.setTextColor(0xffffffff); swatch.setGravity(Gravity.BOTTOM | Gravity.START);
            swatch.setPadding(MetroUi.dp(host, 6), 0, MetroUi.dp(host, 4), MetroUi.dp(host, 6)); swatch.setBackgroundColor(LauncherState.COLORS[i]);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(); params.width = cell - MetroUi.dp(host, 8); params.height = cell - MetroUi.dp(host, 8);
            params.setMargins(MetroUi.dp(host, 4), MetroUi.dp(host, 4), MetroUi.dp(host, 4), MetroUi.dp(host, 4));
            swatch.setContentDescription(LauncherState.COLOR_NAMES[i] + (index == host.state.accentIndex ? ", selected" : ""));
            swatch.setFocusable(true); swatch.setOnClickListener(v -> { host.state.accentIndex = index; host.applyTheme(); }); grid.addView(swatch, params);
        }
        panel.addView(grid); ScrollView scroll = new ScrollView(host); scroll.addView(panel); host.showPanel(scroll);
    }
}
