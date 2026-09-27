package app.topjournal.client;

import org.junit.Test;
import static org.junit.Assert.*;

public class SessionRecoveryTest {
    @Test public void expirationAllowsTheSiteToRefreshBeforeLogin() {
        assertFalse(SessionRecovery.shouldOpenLogin("expired", false, true, false, false, 5999));
        assertTrue(SessionRecovery.shouldOpenLogin("expired", false, true, false, false, 6001));
        assertFalse(SessionRecovery.shouldOpenLogin("verified", true, true, false, false, 30000));
    }
    @Test public void blockedOrOfflineRequestsNeverImplyLogout() {
        for (String state : new String[]{"forbidden", "network_or_cors", "timeout", "invalid_profile", "login"}) {
            assertFalse(SessionRecovery.shouldOpenLogin(state, false, false, false, false, 90000));
        }
        assertFalse(SessionRecovery.shouldOpenLogin("pending", false, false, true, false, 90000));
    }
    @Test public void missingCredentialHasADifferentDeadlineFromAnUnverifiedCredential() {
        assertFalse(SessionRecovery.shouldOpenLogin("pending", false, false, false, false, 24000));
        assertTrue(SessionRecovery.shouldOpenLogin("pending", false, false, false, false, 26000));
        assertFalse(SessionRecovery.shouldOpenLogin("pending", false, true, false, false, 90000));
    }
    @Test public void repeatedObserversCannotCauseARedirectLoop() {
        assertFalse(SessionRecovery.shouldOpenLogin("expired", false, true, false, true, 90000));
        assertFalse(SessionRecovery.shouldOpenLogin("pending", false, false, false, true, 90000));
    }
}
