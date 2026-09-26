package com.jnf.harness.infrastructure.dao.po;

/**
 * 审批记录持久化对象。
 */
public class ApprovalRecordPO {

    private String id;
    private String sessionId;
    private String toolName;
    private String argsJson;
    private String verdict;
    private java.sql.Timestamp createdAt;
    private java.sql.Timestamp decidedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getArgsJson() {
        return argsJson;
    }

    public void setArgsJson(String argsJson) {
        this.argsJson = argsJson;
    }

    public String getVerdict() {
        return verdict;
    }

    public void setVerdict(String verdict) {
        this.verdict = verdict;
    }

    public java.sql.Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(java.sql.Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public java.sql.Timestamp getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(java.sql.Timestamp decidedAt) {
        this.decidedAt = decidedAt;
    }
}
