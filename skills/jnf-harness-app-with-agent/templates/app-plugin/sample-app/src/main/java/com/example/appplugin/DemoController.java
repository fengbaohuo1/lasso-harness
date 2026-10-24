package com.example.appplugin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 业务 REST（演示用内存数据，重启即还原）。
 * <p>关键约定：
 * <ul>
 *   <li>统一信封 {@code {code, info, data}}，成功 {@code 00000}，HTTP 恒 200；</li>
 *   <li>应用 REST 是唯一数据源——页面和插件工具都查这里，口径必须一致；</li>
 *   <li>写操作（真实项目）也走同一 service，别让工具绕过业务规则。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/demo")
public class DemoController {

    /** 5 条内存种子数据（换成你的业务实体：字段够页面渲染与汇总即可）。 */
    private static final List<Map<String, Object>> ITEMS = List.of(
            item(1, "智能门锁 Pro", "安防", 899, 12, "在售"),
            item(2, "温湿度传感器", "安防", 129, 48, "在售"),
            item(3, "智能网关 Mini", "网络", 259, 7, "低库存"),
            item(4, "无线开关面板", "电工", 79, 120, "在售"),
            item(5, "门窗磁感应器", "安防", 99, 0, "缺货"));

    @GetMapping("/items")
    public Map<String, Object> items(@RequestParam(name = "keyword", required = false) String keyword) {
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        List<Map<String, Object>> data = new ArrayList<>();
        for (Map<String, Object> row : ITEMS) {
            if (kw.isEmpty()
                    || String.valueOf(row.get("name")).toLowerCase(Locale.ROOT).contains(kw)
                    || String.valueOf(row.get("category")).toLowerCase(Locale.ROOT).contains(kw)) {
                data.add(row);
            }
        }
        return envelope(data);
    }

    /** 统一信封；别在 controller 里直接 return List（前端与工具都要靠 code 判断成败）。 */
    private static Map<String, Object> envelope(Object data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "00000");
        body.put("info", "success");
        body.put("data", data);
        return body;
    }

    private static Map<String, Object> item(int id, String name, String category,
                                            int price, int stock, String status) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("name", name);
        row.put("category", category);
        row.put("price", price);
        row.put("stock", stock);
        row.put("status", status);
        return row;
    }
}
