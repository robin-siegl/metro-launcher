package at.robin.metro.data;

import android.app.Notification;
import android.content.Intent;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** In-memory only. Notification access is optional and enabled through Android settings. */
public final class TileNotifications extends NotificationListenerService {
    public static final String CHANGED = "at.robin.metro.TILES_CHANGED";
    public static final class Summary {
        public int count;
        public String title = "", text = "";
        long time;
    }
    private static volatile Map<String, Summary> summaries = Collections.emptyMap();
    public static Summary get(String packageName) { return summaries.get(packageName); }

    @Override public void onListenerConnected() { rebuild(); }
    @Override public void onNotificationPosted(StatusBarNotification notification) { rebuild(); }
    @Override public void onNotificationRemoved(StatusBarNotification notification) { rebuild(); }
    @Override public void onListenerDisconnected() { summaries = Collections.emptyMap(); changed(); }

    private void rebuild() {
        Map<String, Summary> next = new HashMap<>();
        try {
            StatusBarNotification[] active = getActiveNotifications();
            if (active != null) for (StatusBarNotification item : active) {
                Notification notification = item.getNotification();
                if ((notification.flags & (Notification.FLAG_GROUP_SUMMARY | Notification.FLAG_ONGOING_EVENT)) != 0) continue;
                if (item.getPackageName().equals(getPackageName())) continue;
                Summary summary = next.computeIfAbsent(item.getPackageName(), key -> new Summary());
                summary.count++;
                if (item.getPostTime() >= summary.time && notification.visibility != Notification.VISIBILITY_SECRET) {
                    summary.time = item.getPostTime();
                    summary.title = String.valueOf(notification.extras.getCharSequence(Notification.EXTRA_TITLE, ""));
                    summary.text = String.valueOf(notification.extras.getCharSequence(Notification.EXTRA_TEXT, ""));
                }
            }
        } catch (SecurityException ignored) { next.clear(); }
        summaries = Collections.unmodifiableMap(next); changed();
    }
    private void changed() { sendBroadcast(new Intent(CHANGED).setPackage(getPackageName())); }
}
