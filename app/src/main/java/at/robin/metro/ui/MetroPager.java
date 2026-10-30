package at.robin.metro.ui;

import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.Scroller;

/** Two physical pages, with axis locking so a vertical tile scroll never changes page. */
public final class MetroPager extends ViewGroup {
    public interface Listener { void onPageChanged(int page); }
    private final Scroller scroller;
    private final int slop;
    private float downX, downY, lastX;
    private boolean dragging, vertical;
    private VelocityTracker velocity;
    public boolean locked, animations = true;
    private int page;
    public Listener listener;

    public MetroPager(Context context) {
        super(context); scroller = new Scroller(context); slop = ViewConfiguration.get(context).getScaledTouchSlop();
        setClipChildren(true);
    }
    public int page() { return page; }
    public void setPage(int target, boolean animate) {
        page = Math.max(0, Math.min(target, 1));
        if (animate && animations && getWidth() > 0) {
            scroller.startScroll(getScrollX(), 0, page * getWidth() - getScrollX(), 0, 280); postInvalidateOnAnimation();
        } else { scroller.abortAnimation(); scrollTo(page * getWidth(), 0); }
        if (listener != null) listener.onPageChanged(page);
    }
    @Override protected void onMeasure(int width, int height) {
        int w = MeasureSpec.getSize(width), h = MeasureSpec.getSize(height);
        setMeasuredDimension(w, h);
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).measure(
            MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
    }
    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left;
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).layout(i * width, 0, (i + 1) * width, bottom - top);
        if (changed && !dragging) { scroller.abortAnimation(); scrollTo(page * width, 0); }
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        if (locked) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = event.getX(); downY = event.getY(); dragging = vertical = false;
                if (!scroller.isFinished()) { scroller.abortAnimation(); scrollTo(page * getWidth(), 0); }
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - downX, dy = event.getY() - downY;
                if (vertical) return false;
                if (Math.abs(dy) > slop && Math.abs(dy) > Math.abs(dx)) { vertical = true; return false; }
                if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.25f) {
                    dragging = true; lastX = event.getX(); getParent().requestDisallowInterceptTouchEvent(true); return true;
                }
                break;
            default: break;
        }
        return dragging;
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (locked || getWidth() == 0) return false;
        if (velocity == null) velocity = VelocityTracker.obtain();
        velocity.addMovement(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = event.getX(); downY = event.getY(); return true;
            case MotionEvent.ACTION_MOVE:
                int x = Math.max(0, Math.min(getWidth(), getScrollX() + Math.round(lastX - event.getX())));
                scrollTo(x, 0); lastX = event.getX(); return true;
            case MotionEvent.ACTION_UP:
                if (Math.abs(event.getX() - downX) < slop) performClick();
                velocity.computeCurrentVelocity(1000);
                float speed = velocity.getXVelocity();
                int target = Math.abs(speed) > MetroUi.dp(getContext(), 600) ? (speed < 0 ? 1 : 0)
                    : (getScrollX() > getWidth() / 2 ? 1 : 0);
                finish(target); return true;
            case MotionEvent.ACTION_CANCEL: finish(page); return true;
            default: return true;
        }
    }
    private void finish(int target) {
        dragging = false;
        if (velocity != null) { velocity.recycle(); velocity = null; }
        setPage(target, true);
    }
    @Override public boolean performClick() { return super.performClick(); }
    @Override public void computeScroll() {
        if (scroller.computeScrollOffset()) { scrollTo(scroller.getCurrX(), 0); postInvalidateOnAnimation(); }
    }
}
