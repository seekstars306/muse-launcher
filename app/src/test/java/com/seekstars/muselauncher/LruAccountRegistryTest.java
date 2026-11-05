package com.seekstars.muselauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;
import java.util.Map;

/** M2/M4：LRU 注册表的驱逐决策 */
public class LruAccountRegistryTest {

    @Test
    public void putGetRemoveContains() {
        LruAccountRegistry<String> r = new LruAccountRegistry<>(3);
        r.put(1L, "a");
        assertTrue(r.contains(1L));
        assertEquals("a", r.get(1L));
        assertEquals("a", r.remove(1L));
        assertNull(r.remove(1L));
        assertFalse(r.contains(1L));
        assertEquals(0, r.size());
    }

    @Test
    public void underCapacity_neverEvicts() {
        LruAccountRegistry<String> r = new LruAccountRegistry<>(3);
        r.put(1L, "a");
        r.put(2L, "b");
        List<Long> evict = r.touchAndEvict(2L, "b2");
        assertTrue(evict.isEmpty());
        assertEquals(2, r.size());
        assertEquals("b2", r.get(2L));
    }

    @Test
    public void overCapacity_evictsEldest() {
        LruAccountRegistry<String> r = new LruAccountRegistry<>(3);
        r.put(1L, "a");
        r.put(2L, "b");
        r.put(3L, "c");
        List<Long> evict = r.touchAndEvict(4L, "d"); // 放入第 4 个
        assertEquals(1, evict.size());
        assertEquals(Long.valueOf(1L), evict.get(0)); // 最久未用的 1 被驱逐
        assertFalse(r.contains(1L));
        assertEquals(3, r.size());
        // 刚放入的 4 绝不会被驱逐
        assertTrue(r.contains(4L));
    }

    @Test
    public void getRefreshesRecency() {
        LruAccountRegistry<String> r = new LruAccountRegistry<>(3);
        r.put(1L, "a");
        r.put(2L, "b");
        r.put(3L, "c");
        // 访问 1，使 2 变成最旧
        r.get(1L);
        List<Long> evict = r.touchAndEvict(4L, "d");
        assertEquals(1, evict.size());
        assertEquals(Long.valueOf(2L), evict.get(0));
        assertTrue(r.contains(1L));
        assertTrue(r.contains(3L));
        assertTrue(r.contains(4L));
    }

    @Test
    public void touchExisting_neverEvictsIt() {
        LruAccountRegistry<String> r = new LruAccountRegistry<>(2);
        r.put(1L, "a");
        r.put(2L, "b");
        List<Long> evict = r.touchAndEvict(1L, "a2"); // 刷新已存在的 1
        assertTrue(evict.isEmpty() || !evict.contains(1L));
        assertTrue(r.contains(1L));
        assertEquals("a2", r.get(1L));
    }

    @Test
    public void capacityOne_keepsOnlyTouched() {
        LruAccountRegistry<String> r = new LruAccountRegistry<>(1);
        r.put(1L, "a");
        List<Long> evict = r.touchAndEvict(2L, "b");
        assertEquals(1, evict.size());
        assertEquals(Long.valueOf(1L), evict.get(0));
        assertEquals(1, r.size());
        assertEquals("b", r.get(2L));
    }

    @Test
    public void snapshot_isCopy() {
        LruAccountRegistry<String> r = new LruAccountRegistry<>(3);
        r.put(1L, "a");
        Map<Long, String> snap = r.snapshot();
        snap.remove(1L);
        snap.put(99L, "x");
        assertTrue(r.contains(1L));
        assertFalse(r.contains(99L));
    }

    @Test
    public void massEviction_multipleAtOnce() {
        LruAccountRegistry<String> r = new LruAccountRegistry<>(2);
        for (long id = 1; id <= 5; id++) r.put(id, "v" + id);
        List<Long> evict = r.touchAndEvict(6L, "v6");
        // 6 个超出容量 2：应驱逐 1..4（按插入序），保留 5、6
        assertEquals(java.util.Arrays.asList(1L, 2L, 3L, 4L), evict);
        assertTrue(r.contains(5L));
        assertTrue(r.contains(6L));
        assertEquals(2, r.size());
    }
}
