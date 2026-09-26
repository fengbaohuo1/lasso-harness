package com.jnf.harness.domain.agent.service.ask;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 用户问答网关缝：ask_user_question 工具经此向用户提问并等待回答（UI 层实现接入）。
 */
public interface UserQuestionGateway {

    /** 提交问题，返回回答（异步完成）。 */
    CompletableFuture<String> ask(Question question);

    record Question(String questionId, String question, java.util.List<String> options) {
    }

    /** 内存实现：挂起等待外部 answer()（测试与无 UI 场景用）。 */
    class InMemoryUserQuestionGateway implements UserQuestionGateway {
        private final Map<String, CompletableFuture<String>> pending = new java.util.concurrent.ConcurrentHashMap<>();
        private final Map<String, Question> questions = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public CompletableFuture<String> ask(Question question) {
            CompletableFuture<String> future = new CompletableFuture<>();
            pending.put(question.questionId(), future);
            questions.put(question.questionId(), question);
            future.whenComplete((r, e) -> {
                pending.remove(question.questionId());
                questions.remove(question.questionId());
            });
            return future;
        }

        /** 外部回答（UI/REST 层调用）。 */
        public void answer(String questionId, String answer) {
            CompletableFuture<String> future = pending.get(questionId);
            if (future != null) {
                future.complete(answer);
            }
        }

        public java.util.List<Question> pendingQuestions() {
            return java.util.List.copyOf(questions.values());
        }
    }
}
