package com.jnf.harness.infrastructure.dao.po;

/**
 * 模型条目持久化对象（P11-1，表 harness_model）。
 */
public class ModelPO {

    private String channelCode;
    private String modelCode;
    private String displayName;
    private Integer contextWindow;
    private Integer maxOutputTokens;
    private String inputModalities;
    private String outputModalities;
    private boolean enabled;
    /** 列名 is_default；字段名避开 JavaBeans 的 "default" 属性名。 */
    private boolean defaultFlag;
    private int sortOrder;

    public String getChannelCode() {
        return channelCode;
    }

    public void setChannelCode(String channelCode) {
        this.channelCode = channelCode;
    }

    public String getModelCode() {
        return modelCode;
    }

    public void setModelCode(String modelCode) {
        this.modelCode = modelCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Integer getContextWindow() {
        return contextWindow;
    }

    public void setContextWindow(Integer contextWindow) {
        this.contextWindow = contextWindow;
    }

    public Integer getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(Integer maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public String getInputModalities() {
        return inputModalities;
    }

    public void setInputModalities(String inputModalities) {
        this.inputModalities = inputModalities;
    }

    public String getOutputModalities() {
        return outputModalities;
    }

    public void setOutputModalities(String outputModalities) {
        this.outputModalities = outputModalities;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isDefaultFlag() {
        return defaultFlag;
    }

    public void setDefaultFlag(boolean defaultFlag) {
        this.defaultFlag = defaultFlag;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
