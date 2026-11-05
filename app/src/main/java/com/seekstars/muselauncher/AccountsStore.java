package com.seekstars.muselauncher;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 账号元数据持久化（SharedPreferences + JSON）。各账号的登录数据由 WebView Profile 负责持久化。 */
public class AccountsStore {
    private static final String PREFS = "accounts";
    private static final String KEY_FULLSCREEN = "fullscreen";

    private final SharedPreferences prefs;
    private final List<Account> accounts = new ArrayList<>();
    private long nextId = 1;
    private long currentId = -1;

    public AccountsStore(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        load();
    }

    /** 重新从磁盘加载。其他页面（如账号配置）可能已修改存储，写回前必须刷新，否则会用旧内存覆盖掉新数据。 */
    public void reload() {
        load();
    }

    private void load() {
        accounts.clear();
        try {
            JSONArray arr = new JSONArray(prefs.getString("list", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Account a = new Account(o.getLong("id"), o.getString("name"));
                a.avatar = o.optString("avatar", "m");
                a.proxy = o.optString("proxy", "");
                JSONArray tagArr = o.optJSONArray("tags");
                if (tagArr != null) {
                    for (int j = 0; j < tagArr.length(); j++) {
                        String t = tagArr.optString(j);
                        if (!t.isEmpty()) a.tags.add(t);
                    }
                }
                accounts.add(a);
            }
            nextId = prefs.getLong("nextId", 1);
            currentId = prefs.getLong("currentId", -1);
            if (accounts.isEmpty()) {
                currentId = -1;
            } else if (findById(currentId) == null) {
                currentId = accounts.get(0).id;
            }
        } catch (Exception ignored) {
        }
    }

    public void save() {
        try {
            JSONArray arr = new JSONArray();
            for (Account a : accounts) {
                JSONObject o = new JSONObject();
                o.put("id", a.id);
                o.put("name", a.name);
                o.put("avatar", a.avatar);
                o.put("proxy", a.proxy);
                JSONArray tagArr = new JSONArray();
                for (String t : a.tags) tagArr.put(t);
                o.put("tags", tagArr);
                arr.put(o);
            }
            prefs.edit()
                    .putString("list", arr.toString())
                    .putLong("nextId", nextId)
                    .putLong("currentId", currentId)
                    .apply();
        } catch (Exception ignored) {
        }
    }

    public List<Account> all() {
        return accounts;
    }

    public Account findById(long id) {
        for (Account a : accounts) {
            if (a.id == id) return a;
        }
        return null;
    }

    public long getCurrentId() {
        return currentId;
    }

    /** 新建账号，默认名 "Muse N"（N 与账号 id 一致）。 */
    public Account addDefault() {
        Account a = new Account(nextId, "Muse " + nextId);
        nextId++;
        accounts.add(a);
        currentId = a.id;
        save();
        return a;
    }

    public void rename(long id, String name) {
        Account a = findById(id);
        if (a != null) {
            a.name = name;
            save();
        }
    }

    public void remove(long id) {
        Account a = findById(id);
        if (a != null) accounts.remove(a);
        if (currentId == id) {
            currentId = accounts.isEmpty() ? -1 : accounts.get(0).id;
        }
        save();
    }

    public void setCurrent(long id) {
        currentId = id;
        save();
    }

    public boolean isFullscreen() {
        return prefs.getBoolean(KEY_FULLSCREEN, false);
    }

    public void setFullscreen(boolean v) {
        prefs.edit().putBoolean(KEY_FULLSCREEN, v).apply();
    }
}
