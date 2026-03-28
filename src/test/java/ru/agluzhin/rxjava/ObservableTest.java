package ru.agluzhin.rxjava;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import ru.agluzhin.rxjava.schedulers.IOThreadScheduler;
import ru.agluzhin.rxjava.core.Observable;
import ru.agluzhin.rxjava.core.Observer;
import ru.agluzhin.rxjava.disposable.Disposable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Юнит-тесты базовых компонентов: {@code Observable}, {@code Observer},
 * фабричных методов и корректности поведения терминальных сигналов.
 */
class ObservableTest {

    // -----------------------------------------------------------------------
    // Фабричные методы
    // -----------------------------------------------------------------------

    @Test
    void just_emitsAllItemsAndCompletes() throws InterruptedException {
        List<Integer> received = new ArrayList<>();
        AtomicBoolean completed = new AtomicBoolean(false);

        Observable.just(1, 2, 3)
                .subscribe(new Observer<Integer>() {
                    @Override public void onNext(Integer item) { received.add(item); }
                    @Override public void onError(Throwable t) { fail("Неожиданная ошибка: " + t); }
                    @Override public void onComplete() { completed.set(true); }
                });

        assertEquals(List.of(1, 2, 3), received, "just() должен испустить все элементы по порядку");
        assertTrue(completed.get(), "onComplete() должен быть вызван");
    }

    @Test
    void fromIterable_emitsAllItems() {
        List<String> source = List.of("a", "b", "c");
        List<String> result = Observable.fromIterable(source).blockingToListSafe();
        assertEquals(source, result);
    }

    @Test
    void empty_callsOnCompleteImmediately() {
        AtomicBoolean completed = new AtomicBoolean(false);
        List<Object> items = new ArrayList<>();

        Observable.empty().subscribe(new Observer<Object>() {
            @Override public void onNext(Object item) { items.add(item); }
            @Override public void onError(Throwable t) { fail(); }
            @Override public void onComplete() { completed.set(true); }
        });

        assertTrue(items.isEmpty(), "empty() не должен испускать элементы");
        assertTrue(completed.get(), "empty() должен вызвать onComplete()");
    }

    @Test
    void error_callsOnError() {
        AtomicReference<Throwable> caught = new AtomicReference<>();
        RuntimeException expected = new RuntimeException("test error");

        Observable.error(expected).subscribe(new Observer<Object>() {
            @Override public void onNext(Object item) { fail("Элементы не ожидались"); }
            @Override public void onError(Throwable t) { caught.set(t); }
            @Override public void onComplete() { fail("onComplete не ожидался"); }
        });

        assertSame(expected, caught.get(), "onError должен получить исходное исключение");
    }

    // -----------------------------------------------------------------------
    // Поведение SafeEmitter
    // -----------------------------------------------------------------------

    @Test
    void onComplete_canBeCalledOnlyOnce() {
        List<String> events = new ArrayList<>();

        Observable.<String>create(emitter -> {
                    emitter.onComplete();
                    emitter.onComplete(); // второй вызов должен быть проигнорирован
                    emitter.onNext("не должно прийти");
                })
                .subscribe(new Observer<String>() {
                    @Override public void onNext(String item) { events.add("next:" + item); }
                    @Override public void onError(Throwable t) { events.add("error"); }
                    @Override public void onComplete() { events.add("complete"); }
                });

        assertEquals(List.of("complete"), events, "onComplete должен быть обработан ровно один раз");
    }

    @Test
    void onError_preventsSubsequentOnNext() {
        List<Object> events = new ArrayList<>();

        Observable.<Integer>create(emitter -> {
                    emitter.onError(new RuntimeException("err"));
                    emitter.onNext(99); // должно игнорироваться
                })
                .subscribe(new Observer<Integer>() {
                    @Override public void onNext(Integer item) { events.add("next:" + item); }
                    @Override public void onError(Throwable t) { events.add("error"); }
                    @Override public void onComplete() { events.add("complete"); }
                });

        assertEquals(List.of("error"), events);
    }

    // -----------------------------------------------------------------------
    // Disposable
    // -----------------------------------------------------------------------

    @Test
    @Timeout(3)
    void dispose_stopsElementDelivery() throws InterruptedException {
        List<Integer> received = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch started = new CountDownLatch(1);

        Observable<Integer> infinite = Observable.create(emitter -> {
            int i = 0;
            started.countDown();
            while (!emitter.isDisposed()) {
                emitter.onNext(i++);
                Thread.sleep(50);
            }
        });

        Disposable disposable = infinite
                .subscribeOn(new IOThreadScheduler())
                .subscribe(received::add);

        started.await();
        Thread.sleep(200);
        disposable.dispose();
        int countAfterDispose = received.size();
        Thread.sleep(200);

        assertTrue(disposable.isDisposed(), "Подписка должна быть помечена как отменённая");
        assertEquals(countAfterDispose, received.size(),
                "После dispose() новые элементы не должны поступать");
    }
}
