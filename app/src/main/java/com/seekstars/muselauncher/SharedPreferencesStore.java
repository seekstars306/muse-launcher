package com.seekstars.muselauncher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Map;

/** KeyValueStore 的 SharedPreferences 实现 */
public class SharedPreferencesStore implements KeyValueStore {

    private final SharedPreferences prefs;

    public SharedPreferencesStore(Context ctx, String name) {
        prefs = ctx.getSharedPreferences(name, Context.MODE_PRIVATE);
    }

    @Override
    public String getString(String key, String defValue) {
        return prefs.getString(key, defValue);
    }

    @Override
    public long getLong(String key, long defValue) {
        return prefs.getLong(key, defValue);
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        return prefs.getBoolean(key, defValue);
    }

    @Override
    public void putAll(Map<String, Object> values) {
        SharedPreferences.Editor e = prefs.edit();
        for (Map.Entry<String, Object> en : values.entrySet()) {
            Object v = en.getValue();
            if (v instanceof Long) e.putLong(en.getKey(), (Long) v);
            else if (v instanceof Boolean) e.putBoolean(en.getKey(), (Boolean) v);
            else if (v instanceof String) e.putString(en.getKey(), (String) v);
        }
        e.apply();
    }

    @Override
    public void putString(String key, String value) {
        prefs.edit().putString(key, value).apply();
    }

    @Override
    public void putLong(String key, long value) {
        prefs.edit().putLong(key, value).apply();
    }

    @Override
    public void putBoolean(String key, boolean value) {
        prefs.edit().putBoolean(key, value).apply();
    }
}
