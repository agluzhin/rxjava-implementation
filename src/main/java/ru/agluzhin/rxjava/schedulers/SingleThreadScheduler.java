package ru.agluzhin.rxjava.schedulers;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Планировщик с единственным фоновым потоком.
 *
 * <p>Все задачи выполняются последовательно в одном выделенном потоке.
 * Гарантирует порядок выполнения задач и отсутствие гонок данных между
 * последовательными операциями.</p>
 *
 * <p>Аналог {@code Schedulers.single()} в оригинальной библиотеке RxJava.</p>
 */
public class SingleThreadScheduler implements Scheduler {

    private final ExecutorService executor;

    public SingleThreadScheduler() {
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("single-thread");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public void execute(Runnable task) {
        executor.execute(task);
    }

    @Override
    public void shutdown() {
        executor.shutdown();
    }
}
