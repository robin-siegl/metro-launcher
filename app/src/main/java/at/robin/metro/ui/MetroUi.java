package at.robin.metro.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import at.robin.metro.MainActivity;

public final class MetroUi {
    private static Typeface defaultTypeface;
    private MetroUi() { }
    public static int dp(Context context, float value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    public static TextView text(MainActivity host, String value, float size) {
        TextView view = new TextView(host);
        view.setText(value); view.setTextSize(size); view.setTextColor(host.state.foreground());
        view.setFontFeatureSettings("kern"); view.setTypeface(host.font());
        view.setIncludeFontPadding(false);
        return view;
    }
    public static TextView button(MainActivity host, String label, Runnable action) {
        TextView view = text(host, label, 19);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(dp(host, 14), dp(host, 12), dp(host, 14), dp(host, 12));
        view.setMinHeight(dp(host, 48));
        view.setBackground(border(host.state.foreground(), host.state.surface(), dp(host, 2)));
        view.setFocusable(true); view.setClickable(true);
        view.setOnClickListener(v -> action.run());
        return view;
    }
    public static GradientDrawable border(int stroke, int fill, int width) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill); drawable.setStroke(width, stroke); return drawable;
    }
    public static LinearLayout vertical(Context context) {
        LinearLayout view = new LinearLayout(context); view.setOrientation(LinearLayout.VERTICAL); return view;
    }
    public static void space(LinearLayout parent, int height) {
        View spacer = new View(parent.getContext()); parent.addView(spacer, new LinearLayout.LayoutParams(1, height));
    }
    public static Typeface defaultFont(Context context) {
        if (defaultTypeface == null) defaultTypeface = Typeface.createFromAsset(context.getAssets(), "fonts/selawkl.ttf");
        return defaultTypeface;
    }
}
