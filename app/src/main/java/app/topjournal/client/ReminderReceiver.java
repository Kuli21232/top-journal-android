package app.topjournal.client;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import org.json.JSONException;
import org.json.JSONObject;

public final class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent receivedIntent) {
        if (!context.getSharedPreferences("top_journal_preferences", Context.MODE_PRIVATE)
                .getBoolean("daily_reminder", true)) return;
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;

        JSONObject tasks;
        JSONObject home;
        try {
            android.content.SharedPreferences cache = context.getSharedPreferences("native_screens_v12", Context.MODE_PRIVATE);
            tasks = NativeSnapshot.restore(cache.getString("tasks", "{}"), System.currentTimeMillis());
            home = NativeSnapshot.restore(cache.getString("home", "{}"), System.currentTimeMillis());
        } catch (JSONException ignored) {
            return;
        }
        int unfinished = home != null && home.optJSONObject("tasks") != null
                ? JournalNotifications.unfinishedHomeworkCount(home)
                : JournalNotifications.unfinishedHomeworkCount(tasks);
        if (unfinished <= 0) return;

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(
                MainActivity.CHANNEL_REMINDERS,
                "Напоминания",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        manager.createNotificationChannel(channel);

        Intent launch = new Intent(context, MainActivity.class).putExtra("open_route", "tasks");
        launch.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                7002,
                launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new Notification.Builder(context, MainActivity.CHANNEL_REMINDERS)
                .setSmallIcon(R.drawable.ic_tasks)
                .setContentTitle("Не выполнено заданий: " + unfinished)
                .setContentText(JournalNotifications.unfinishedHomeworkSummary(tasks))
                .setStyle(new Notification.BigTextStyle().bigText(JournalNotifications.unfinishedHomeworkSummary(tasks)))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build();
        manager.notify(42, notification);
    }
}
