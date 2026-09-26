package com.jnf.harness.domain.agent.service;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Agent 输入箱：用户消息在此排队，由 turn 循环批量认领（claim）。
 * <p>消息自带完成信号（future 在对应 turn 结束时完成）。
 */
public class Inbox {

    /** 待处理消息：文本 + 完成信号 + 可选流式监听（每问一监听）。 */
    public record PendingMessage(String text, CompletableFuture<TurnSummary> future,
                                 com.jnf.harness.domain.agent.service.run.AgentEventListener listener) {
    }

    /** 一个 turn 的结果摘要。 */
    public record TurnSummary(String agentId, long turn, String lastAssistantText,
                              com.jnf.harness.domain.session.event.TurnEndReason reason) {
    }

    private final Deque<PendingMessage> queue = new ArrayDeque<>();

    public synchronized void add(PendingMessage message) {
        queue.addLast(message);
        notifyAll();
    }

    /** 批量认领全部待处理消息（无则立即返回空）。 */
    public synchronized List<PendingMessage> claimAll() {
        List<PendingMessage> claimed = new ArrayList<>(queue);
        queue.clear();
        return claimed;
    }

    /** 认领前最多等待 waitMillis（用于驱动线程空转休眠）。 */
    public synchronized List<PendingMessage> claimAll(long waitMillis) throws InterruptedException {
        if (queue.isEmpty() && waitMillis > 0) {
            wait(waitMillis);
        }
        return claimAll();
    }

    public synchronized boolean hasPending() {
        return !queue.isEmpty();
    }

    public synchronized int size() {
        return queue.size();
    }
}
