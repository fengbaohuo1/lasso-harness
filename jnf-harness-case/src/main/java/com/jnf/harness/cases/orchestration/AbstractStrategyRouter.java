package com.jnf.harness.cases.orchestration;

/**
 * 策略树节点（责任链）：doApply 处理本节点，getNext 显式路由后继。
 * <p>与参照实现同构：流程用例 = factory 建链 + node 单步 + DynamicContext 传状态。
 */
public abstract class AbstractStrategyRouter {

    /** 执行当前节点并推进到后继（ctx.stop() 可短路）。 */
    public final void execute(DynamicContext context) {
        doApply(context);
        if (context.isStopped()) {
            return;
        }
        AbstractStrategyRouter next = getNext();
        if (next != null) {
            next.execute(context);
        }
    }

    /** 处理本节点。 */
    protected abstract void doApply(DynamicContext context);

    /** 显式路由后继节点（null 表示链尾）。 */
    protected abstract AbstractStrategyRouter getNext();
}
