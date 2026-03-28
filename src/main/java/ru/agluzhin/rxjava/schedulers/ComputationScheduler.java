package ru.agluzhin.rxjava.schedulers;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Планировщик для вычислительных задач.
 *
 * <p>Использует {@code FixedThreadPool} с размером, равным количеству доступных
 * процессорных ядер. Оптимален для CPU-интенсивных операций: обработки данных,
 * математических расчётов, преобразований без блокировок.</p>
 *
 * <p>Аналог {@code Schedulers.computation()} в оригинальной библиотеке RxJava.</p>
 */
public class ComputationScheduler implements Scheduler {

    private final ExecutorService executor;
    private final int threadCount;

    public ComputationScheduler() {
        this.threadCount = Runtime.getRuntime().availableProcessors();
        this.executor = Executors.newFixedThreadPool(threadCount, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("computation-thread-" + thread.getId());
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

    /**
     * Возвращает количество потоков в пуле (равно числу доступных ядер CPU).
     *
     * @return размер пула потоков
     */
    public int getThreadCount() {
        return threadCount;
    }
}
