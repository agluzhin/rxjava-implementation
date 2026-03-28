package ru.agluzhin.rxjava.core;

/**
 * Функциональный интерфейс, описывающий логику источника данных.
 *
 * <p>Реализация этого интерфейса определяет, какие элементы будут
 * отправлены подписчику при вызове {@link Observable#subscribe}.</p>
 *
 * @param <T> тип элементов потока
 */
@FunctionalInterface
public interface ObservableOnSubscribe<T> {

    /**
     * Вызывается при подписке нового наблюдателя.
     *
     * @param emitter объект для отправки элементов наблюдателю
     * @throws Exception при возникновении ошибки в логике источника
     */
    void subscribe(Emitter<T> emitter) throws Exception;
}
