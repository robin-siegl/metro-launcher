package at.robin.metro.ui;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import at.robin.metro.MainActivity;
import at.robin.metro.data.AppCatalog;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// Constructed with its controller in Java; never inflated from XML.
@android.annotation.SuppressLint("ViewConstructor")
public final class AppListView extends LinearLayout {
    private final MainActivity host;
    private final ListView list;
    private final EditText search;
    private final Adapter adapter = new Adapter();
    private final List<Object> rows = new ArrayList<>();
    private FrameLayout jump;
    public AppListView(MainActivity host) {
        super(host); this.host = host; setOrientation(VERTICAL);
        setPadding(MetroUi.dp(host, 16), MetroUi.dp(host, 12), MetroUi.dp(host, 16), 0);
        LinearLayout header = new LinearLayout(host); header.setGravity(Gravity.CENTER_VERTICAL);
        View searchButton = new View(host) {
            @Override protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                MetroGlyph.draw(canvas, "search", MetroUi.dp(host, 8), MetroUi.dp(host, 8), MetroUi.dp(host, 30), host.state.foreground());
            }
        };
        searchButton.setContentDescription("Search apps"); searchButton.setFocusable(true);
        searchButton.setOnClickListener(v -> openSearch());
        header.addView(searchButton, new LayoutParams(MetroUi.dp(host, 48), MetroUi.dp(host, 52)));
        search = new EditText(host); search.setSingleLine(true); search.setTextSize(22);
        search.setTypeface(host.font()); search.setTextColor(host.state.foreground());
        search.setHintTextColor(host.state.light ? 0xff666666 : 0xffaaaaaa); search.setHint("search apps");
        search.setBackgroundColor(Color.TRANSPARENT); search.setVisibility(INVISIBLE);
        search.setPadding(0, 0, 0, 0); search.setSelectAllOnFocus(true);
        header.addView(search, new LayoutParams(0, MetroUi.dp(host, 52), 1));
        addView(header, new LayoutParams(LayoutParams.MATCH_PARENT, MetroUi.dp(host, 60)));
        list = new ListView(host); list.setDivider(null); list.setVerticalScrollBarEnabled(false);
        list.setCacheColorHint(Color.TRANSPARENT); list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            Object item = rows.get(position);
            if (item instanceof String) showJump(); else host.launch(((AppCatalog.Entry) item).key);
        });
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            Object item = rows.get(position);
            if (!(item instanceof AppCatalog.Entry)) return false;
            AppCatalog.Entry entry = (AppCatalog.Entry) item;
            host.appMenu(entry); return true;
        });
        addView(list, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { refresh(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        refresh();
    }
    public void refresh() {
        rows.clear(); String previous = "";
        for (AppCatalog.Entry entry : host.catalog.search(search.getText().toString())) {
            if (search.length() == 0 && !entry.section.equals(previous)) { rows.add(entry.section); previous = entry.section; }
            rows.add(entry);
        }
        adapter.notifyDataSetChanged();
    }
    public void openSearch() {
        search.setVisibility(VISIBLE); search.requestFocus();
        host.getSystemService(InputMethodManager.class).showSoftInput(search, InputMethodManager.SHOW_IMPLICIT);
    }
    public boolean closeSearch() {
        if (search.getVisibility() != VISIBLE) return false;
        search.setText(""); search.clearFocus(); search.setVisibility(INVISIBLE); host.hideKeyboard(); return true;
    }
    public boolean closeJump() {
        if (jump == null) return false;
        host.root.removeView(jump); jump = null; return true;
    }
    private void showJump() {
        if (jump != null) return;
        host.hideKeyboard(); jump = new FrameLayout(host); jump.setBackgroundColor(host.state.surface());
        jump.setClickable(true);
        Set<String> available = new LinkedHashSet<>(); for (AppCatalog.Entry entry : host.catalog.entries) available.add(entry.section);
        List<String> letters = new ArrayList<>(); letters.add("#");
        for (char c = 'A'; c <= 'Z'; c++) letters.add(String.valueOf(c));
        for (String section : available) if (!letters.contains(section)) letters.add(section);
        GridLayout grid = new GridLayout(host); grid.setColumnCount(4);
        int margin = MetroUi.dp(host, 6);
        int cell = Math.min(MetroUi.dp(host, 82), (host.root.getWidth() - MetroUi.dp(host, 40)) / 4 - margin * 2);
        for (String letter : letters) {
            TextView view = MetroUi.text(host, letter.toLowerCase(Locale.getDefault()), 36); view.setGravity(Gravity.CENTER);
            boolean enabled = available.contains(letter);
            view.setTextColor(enabled ? Color.WHITE : host.state.light ? 0xff999999 : 0xff555555);
            view.setBackground(enabled ? MetroUi.border(host.state.accent(), host.state.accent(), 1)
                : MetroUi.border(host.state.light ? 0xffcccccc : 0xff333333, host.state.surface(), MetroUi.dp(host, 1)));
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(); params.width = cell; params.height = cell;
            params.setMargins(margin, margin, margin, margin); grid.addView(view, params);
            view.setContentDescription("Jump to " + letter); view.setEnabled(enabled); view.setFocusable(enabled);
            view.setOnClickListener(v -> {
                closeJump();
                for (int i = 0; i < rows.size(); i++) if (letter.equals(rows.get(i))) { list.setSelection(i); break; }
            });
        }
        android.widget.ScrollView scroll = new android.widget.ScrollView(host); scroll.addView(grid);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        jump.addView(scroll, params); host.root.addView(jump, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    private final class Adapter extends BaseAdapter {
        @Override public int getCount() { return rows.size(); }
        @Override public Object getItem(int position) { return rows.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public int getViewTypeCount() { return 2; }
        @Override public int getItemViewType(int position) { return rows.get(position) instanceof String ? 0 : 1; }
        @Override public View getView(int position, View convert, ViewGroup parent) {
            Object item = rows.get(position);
            if (item instanceof String) {
                FrameLayout frame = new FrameLayout(host);
                TextView section = MetroUi.text(host, ((String) item).toLowerCase(Locale.getDefault()), 28);
                section.setGravity(Gravity.CENTER); section.setTextColor(host.state.accent());
                section.setBackground(MetroUi.border(host.state.accent(), host.state.surface(), MetroUi.dp(host, 2)));
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(MetroUi.dp(host, 40), MetroUi.dp(host, 40));
                params.topMargin = MetroUi.dp(host, 12); params.bottomMargin = MetroUi.dp(host, 8);
                frame.addView(section, params); frame.setContentDescription("Jump to letter " + item); return frame;
            }
            AppCatalog.Entry entry = (AppCatalog.Entry) item;
            LinearLayout row = new LinearLayout(host); row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(MetroUi.dp(host, 58)); row.setPadding(0, MetroUi.dp(host, 5), 0, MetroUi.dp(host, 5));
            View icon = new View(host) {
                private final Rect old = new Rect();
                @Override protected void onDraw(Canvas canvas) {
                    canvas.drawColor(host.state.accent());
                    if (!entry.glyph.isEmpty()) MetroGlyph.draw(canvas, entry.glyph, getWidth() * .15f, getHeight() * .15f, getWidth() * .7f, Color.WHITE);
                    else if (entry.icon != null) {
                        Drawable drawable = entry.icon; old.set(drawable.getBounds());
                        drawable.setBounds(0, 0, getWidth(), getHeight()); drawable.draw(canvas); drawable.setBounds(old);
                    }
                }
            };
            row.addView(icon, new LayoutParams(MetroUi.dp(host, 42), MetroUi.dp(host, 42)));
            TextView label = MetroUi.text(host, entry.label, 26); label.setSingleLine(true);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END); label.setPadding(MetroUi.dp(host, 14), 0, 0, 0);
            row.addView(label, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1)); row.setContentDescription(entry.label); return row;
        }
    }
}
