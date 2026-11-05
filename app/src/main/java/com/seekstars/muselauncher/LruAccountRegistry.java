package com.seekstars.muselauncher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按账号 id 保存重资源的注册表（WebView 等），带 LRU 容量上限：
 * touchAndEvict 把指定 id 标记为最近使用，超过容量时从最旧开始驱逐。
 * 纯 Java 实现，可单元测试；驱逐决策与资源销毁（Android 侧）解耦。
 */
public class LruAccountRegistry<T> {

    private final int maxAlive;
    /** accessOrder=true：get/put 都会刷新访问顺序， eldest 是最久未用 */
    private final LinkedHashMap<Long, T> map = new LinkedHashMap<>(16, 0.75f, true);

    public LruAccountRegistry(int maxAlive) {
        this.maxAlive = Math.max(1, maxAlive);
    }

    public T get(long id) {
        return map.get(id);
    }

    public void put(long id, T value) {
        map.put(id, value);
    }

    public T remove(long id) {
        return map.remove(id);
    }

    public boolean contains(long id) {
        return map.containsKey(id);
    }

    public int size() {
        return map.size();
    }

    /** 副本，供遍历时安全使用（例如在 Android 侧做可见性切换 / 崩溃匹配） */
    public Map<Long, T> snapshot() {
        return new HashMap<>(map);
    }

    /**
     * 放入（或刷新）id 对应的资源并标记为最近使用；若总量超出 maxAlive，
     * 从最久未用的开始返回应驱逐的 id。返回列表永远不包含 id 本身。
     */
    public List<Long> touchAndEvict(long id, T value) {
        map.put(id, value); // accessOrder=true：put 同时刷新为最新
        List<Long> evict = new ArrayList<>();
        while (map.size() > maxAlive) {
            Long eldest = map.keySet().iterator().next();
            if (eldest == null) break;
            if (eldest == id) break; // 理论不可达（刚 put 的 id 最新），防御性保护
            evict.add(eldest);
            map.remove(eldest);
        }
        return evict;
    }
}
