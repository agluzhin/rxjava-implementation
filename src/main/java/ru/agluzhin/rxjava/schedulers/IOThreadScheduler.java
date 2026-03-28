package ru.agluzhin.rxjava.schedulers;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Планировщик для I/O-операций.
 *
 * <p>Использует {@code CachedThreadPool} — пул, который создаёт новые потоки
 * по требованию и переиспользует ранее созданные. Оптимален для задач,
 * большую часть времени ожидающих завершения ввода-вывода (сеть, файлы, БД).</p>
 *
 * <p>Аналог {@code Schedulers.io()} в оригинальной библиотеке RxJava.</p>
 */
public class IOThreadScheduler implements Scheduler {

    private final ExecutorService executor;

    public IOThreadScheduler() {
        this.executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("io-thread-" + thread.getId());
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
