package com.seekstars.muselauncher;

import java.util.Map;

/** 键值存储抽象：让 AccountsStore 的逻辑可以用 HashMap 假实现做单元测试 */
public interface KeyValueStore {

    String getString(String key, String defValue);

    long getLong(String key, long defValue);

    boolean getBoolean(String key, boolean defValue);

    /** 原子批量写入（同一 editor/事务），value 支持 String / Long / Boolean */
    void putAll(Map<String, Object> values);

    void putString(String key, String value);

    void putLong(String key, long value);

    void putBoolean(String key, boolean value);
}
