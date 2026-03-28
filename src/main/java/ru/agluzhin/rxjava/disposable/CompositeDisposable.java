package ru.agluzhin.rxjava.disposable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Реализация {@link Disposable} — атомарный флаг отмены подписки.
 */
public class CompositeDisposable implements Disposable {

    private final AtomicBoolean disposed = new AtomicBoolean(false);
    private final List<Disposable> disposables = new ArrayList<>();

    @Override
    public void dispose() {
        if (disposed.compareAndSet(false, true)) {
            synchronized (disposables) {
                disposables.forEach(Disposable::dispose);
                disposables.clear();
            }
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed.get();
    }

    /**
     * Добавляет дочернюю подписку для групповой отмены.
     *
     * @param disposable подписка для добавления
     */
    public void add(Disposable disposable) {
        if (disposed.get()) {
            disposable.dispose();
            return;
        }
        synchronized (disposables) {
            disposables.add(disposable);
        }
    }
}
