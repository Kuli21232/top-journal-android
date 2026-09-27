package app.topjournal.client;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class NativeSnapshotTest {
    @Test public void partialRefreshPreservesPreviouslyLoadedFields() throws Exception {
        JSONObject old = new JSONObject("{\"score\":10,\"tasks\":{\"total\":7},\"_sources\":{\"score\":10,\"tasks\":10}}");
        JSONObject updated = NativeSnapshot.merge(old, new JSONObject("{\"score\":11,\"_sources\":{\"score\":20}}"));
        assertEquals(11, updated.getInt("score"));
        assertEquals(7, updated.getJSONObject("tasks").getInt("total"));
        assertEquals(10, old.getInt("score"));
    }
    @Test public void emptySuccessfulListReplacesOldItems() throws Exception {
        JSONObject old = new JSONObject("{\"items\":[1],\"_sources\":{\"items\":10}}");
        JSONObject updated = NativeSnapshot.merge(old, new JSONObject("{\"items\":[],\"_sources\":{\"items\":20}}"));
        assertEquals(0, updated.getJSONArray("items").length());
    }
    @Test public void lateResponseCannotOverwriteNewerData() throws Exception {
        JSONObject old = new JSONObject("{\"score\":11,\"_sources\":{\"score\":20}}");
        JSONObject updated = NativeSnapshot.merge(old, new JSONObject("{\"score\":9,\"_sources\":{\"score\":10}}"));
        assertEquals(11, updated.getInt("score"));
    }
    @Test public void failedAndUnbackedFieldsDoNotEraseCache() throws Exception {
        JSONObject old = new JSONObject("{\"items\":[1],\"_sources\":{\"items\":10}}");
        JSONObject updated = NativeSnapshot.merge(old, new JSONObject("{\"items\":[],\"_sources\":{}}"));
        assertEquals(1, updated.getJSONArray("items").length());
    }
    @Test public void restartRestoresOnlyUnexpiredFields() throws Exception {
        long now = NativeSnapshot.MAX_AGE_MS + 100;
        JSONObject value = new JSONObject("{\"score\":10,\"items\":[],\"_sources\":{\"score\":50,\"items\":100}}");
        JSONObject restored = NativeSnapshot.restore(value.toString(), now);
        assertNotNull(restored);
        assertFalse(restored.has("score"));
        assertEquals(0, restored.getJSONArray("items").length());
    }
    @Test public void unknownAndEntirelyExpiredSnapshotsAreNotRestored() throws Exception {
        assertNull(NativeSnapshot.restore("{}", 100));
        assertNull(NativeSnapshot.restore("{\"score\":10,\"_sources\":{\"score\":1}}", NativeSnapshot.MAX_AGE_MS + 2));
    }
}
