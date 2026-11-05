package com.seekstars.muselauncher;

import java.util.ArrayList;
import java.util.List;

public class Account {
    public final long id;
    public String name;
    /** 头像：1-2 个字符或 emoji，默认 "m" */
    public String avatar = "m";
    /** 代理地址，空 = 跟随系统网络 */
    public String proxy = "";
    /** 标签（侧栏可按标签搜索） */
    public List<String> tags = new ArrayList<>();
    /** 登录态：运行时从 CookieManager 探测，不持久化 */
    public boolean logged;

    public Account(long id, String name) {
        this.id = id;
        this.name = name;
    }
}
