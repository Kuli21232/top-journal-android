package app.topjournal.client;

/** Navigation policy is deliberately independent from HTTP retries and UI rendering. */
final class SessionRecovery {
    static boolean shouldOpenLogin(String state, boolean verified, boolean authObserved,
                                   boolean unavailable, boolean alreadyRedirected, long elapsedMs) {
        if (verified || unavailable || alreadyRedirected) return false;
        return ("expired".equals(state) && elapsedMs > 6000)
                || ("pending".equals(state) && !authObserved && elapsedMs > 25000);
    }
}
