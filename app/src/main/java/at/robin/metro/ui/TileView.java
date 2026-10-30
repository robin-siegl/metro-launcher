package at.robin.metro.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;
import at.robin.metro.MainActivity;
import at.robin.metro.data.AppCatalog;
import at.robin.metro.data.TileNotifications;
import at.robin.metro.model.Tile;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

// Constructed with controller and tile model in Java; never inflated from XML.
@android.annotation.SuppressLint("ViewConstructor")
public final class TileView extends View {
    private static final int RESIZE_ACTION = 0x01030001, UNPIN_ACTION = 0x01030002, CUSTOMIZE_ACTION = 0x01030003;
    public final Tile tile;
    private final MainActivity host;
    private final TileBoard board;
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Matrix imageMatrix = new Matrix();
    private final Rect oldIconBounds = new Rect();
    private final Date now = new Date();
    private final DateFormat dayNumber = new SimpleDateFormat("d", Locale.getDefault());
    private final DateFormat dayName = new SimpleDateFormat("EEEE", Locale.getDefault());
    private final DateFormat dateLabel = DateFormat.getDateInstance(DateFormat.MEDIUM);
    private final DateFormat timeLabel;
    private float downX, downY;
    private boolean held, moved, control, selectedOnDown;
    private final Runnable longPress = () -> { held = true; performLongClick(); };

    public TileView(MainActivity host, TileBoard board, Tile tile) {
        super(host); this.host = host; this.board = board; this.tile = tile;
        timeLabel = android.text.format.DateFormat.getTimeFormat(host);
        setFocusable(true); setClickable(true); setLongClickable(true);
        setCameraDistance(MetroUi.dp(host, 4000));
        setOnClickListener(v -> {
            if (host.editingBoard != null) {
                if (board.selected == tile) host.endEditing(); else board.select(tile);
            } else board.activate(tile);
        });
        setOnLongClickListener(v -> {
            board.select(tile); getParent().requestDisallowInterceptTouchEvent(true);
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS); return true;
        });
        updateDescription();
    }
    public void updateDescription() {
        setContentDescription(board.label(tile) + ", " + tile.size.name().toLowerCase(Locale.ROOT)
            + (tile.isFolder() ? ", folder, " + tile.children.size() + " apps" : "") + ". Hold to arrange.");
    }
    private float dp(float value) { return MetroUi.dp(host, value); }
    @Override protected void onDraw(Canvas canvas) {
        // The board allows dragged children outside its bounds; each tile still owns its paint area.
        canvas.save(); canvas.clipRect(0, 0, getWidth(), getHeight());
        boolean photo = host.wallpaper != null && !host.state.background.isEmpty() && tile.photo && tile.color == 0 && !host.state.phone8;
        if (photo) {
            Bitmap bitmap = host.wallpaper;
            float targetHeight = host.pager.getHeight() * 1.25f;
            float scale = Math.max(board.getWidth() / (float) bitmap.getWidth(), targetHeight / bitmap.getHeight());
            float progress = Math.min(1, Math.max(0, board.scrollOffset() / (float) Math.max(1, board.getHeight() - host.pager.getHeight())));
            float photoOffset = Math.max(0, bitmap.getHeight() * scale - host.pager.getHeight()) * progress;
            imageMatrix.reset(); imageMatrix.setScale(scale, scale);
            imageMatrix.postTranslate((board.getWidth() - bitmap.getWidth() * scale) / 2 - getLeft(),
                -getTop() + board.scrollOffset() - photoOffset);
            canvas.drawBitmap(bitmap, imageMatrix, paint);
        } else canvas.drawColor(tile.color == 0 ? host.state.accent() : tile.color);
        if (isPressed()) { paint.setColor(0x22000000); canvas.drawRect(0, 0, getWidth(), getHeight(), paint); }
        text.setTypeface(host.font()); text.setColor(Color.WHITE); text.setTextSize(dp(15));
        boolean small = tile.size == Tile.Size.SMALL;
        float inset = dp(small ? 8 : 12);
        String label = board.label(tile);
        AppCatalog.Entry app = host.catalog.get(tile.app);
        TileNotifications.Summary notification = TileNotifications.get(AppCatalog.packageOf(tile.app));
        boolean showLive = !small && tile.live && board.selected == null;
        if (tile.isFolder()) {
            int columns = small ? 2 : 3;
            float cell = Math.min(getWidth() - inset * 2, getHeight() - inset * 2 - (small ? 0 : dp(20))) / columns;
            int count = Math.min(tile.children.size(), columns * columns);
            float originX = (getWidth() - columns * cell) / 2;
            for (int i = 0; i < count; i++) drawIcon(canvas, tile.children.get(i).app,
                originX + (i % columns) * cell + cell * .13f, inset + (i / columns) * cell + cell * .13f, cell * .72f);
        } else if (showLive && ("@calendar".equals(tile.app) || app != null && "calendar".equals(app.glyph))) {
            now.setTime(System.currentTimeMillis());
            text.setTextSize(Math.min(dp(72), getHeight() * .49f));
            canvas.drawText(dayNumber.format(now), inset, getHeight() * .61f, text);
            text.setTextSize(dp(17));
            canvas.drawText(dayName.format(now), inset, getHeight() * .78f, text);
        } else if (showLive && "@clock".equals(tile.app)) {
            now.setTime(System.currentTimeMillis()); String time = timeLabel.format(now);
            text.setTextSize(Math.min(dp(tile.size == Tile.Size.WIDE ? 64 : 42), getWidth() / 3.1f));
            float available = getWidth() - inset * 2, measured = text.measureText(time);
            if (measured > available) text.setTextSize(text.getTextSize() * available / measured);
            canvas.drawText(time, inset, getHeight() * .53f, text);
            text.setTextSize(dp(16));
            canvas.drawText(dateLabel.format(now), inset, getHeight() * .75f, text);
        } else if (showLive && host.state.notificationPreview && notification != null && !notification.title.isEmpty()
                && tile.size == Tile.Size.WIDE) {
            drawIcon(canvas, tile.app, inset, inset, dp(30));
            text.setTextSize(dp(20));
            canvas.drawText(ellipsize(notification.title, getWidth() - inset * 2), inset, getHeight() * .49f, text);
            text.setTextSize(dp(16));
            canvas.drawText(ellipsize(notification.text, getWidth() - inset * 2), inset, getHeight() * .67f, text);
        } else {
            float iconSize = Math.min(getWidth(), getHeight()) * (small ? .57f : .48f);
            drawIcon(canvas, tile.app, (getWidth() - iconSize) / 2, (getHeight() - iconSize) / 2 - (small ? 0 : dp(5)), iconSize);
        }
        if (!small) {
            text.setTextSize(dp(15));
            canvas.drawText(ellipsize(label, getWidth() - inset * 2), inset, getHeight() - dp(10), text);
        }
        if (tile.live && notification != null && notification.count > 0) {
            text.setTextSize(dp(small ? 17 : 25));
            String count = notification.count > 99 ? "99+" : String.valueOf(notification.count);
            canvas.drawText(count, getWidth() - text.measureText(count) - dp(8), dp(small ? 23 : 32), text);
        }
        if (board.selected == tile) {
            drawControl(canvas, getWidth() - dp(17), dp(17), true);
            drawControl(canvas, getWidth() - dp(17), getHeight() - dp(17), false);
        }
        if (isFocused()) {
            paint.setColor(Color.WHITE); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(2));
            canvas.drawRect(dp(2), dp(2), getWidth() - dp(2), getHeight() - dp(2), paint); paint.setStyle(Paint.Style.FILL);
        }
        canvas.restore();
    }

    public void drawIcon(Canvas canvas, String key, float x, float y, float size) {
        AppCatalog.Entry entry = host.catalog.get(key);
        if (entry == null || !entry.glyph.isEmpty()) {
            MetroGlyph.draw(canvas, entry == null ? "" : entry.glyph, x, y, size, Color.WHITE); return;
        }
        Drawable icon = entry.icon;
        if (icon == null) return;
        icon = icon.getConstantState() == null ? icon.mutate() : icon.getConstantState().newDrawable().mutate();
        if (host.state.monochrome && icon instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable adaptive = (AdaptiveIconDrawable) icon;
            Drawable mono = Build.VERSION.SDK_INT >= 33 ? adaptive.getMonochrome() : null;
            icon = (mono != null ? mono : adaptive.getForeground()).mutate();
        }
        oldIconBounds.set(icon.getBounds()); icon.setBounds(Math.round(x), Math.round(y), Math.round(x + size), Math.round(y + size));
        if (host.state.monochrome) icon.setTint(Color.WHITE); else icon.setTintList(null);
        icon.draw(canvas); icon.setTintList(null); icon.clearColorFilter(); icon.setBounds(oldIconBounds);
    }
    private String ellipsize(String value, float width) { return TextUtils.ellipsize(value, text, Math.max(0, width), TextUtils.TruncateAt.END).toString(); }
    private void drawControl(Canvas canvas, float x, float y, boolean unpin) {
        paint.setColor(Color.BLACK); paint.setStyle(Paint.Style.FILL); canvas.drawCircle(x, y, dp(14), paint);
        paint.setColor(Color.WHITE); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1.5f));
        canvas.drawCircle(x, y, dp(13), paint);
        if (unpin) {
            canvas.drawLine(x - dp(5), y - dp(5), x + dp(5), y + dp(5), paint);
            canvas.drawLine(x - dp(5), y + dp(5), x + dp(5), y - dp(5), paint);
        } else {
            canvas.drawLine(x - dp(5), y + dp(5), x + dp(5), y - dp(5), paint);
            canvas.drawLine(x, y - dp(5), x + dp(5), y - dp(5), paint);
            canvas.drawLine(x + dp(5), y, x + dp(5), y - dp(5), paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getRawX(); downY = event.getRawY(); held = moved = false;
                selectedOnDown = board.selected == tile;
                control = board.selected == tile && event.getX() >= getWidth() - dp(42)
                    && (event.getY() < dp(42) || event.getY() > getHeight() - dp(42));
                setPressed(true);
                if (selectedOnDown && !control) { held = true; getParent().requestDisallowInterceptTouchEvent(true); }
                else if (!control) postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                if (host.state.animations && board.selected == null) animate().scaleX(.97f).scaleY(.97f).setDuration(90).start();
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - downX, dy = event.getRawY() - downY;
                if (held && !control) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                    if (Math.abs(dx) + Math.abs(dy) > dp(6)) {
                        if (!moved) board.beginDrag(this, downX, downY);
                        moved = true; board.drag(this, event.getRawX(), event.getRawY());
                    }
                } else if (Math.abs(dx) + Math.abs(dy) > ViewConfiguration.get(host).getScaledTouchSlop()) {
                    removeCallbacks(longPress); setPressed(false); moved = true; resetScale();
                }
                return true;
            case MotionEvent.ACTION_UP:
                removeCallbacks(longPress); setPressed(false); resetScale();
                getParent().requestDisallowInterceptTouchEvent(false);
                if (control && !moved) {
                    if (event.getY() < dp(42)) board.unpin(tile); else board.resize(tile);
                } else if (held && moved) board.drop(this, true);
                else if ((!held || selectedOnDown) && !moved) performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(longPress); setPressed(false); resetScale();
                getParent().requestDisallowInterceptTouchEvent(false);
                if (held && moved) board.drop(this, false);
                return true;
            default: return true;
        }
    }
    private void resetScale() { animate().scaleX(1).scaleY(1).rotationY(0).setDuration(host.state.animations ? 120 : 0).start(); }
    @Override public boolean performClick() { return super.performClick(); }
    @Override protected void onDetachedFromWindow() { removeCallbacks(longPress); super.onDetachedFromWindow(); }
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info); info.setClassName("android.widget.Button");
        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(RESIZE_ACTION, "Resize tile"));
        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(UNPIN_ACTION, "Unpin from Start"));
        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(CUSTOMIZE_ACTION, "Customize tile"));
    }
    @Override public boolean performAccessibilityAction(int action, Bundle args) {
        if (action == RESIZE_ACTION) { board.resize(tile); return true; }
        if (action == UNPIN_ACTION) { board.unpin(tile); return true; }
        if (action == CUSTOMIZE_ACTION) { host.customize(tile, board); return true; }
        return super.performAccessibilityAction(action, args);
    }
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (board.selected == tile) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) return board.moveBy(tile, -1, 0);
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) return board.moveBy(tile, 1, 0);
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return board.moveBy(tile, 0, -1);
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return board.moveBy(tile, 0, 1);
        }
        return super.onKeyDown(keyCode, event);
    }
}
