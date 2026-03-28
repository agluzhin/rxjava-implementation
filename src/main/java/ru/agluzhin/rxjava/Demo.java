package ru.agluzhin.rxjava;

import ru.agluzhin.rxjava.core.Observable;
import ru.agluzhin.rxjava.core.Observer;
import ru.agluzhin.rxjava.disposable.Disposable;
import ru.agluzhin.rxjava.schedulers.Scheduler;
import ru.agluzhin.rxjava.schedulers.Schedulers;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Демонстрационный класс, показывающий основные возможности реализованной
 * библиотеки реактивных потоков.
 */
public class Demo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Демонстрация кастомной RxJava библиотеки ===\n");

        demo1_basicObservable();
        demo2_mapFilter();
        demo3_flatMap();
        demo4_schedulers();
        demo5_errorHandling();
        demo6_disposable();

        System.out.println("\n=== Демонстрация завершена ===");
    }

    // ------------------------------------------------------------------

    private static void demo1_basicObservable() throws InterruptedException {
        System.out.println("--- 1. Базовый Observable ---");
        Observable.just(1, 2, 3, 4, 5)
                .subscribe(new Observer<Integer>() {
                    @Override public void onNext(Integer item) {
                        System.out.println("  onNext: " + item);
                    }
                    @Override public void onError(Throwable t) {
                        System.err.println("  onError: " + t.getMessage());
                    }
                    @Override public void onComplete() {
                        System.out.println("  onComplete");
                    }
                });
    }

    private static void demo2_mapFilter() {
        System.out.println("\n--- 2. Операторы map и filter ---");
        List<String> result = Observable.just(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
                .filter(n -> n % 2 == 0)          // оставляем чётные
                .map(n -> "Число: " + (n * n))     // возводим в квадрат
                .blockingToListSafe();

        result.forEach(s -> System.out.println("  " + s));
    }

    private static void demo3_flatMap() {
        System.out.println("\n--- 3. Оператор flatMap ---");
        Observable.just("A", "B", "C")
                .flatMap(letter -> Observable.just(letter + "1", letter + "2"))
                .subscribe(item -> System.out.println("  " + item));
    }

    private static void demo4_schedulers() throws InterruptedException {
        System.out.println("\n--- 4. Schedulers: subscribeOn + observeOn ---");
        CountDownLatch latch = new CountDownLatch(1);
        Scheduler io = Schedulers.io();
        Scheduler computation = Schedulers.computation();

        Observable.<Integer>create(emitter -> {
                    System.out.println("  Источник в потоке: " + Thread.currentThread().getName());
                    emitter.onNext(42);
                    emitter.onComplete();
                })
                .subscribeOn(io)
                .map(n -> {
                    System.out.println("  map в потоке: " + Thread.currentThread().getName());
                    return n * 2;
                })
                .observeOn(computation)
                .subscribe(new Observer<Integer>() {
                    @Override public void onNext(Integer item) {
                        System.out.println("  Результат " + item + " в потоке: " + Thread.currentThread().getName());
                    }
                    @Override public void onError(Throwable t) { latch.countDown(); }
                    @Override public void onComplete() { latch.countDown(); }
                });

        latch.await(3, TimeUnit.SECONDS);
        io.shutdown();
        computation.shutdown();
    }

    private static void demo5_errorHandling() {
        System.out.println("\n--- 5. Обработка ошибок ---");
        Observable.<Integer>create(emitter -> {
                    emitter.onNext(10);
                    emitter.onNext(0);
                    emitter.onNext(5);
                })
                .map(n -> {
                    if (n == 0) throw new ArithmeticException("Деление на ноль!");
                    return 100 / n;
                })
                .subscribe(new Observer<Integer>() {
                    @Override public void onNext(Integer item) {
                        System.out.println("  Результат: " + item);
                    }
                    @Override public void onError(Throwable t) {
                        System.out.println("  Ошибка перехвачена: " + t.getMessage());
                    }
                    @Override public void onComplete() {
                        System.out.println("  onComplete");
                    }
                });
    }

    private static void demo6_disposable() {
        System.out.println("\n--- 6. Disposable — отмена подписки ---");
        Observable<Integer> infinite = Observable.create(emitter -> {
            int i = 0;
            while (!emitter.isDisposed()) {
                emitter.onNext(i++);
                try { Thread.sleep(100); } catch (InterruptedException ignored) { }
            }
        });

        Disposable disposable = infinite.subscribeOn(Schedulers.io())
                .subscribe(n -> System.out.println("  Получено: " + n));

        try { Thread.sleep(350); } catch (InterruptedException ignored) { }
        disposable.dispose();
        System.out.println("  Подписка отменена (isDisposed=" + disposable.isDisposed() + ")");
    }
}
