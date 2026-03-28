package ru.agluzhin.rxjava.disposable;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Простая реализация {@link Disposable} на основе атомарного флага.
 */
public class SimpleDisposable implements Disposable {

    private final AtomicBoolean disposed = new AtomicBoolean(false);

    @Override
    public void dispose() {
        disposed.set(true);
    }

    @Override
    public boolean isDisposed() {
        return disposed.get();
    }
}
