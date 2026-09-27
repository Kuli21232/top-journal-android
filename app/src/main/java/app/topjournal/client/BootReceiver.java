package app.topjournal.client;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            if (context.getSharedPreferences("top_journal_preferences", Context.MODE_PRIVATE)
                    .getBoolean("daily_reminder", true)) ReminderScheduler.schedule(context);
        }
    }
}
