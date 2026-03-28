package ru.agluzhin.rxjava;

import org.junit.jupiter.api.Test;
import ru.agluzhin.rxjava.core.Observable;
import ru.agluzhin.rxjava.core.Observer;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Юнит-тесты операторов преобразования: map, filter, flatMap.
 */
class OperatorsTest {

    // -----------------------------------------------------------------------
    // map
    // -----------------------------------------------------------------------

    @Test
    void map_transformsEachElement() {
        List<String> result = Observable.just(1, 2, 3)
                .map(n -> "item-" + n)
                .blockingToListSafe();

        assertEquals(List.of("item-1", "item-2", "item-3"), result);
    }

    @Test
    void map_propagatesExceptionAsOnError() {
        AtomicReference<Throwable> error = new AtomicReference<>();

        Observable.just(1, 0, 2)
                .map(n -> 10 / n)
                .subscribe(new Observer<Integer>() {
                    @Override public void onNext(Integer item) { /* ignore */ }
                    @Override public void onError(Throwable t) { error.set(t); }
                    @Override public void onComplete() { }
                });

        assertNotNull(error.get(), "Ошибка в mapper должна попасть в onError");
        assertInstanceOf(ArithmeticException.class, error.get());
    }

    @Test
    void map_chainsCorrectly() {
        List<Integer> result = Observable.just(1, 2, 3)
                .map(n -> n * 2)
                .map(n -> n + 1)
                .blockingToListSafe();

        assertEquals(List.of(3, 5, 7), result);
    }

    @Test
    void map_emptySourceProducesEmptyResult() {
        List<String> result = Observable.<Integer>empty()
                .map(n -> "x" + n)
                .blockingToListSafe();

        assertTrue(result.isEmpty());
    }

    // -----------------------------------------------------------------------
    // filter
    // -----------------------------------------------------------------------

    @Test
    void filter_keepsMatchingElements() {
        List<Integer> result = Observable.just(1, 2, 3, 4, 5, 6)
                .filter(n -> n % 2 == 0)
                .blockingToListSafe();

        assertEquals(List.of(2, 4, 6), result);
    }

    @Test
    void filter_rejectsAllElements() {
        List<Integer> result = Observable.just(1, 3, 5)
                .filter(n -> n % 2 == 0)
                .blockingToListSafe();

        assertTrue(result.isEmpty(), "Нечётные числа должны быть отфильтрованы полностью");
    }

    @Test
    void filter_propagatesExceptionAsOnError() {
        AtomicReference<Throwable> error = new AtomicReference<>();

        Observable.just("hello", null, "world")
                .filter(s -> s.length() > 3)   // NPE на null
                .subscribe(new Observer<String>() {
                    @Override public void onNext(String item) { }
                    @Override public void onError(Throwable t) { error.set(t); }
                    @Override public void onComplete() { }
                });

        assertNotNull(error.get());
        assertInstanceOf(NullPointerException.class, error.get());
    }

    @Test
    void filter_chainedWithMap() {
        List<String> result = Observable.just(1, 2, 3, 4, 5)
                .filter(n -> n > 2)
                .map(n -> ">" + n)
                .blockingToListSafe();

        assertEquals(List.of(">3", ">4", ">5"), result);
    }

    // -----------------------------------------------------------------------
    // flatMap
    // -----------------------------------------------------------------------

    @Test
    void flatMap_expandsEachElement() {
        List<Integer> result = Observable.just(1, 2, 3)
                .flatMap(n -> Observable.just(n, n * 10))
                .blockingToListSafe();

        // Порядок может варьироваться, проверяем содержимое
        assertEquals(6, result.size());
        assertTrue(result.containsAll(List.of(1, 10, 2, 20, 3, 30)));
    }

    @Test
    void flatMap_emptyInnerObservable() {
        List<Integer> result = Observable.just(1, 2, 3)
                .flatMap(n -> Observable.<Integer>empty())
                .blockingToListSafe();

        assertTrue(result.isEmpty(), "flatMap с пустым внутренним Observable даёт пустой результат");
    }

    @Test
    void flatMap_propagatesInnerError() {
        AtomicReference<Throwable> error = new AtomicReference<>();

        Observable.just(1, 2, 3)
                .flatMap(n -> n == 2
                        ? Observable.error(new IllegalStateException("inner error"))
                        : Observable.just(n))
                .subscribe(new Observer<Integer>() {
                    @Override public void onNext(Integer item) { }
                    @Override public void onError(Throwable t) { error.set(t); }
                    @Override public void onComplete() { }
                });

        assertNotNull(error.get());
        assertInstanceOf(IllegalStateException.class, error.get());
    }

    @Test
    void flatMap_propagatesMapperException() {
        AtomicReference<Throwable> error = new AtomicReference<>();

        Observable.just("ok", null)
                .<String>flatMap(s -> Observable.just(s.toUpperCase()))  // NPE на null
                .subscribe(new Observer<String>() {
                    @Override public void onNext(String item) { }
                    @Override public void onError(Throwable t) { error.set(t); }
                    @Override public void onComplete() { }
                });

        assertNotNull(error.get());
    }

    @Test
    void flatMap_chainsWithFilter() {
        List<Integer> result = Observable.just(1, 2, 3, 4)
                .filter(n -> n % 2 == 0)
                .flatMap(n -> Observable.just(n, n * n))
                .blockingToListSafe();

        assertEquals(4, result.size());
        assertTrue(result.containsAll(List.of(2, 4, 4, 16)));
    }
}
