package app.topjournal.client;

import android.app.job.JobScheduler;
import android.content.Context;

final class BackgroundSyncScheduler {
    private static final int JOB_ID = 7101;

    private BackgroundSyncScheduler() {}

    static void cancel(Context context) {
        try {
            JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (scheduler != null) scheduler.cancel(JOB_ID);
        } catch (RuntimeException ignored) { }
    }
}
