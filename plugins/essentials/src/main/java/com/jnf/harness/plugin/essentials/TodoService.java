package com.jnf.harness.plugin.essentials;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 待办服务：todo_write 工具的载体（按 agent 隔离，进程内存 + 事件留痕）。
 */
public class TodoService {

    /** 一条待办。 */
    public record TodoItem(String content, String status) {
        // status: pending / in_progress / completed
    }

    private final Map<String, List<TodoItem>> todosByAgent = new ConcurrentHashMap<>();

    public List<TodoItem> replace(String agentId, List<TodoItem> items) {
        List<TodoItem> copy = List.copyOf(items == null ? List.of() : items);
        todosByAgent.put(agentId, new ArrayList<>(copy));
        return copy;
    }

    public List<TodoItem> list(String agentId) {
        return List.copyOf(todosByAgent.getOrDefault(agentId, List.of()));
    }

    public String render(String agentId) {
        List<TodoItem> items = list(agentId);
        if (items.isEmpty()) {
            return "（待办为空）";
        }
        StringBuilder sb = new StringBuilder();
        for (TodoItem item : items) {
            sb.append("- [").append(item.status()).append("] ").append(item.content()).append('\n');
        }
        return sb.toString();
    }
}
