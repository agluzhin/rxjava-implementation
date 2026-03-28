package ru.agluzhin.rxjava.schedulers;

/**
 * Фабричный класс для получения стандартных планировщиков.
 *
 * <p>Предоставляет статические методы-фабрики по аналогии с классом
 * {@code Schedulers} в оригинальной RxJava.</p>
 */
public final class Schedulers {

    private Schedulers() {
        // Утилитный класс — создание объектов не предусмотрено
    }

    /**
     * Создаёт новый {@link IOThreadScheduler}.
     *
     * @return планировщик для I/O-задач
     */
    public static Scheduler io() {
        return new IOThreadScheduler();
    }

    /**
     * Создаёт новый {@link ComputationScheduler}.
     *
     * @return планировщик для вычислительных задач
     */
    public static Scheduler computation() {
        return new ComputationScheduler();
    }

    /**
     * Создаёт новый {@link SingleThreadScheduler}.
     *
     * @return однопоточный планировщик
     */
    public static Scheduler single() {
        return new SingleThreadScheduler();
    }
}
