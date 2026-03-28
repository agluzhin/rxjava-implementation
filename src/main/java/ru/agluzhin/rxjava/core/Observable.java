package ru.agluzhin.rxjava.core;

import ru.agluzhin.rxjava.disposable.Disposable;
import ru.agluzhin.rxjava.disposable.SimpleDisposable;
import ru.agluzhin.rxjava.schedulers.Scheduler;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Центральный класс реактивной библиотеки — представляет поток данных.
 *
 * <p>{@code Observable} является «холодным» источником: он начинает генерировать
 * элементы только при наличии подписчика. Каждая подписка порождает
 * независимое выполнение источника.</p>
 *
 * <p>Поддерживаемые операторы:</p>
 * <ul>
 *   <li>{@link #map(Function)} — преобразование элементов</li>
 *   <li>{@link #filter(Predicate)} — фильтрация элементов</li>
 *   <li>{@link #flatMap(Function)} — «разворачивание» вложенных потоков</li>
 *   <li>{@link #subscribeOn(Scheduler)} — поток выполнения источника</li>
 *   <li>{@link #observeOn(Scheduler)} — поток обработки элементов наблюдателем</li>
 * </ul>
 *
 * @param <T> тип элементов потока
 */
public class Observable<T> {

    /** Логика источника данных, выполняемая при каждой подписке. */
    private final ObservableOnSubscribe<T> source;

    private Observable(ObservableOnSubscribe<T> source) {
        this.source = source;
    }

    // -------------------------------------------------------------------------
    // Фабричные методы
    // -------------------------------------------------------------------------

    /**
     * Создаёт {@code Observable} из произвольной логики источника.
     *
     * @param source источник данных
     * @param <T>    тип элементов
     * @return новый {@code Observable}
     */
    public static <T> Observable<T> create(ObservableOnSubscribe<T> source) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        return new Observable<>(source);
    }

    /**
     * Создаёт {@code Observable}, синхронно испускающий заданные элементы.
     *
     * @param items элементы для испускания
     * @param <T>   тип элементов
     * @return новый {@code Observable}
     */
    @SafeVarargs
    public static <T> Observable<T> just(T... items) {
        return create(emitter -> {
            for (T item : items) {
                if (emitter.isDisposed()) return;
                emitter.onNext(item);
            }
            emitter.onComplete();
        });
    }

    /**
     * Создаёт {@code Observable}, испускающий элементы из {@link Iterable}.
     *
     * @param items коллекция элементов
     * @param <T>   тип элементов
     * @return новый {@code Observable}
     */
    public static <T> Observable<T> fromIterable(Iterable<T> items) {
        return create(emitter -> {
            for (T item : items) {
                if (emitter.isDisposed()) return;
                emitter.onNext(item);
            }
            emitter.onComplete();
        });
    }

    /**
     * Создаёт пустой {@code Observable}, немедленно вызывающий {@code onComplete}.
     *
     * @param <T> тип элементов
     * @return пустой {@code Observable}
     */
    public static <T> Observable<T> empty() {
        return create(Emitter::onComplete);
    }

    /**
     * Создаёт {@code Observable}, немедленно завершающийся с ошибкой.
     *
     * @param error ошибка для передачи
     * @param <T>   тип элементов
     * @return {@code Observable}, всегда завершающийся ошибкой
     */
    public static <T> Observable<T> error(Throwable error) {
        return create(emitter -> emitter.onError(error));
    }

    // -------------------------------------------------------------------------
    // Подписка
    // -------------------------------------------------------------------------

    /**
     * Подписывает наблюдателя на данный поток.
     *
     * @param observer наблюдатель
     * @return {@link Disposable} для отмены подписки
     */
    public Disposable subscribe(Observer<T> observer) {
        SimpleDisposable disposable = new SimpleDisposable();
        Emitter<T> emitter = new SafeEmitter<>(observer, disposable);
        try {
            source.subscribe(emitter);
        } catch (Exception e) {
            if (!disposable.isDisposed()) {
                observer.onError(e);
            }
        }
        return disposable;
    }

    /**
     * Подписывается только на элементы (ошибки игнорируются, завершение — тоже).
     *
     * @param onNext обработчик элементов
     * @return {@link Disposable} для отмены подписки
     */
    public Disposable subscribe(java.util.function.Consumer<T> onNext) {
        return subscribe(new Observer<T>() {
            @Override public void onNext(T item) { onNext.accept(item); }
            @Override public void onError(Throwable t) { /* не обрабатывается */ }
            @Override public void onComplete() { /* не обрабатывается */ }
        });
    }

    // -------------------------------------------------------------------------
    // Операторы преобразования
    // -------------------------------------------------------------------------

    /**
     * Преобразует каждый элемент потока с помощью заданной функции.
     *
     * @param mapper функция преобразования
     * @param <R>    тип результирующих элементов
     * @return новый {@code Observable} с преобразованными элементами
     */
    public <R> Observable<R> map(Function<T, R> mapper) {
        return create(emitter -> subscribe(new Observer<T>() {
            @Override
            public void onNext(T item) {
                try {
                    emitter.onNext(mapper.apply(item));
                } catch (Exception e) {
                    emitter.onError(e);
                }
            }

            @Override
            public void onError(Throwable t) {
                emitter.onError(t);
            }

            @Override
            public void onComplete() {
                emitter.onComplete();
            }
        }));
    }

    /**
     * Пропускает только те элементы, для которых предикат возвращает {@code true}.
     *
     * @param predicate условие фильтрации
     * @return новый {@code Observable} с отфильтрованными элементами
     */
    public Observable<T> filter(Predicate<T> predicate) {
        return create(emitter -> subscribe(new Observer<T>() {
            @Override
            public void onNext(T item) {
                try {
                    if (predicate.test(item)) {
                        emitter.onNext(item);
                    }
                } catch (Exception e) {
                    emitter.onError(e);
                }
            }

            @Override
            public void onError(Throwable t) {
                emitter.onError(t);
            }

            @Override
            public void onComplete() {
                emitter.onComplete();
            }
        }));
    }

    /**
     * Преобразует каждый элемент в {@code Observable} и объединяет результаты.
     *
     * <p>Для каждого элемента вызывается {@code mapper}, возвращающий вложенный
     * {@code Observable}. Элементы всех вложенных потоков объединяются в единый
     * выходной поток. Завершение основного потока ожидает завершения всех
     * вложенных потоков.</p>
     *
     * @param mapper функция, возвращающая {@code Observable} для каждого элемента
     * @param <R>    тип элементов вложенных потоков
     * @return объединённый {@code Observable}
     */
    public <R> Observable<R> flatMap(Function<T, Observable<R>> mapper) {
        return create(emitter -> {
            AtomicBoolean sourceCompleted = new AtomicBoolean(false);
            AtomicBoolean hasError = new AtomicBoolean(false);
            java.util.concurrent.atomic.AtomicInteger activeCount = new java.util.concurrent.atomic.AtomicInteger(0);
            Object lock = new Object();

            subscribe(new Observer<T>() {
                @Override
                public void onNext(T item) {
                    if (emitter.isDisposed() || hasError.get()) return;
                    Observable<R> inner;
                    try {
                        inner = mapper.apply(item);
                    } catch (Exception e) {
                        emitter.onError(e);
                        hasError.set(true);
                        return;
                    }
                    activeCount.incrementAndGet();
                    inner.subscribe(new Observer<R>() {
                        @Override
                        public void onNext(R r) {
                            synchronized (lock) {
                                if (!emitter.isDisposed()) emitter.onNext(r);
                            }
                        }

                        @Override
                        public void onError(Throwable t) {
                            hasError.set(true);
                            synchronized (lock) {
                                emitter.onError(t);
                            }
                        }

                        @Override
                        public void onComplete() {
                            int remaining = activeCount.decrementAndGet();
                            if (remaining == 0 && sourceCompleted.get() && !hasError.get()) {
                                synchronized (lock) {
                                    emitter.onComplete();
                                }
                            }
                        }
                    });
                }

                @Override
                public void onError(Throwable t) {
                    hasError.set(true);
                    emitter.onError(t);
                }

                @Override
                public void onComplete() {
                    sourceCompleted.set(true);
                    if (activeCount.get() == 0 && !hasError.get()) {
                        emitter.onComplete();
                    }
                }
            });
        });
    }

    // -------------------------------------------------------------------------
    // Управление потоками выполнения
    // -------------------------------------------------------------------------

    /**
     * Указывает планировщик, в котором будет выполняться логика источника данных.
     *
     * <p>Применяется один раз к самому верхнему Observable в цепочке.
     * Повторные вызовы {@code subscribeOn} на промежуточных операторах
     * игнорируются — действует только первый «снизу вверх».</p>
     *
     * @param scheduler планировщик выполнения подписки
     * @return новый {@code Observable}, чья логика выполняется в заданном планировщике
     */
    public Observable<T> subscribeOn(Scheduler scheduler) {
        return create(emitter -> scheduler.execute(() -> {
            try {
                source.subscribe(emitter);
            } catch (Exception e) {
                emitter.onError(e);
            }
        }));
    }

    /**
     * Указывает планировщик, в котором будут обрабатываться элементы наблюдателем.
     *
     * <p>Все вызовы {@code onNext}, {@code onError}, {@code onComplete}
     * будут перенаправлены в указанный планировщик. В отличие от {@code subscribeOn},
     * каждый {@code observeOn} в цепочке задаёт отдельный переключатель потока.</p>
     *
     * @param scheduler планировщик обработки событий
     * @return новый {@code Observable}, передающий события через заданный планировщик
     */
    public Observable<T> observeOn(Scheduler scheduler) {
        return create(emitter -> subscribe(new Observer<T>() {
            @Override
            public void onNext(T item) {
                scheduler.execute(() -> {
                    if (!emitter.isDisposed()) emitter.onNext(item);
                });
            }

            @Override
            public void onError(Throwable t) {
                scheduler.execute(() -> emitter.onError(t));
            }

            @Override
            public void onComplete() {
                scheduler.execute(emitter::onComplete);
            }
        }));
    }

    // -------------------------------------------------------------------------
    // Утилитные методы
    // -------------------------------------------------------------------------

    /**
     * Блокирует текущий поток до завершения Observable и возвращает все элементы.
     *
     * <p>Используется в тестах и демонстрационном коде для упрощённого
     * получения результатов из асинхронных потоков.</p>
     *
     * @return список полученных элементов
     * @throws InterruptedException если поток прерван во время ожидания
     */
    public java.util.List<T> blockingToList() throws InterruptedException {
        java.util.List<T> result = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean[] errorFlag = { new AtomicBoolean(false) };
        Throwable[] errorRef = { null };

        subscribe(new Observer<T>() {
            @Override public void onNext(T item) { result.add(item); }
            @Override public void onError(Throwable t) {
                errorRef[0] = t;
                errorFlag[0].set(true);
                latch.countDown();
            }
            @Override public void onComplete() { latch.countDown(); }
        });

        latch.await();
        if (errorFlag[0].get()) {
            throw new RuntimeException("Observable terminated with error", errorRef[0]);
        }
        return result;
    }

    /**
     * То же, что {@link #blockingToList()}, но выбрасывает {@link RuntimeException}
     * вместо проверяемого {@link InterruptedException}.
     *
     * @return список полученных элементов
     */
    public java.util.List<T> blockingToListSafe() {
        try {
            return blockingToList();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for Observable", e);
        }
    }

    // -------------------------------------------------------------------------
    // Внутренний класс SafeEmitter
    // -------------------------------------------------------------------------

    /**
     * Безопасная обёртка над {@link Observer}, предотвращающая вызов методов
     * после завершения потока или отмены подписки.
     */
    private static class SafeEmitter<T> implements Emitter<T> {
        private final Observer<T> observer;
        private final SimpleDisposable disposable;
        private final AtomicBoolean terminated = new AtomicBoolean(false);

        SafeEmitter(Observer<T> observer, SimpleDisposable disposable) {
            this.observer = observer;
            this.disposable = disposable;
        }

        @Override
        public void onNext(T item) {
            if (!terminated.get() && !disposable.isDisposed()) {
                observer.onNext(item);
            }
        }

        @Override
        public void onError(Throwable t) {
            if (terminated.compareAndSet(false, true)) {
                disposable.dispose();
                observer.onError(t);
            }
        }

        @Override
        public void onComplete() {
            if (terminated.compareAndSet(false, true)) {
                disposable.dispose();
                observer.onComplete();
            }
        }

        @Override
        public boolean isDisposed() {
            return disposable.isDisposed();
        }
    }
}
