package com.seekstars.muselauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** M3：账号列表 JSON 编解码的容错 */
public class AccountJsonCodecTest {

    private List<Account> sample() {
        List<Account> l = new ArrayList<>();
        Account a = new Account(2, "干杂活");
        a.avatar = "干";
        a.proxy = "http://u:p@1.2.3.4:3129";
        a.tags.add("电商");
        a.tags.add("主力");
        l.add(a);
        Account b = new Account(5, "Muse 😊");
        b.avatar = "😊";
        l.add(b);
        return l;
    }

    @Test
    public void roundTrip_preservesAllFields() {
        String json = AccountJsonCodec.toJson(sample());
        List<Account> back = AccountJsonCodec.fromJsonOrNull(json);
        assertEquals(2, back.size());
        Account a = back.get(0);
        assertEquals(2L, a.id);
        assertEquals("干杂活", a.name);
        assertEquals("干", a.avatar);
        assertEquals("http://u:p@1.2.3.4:3129", a.proxy);
        assertEquals(2, a.tags.size());
        assertEquals("电商", a.tags.get(0));
        assertEquals("主力", a.tags.get(1));
        assertEquals("😊", back.get(1).avatar);
    }

    @Test
    public void nullOrGarbage_returnsNull() {
        assertNull(AccountJsonCodec.fromJsonOrNull(null));
        assertNull(AccountJsonCodec.fromJsonOrNull("not json at all"));
        assertNull(AccountJsonCodec.fromJsonOrNull("{\"a\":1}"));   // 对象而非数组
        assertNull(AccountJsonCodec.fromJsonOrNull("[{broken"));
    }

    @Test
    public void emptyArray_emptyList() {
        List<Account> out = AccountJsonCodec.fromJsonOrNull("[]");
        assertTrue(out.isEmpty());
    }

    /** 单条损坏：跳过该条，其余保留 */
    @Test
    public void partialCorruption_skipsBadEntryOnly() {
        String json = "[{\"id\":2,\"name\":\"好账号\"},"
                + "{\"id\":\"不是数字\",\"name\":\"坏账号\"},"
                + "{\"id\":5,\"name\":\"另一个好账号\",\"proxy\":\"\",\"tags\":[]}]";
        List<Account> out = AccountJsonCodec.fromJsonOrNull(json);
        assertEquals(2, out.size());
        assertEquals("好账号", out.get(0).name);
        assertEquals("另一个好账号", out.get(1).name);
    }

    @Test
    public void missingOptionalFields_defaults() {
        List<Account> out = AccountJsonCodec.fromJsonOrNull("[{\"id\":3,\"name\":\"n\"}]");
        assertEquals(1, out.size());
        assertEquals("m", out.get(0).avatar);
        assertEquals("", out.get(0).proxy);
        assertTrue(out.get(0).tags.isEmpty());
    }
}
