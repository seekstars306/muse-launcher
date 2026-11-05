package com.seekstars.muselauncher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 账号列表 JSON 编解码（纯 Java，可单元测试）。读取对损坏数据有容错。 */
final class AccountJsonCodec {

    private AccountJsonCodec() {
    }

    /** 序列化失败（理论上不会发生）返回 null，调用方应跳过本次写盘以保留旧数据 */
    static String toJson(List<Account> accounts) {
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
            return arr.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解析账号列表。
     *
     * @return 顶层不是合法 JSON 数组时返回 null（由调用方走备份恢复）；
     *         数组合法但某一条目字段损坏时跳过该条目，其余照常返回。
     */
    static List<Account> fromJsonOrNull(String raw) {
        if (raw == null) return null;
        JSONArray arr;
        try {
            arr = new JSONArray(raw);
        } catch (Exception e) {
            return null;
        }
        List<Account> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            try {
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
                out.add(a);
            } catch (Exception ignoredEntry) {
                // 单条损坏不影响其余条目
            }
        }
        return out;
    }
}
