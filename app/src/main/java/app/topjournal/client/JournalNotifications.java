package app.topjournal.client;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class JournalNotifications {
    static final String CHANNEL_GRADES = "journal_grades";
    private static final String PREFS = "background_sync_state";
    private static final int GRADE_NOTIFICATION_ID = 43;

    private JournalNotifications() {}

    static void updateGrades(Context context, JSONObject snapshot, boolean notifyChanges) {
        JSONArray items = snapshot == null ? null : snapshot.optJSONArray("items");
        if (items == null) return;
        LinkedHashSet<String> current = new LinkedHashSet<>();
        List<JSONObject> newItems = new ArrayList<>();
        SharedPreferences state = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> known = new HashSet<>(state.getStringSet("known_grades", new HashSet<>()));
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            JSONArray marks = item == null ? null : item.optJSONArray("marks");
            if (item == null || marks == null || marks.length() == 0) continue;
            String signature = digest(item.optString("date") + "|" + item.optString("title") + "|"
                    + item.optString("theme") + "|" + marks);
            current.add(signature);
            if (!known.isEmpty() && !known.contains(signature)) newItems.add(item);
        }
        known.addAll(current);
        if (known.size() > 500) known = new HashSet<>(current);
        state.edit().putStringSet("known_grades", known).apply();
        if (notifyChanges && !newItems.isEmpty()
                && context.getSharedPreferences("top_journal_preferences", Context.MODE_PRIVATE)
                .getBoolean("new_grade_notifications", true)) {
            postGrades(context, newItems);
        }
    }

    static int unfinishedHomeworkCount(JSONObject snapshot) {
        if (snapshot == null) return 0;
        JSONObject counters = snapshot.optJSONObject("tasks");
        if (counters != null) return number(counters.optString("open")) + number(counters.optString("expired"));
        int count = 0;
        for (String key : new String[]{"tasks_3_1", "tasks_0_1"}) {
            JSONArray items = snapshot.optJSONArray(key);
            if (items != null) count += items.length();
        }
        return count;
    }

    private static int number(String value) {
        try { return Math.max(0, Integer.parseInt(value)); }
        catch (NumberFormatException ignored) { return 0; }
    }

    static String unfinishedHomeworkSummary(JSONObject snapshot) {
        if (snapshot == null) return "Откройте дневник, чтобы посмотреть задания";
        LinkedHashSet<String> subjects = new LinkedHashSet<>();
        for (String key : new String[]{"tasks_3_1", "tasks_0_1"}) {
            JSONArray items = snapshot.optJSONArray(key);
            if (items == null) continue;
            for (int i = 0; i < items.length() && subjects.size() < 3; i++) {
                JSONObject item = items.optJSONObject(i);
                if (item != null && !item.optString("title").isEmpty()) subjects.add(item.optString("title"));
            }
        }
        return subjects.isEmpty() ? "Откройте дневник, чтобы посмотреть задания" : TextUtils.join(" · ", subjects);
    }

    private static void postGrades(Context context, List<JSONObject> grades) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(CHANNEL_GRADES, "Новые оценки", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Уведомления о новых оценках в дневнике");
        manager.createNotificationChannel(channel);

        JSONObject first = grades.get(0);
        JSONArray marks = first.optJSONArray("marks");
        String title = grades.size() == 1 ? "Новая оценка" : "Новые оценки: " + grades.size();
        String text = first.optString("title", "Предмет") + (marks == null ? "" : ": " + marksLabel(marks));
        if (grades.size() > 1) text += " и ещё " + (grades.size() - 1);
        Intent launch = new Intent(context, MainActivity.class).putExtra("open_route", "grades")
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(context, 7103, launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(context, CHANNEL_GRADES)
                .setSmallIcon(R.drawable.ic_grades)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build();
        manager.notify(GRADE_NOTIFICATION_ID, notification);
    }

    private static String marksLabel(JSONArray marks) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < marks.length(); i++) {
            double value = marks.optDouble(i, Double.NaN);
            if (Double.isNaN(value)) continue;
            values.add(Math.abs(value - Math.rint(value)) < .01
                    ? String.valueOf(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value));
        }
        return TextUtils.join(", ", values);
    }

    private static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder output = new StringBuilder();
            for (int i = 0; i < 12; i++) output.append(String.format(Locale.ROOT, "%02x", bytes[i]));
            return output.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
