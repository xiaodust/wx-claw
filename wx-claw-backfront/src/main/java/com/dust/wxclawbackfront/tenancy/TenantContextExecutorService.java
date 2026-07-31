package com.dust.wxclawbackfront.tenancy;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 为标准 {@link ExecutorService} 增加租户上下文传播能力的代理。
 *
 * <p>生命周期方法全部委托给原执行器，只有 {@link #execute(Runnable)} 会装饰任务；
 * {@link AbstractExecutorService} 提供的 submit/invokeAll 最终也会经过 execute。</p>
 */
public class TenantContextExecutorService extends AbstractExecutorService {
    private final ExecutorService delegate;
    private final TenantContextTaskDecorator decorator;

    public TenantContextExecutorService(ExecutorService delegate, TenantContextTaskDecorator decorator) {
        this.delegate = delegate;
        this.decorator = decorator;
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }

    @Override
    public void execute(Runnable command) {
        // 在任务入队前捕获调用线程上下文。
        delegate.execute(decorator.decorate(command));
    }
}
