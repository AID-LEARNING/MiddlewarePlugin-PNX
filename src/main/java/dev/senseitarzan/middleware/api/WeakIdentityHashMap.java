package dev.senseitarzan.middleware.api;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * A concurrent identity-based map with weak keys.
 * Compares keys using {@code ==} (object identity) and {@link System#identityHashCode(Object)},
 * ensuring that mutable keys (such as classes generated with Lombok @Data) do not cause hash mismatches.
 *
 * @param <K> Key type
 * @param <V> Value type
 */
public class WeakIdentityHashMap<K, V> {

    private final ConcurrentMap<Object, V> map = new ConcurrentHashMap<>();
    private final ReferenceQueue<K> queue = new ReferenceQueue<>();

    /**
     * Constructs a new, empty WeakIdentityHashMap.
     */
    public WeakIdentityHashMap() {
    }

    /**
     * Cleans up garbage collected keys from the backing concurrent map.
     */
    private void expunge() {
        Reference<? extends K> ref;
        while ((ref = queue.poll()) != null) {
            map.remove(ref);
        }
    }

    /**
     * Retrieves the value associated with the specified key using object identity comparison.
     *
     * @param key the key whose associated value is to be returned
     * @return the value associated with the key, or {@code null} if no mapping exists
     */
    public V get(K key) {
        if (key == null) return null;
        expunge();
        return map.get(new LookupKey(key));
    }

    /**
     * Associates the specified value with the specified key using object identity comparison.
     *
     * @param key the key with which the specified value is to be associated
     * @param value the value to associate with the key
     * @return the previous value associated with the key, or {@code null}
     */
    public V put(K key, V value) {
        if (key == null) return null;
        Objects.requireNonNull(value, "value cannot be null");
        expunge();
        return map.put(new KeyReference<>(key, queue), value);
    }

    /**
     * Associates the specified value with the key if not already present, using object identity comparison.
     *
     * @param key the key with which the specified value is to be associated
     * @param value the value to associate with the key
     * @return the existing value associated with the key, or {@code null} if none was present
     */
    public V putIfAbsent(K key, V value) {
        if (key == null) return null;
        Objects.requireNonNull(value, "value cannot be null");
        expunge();
        return map.putIfAbsent(new KeyReference<>(key, queue), value);
    }

    /**
     * Computes and inserts a value for the specified key if not already present.
     *
     * @param key the key whose associated value is to be computed
     * @param mappingFunction the function to compute a value
     * @return the current (existing or computed) value associated with the specified key
     */
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        if (key == null) return null;
        expunge();
        LookupKey lookup = new LookupKey(key);
        V existing = map.get(lookup);
        if (existing != null) {
            return existing;
        }
        V created = mappingFunction.apply(key);
        if (created == null) {
            return null;
        }
        V prev = map.putIfAbsent(new KeyReference<>(key, queue), created);
        return prev != null ? prev : created;
    }

    /**
     * Removes the mapping for a key from this map if it is present using object identity comparison.
     *
     * @param key the key whose mapping is to be removed
     * @return the previous value associated with the key, or {@code null}
     */
    public V remove(K key) {
        if (key == null) return null;
        expunge();
        return map.remove(new LookupKey(key));
    }

    /**
     * Tests if the specified object is a key in this map using object identity comparison.
     *
     * @param key the key whose presence in this map is to be tested
     * @return {@code true} if and only if the specified object is a key in this map
     */
    public boolean containsKey(K key) {
        if (key == null) return false;
        expunge();
        return map.containsKey(new LookupKey(key));
    }

    /**
     * Removes all mappings from this map and drains the reference queue.
     */
    public void clear() {
        map.clear();
        while (queue.poll() != null) {
        }
    }

    /**
     * Returns the number of key-value mappings in this map.
     *
     * @return the number of mappings
     */
    public int size() {
        expunge();
        return map.size();
    }

    /**
     * Returns {@code true} if this map contains no key-value mappings.
     *
     * @return {@code true} if this map is empty
     */
    public boolean isEmpty() {
        expunge();
        return map.isEmpty();
    }

    /**
     * WeakReference wrapper using System.identityHashCode and == comparison.
     *
     * @param <T> Referent type
     */
    private static final class KeyReference<T> extends WeakReference<T> {
        private final int hash;

        /**
         * Creates a new KeyReference.
         *
         * @param referent the object referent
         * @param q the reference queue
         */
        KeyReference(T referent, ReferenceQueue<T> q) {
            super(referent, q);
            this.hash = System.identityHashCode(referent);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj instanceof KeyReference<?> other) {
                T a = this.get();
                Object b = other.get();
                return a != null && a == b;
            }
            if (obj instanceof LookupKey lookup) {
                T a = this.get();
                return a != null && a == lookup.key;
            }
            return false;
        }
    }

    /**
     * Non-allocating lookup key used for querying the map without wrapping in WeakReference.
     */
    private static final class LookupKey {
        private final Object key;
        private final int hash;

        /**
         * Creates a new LookupKey.
         *
         * @param key the target key object
         */
        LookupKey(Object key) {
            this.key = key;
            this.hash = System.identityHashCode(key);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj instanceof LookupKey other) {
                return this.key == other.key;
            }
            if (obj instanceof KeyReference<?> ref) {
                return this.key == ref.get();
            }
            return false;
        }
    }
}
