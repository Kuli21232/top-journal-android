package app.topjournal.client;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.Iterator;

/** Merge only fields backed by a successful API response. Missing is different from empty. */
final class NativeSnapshot {
    static final long MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000;

    static JSONObject merge(JSONObject previous, JSONObject incoming) throws JSONException {
        JSONObject result = previous == null ? new JSONObject() : new JSONObject(previous.toString());
        JSONObject times = result.optJSONObject("_sources");
        if (times == null) times = new JSONObject();
        JSONObject updates = incoming.optJSONObject("_sources");
        if (updates != null) {
            Iterator<String> keys = updates.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                long time = updates.optLong(key);
                if (!key.startsWith("_") && incoming.has(key) && time > 0 && time >= times.optLong(key)) {
                    result.put(key, incoming.get(key));
                    times.put(key, time);
                }
            }
        }
        result.put("_sources", times);
        return result;
    }

    static JSONObject restore(String json, long now) throws JSONException {
        JSONObject value = new JSONObject(json);
        JSONObject times = value.optJSONObject("_sources");
        if (times == null) return null;
        JSONObject fresh = new JSONObject();
        JSONObject freshTimes = new JSONObject();
        Iterator<String> keys = times.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            long time = times.optLong(key);
            if (!key.startsWith("_") && value.has(key) && time > 0 && time <= now + 60000 && now - time <= MAX_AGE_MS) {
                fresh.put(key, value.get(key));
                freshTimes.put(key, time);
            }
        }
        if (freshTimes.length() == 0) return null;
        fresh.put("_sources", freshTimes);
        return fresh;
    }
}
