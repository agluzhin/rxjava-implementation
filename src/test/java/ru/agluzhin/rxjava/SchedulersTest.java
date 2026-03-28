package ru.agluzhin.rxjava;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import ru.agluzhin.rxjava.core.Observable;
import ru.agluzhin.rxjava.core.Observer;
import ru.agluzhin.rxjava.schedulers.*;
import ru.rxjava.schedulers.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Юнит-тесты Schedulers и методов subscribeOn / observeOn.
 *
 * <p>Каждый тест проверяет, что операции выполняются в ожидаемом потоке
 * и что переключение потоков не нарушает корректность данных.</p>
 */
class SchedulersTest {

    private IOThreadScheduler ioScheduler;
    private ComputationScheduler computationScheduler;
    private SingleThreadScheduler singleScheduler;

    @BeforeEach
    void setUp() {
        ioScheduler = new IOThreadScheduler();
        computationScheduler = new ComputationScheduler();
        singleScheduler = new SingleThreadScheduler();
    }

    @AfterEach
    void tearDown() {
        ioScheduler.shutdown();
        computationScheduler.shutdown();
        singleScheduler.shutdown();
    }

    // -----------------------------------------------------------------------
    // Базовые тесты Scheduler
    // -----------------------------------------------------------------------

    @Test
    @Timeout(3)
    void ioScheduler_executesTask() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> threadName = new AtomicReference<>();

        ioScheduler.execute(() -> {
            threadName.set(Thread.currentThread().getName());
            latch.countDown();
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(threadName.get().startsWith("io-thread-"),
                "Задача должна выполняться в io-потоке, а не в: " + threadName.get());
    }

    @Test
    @Timeout(3)
    void computationScheduler_executesInNamedThread() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> threadName = new AtomicReference<>();

        computationScheduler.execute(() -> {
            threadName.set(Thread.currentThread().getName());
            latch.countDown();
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(threadName.get().startsWith("computation-thread-"),
                "Задача должна выполняться в computation-потоке");
    }

    @Test
    @Timeout(3)
    void singleScheduler_executesSequentially() throws InterruptedException {
        int taskCount = 10;
        List<Integer> order = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(taskCount);

        for (int i = 0; i < taskCount; i++) {
            final int n = i;
            singleScheduler.execute(() -> {
                order.add(n);
                latch.countDown();
            });
        }

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        // В однопоточном планировщике порядок должен совпадать с порядком отправки
        for (int i = 0; i < taskCount; i++) {
            assertEquals(i, order.get(i), "Задача " + i + " должна выполниться на позиции " + i);
        }
    }

    @Test
    void computationScheduler_threadCountEqualsAvailableProcessors() {
        int expected = Runtime.getRuntime().availableProcessors();
        assertEquals(expected, computationScheduler.getThreadCount());
    }

    // -----------------------------------------------------------------------
    // subscribeOn
    // -----------------------------------------------------------------------

    @Test
    @Timeout(3)
    void subscribeOn_sourceRunsInSpecifiedScheduler() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> sourceThread = new AtomicReference<>();

        Observable.<Integer>create(emitter -> {
                    sourceThread.set(Thread.currentThread().getName());
                    emitter.onNext(1);
                    emitter.onComplete();
                })
                .subscribeOn(ioScheduler)
                .subscribe(new Observer<Integer>() {
                    @Override public void onNext(Integer item) { }
                    @Override public void onError(Throwable t) { latch.countDown(); }
                    @Override public void onComplete() { latch.countDown(); }
                });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(sourceThread.get().startsWith("io-thread-"),
                "Источник должен работать в io-потоке, а не в: " + sourceThread.get());
    }

    @Test
    @Timeout(3)
    void subscribeOn_doesNotBlockCallerThread() throws InterruptedException {
        String callerThread = Thread.currentThread().getName();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> sourceThread = new AtomicReference<>();

        Observable.just(42)
                .subscribeOn(ioScheduler)
                .subscribe(new Observer<Integer>() {
                    @Override public void onNext(Integer item) {
                        sourceThread.set(Thread.currentThread().getName());
                    }
                    @Override public void onError(Throwable t) { latch.countDown(); }
                    @Override public void onComplete() { latch.countDown(); }
                });

        // Вызывающий поток не должен блокироваться
        latch.await(2, TimeUnit.SECONDS);
        assertNotEquals(callerThread, sourceThread.get(),
                "Источник не должен выполняться в вызывающем потоке при использовании subscribeOn");
    }

    // -----------------------------------------------------------------------
    // observeOn
    // -----------------------------------------------------------------------

    @Test
    @Timeout(3)
    void observeOn_observerRunsInSpecifiedScheduler() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> observerThread = new AtomicReference<>();

        Observable.just(1, 2, 3)
                .observeOn(computationScheduler)
                .subscribe(new Observer<Integer>() {
                    @Override public void onNext(Integer item) {
                        observerThread.set(Thread.currentThread().getName());
                    }
                    @Override public void onError(Throwable t) { latch.countDown(); }
                    @Override public void onComplete() { latch.countDown(); }
                });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(observerThread.get().startsWith("computation-thread-"),
                "Наблюдатель должен получать элементы в computation-потоке, а не в: " + observerThread.get());
    }

    // -----------------------------------------------------------------------
    // subscribeOn + observeOn вместе
    // -----------------------------------------------------------------------

    @Test
    @Timeout(3)
    void subscribeOnAndObserveOn_differentThreads() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> sourceThread = new AtomicReference<>();
        AtomicReference<String> observerThread = new AtomicReference<>();

        Observable.<Integer>create(emitter -> {
                    sourceThread.set(Thread.currentThread().getName());
                    emitter.onNext(100);
                    emitter.onComplete();
                })
                .subscribeOn(ioScheduler)
                .observeOn(computationScheduler)
                .subscribe(new Observer<Integer>() {
                    @Override public void onNext(Integer item) {
                        observerThread.set(Thread.currentThread().getName());
                    }
                    @Override public void onError(Throwable t) { latch.countDown(); }
                    @Override public void onComplete() { latch.countDown(); }
                });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(sourceThread.get().startsWith("io-thread-"),
                "Источник в io-потоке, а не: " + sourceThread.get());
        assertTrue(observerThread.get().startsWith("computation-thread-"),
                "Наблюдатель в computation-потоке, а не: " + observerThread.get());
    }

    // -----------------------------------------------------------------------
    // Многопоточность: корректность данных
    // -----------------------------------------------------------------------

    @Test
    @Timeout(5)
    void concurrentObservables_allItemsDelivered() throws InterruptedException {
        int streamCount = 10;
        int itemsPerStream = 100;
        List<Integer> received = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(streamCount);

        for (int s = 0; s < streamCount; s++) {
            final int base = s * itemsPerStream;
            Observable.<Integer>create(emitter -> {
                        for (int i = 0; i < itemsPerStream; i++) {
                            emitter.onNext(base + i);
                        }
                        emitter.onComplete();
                    })
                    .subscribeOn(ioScheduler)
                    .observeOn(computationScheduler)
                    .subscribe(new Observer<Integer>() {
                        @Override public void onNext(Integer item) { received.add(item); }
                        @Override public void onError(Throwable t) { latch.countDown(); }
                        @Override public void onComplete() { latch.countDown(); }
                    });
        }

        assertTrue(latch.await(4, TimeUnit.SECONDS));
        assertEquals(streamCount * itemsPerStream, received.size(),
                "Должны быть получены все элементы всех потоков");
    }

    @Test
    @Timeout(3)
    void singleScheduler_observeOn_allItemsInOrder() throws InterruptedException {
        int count = 50;
        List<Integer> received = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        Observable.<Integer>create(emitter -> {
                    for (int i = 0; i < count; i++) emitter.onNext(i);
                    emitter.onComplete();
                })
                .observeOn(singleScheduler)
                .subscribe(new Observer<Integer>() {
                    @Override public void onNext(Integer item) { received.add(item); }
                    @Override public void onError(Throwable t) { latch.countDown(); }
                    @Override public void onComplete() { latch.countDown(); }
                });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(count, received.size());
        for (int i = 0; i < count; i++) {
            assertEquals(i, received.get(i), "Порядок элементов должен сохраняться в SingleThreadScheduler");
        }
    }

    // -----------------------------------------------------------------------
    // Фабрика Schedulers
    // -----------------------------------------------------------------------

    @Test
    void schedulersFactory_createsCorrectTypes() {
        Scheduler io = Schedulers.io();
        Scheduler computation = Schedulers.computation();
        Scheduler single = Schedulers.single();

        assertInstanceOf(IOThreadScheduler.class, io);
        assertInstanceOf(ComputationScheduler.class, computation);
        assertInstanceOf(SingleThreadScheduler.class, single);

        ((IOThreadScheduler) io).shutdown();
        ((ComputationScheduler) computation).shutdown();
        ((SingleThreadScheduler) single).shutdown();
    }
}
