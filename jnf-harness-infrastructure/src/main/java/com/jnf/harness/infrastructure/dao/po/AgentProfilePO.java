package com.jnf.harness.infrastructure.dao.po;

/**
 * harness_agent_profile 行对象（A1 画像）。
 */
public class AgentProfilePO {

    private String profileCode;
    private String displayName;
    private int maxStepsPerTurn;
    private int maxTokenContinuations;
    private long contextTokenBudget;
    private int keepRecentMessages;
    private long compactionThresholdTokens;
    private Integer maxTokens;
    private Double temperature;
    private String toolProfile;
    private String promptExtra;

    public String getProfileCode() {
        return profileCode;
    }

    public void setProfileCode(String profileCode) {
        this.profileCode = profileCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public int getMaxStepsPerTurn() {
        return maxStepsPerTurn;
    }

    public void setMaxStepsPerTurn(int maxStepsPerTurn) {
        this.maxStepsPerTurn = maxStepsPerTurn;
    }

    public int getMaxTokenContinuations() {
        return maxTokenContinuations;
    }

    public void setMaxTokenContinuations(int maxTokenContinuations) {
        this.maxTokenContinuations = maxTokenContinuations;
    }

    public long getContextTokenBudget() {
        return contextTokenBudget;
    }

    public void setContextTokenBudget(long contextTokenBudget) {
        this.contextTokenBudget = contextTokenBudget;
    }

    public int getKeepRecentMessages() {
        return keepRecentMessages;
    }

    public void setKeepRecentMessages(int keepRecentMessages) {
        this.keepRecentMessages = keepRecentMessages;
    }

    public long getCompactionThresholdTokens() {
        return compactionThresholdTokens;
    }

    public void setCompactionThresholdTokens(long compactionThresholdTokens) {
        this.compactionThresholdTokens = compactionThresholdTokens;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public String getToolProfile() {
        return toolProfile;
    }

    public void setToolProfile(String toolProfile) {
        this.toolProfile = toolProfile;
    }

    public String getPromptExtra() {
        return promptExtra;
    }

    public void setPromptExtra(String promptExtra) {
        this.promptExtra = promptExtra;
    }
}
