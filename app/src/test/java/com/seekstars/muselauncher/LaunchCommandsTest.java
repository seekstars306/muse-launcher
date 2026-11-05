package com.seekstars.muselauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/** M1：调试 extra 解析与门控契约 */
public class LaunchCommandsTest {

    @Test
    public void emptyExtras_allDefaults() {
        LaunchCommands c = LaunchCommands.parse(new HashMap<>());
        assertFalse(c.showDrawer);
        assertEquals(-1L, c.startAccount);
        assertFalse(c.hasFullscreen);
        assertFalse(c.fullscreen);
        assertFalse(c.hasOpenConfig);
        assertEquals(-1L, c.openConfigId);
        assertNull(c.proxyPreset);
        assertFalse(c.proxyAutoTest);
        assertFalse(c.proxySave);
        assertNull(c.searchQuery);
        assertFalse(c.dumpAuth);
        assertFalse(c.addAccount);
        assertFalse(c.hasDelAccount);
        assertEquals(-1L, c.delAccountId);
    }

    @Test
    public void nullExtras_allDefaults() {
        LaunchCommands c = LaunchCommands.parse(null);
        assertFalse(c.showDrawer);
        assertEquals(-1L, c.startAccount);
    }

    @Test
    public void fullExtras_parsedCorrectly() {
        Map<String, Object> e = new HashMap<>();
        e.put("show_drawer", true);
        e.put("start_account", 5L);
        e.put("fullscreen", true);
        e.put("open_config", 8L);
        e.put("proxy_preset", "http://u:p@1.2.3.4:3129");
        e.put("proxy_autotest", true);
        e.put("proxy_save", true);
        e.put("search_query", "Muse");
        e.put("dump_auth", true);
        e.put("add_account", true);
        e.put("del_account", 9L);

        LaunchCommands c = LaunchCommands.parse(e);
        assertTrue(c.showDrawer);
        assertEquals(5L, c.startAccount);
        assertTrue(c.hasFullscreen);
        assertTrue(c.fullscreen);
        assertTrue(c.hasOpenConfig);
        assertEquals(8L, c.openConfigId);
        assertEquals("http://u:p@1.2.3.4:3129", c.proxyPreset);
        assertTrue(c.proxyAutoTest);
        assertTrue(c.proxySave);
        assertEquals("Muse", c.searchQuery);
        assertTrue(c.dumpAuth);
        assertTrue(c.addAccount);
        assertTrue(c.hasDelAccount);
        assertEquals(9L, c.delAccountId);
    }

    /** am start 类型不匹配时（如 --es 传了本应 --ez 的键），必须安全回退到默认值而不是崩溃 */
    @Test
    public void wrongTypedExtras_safeDefaults() {
        Map<String, Object> e = new HashMap<>();
        e.put("show_drawer", "true");      // 字符串冒充布尔
        e.put("start_account", "5");       // 字符串冒充 long
        e.put("proxy_preset", 123L);       // long 冒充字符串
        e.put("fullscreen", 1);            // int 冒充布尔

        LaunchCommands c = LaunchCommands.parse(e);
        assertFalse(c.showDrawer);
        assertEquals(-1L, c.startAccount);
        assertNull(c.proxyPreset);
        assertFalse(c.fullscreen);
        // has* 语义保留：键存在即视为“带了参数”，但值取默认
        assertTrue(c.hasFullscreen);
    }

    @Test
    public void falseBoolean_extraRecognizedButInactive() {
        Map<String, Object> e = new HashMap<>();
        e.put("show_drawer", false);
        e.put("fullscreen", false);
        LaunchCommands c = LaunchCommands.parse(e);
        assertFalse(c.showDrawer);
        assertTrue(c.hasFullscreen);
        assertFalse(c.fullscreen);
    }

    /** M1 门控契约：release 构建（debugBuild=false）必须禁用全部调试参数 */
    @Test
    public void gate_disabledOnReleaseBuild() {
        assertFalse(LaunchCommands.enabled(false));
        assertTrue(LaunchCommands.enabled(true));
    }
}
