package app.topjournal.client;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.util.Calendar;

final class ReminderScheduler {
    private static final int REQUEST_CODE = 7001;

    private ReminderScheduler() {}

    static void schedule(Context context) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, 19);
        next.set(Calendar.MINUTE, 0);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (next.getTimeInMillis() <= System.currentTimeMillis()) {
            next.add(Calendar.DAY_OF_YEAR, 1);
        }
        manager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                next.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY,
                reminderIntent(context)
        );
    }

    static void cancel(Context context) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        manager.cancel(reminderIntent(context));
    }

    private static PendingIntent reminderIntent(Context context) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
}
