package at.robin.metro;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.SystemClock;
import android.view.MotionEvent;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import at.robin.metro.data.LauncherState;
import at.robin.metro.model.Tile;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class LauncherInstrumentedTest {
    @Test public void applicationIsAnActualHomeCandidate() {
        Context context = ApplicationProvider.getApplicationContext();
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(context.getPackageName());
        List<ResolveInfo> candidates = context.getPackageManager().queryIntentActivities(home, 0);
        assertTrue(candidates.stream().anyMatch(info -> info.activityInfo.name.equals(MainActivity.class.getName())));
    }

    @Test public void backupRoundTripPreservesFoldersPositionsAndCustomization() throws Exception {
        LauncherState state = new LauncherState(); state.moreTiles = true; state.light = true; state.accentIndex = 12;
        state.background = "background.jpg"; state.notificationPreview = true;
        Tile folder = new Tile("folder-id", "@folder"); folder.label = "Österreich"; folder.row = 8; folder.column = 2;
        Tile child = new Tile("child-id", "com.example/.Main"); child.size = Tile.Size.WIDE; child.color = 0xff825a2c;
        child.label = "Meine App"; child.photo = false; child.live = false; folder.children.add(child); state.tiles.add(folder);
        LauncherState decoded = LauncherState.parse(state.json());
        assertEquals(6, decoded.columns()); assertTrue(decoded.light); assertTrue(decoded.notificationPreview);
        assertEquals(12, decoded.accentIndex); assertEquals("background.jpg", decoded.background);
        assertEquals(8, decoded.tiles.get(0).row); assertEquals(2, decoded.tiles.get(0).column);
        assertEquals("Österreich", decoded.tiles.get(0).label);
        Tile restored = decoded.tiles.get(0).children.get(0);
        assertEquals("child-id", restored.id); assertEquals(Tile.Size.WIDE, restored.size);
        assertEquals(0xff825a2c, restored.color); assertFalse(restored.photo); assertFalse(restored.live);
    }

    @Test public void invalidAndDuplicateBackupsAreRejectedBeforeReplacingState() throws Exception {
        LauncherState state = new LauncherState(); Tile tile = new Tile("x", "com.example/.Main"); state.tiles.add(tile);
        JSONObject value = new JSONObject(state.json()); JSONArray array = value.getJSONArray("tiles");
        JSONObject duplicate = new JSONObject(array.getJSONObject(0).toString()); duplicate.put("id", "different-id"); array.put(duplicate);
        reject(value.toString());
        value = new JSONObject(state.json()); value.put("version", 99); reject(value.toString());
        value = new JSONObject(state.json()); value.getJSONArray("tiles").getJSONObject(0).put("size", "ENORMOUS"); reject(value.toString());
        value = new JSONObject(state.json()); value.put("background", "../../private");
        assertEquals("", LauncherState.parse(value.toString()).background);
    }

    @Test public void realHorizontalSwipesSwitchBetweenStartAndApps() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            awaitReady(scenario); AtomicInteger width = new AtomicInteger(), y = new AtomicInteger();
            scenario.onActivity(activity -> {
                activity.closePanel(); activity.endEditing(); activity.pager.setPage(0, false);
                int[] position = new int[2]; activity.pager.getLocationOnScreen(position);
                // Use the page's top gutter so a slow software emulator cannot turn the gesture into a tile long-press.
                width.set(activity.pager.getWidth()); y.set(position[1] + Math.round(8 * activity.getResources().getDisplayMetrics().density));
            });
            swipe(width.get() * .85f, width.get() * .1f, y.get());
            awaitPage(scenario, 1);
            swipe(width.get() * .1f, width.get() * .85f, y.get());
            awaitPage(scenario, 0);
        }
    }
    private static void awaitReady(ActivityScenario<MainActivity> scenario) throws InterruptedException {
        AtomicBoolean ready = new AtomicBoolean(); long deadline = SystemClock.uptimeMillis() + 15_000;
        while (!ready.get() && SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity(activity -> ready.set(activity.board != null && activity.hasWindowFocus())); Thread.sleep(30);
        }
        assertTrue("Launcher did not finish loading or gain window focus", ready.get());
    }
    private static void swipe(float from, float to, int y) {
        var instrumentation = InstrumentationRegistry.getInstrumentation(); long down = SystemClock.uptimeMillis();
        for (int i = 0; i <= 10; i++) {
            int action = i == 0 ? MotionEvent.ACTION_DOWN : i == 10 ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE;
            MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, from + (to - from) * i / 10, y, 0);
            event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
            assertTrue("Pointer injection failed", instrumentation.getUiAutomation().injectInputEvent(event, false));
            event.recycle(); SystemClock.sleep(20);
        }
        instrumentation.waitForIdleSync(); SystemClock.sleep(350);
    }
    private static void awaitPage(ActivityScenario<MainActivity> scenario, int expected) throws InterruptedException {
        AtomicBoolean reached = new AtomicBoolean(); long deadline = SystemClock.uptimeMillis() + 5_000;
        while (!reached.get() && SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity(activity -> reached.set(activity.pager.page() == expected)); Thread.sleep(30);
        }
        scenario.onActivity(activity -> assertEquals("Page swipe failed; locked=" + activity.pager.locked
            + ", scroll=" + activity.pager.getScrollX(), expected, activity.pager.page()));
    }
    private static void reject(String json) throws Exception {
        try { LauncherState.parse(json); fail("Invalid backup was accepted"); } catch (JSONException expected) { }
    }
}
