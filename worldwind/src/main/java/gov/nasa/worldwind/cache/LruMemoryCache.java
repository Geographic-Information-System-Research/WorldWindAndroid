/*
 * Copyright (c) 2016 United States Government as represented by the Administrator of the
 * National Aeronautics and Space Administration. All Rights Reserved.
 */

package gov.nasa.worldwind.cache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import gov.nasa.worldwind.util.Logger;

public class LruMemoryCache<K, V> {

    protected final Map<K, Entry<K, V>> entries = new HashMap<>();

    protected final Comparator<Entry<K, V>> lruComparator = new Comparator<Entry<K, V>>() {
        @Override
        public int compare(Entry<K, V> lhs, Entry<K, V> rhs) {
            return (int) (lhs.lastUsed - rhs.lastUsed);
        }
    };

    protected int capacity;

    protected int lowWater;

    protected int usedCapacity;

    protected long lastUsedSequence; // overflows after thousands of years, given 1.0+E6 cache accesses per frame at 60Hz

    protected static long keyPool;

    public LruMemoryCache(int capacity, int lowWater) {
        if (capacity < 1) {
            throw new IllegalArgumentException(Logger.logMessage(Logger.ERROR, "LruMemoryCache", "constructor",
                "The specified capacity is less than 1"));
        }

        if (lowWater >= capacity || lowWater < 0) {
            throw new IllegalArgumentException(Logger.logMessage(Logger.ERROR, "LruMemoryCache", "constructor",
                "The specified low-water value is greater than or equal to the capacity, or less than 1"));
        }

        this.capacity = capacity;
        this.lowWater = lowWater;
    }

    public String generateCacheKey() {
        return "LruMemoryCache " + (++keyPool);
    }

    public int count() {
        return this.entries.size();
    }

    public V get(K key) {
        if (key == null) {
            throw new IllegalArgumentException(
                Logger.logMessage(Logger.ERROR, "LruMemoryCache", "get", "missingKey"));
        }

        Entry<K, V> entry = this.entries.get(key);
        if (entry != null) {
            entry.lastUsed = ++this.lastUsedSequence;
            return entry.value;
        } else {
            return null;
        }
    }

    public V put(K key, V value, int size) {
        if (key == null) {
            throw new IllegalArgumentException(
                Logger.logMessage(Logger.ERROR, "LruMemoryCache", "put", "missingKey"));
        }

        if (value == null) {
            throw new IllegalArgumentException(
                Logger.logMessage(Logger.ERROR, "LruMemoryCache", "put", "missingValue"));
        }

        if (size < 1) {
            throw new IllegalArgumentException(
                Logger.logMessage(Logger.ERROR, "LruMemoryCache", "put", "invalidSize"));
        }

        if (this.usedCapacity + size > this.capacity) {
            this.makeSpace(size);
        }

        Entry<K, V> newEntry = new Entry<>(key, value, size, ++this.lastUsedSequence);
        Entry<K, V> oldEntry = this.entries.put(key, newEntry);
        this.entryAdded(newEntry);
        return (oldEntry != null) ? this.entryRemoved(oldEntry) : null;
    }

    public V remove(K key) {
        if (key == null) {
            throw new IllegalArgumentException(
                Logger.logMessage(Logger.ERROR, "LruMemoryCache", "put", "missingKey"));
        }

        Entry<K, V> entry = this.entries.remove(key);
        return (entry != null) ? this.entryRemoved(entry) : null;
    }

    public boolean containsKey(K key) {
        return key != null && this.entries.containsKey(key);
    }

    public void clear() {
        this.entries.clear();
        this.usedCapacity = 0;
    }

    protected void makeSpace(int spaceRequired) {
        // Sort the entries from least recently used to most recently used, then remove the least recently used entries
        // until the cache capacity reaches the low water and the cache has enough free capacity for the required space.

        ArrayList<Entry<K, V>> sortedEntries = new ArrayList<>(this.entries.size());

        for (Entry<K, V> entry : this.entries.values()) {
            sortedEntries.add(entry);
        }

        Collections.sort(sortedEntries, this.lruComparator);

        for (Entry<K, V> entry : sortedEntries) {
            if (this.usedCapacity > this.lowWater || (this.capacity - this.usedCapacity) < spaceRequired) {
                this.entries.remove(entry.key);
                this.entryRemoved(entry);
            } else {
                break;
            }
        }
    }

    protected void entryAdded(Entry<K, V> entry) {
        this.usedCapacity += entry.size;
    }

    protected V entryRemoved(Entry<K, V> entry) {
        this.usedCapacity -= entry.size;
        return entry.value;
    }

    protected static class Entry<K, V> {

        public final K key;

        public final V value;

        public final int size;

        public long lastUsed;

        public Entry(K key, V value, int size, long lastUsed) {
            this.key = key;
            this.value = value;
            this.size = size;
            this.lastUsed = lastUsed;
        }
    }
}
