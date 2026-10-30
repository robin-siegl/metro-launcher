package at.robin.metro.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import at.robin.metro.MainActivity;

// Constructed with its photo in Java; never inflated from XML.
@android.annotation.SuppressLint("ViewConstructor")
public final class WallpaperCrop extends LinearLayout {
    public WallpaperCrop(MainActivity host, Bitmap bitmap) {
        super(host); setOrientation(VERTICAL); setBackgroundColor(host.state.surface());
        int padding = MetroUi.dp(host, 20); setPadding(padding, padding, padding, padding);
        TextView title = MetroUi.text(host, "choose your crop", 32); addView(title);
        TextView help = MetroUi.text(host, "Drag to position. Pinch to zoom.", 16); addView(help);
        CropView crop = new CropView(host, bitmap); addView(crop, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));
        LinearLayout actions = new LinearLayout(host); actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.addView(MetroUi.button(host, "cancel", host::showSettings), new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        actions.addView(MetroUi.button(host, "✓  done", () -> {
            try { host.applyWallpaper(crop.result()); }
            catch (OutOfMemoryError error) { host.toast("This photo is too large to crop"); }
        }), new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1)); addView(actions);
    }
    private static final class CropView extends View {
        private final Bitmap bitmap;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final RectF frame = new RectF();
        private final Matrix matrix = new Matrix();
        private final ScaleGestureDetector detector;
        private float scale, minimum, tx, ty, lastX, lastY;
        private boolean reset = true;
        CropView(MainActivity host, Bitmap bitmap) {
            super(host); this.bitmap = bitmap;
            detector = new ScaleGestureDetector(host, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override public boolean onScale(ScaleGestureDetector gesture) {
                    float next = Math.max(minimum, Math.min(minimum * 6, scale * gesture.getScaleFactor()));
                    float ratio = next / scale;
                    tx = gesture.getFocusX() - (gesture.getFocusX() - tx) * ratio;
                    ty = gesture.getFocusY() - (gesture.getFocusY() - ty) * ratio;
                    scale = next; clamp(); invalidate(); return true;
                }
            });
            setContentDescription("Crop photo. Drag with one finger and pinch with two to zoom.");
        }
        @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            float w = Math.min(width, (height - 32) * 9f / 16f), h = w * 16f / 9f;
            frame.set((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2); reset = true;
        }
        @Override protected void onDraw(Canvas canvas) {
            if (frame.width() <= 0) return;
            if (reset) {
                minimum = Math.max(frame.width() / bitmap.getWidth(), frame.height() / bitmap.getHeight());
                scale = minimum; tx = frame.centerX() - bitmap.getWidth() * scale / 2;
                ty = frame.centerY() - bitmap.getHeight() * scale / 2; reset = false;
            }
            canvas.save(); canvas.clipRect(frame); matrix.reset(); matrix.setScale(scale, scale); matrix.postTranslate(tx, ty);
            canvas.drawBitmap(bitmap, matrix, paint); canvas.restore();
            paint.setColor(0xffaaaaaa); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2);
            canvas.drawRect(frame, paint); paint.setStyle(Paint.Style.FILL);
        }
        private void clamp() {
            tx = Math.max(frame.right - bitmap.getWidth() * scale, Math.min(tx, frame.left));
            ty = Math.max(frame.bottom - bitmap.getHeight() * scale, Math.min(ty, frame.top));
        }
        @Override public boolean onTouchEvent(MotionEvent event) {
            detector.onTouchEvent(event);
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE && !detector.isInProgress()) {
                tx += event.getX() - lastX; ty += event.getY() - lastY; clamp(); invalidate();
            }
            lastX = event.getX(); lastY = event.getY();
            if (event.getActionMasked() == MotionEvent.ACTION_UP) performClick(); return true;
        }
        @Override public boolean performClick() { super.performClick(); return true; }
        Bitmap result() {
            Bitmap result = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888); Canvas canvas = new Canvas(result);
            float output = 1080f / frame.width(); Matrix matrix = new Matrix(); matrix.setScale(scale * output, scale * output);
            matrix.postTranslate((tx - frame.left) * output, (ty - frame.top) * output); canvas.drawBitmap(bitmap, matrix, paint); return result;
        }
    }
}
