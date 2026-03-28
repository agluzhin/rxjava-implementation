package ru.agluzhin.rxjava;

import org.junit.jupiter.api.Test;
import ru.agluzhin.rxjava.core.Observable;
import ru.agluzhin.rxjava.core.Observer;
import ru.agluzhin.rxjava.disposable.CompositeDisposable;
import ru.agluzhin.rxjava.disposable.Disposable;
import ru.agluzhin.rxjava.disposable.SimpleDisposable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Юнит-тесты обработки ошибок и механизма Disposable.
 */
class ErrorHandlingAndDisposableTest {

    // -----------------------------------------------------------------------
    // Обработка ошибок
    // -----------------------------------------------------------------------

    @Test
    void sourceException_deliveredToOnError() {
        AtomicReference<Throwable> error = new AtomicReference<>();
        RuntimeException expected = new RuntimeException("source error");

        Observable.<String>create(emitter -> { throw expected; })
                .subscribe(new Observer<String>() {
                    @Override public void onNext(String item) { fail(); }
                    @Override public void onError(Throwable t) { error.set(t); }
                    @Override public void onComplete() { fail(); }
                });

        assertSame(expected, error.get());
    }

    @Test
    void onError_noFurtherOnNextAfterError() {
        List<Integer> received = new ArrayList<>();
        AtomicBoolean errorReceived = new AtomicBoolean(false);

        Observable.<Integer>create(emitter -> {
                    emitter.onNext(1);
                    emitter.onError(new RuntimeException("stop"));
                    emitter.onNext(2); // должно быть проигнорировано
                    emitter.onNext(3); // должно быть проигнорировано
                })
                .subscribe(new Observer<Integer>() {
                    @Override public void onNext(Integer item) { received.add(item); }
                    @Override public void onError(Throwable t) { errorReceived.set(true); }
                    @Override public void onComplete() { }
                });

        assertEquals(List.of(1), received, "После onError не должны приходить элементы");
        assertTrue(errorReceived.get());
    }

    @Test
    void onComplete_noFurtherOnNextAfterComplete() {
        List<Integer> received = new ArrayList<>();

        Observable.<Integer>create(emitter -> {
                    emitter.onNext(10);
                    emitter.onComplete();
                    emitter.onNext(20); // должно быть проигнорировано
                })
                .subscribe(new Observer<Integer>() {
                    @Override public void onNext(Integer item) { received.add(item); }
                    @Override public void onError(Throwable t) { fail(); }
                    @Override public void onComplete() { }
                });

        assertEquals(List.of(10), received);
    }

    @Test
    void mapException_doesNotCallOnComplete() {
        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicReference<Throwable> error = new AtomicReference<>();

        Observable.just(1)
                .map(n -> { throw new RuntimeException("map error"); })
                .subscribe(new Observer<Object>() {
                    @Override public void onNext(Object item) { }
                    @Override public void onError(Throwable t) { error.set(t); }
                    @Override public void onComplete() { completed.set(true); }
                });

        assertNotNull(error.get(), "Ошибка в map должна попасть в onError");
        assertFalse(completed.get(), "onComplete не должен вызываться после ошибки");
    }

    @Test
    void errorObservable_neverCallsOnNext() {
        List<Object> items = new ArrayList<>();
        AtomicBoolean errorCalled = new AtomicBoolean(false);

        Observable.error(new IllegalArgumentException("bad"))
                .subscribe(new Observer<Object>() {
                    @Override public void onNext(Object item) { items.add(item); }
                    @Override public void onError(Throwable t) { errorCalled.set(true); }
                    @Override public void onComplete() { fail(); }
                });

        assertTrue(items.isEmpty());
        assertTrue(errorCalled.get());
    }

    // -----------------------------------------------------------------------
    // SimpleDisposable
    // -----------------------------------------------------------------------

    @Test
    void simpleDisposable_initiallyNotDisposed() {
        SimpleDisposable d = new SimpleDisposable();
        assertFalse(d.isDisposed());
    }

    @Test
    void simpleDisposable_afterDispose_isDisposed() {
        SimpleDisposable d = new SimpleDisposable();
        d.dispose();
        assertTrue(d.isDisposed());
    }

    @Test
    void simpleDisposable_idempotent() {
        SimpleDisposable d = new SimpleDisposable();
        d.dispose();
        d.dispose(); // повторный вызов не должен выбрасывать исключение
        assertTrue(d.isDisposed());
    }

    // -----------------------------------------------------------------------
    // CompositeDisposable
    // -----------------------------------------------------------------------

    @Test
    void compositeDisposable_disposesAllChildren() {
        CompositeDisposable composite = new CompositeDisposable();
        SimpleDisposable d1 = new SimpleDisposable();
        SimpleDisposable d2 = new SimpleDisposable();
        SimpleDisposable d3 = new SimpleDisposable();

        composite.add(d1);
        composite.add(d2);
        composite.add(d3);

        composite.dispose();

        assertTrue(composite.isDisposed());
        assertTrue(d1.isDisposed());
        assertTrue(d2.isDisposed());
        assertTrue(d3.isDisposed());
    }

    @Test
    void compositeDisposable_addAfterDispose_immediatelyDisposes() {
        CompositeDisposable composite = new CompositeDisposable();
        composite.dispose();

        SimpleDisposable late = new SimpleDisposable();
        composite.add(late);

        assertTrue(late.isDisposed(),
                "Подписка, добавленная после dispose(), должна немедленно отменяться");
    }

    @Test
    void subscribe_returnsDisposable_thatCanBeDisposed() {
        // Синхронный just() завершается до возврата из subscribe(),
        // поэтому SafeEmitter уже вызвал dispose() внутри onComplete().
        // Проверяем: Disposable валиден и повторный dispose() не бросает исключений.
        Disposable d = Observable.just(1, 2, 3).subscribe(item -> { });
        assertNotNull(d);
        assertTrue(d.isDisposed(), "После синхронного завершения потока Disposable должен быть отменён");
        assertDoesNotThrow(d::dispose, "Повторный вызов dispose() не должен бросать исключение");
    }
}
