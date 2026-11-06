package com.seekstars.muselauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/** M3：AccountsStore 备份/自愈/通知逻辑（HashMap 假 KV，纯 JVM 测试） */
public class AccountsStoreTest {

    /** 最小 HashMap 键值实现：putAll 覆盖写，模拟 SharedPreferences */
    static class FakeKv implements KeyValueStore {
        final Map<String, Object> m = new HashMap<>();

        @Override
        public String getString(String key, String defValue) {
            Object v = m.get(key);
            return v instanceof String ? (String) v : defValue;
        }

        @Override
        public long getLong(String key, long defValue) {
            Object v = m.get(key);
            return v instanceof Long ? (Long) v : defValue;
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            Object v = m.get(key);
            return v instanceof Boolean ? (Boolean) v : defValue;
        }

        @Override
        public void putAll(Map<String, Object> values) {
            m.putAll(values);
        }

        @Override
        public void putString(String key, String value) {
            m.put(key, value);
        }

        @Override
        public void putLong(String key, long value) {
            m.put(key, value);
        }

        @Override
        public void putBoolean(String key, boolean value) {
            m.put(key, value);
        }
    }

    @Test
    public void roundTripAcrossInstances() {
        FakeKv kv = new FakeKv();
        AccountsStore s1 = new AccountsStore(kv);
        Account a = s1.addDefault();
        s1.rename(a.id, "主力号");
        Account b = s1.addDefault();
        b.proxy = "socks5://127.0.0.1:1080";
        b.avatar = "M";
        b.tags.add("测试");
        s1.setCurrent(a.id);
        s1.save();

        AccountsStore s2 = new AccountsStore(kv);
        assertEquals(2, s2.all().size());
        assertEquals(a.id, s2.getCurrentId());
        assertEquals("主力号", s2.findById(a.id).name);
        assertEquals("socks5://127.0.0.1:1080", s2.findById(b.id).proxy);
        assertEquals("测试", s2.findById(b.id).tags.get(0));
        assertNull(s2.takeLoadNotice());
    }

    @Test
    public void remove_fixesCurrentAndPersists() {
        FakeKv kv = new FakeKv();
        AccountsStore s = new AccountsStore(kv);
        Account a = s.addDefault();
        Account b = s.addDefault();
        s.setCurrent(b.id);
        s.remove(b.id);
        assertEquals(a.id, s.getCurrentId());
        assertNull(s.findById(b.id));

        AccountsStore s2 = new AccountsStore(kv);
        assertEquals(1, s2.all().size());
        assertEquals(a.id, s2.getCurrentId());
    }

    @Test
    public void removeLastAccount_currentBecomesMinusOne() {
        FakeKv kv = new FakeKv();
        AccountsStore s = new AccountsStore(kv);
        Account a = s.addDefault();
        s.remove(a.id);
        assertEquals(-1L, s.getCurrentId());
        AccountsStore s2 = new AccountsStore(kv);
        assertTrue(s2.all().isEmpty());
        assertEquals(-1L, s2.getCurrentId());
    }

    /** list 损坏但 list_prev 完好 → 自愈并通知 recovered */
    @Test
    public void corruptList_recoversFromPrev() {
        FakeKv kv = new FakeKv();
        AccountsStore s = new AccountsStore(kv);
        Account a = s.addDefault();      // save#1（prev 为空）
        a.proxy = "http://1.2.3.4:3129";
        s.save();                        // save#2（prev = save#1 的值，proxy 还是空）
        s.save();                        // save#3（prev = save#2 的值，带 proxy）
        String goodList = kv.getString("list", null);
        assertNotNull(goodList);
        assertTrue(kv.getString("list_prev", "MISSING").startsWith("["));

        // 破坏主键
        kv.putString("list", "{这不是JSON");
        // list_prev 仍是上一次的好数据
        AccountsStore s2 = new AccountsStore(kv);
        assertEquals("recovered", s2.takeLoadNotice());
        assertEquals(1, s2.all().size());
        assertEquals("http://1.2.3.4:3129", s2.all().get(0).proxy);
        // 自愈后主键被写回好数据
        assertEquals(goodList, kv.getString("list", null));
        // 损坏原始值已留底
        assertEquals("{这不是JSON", kv.getString("list_corrupt", null));
        // 回归（真机踩过的坑）：自愈绝不能把 nextId 重置为 1，否则后续建号 id 冲突。
        // 本用例只建过一个账号（id=1），恢复后 nextId 必须仍是 2
        assertEquals(2L, kv.getLong("nextId", -1));
        Account added = s2.addDefault();
        assertEquals(2L, added.id);
    }

    /** list 与 list_prev 双双损坏 → 留底 + 通知 corrupt + 空列表，不再抛异常 */
    @Test
    public void corruptEverything_backsUpAndDegrades() {
        FakeKv kv = new FakeKv();
        kv.putString("list", "{{{坏数据");
        kv.putString("list_prev", "]]]也是坏的");
        AccountsStore s = new AccountsStore(kv);
        assertEquals("corrupt", s.takeLoadNotice());
        assertTrue(s.all().isEmpty());
        assertEquals("{{{坏数据", kv.getString("list_corrupt", null));
        // 取走即清
        assertNull(s.takeLoadNotice());
        // 后续仍可正常添加账号（新数据会覆盖，但坏数据已留底）
        s.addDefault();
        assertEquals(1, s.all().size());
    }

    /** list 为合法数组但其中一条损坏 → 跳过坏条目，不算损坏事件 */
    @Test
    public void partialCorruption_noNotice() {
        FakeKv kv = new FakeKv();
        kv.putString("list", "[{\"id\":2,\"name\":\"好的\"},{\"id\":\"x\"}]");
        AccountsStore s = new AccountsStore(kv);
        assertNull(s.takeLoadNotice());
        assertEquals(1, s.all().size());
        assertEquals("好的", s.all().get(0).name);
    }

    /** 滚动备份：第二次 save 后 list_prev 应是第一次的 list 值 */
    @Test
    public void rollingPrevBackup() {
        FakeKv kv = new FakeKv();
        AccountsStore s = new AccountsStore(kv);
        s.addDefault(); // 第一次 save：无 prev
        assertNull(kv.getString("list_prev", null));
        String first = kv.getString("list", null);
        s.rename(1L, "改名"); // 第二次 save：prev = 第一次的 list
        assertEquals(first, kv.getString("list_prev", null));
    }

    /** 新建环境继承代理：proxy 传入即写入并持久化；空/null 则保持直连 */
    @Test
    public void addDefault_inheritsProxy() {
        FakeKv kv = new FakeKv();
        AccountsStore s = new AccountsStore(kv);
        Account a = s.addDefault("http://u:p@1.2.3.4:3129");
        assertEquals("http://u:p@1.2.3.4:3129", a.proxy);
        // 持久化校验：新实例读回仍有代理
        AccountsStore s2 = new AccountsStore(kv);
        assertEquals("http://u:p@1.2.3.4:3129", s2.findById(a.id).proxy);

        Account b = s2.addDefault(null);
        assertEquals("", b.proxy);
        Account c = s2.addDefault("");
        assertEquals("", c.proxy);
    }

    /** M4：setCurrent 只写 currentId 独立键，不全量重写 list（账号多时切换零开销） */
    @Test
    public void setCurrent_writesOnlyCurrentKey() {
        FakeKv kv = new FakeKv();
        AccountsStore s = new AccountsStore(kv);
        s.addDefault();
        s.addDefault();
        String listBefore = kv.getString("list", null);
        s.setCurrent(2L);
        assertEquals(listBefore, kv.getString("list", null)); // list 未被重写
        assertEquals(2L, kv.getLong("currentId", -1));
        // 相同值不写盘
        String listAfter = kv.getString("list", null);
        s.setCurrent(2L);
        assertEquals(listAfter, kv.getString("list", null));
        // 新实例读取正常
        AccountsStore s2 = new AccountsStore(kv);
        assertEquals(2L, s2.getCurrentId());
        assertEquals(2, s2.all().size());
    }

    /** takeLoadNotice 取走即清，不重复提示 */
    @Test
    public void notice_takeOnce() {
        FakeKv kv = new FakeKv();
        kv.putString("list", "坏的");
        AccountsStore s = new AccountsStore(kv);
        assertEquals("corrupt", s.takeLoadNotice());
        assertNull(s.takeLoadNotice());
    }

    /** reload() 与构造行为一致 */
    @Test
    public void reload_rerunsRecovery() {
        FakeKv kv = new FakeKv();
        AccountsStore s = new AccountsStore(kv);
        s.addDefault();
        s.save();
        kv.putString("list", "又坏了");
        s.reload();
        assertEquals("recovered", s.takeLoadNotice());
        assertEquals(1, s.all().size());
    }
}
