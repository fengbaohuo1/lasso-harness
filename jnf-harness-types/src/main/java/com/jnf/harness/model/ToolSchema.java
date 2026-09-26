package com.jnf.harness.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSON Schema 构建辅助（工具参数描述）。
 * <p>数据示例：
 * <pre>
 * objectSchema().prop("city", stringSchema("城市名称")).required("city").build()
 * </pre>
 */
public final class ToolSchema {

    private ToolSchema() {
    }

    /** object 类型 schema 构建器。 */
    public static Builder objectSchema() {
        return new Builder();
    }

    /** string 类型属性 schema。 */
    public static Map<String, Object> stringSchema(String description) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "string");
        if (description != null) {
            schema.put("description", description);
        }
        return schema;
    }

    /** number/integer 类型属性 schema。 */
    public static Map<String, Object> numberSchema(String description, boolean integer) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", integer ? "integer" : "number");
        if (description != null) {
            schema.put("description", description);
        }
        return schema;
    }

    /** boolean 类型属性 schema。 */
    public static Map<String, Object> booleanSchema(String description) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "boolean");
        if (description != null) {
            schema.put("description", description);
        }
        return schema;
    }

    public static final class Builder {
        private final Map<String, Object> properties = new LinkedHashMap<>();
        private java.util.List<String> required = new java.util.ArrayList<>();
        private String description;

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder prop(String name, Map<String, Object> schema) {
            properties.put(name, schema);
            return this;
        }

        public Builder required(String... names) {
            required.addAll(java.util.List.of(names));
            return this;
        }

        public Map<String, Object> build() {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            if (description != null) {
                schema.put("description", description);
            }
            schema.put("properties", properties);
            if (!required.isEmpty()) {
                schema.put("required", required);
            }
            return schema;
        }
    }
}
