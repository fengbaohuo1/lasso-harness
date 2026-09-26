package com.jnf.harness.infrastructure.adapter.llm;

import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 单订阅者的同步推送 Publisher：适配器在读流线程里逐块 next()，
 * 下游（Agent 循环）在回调里同步处理。
 * <p>约束：只支持单订阅者；body 在 subscribe 后于调用线程同步执行
 * （调用方自行决定是否放到工作线程，见 {@link #startAsync}）。
 */
public final class StreamEmitter<T> implements Flow.Publisher<T> {

    private final Consumer<StreamEmitter<T>> body;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean done = new AtomicBoolean(false);
    private volatile Flow.Subscriber<? super T> subscriber;

    public StreamEmitter(Consumer<StreamEmitter<T>> body) {
        this.body = body;
    }

    /** 创建即在后台线程执行 body（由订阅触发）。 */
    public static <T> Flow.Publisher<T> startAsync(Consumer<StreamEmitter<T>> body) {
        return new StreamEmitter<>(body);
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> s) {
        if (started.compareAndSet(false, true)) {
            subscriber = s;
            s.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {
                    // 同步推送，不做背压排队
                }

                @Override
                public void cancel() {
                    done.set(true);
                }
            });
            if (!done.get()) {
                body.accept(this);
            }
        } else {
            s.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {
                }

                @Override
                public void cancel() {
                }
            });
            s.onError(new IllegalStateException("StreamEmitter supports a single subscriber only"));
        }
    }

    public void next(T item) {
        if (!done.get() && subscriber != null) {
            subscriber.onNext(item);
        }
    }

    public void complete() {
        if (done.compareAndSet(false, true) && subscriber != null) {
            subscriber.onComplete();
        }
    }

    public void error(Throwable t) {
        if (done.compareAndSet(false, true) && subscriber != null) {
            subscriber.onError(t);
        }
    }

    public boolean isDone() {
        return done.get();
    }
}
