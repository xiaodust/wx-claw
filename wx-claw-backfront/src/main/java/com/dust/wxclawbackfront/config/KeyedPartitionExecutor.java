package com.dust.wxclawbackfront.config;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

/**
 * 按 key 分区的顺序执行器。
 *
 * <p>同一 key 的任务保证串行且按提交顺序执行（每个分区同一时刻只有一个消费者），
 * 不同 key 的任务分散到不同分区、可以并行。用于消息处理等"同用户保序、跨用户并行"的场景。
 *
 * <p>分区队列与 draining 标志都由每分区锁保护：任务入队后若该分区尚无消费者，
 * 则提交一个 drainer 到底层线程池；drainer 串行取队首任务执行，队列空时复位标志退出。
 * 提交被拒绝（如线程池关闭）时复位标志，已入队任务保留，等待下次提交重新启动。
 */
@Slf4j
public class KeyedPartitionExecutor {

    private final ExecutorService delegate;
    private final int partitions;
    private final ArrayDeque<Runnable>[] queues;
    private final Object[] locks;
    private final boolean[] draining;

    @SuppressWarnings("unchecked")
    public KeyedPartitionExecutor(ExecutorService delegate, int partitions) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate executor must not be null");
        }
        if (partitions <= 0) {
            throw new IllegalArgumentException("partitions must be positive: " + partitions);
        }
        this.delegate = delegate;
        this.partitions = partitions;
        this.queues = new ArrayDeque[partitions];
        this.locks = new Object[partitions];
        this.draining = new boolean[partitions];
        for (int i = 0; i < partitions; i++) {
            this.queues[i] = new ArrayDeque<>();
            this.locks[i] = new Object();
        }
    }

    /**
     * 提交任务：同一 key 串行执行，不同 key 并行执行。
     *
     * @throws RejectedExecutionException 底层线程池拒绝执行时抛出（如已关闭）
     */
    public void execute(Object key, Runnable task) {
        if (key == null) {
            throw new NullPointerException("partition key must not be null");
        }
        if (task == null) {
            throw new NullPointerException("task must not be null");
        }
        int index = Math.floorMod(key.hashCode(), partitions);
        synchronized (locks[index]) {
            queues[index].addLast(task);
            if (draining[index]) {
                return;
            }
            draining[index] = true;
        }
        submitDrainer(index);
    }

    private void submitDrainer(int index) {
        try {
            delegate.execute(() -> drain(index));
        } catch (RejectedExecutionException ex) {
            synchronized (locks[index]) {
                // 还原标志使后续 execute 能重新启动 drainer；已入队任务保留等待下次提交。
                draining[index] = false;
            }
            throw ex;
        }
    }

    private void drain(int index) {
        try {
            while (true) {
                Runnable task;
                synchronized (locks[index]) {
                    task = queues[index].pollFirst();
                    if (task == null) {
                        // 与入队同锁：poll 为空即队列为空，置 false 后新的 execute 会重新启动 drainer。
                        draining[index] = false;
                        return;
                    }
                }
                try {
                    task.run();
                } catch (Throwable ex) {
                    // 单个任务异常不能中断分区消费，也不能让 draining 标志残留。
                    log.error("分区任务执行异常: partition={}, error={}", index, ex.getMessage(), ex);
                }
            }
        } finally {
            // 兜底：任何退出路径都不允许 draining 残留，否则该分区后续任务无法启动。
            synchronized (locks[index]) {
                draining[index] = false;
            }
        }
    }
}
