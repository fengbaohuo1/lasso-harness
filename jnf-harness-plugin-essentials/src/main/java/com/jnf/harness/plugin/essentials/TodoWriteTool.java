package com.jnf.harness.plugin.essentials;

import com.jnf.harness.model.AbstractTool;
import com.jnf.harness.model.ToolExecutionResult;
import com.jnf.harness.model.ToolRunContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * todo_write 工具：整表更新当前任务的待办清单。
 */
public class TodoWriteTool extends AbstractTool {

    private final TodoService todoService;

    public TodoWriteTool(TodoService todoService) {
        this.todoService = todoService;
    }

    @Override
    public String name() {
        return "todo_write";
    }

    @Override
    public String description() {
        return "更新任务待办清单（整表覆盖）。status 取值：pending / in_progress / completed。";
    }

    @Override
    public Map<String, Object> parameters() {
        return objectSchema()
                .prop("items", stringSchema("待办数组，元素形如 {content,status}"))
                .required("items")
                .build();
    }

    @Override
    protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext context) {
        Object raw = args.get("items");
        List<TodoService.TodoItem> items = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object element : list) {
                if (element instanceof Map<?, ?> map) {
                    Object contentObj = map.get("content");
                    Object statusObj = map.get("status");
                    String content = contentObj == null ? "" : String.valueOf(contentObj);
                    String status = statusObj == null ? "pending" : String.valueOf(statusObj);
                    if (!content.isBlank()) {
                        items.add(new TodoService.TodoItem(content, normalize(status)));
                    }
                }
            }
        }
        if (items.isEmpty()) {
            return fail("items 不能为空数组", "MISSING_ITEMS");
        }
        todoService.replace(context.agentId(), items);
        return ok("待办已更新：\n" + todoService.render(context.agentId()));
    }

    private String normalize(String status) {
        return switch (status) {
            case "in_progress", "completed" -> status;
            default -> "pending";
        };
    }
}
