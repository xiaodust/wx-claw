package com.dust.wxclawbackfront.config;

import com.dust.wxclawbackfront.ilink.UserMessageKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyedPartitionExecutorTest {

    private static final int PARTITIONS = 8;

    private ExecutorService delegate;
    private KeyedPartitionExecutor executor;

    @BeforeEach
    void setUp() {
        delegate = Executors.newFixedThreadPool(4);
        executor = new KeyedPartitionExecutor(delegate, PARTITIONS);
    }

    @AfterEach
    void tearDown() {
        delegate.shutdownNow();
    }

    @Test
    void sameKeyTasksExecuteSeriallyInSubmissionOrder() throws Exception {
        List<String> events = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch allDone = new CountDownLatch(3);
        Object key = new UserMessageKey("tenant", "bot", "user-1");

        executor.execute(key, () -> {
            events.add("1-start");
            firstStarted.countDown();
            await(releaseFirst);
            events.add("1-end");
            allDone.countDown();
        });
        executor.execute(key, () -> {
            events.add("2");
            allDone.countDown();
        });
        executor.execute(key, () -> {
            events.add("3");
            allDone.countDown();
        });

        assertTrue(firstStarted.await(2, TimeUnit.SECONDS));
        // 任务 1 阻塞期间，同 key 的任务 2/3 不允许开始
        Thread.sleep(150);
        assertEquals(List.of("1-start"), events);

        releaseFirst.countDown();
        assertTrue(allDone.await(5, TimeUnit.SECONDS));
        assertEquals(List.of("1-start", "1-end", "2", "3"), events);
    }

    @Test
    void differentKeysExecuteInParallel() throws Exception {
        String keyA = "user-A";
        String keyB = differentPartitionKey(keyA);
        CountDownLatch aStarted = new CountDownLatch(1);
        CountDownLatch bStarted = new CountDownLatch(1);
        CountDownLatch releaseA = new CountDownLatch(1);

        executor.execute(keyA, () -> {
            aStarted.countDown();
            await(releaseA);
        });
        executor.execute(keyB, () -> bStarted.countDown());

        assertTrue(aStarted.await(2, TimeUnit.SECONDS));
        assertTrue(bStarted.await(2, TimeUnit.SECONDS), "不同 key 的任务应并行执行");
        releaseA.countDown();
    }

    @Test
    void failingTaskDoesNotBlockFollowingTasks() throws Exception {
        List<String> events = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch done = new CountDownLatch(2);
        Object key = new UserMessageKey("tenant", "bot", "user-2");

        executor.execute(key, () -> {
            events.add("boom");
            done.countDown();
            throw new IllegalStateException("boom");
        });
        executor.execute(key, () -> {
            events.add("next");
            done.countDown();
        });

        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(List.of("boom", "next"), events);
    }

    /** 构造一个哈希落在不同分区的 key，避免测试受哈希碰撞影响。 */
    private static String differentPartitionKey(String key) {
        int target = Math.floorMod(key.hashCode(), PARTITIONS);
        String candidate = key;
        while (Math.floorMod(candidate.hashCode(), PARTITIONS) == target) {
            candidate = candidate + "x";
        }
        return candidate;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
