package at.robin.metro.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import at.robin.metro.MainActivity;
import at.robin.metro.model.Tile;
import at.robin.metro.model.TileLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Constructed with controller and tile model in Java; never inflated from XML.
@android.annotation.SuppressLint("ViewConstructor")
public final class TileBoard extends ViewGroup {
    private final MainActivity host;
    public final List<Tile> tiles;
    public final Tile owner;
    public Tile selected;
    private final Map<String, TileView> views = new HashMap<>();
    private final Map<String, Rect> oldPositions = new HashMap<>();
    private float unit, step;
    private int gap, columns, expansionRow;
    private LinearLayout folderPanel;
    private Tile openFolder;
    private TileBoard folderBoard;
    private TileView dragged;
    private float gripX, gripY, rawX, rawY;
    private int dropColumn, dropRow;
    private Tile hover;
    private long hoverSince;
    private boolean folderReady;
    private final Paint guide = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Runnable autoScroll = new Runnable() {
        @Override public void run() {
            if (dragged == null) return;
            ScrollView scroll = host.startScroll;
            int[] where = new int[2]; scroll.getLocationOnScreen(where);
            int edge = MetroUi.dp(host, 72), delta = 0;
            if (rawY < where[1] + edge) delta = -MetroUi.dp(host, 10);
            if (rawY > where[1] + scroll.getHeight() - edge) delta = MetroUi.dp(host, 10);
            if (delta != 0) { scroll.scrollBy(0, delta); updateDrag(); }
            updateHoverReady();
            postDelayed(this, 16);
        }
    };

    public TileBoard(MainActivity host, List<Tile> tiles, Tile owner) {
        super(host); this.host = host; this.tiles = tiles; this.owner = owner;
        setClipChildren(false); setClipToPadding(false); setWillNotDraw(false);
        gap = MetroUi.dp(host, 8); sync();
    }
    public void sync() {
        oldPositions.clear();
        for (Map.Entry<String, TileView> entry : views.entrySet()) {
            View view = entry.getValue();
            if (view.getWidth() > 0) oldPositions.put(entry.getKey(), new Rect(view.getLeft(), view.getTop(), view.getRight(), view.getBottom()));
        }
        List<String> remove = new ArrayList<>(views.keySet());
        for (Tile tile : tiles) {
            TileView view = views.get(tile.id);
            if (view == null) { view = new TileView(host, this, tile); views.put(tile.id, view); addView(view); }
            view.updateDescription(); view.invalidate(); remove.remove(tile.id);
        }
        for (String id : remove) { removeView(views.remove(id)); }
        if (selected != null && !tiles.contains(selected)) select(null);
        if (openFolder != null && !tiles.contains(openFolder)) closeFolder();
        if (folderBoard != null) folderBoard.sync();
        requestLayout(); invalidate();
    }
    public String label(Tile tile) { return tile.label.isEmpty() ? (tile.isFolder() ? "folder" : host.catalog.label(tile.app)) : tile.label; }
    public void activate(Tile tile) {
        if (tile.isFolder()) { if (tile == openFolder) closeFolder(); else expandFolder(tile); }
        else host.launch(tile.app);
    }
    public void select(Tile tile) {
        if (tile != null) host.beginEditing(this);
        selected = tile;
        for (TileView view : views.values()) {
            view.setAlpha(tile == null || view.tile == tile ? 1 : .78f); view.invalidate();
        }
        host.editingChanged(this);
    }
    public void resize(Tile tile) {
        select(tile); TileLayout.resize(tiles, tile, tile.size.next(), columnCount());
        host.persist(); sync();
    }
    public boolean moveBy(Tile tile, int x, int y) {
        TileLayout.move(tiles, tile, tile.column + x, tile.row + y, columnCount());
        host.persist(); sync(); return true;
    }
    public void unpin(Tile tile) {
        if (tile.isFolder()) {
            host.confirm("unpin folder?", "This removes the folder and its pins from Start. Your apps stay installed.", () -> removeTile(tile));
        } else removeTile(tile);
    }
    private void removeTile(Tile tile) {
        tiles.remove(tile); if (selected == tile) select(null);
        if (owner != null && tiles.isEmpty()) host.board.removeEmptyFolder(owner);
        host.persist(); sync();
    }
    public void removeEmptyFolder(Tile folder) {
        if (openFolder == folder) closeFolder();
        tiles.remove(folder); sync();
    }
    public void moveOut(Tile tile) {
        if (owner == null) return;
        tiles.remove(tile); TileLayout.append(host.state.tiles, tile, host.state.columns());
        if (tiles.isEmpty()) host.board.removeEmptyFolder(owner);
        select(null); host.persist(); host.board.sync();
    }

    public void expandFolder(Tile tile) {
        closeFolder(); openFolder = tile;
        folderPanel = MetroUi.vertical(host);
        folderPanel.setPadding(0, MetroUi.dp(host, 10), 0, MetroUi.dp(host, 14));
        TextView name = MetroUi.text(host, label(tile), 24);
        name.setTextColor(host.state.accent()); name.setPadding(0, 0, 0, MetroUi.dp(host, 14));
        name.setContentDescription("Folder name. Tap to rename.");
        name.setOnClickListener(v -> host.rename(tile, () -> { name.setText(label(tile)); sync(); }));
        folderPanel.addView(name);
        folderBoard = new TileBoard(host, tile.children, tile);
        folderPanel.addView(folderBoard, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        TextView close = MetroUi.text(host, "close folder  ↑", 17);
        close.setPadding(0, MetroUi.dp(host, 18), 0, MetroUi.dp(host, 8)); close.setOnClickListener(v -> closeFolder());
        folderPanel.addView(close); addView(folderPanel); requestLayout();
    }
    public boolean closeFolder() {
        if (folderPanel == null) return false;
        if (folderBoard != null && host.editingBoard == folderBoard) folderBoard.select(null);
        removeView(folderPanel); folderPanel = null; folderBoard = null; openFolder = null; requestLayout(); return true;
    }
    private int columnCount() { return host.state.columns(); }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec); columns = columnCount();
        unit = (width - gap * (columns - 1)) / (float) columns; step = unit + gap;
        if (unit <= 0) { setMeasuredDimension(width, 0); return; }
        for (Tile tile : tiles) {
            TileView view = views.get(tile.id);
            view.measure(MeasureSpec.makeMeasureSpec(Math.round(tile.size.width * step - gap), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(Math.round(tile.size.height * step - gap), MeasureSpec.EXACTLY));
        }
        int height = Math.round(TileLayout.rows(tiles) * step);
        if (folderPanel != null) {
            expansionRow = openFolder.row + openFolder.size.height;
            boolean crossing;
            do {
                crossing = false;
                for (Tile tile : tiles) if (tile.row < expansionRow && tile.row + tile.size.height > expansionRow) {
                    expansionRow = tile.row + tile.size.height; crossing = true;
                }
            } while (crossing);
            folderPanel.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            height += folderPanel.getMeasuredHeight();
        }
        setMeasuredDimension(width, Math.max(height, MetroUi.dp(host, 32)));
    }
    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (Tile tile : tiles) {
            TileView view = views.get(tile.id); int x = Math.round(tile.column * step), y = Math.round(tile.row * step);
            if (folderPanel != null && tile.row >= expansionRow) y += folderPanel.getMeasuredHeight();
            view.layout(x, y, x + view.getMeasuredWidth(), y + view.getMeasuredHeight());
            Rect old = oldPositions.get(tile.id);
            if (host.state.animations && old != null && view != dragged && (old.left != x || old.top != y)) {
                view.setTranslationX(old.left - x); view.setTranslationY(old.top - y);
                view.animate().translationX(0).translationY(0).setDuration(180).start();
            }
        }
        if (folderPanel != null) {
            int top = Math.round(expansionRow * step);
            folderPanel.layout(0, top, r - l, top + folderPanel.getMeasuredHeight());
        }
        oldPositions.clear();
    }
    public int scrollOffset() { return host.startScroll == null ? 0 : host.startScroll.getScrollY(); }
    public void redraw() {
        for (TileView view : views.values()) view.invalidate();
        if (folderBoard != null) folderBoard.redraw();
    }
    public void beginDrag(TileView view, float x, float y) {
        if (folderPanel != null && owner == null) closeFolder();
        dragged = view; int[] location = new int[2]; view.getLocationOnScreen(location);
        gripX = x - location[0]; gripY = y - location[1];
        rawX = x; rawY = y; view.bringToFront(); view.setElevation(MetroUi.dp(host, 8));
        post(autoScroll);
    }
    public void drag(TileView view, float x, float y) { rawX = x; rawY = y; updateDrag(); }
    private void updateDrag() {
        if (dragged == null || step <= 0) return;
        int[] where = new int[2]; getLocationOnScreen(where);
        float x = rawX - where[0] - gripX, y = rawY - where[1] - gripY;
        dragged.setTranslationX(x - dragged.getLeft()); dragged.setTranslationY(y - dragged.getTop());
        dropColumn = Math.max(0, Math.min(Math.round(x / step), columns - dragged.tile.size.width));
        dropRow = Math.max(0, Math.min(Math.round(y / step), TileLayout.MAX_ROW));
        Tile candidate = null;
        if (owner == null && !host.state.phone8 && !dragged.tile.isFolder()) {
            float centerX = x + dragged.getWidth() / 2f, centerY = y + dragged.getHeight() / 2f;
            for (Tile tile : tiles) {
                if (tile == dragged.tile) continue;
                TileView view = views.get(tile.id);
                if (centerX > view.getLeft() + view.getWidth() * .2f && centerX < view.getRight() - view.getWidth() * .2f
                    && centerY > view.getTop() + view.getHeight() * .2f && centerY < view.getBottom() - view.getHeight() * .2f) {
                    candidate = tile; break;
                }
            }
        }
        if (candidate != hover) { hover = candidate; hoverSince = android.os.SystemClock.uptimeMillis(); folderReady = false; }
        updateHoverReady(); invalidate();
    }
    private void updateHoverReady() {
        boolean ready = hover != null && android.os.SystemClock.uptimeMillis() - hoverSince > 650;
        if (ready != folderReady) {
            folderReady = ready;
            if (ready) performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            invalidate();
        }
    }
    public void drop(TileView view, boolean commit) {
        if (dragged == null) return;
        int visualX = Math.round(view.getX()), visualY = Math.round(view.getY());
        removeCallbacks(autoScroll);
        view.setTranslationX(0); view.setTranslationY(0); view.setElevation(0);
        if (commit) {
            if (folderReady && hover != null && tiles.contains(hover)) merge(view.tile, hover);
            else TileLayout.move(tiles, view.tile, dropColumn, dropRow, columns);
            host.persist();
        }
        dragged = null; hover = null; folderReady = false; getParent().requestDisallowInterceptTouchEvent(false); sync();
        if (tiles.contains(view.tile)) oldPositions.put(view.tile.id, new Rect(visualX, visualY,
            visualX + view.getWidth(), visualY + view.getHeight()));
    }
    private void merge(Tile source, Tile target) {
        if (source.isFolder() || owner != null) return;
        if (target.isFolder() && target.children.size() >= 100) { host.toast("This folder is full"); return; }
        tiles.remove(source);
        if (target.isFolder()) TileLayout.append(target.children, source, host.state.columns());
        else {
            Tile folder = new Tile("@folder"); folder.label = "folder";
            folder.size = target.size == Tile.Size.SMALL ? Tile.Size.MEDIUM : target.size;
            folder.row = target.row; folder.column = target.column;
            tiles.remove(target);
            TileLayout.append(folder.children, target, host.state.columns()); TileLayout.append(folder.children, source, host.state.columns());
            tiles.add(folder); TileLayout.move(tiles, folder, folder.column, folder.row, columns);
        }
        select(null);
    }
    @Override protected void onDraw(Canvas canvas) {
        if (dragged == null) return;
        guide.setColor(host.state.accent()); guide.setStyle(Paint.Style.STROKE); guide.setStrokeWidth(MetroUi.dp(host, 2));
        if (folderReady && hover != null) {
            TileView view = views.get(hover.id);
            canvas.drawRect(view.getLeft() - 3, view.getTop() - 3, view.getRight() + 3, view.getBottom() + 3, guide);
        } else canvas.drawRect(dropColumn * step, dropRow * step,
            (dropColumn + dragged.tile.size.width) * step - gap, (dropRow + dragged.tile.size.height) * step - gap, guide);
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_UP) { host.endEditing(); performClick(); }
        return true;
    }
    @Override public boolean performClick() { super.performClick(); return true; }
    @Override protected void onDetachedFromWindow() { removeCallbacks(autoScroll); super.onDetachedFromWindow(); }
}
