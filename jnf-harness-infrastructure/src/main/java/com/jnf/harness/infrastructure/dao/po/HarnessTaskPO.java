package com.jnf.harness.infrastructure.dao.po;

/**
 * 任务持久化对象。
 */
public class HarnessTaskPO {

    private String taskId;
    private String sessionId;
    private String prompt;
    private String enabledToolsJson;
    private String status;
    private java.sql.Timestamp submittedAt;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getEnabledToolsJson() {
        return enabledToolsJson;
    }

    public void setEnabledToolsJson(String enabledToolsJson) {
        this.enabledToolsJson = enabledToolsJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public java.sql.Timestamp getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(java.sql.Timestamp submittedAt) {
        this.submittedAt = submittedAt;
    }
}
