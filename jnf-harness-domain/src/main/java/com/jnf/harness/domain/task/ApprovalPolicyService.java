package com.jnf.harness.domain.task;

/**
 * 审批策略（approval 子域）：按执行画像决定免审直通还是进待审。
 */
public class ApprovalPolicyService {

    /** 执行画像。 */
    public enum Profile {
        WEB, HEADLESS
    }

    /**
     * 决策：画像为 HEADLESS（自动化）且无风险 → 免审；
     * WEB（有人值守）按风险面进待审。可显式 forceApproval。
     */
    public boolean autoApprove(Profile profile, PermissionPolicyService.PermissionAssessment assessment,
                               boolean forceApproval) {
        if (forceApproval) {
            return false;
        }
        return !assessment.requiresApproval();
    }
}
