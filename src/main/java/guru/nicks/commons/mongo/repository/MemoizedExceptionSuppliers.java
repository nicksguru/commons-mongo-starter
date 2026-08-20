package guru.nicks.commons.mongo.repository;

import guru.nicks.commons.utils.ReflectionUtils;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.experimental.UtilityClass;
import org.springframework.beans.BeanInstantiationException;

import java.util.function.Supplier;

/**
 * Memoizes exception construction paths for {@link EnhancedMongoRepository#getById(Object)} misses. Only the
 * construction path is cached - never an exception instance - so every miss still yields a fresh exception while the
 * instantiation logic is resolved just once per exception class. Preserves the semantics of
 * {@link ReflectionUtils#instantiateEvenWithoutDefaultConstructor(Class)} (default constructor first, then an Objenesis
 * fallback).
 */
@UtilityClass
public class MemoizedExceptionSuppliers {

    /**
     * @see #getSupplierFor(Class)
     */
    private static final Cache<
            // key bound must mirror 'E extends RuntimeException'
            // so that computeIfAbsent() inference accepts createSupplier()
            Class<? extends RuntimeException>,
            Supplier<? extends RuntimeException>> SUPPLIERS = Caffeine.newBuilder().build();

    /**
     * Returns a memoized supplier creating fresh instances of the given exception class.
     *
     * @param exceptionClass exception class to construct instances of
     * @param <E>            exception type
     * @return supplier producing a new exception instance on each call
     */
    @SuppressWarnings("unchecked")
    public static <E extends RuntimeException> Supplier<E> getSupplierFor(Class<E> exceptionClass) {
        return (Supplier<E>) SUPPLIERS.get(exceptionClass, MemoizedExceptionSuppliers::createSupplierFor);
    }

    /**
     * Builds the actual construction path, wrapping reflective failures in {@link IllegalStateException}.
     *
     * @param exceptionClass exception class to construct instances of
     * @param <E>            exception type
     * @return supplier producing a new exception instance on each call
     */
    private static <E extends RuntimeException> Supplier<E> createSupplierFor(Class<E> exceptionClass) {
        return () -> {
            try {
                // same instantiation semantics as before memoization: default constructor, then Objenesis fallback
                return ReflectionUtils.instantiateEvenWithoutDefaultConstructor(exceptionClass);
            } catch (BeanInstantiationException e) {
                throw new IllegalStateException("Can't instantiate exception of class [" + exceptionClass.getName()
                        + "]: " + e.getMessage(), e);
            }
        };
    }

}
