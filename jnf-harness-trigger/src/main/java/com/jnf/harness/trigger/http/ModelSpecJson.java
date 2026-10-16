package com.jnf.harness.trigger.http;

import com.jnf.harness.domain.runtime.setting.ModelSpecCatalog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型规格的 REST 序列化（P11-2）：{@code /discover} 与 {@code /test} 共用，
 * 让「发现模型」的结果直接带上官方规格与能力，前端勾选批量添加即可自动填表。
 */
final class ModelSpecJson {

    private ModelSpecJson() {
    }

    static Map<String, Object> of(ModelSpecCatalog.ModelSpec spec) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("modelCode", spec.modelCode());
        item.put("displayName", spec.modelCode());
        item.put("contextWindow", spec.contextWindow());
        item.put("maxOutputTokens", spec.maxOutputTokens());
        item.put("inputModalities", spec.inputModalities());
        item.put("outputModalities", spec.outputModalities());
        item.put("known", spec.known());
        item.put("chatModel", spec.chatModel());
        item.put("note", spec.note());
        return item;
    }

    /** 把一个上游模型编码列表补全成带规格的对象列表。 */
    static List<Map<String, Object>> ofAll(String providerCode, List<String> modelCodes) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (modelCodes == null) {
            return items;
        }
        for (String modelCode : modelCodes) {
            items.add(of(ModelSpecCatalog.resolve(providerCode, modelCode)));
        }
        return items;
    }
}
