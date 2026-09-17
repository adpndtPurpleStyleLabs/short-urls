package com.preonsurl.apis.cache;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Component
public class ShortUrlLruCache {

    private final int capacity;
    private final Map<String, CachedShortUrl> map;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public ShortUrlLruCache(@Value("${preonsurl.cache.lru.capacity:10000}") int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Cache capacity must be positive");
        }
        this.capacity = capacity;
        this.map = new LinkedHashMap<>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CachedShortUrl> eldest) {
                return size() > ShortUrlLruCache.this.capacity;
            }
        };
    }

    public static String buildKey(String dirType, String shortCode) {
        if (shortCode == null) {
            return "";
        }
        String code = shortCode.trim();
        if (dirType == null || dirType.trim().isEmpty()) {
            return code;
        }
        return dirType.trim() + "/" + code;
    }

    public CachedShortUrl get(String dirType, String shortCode) {
        String key = buildKey(dirType, shortCode);
        lock.writeLock().lock(); // write lock needed because access-order LinkedHashMap modifies structural order on get
        try {
            return map.get(key);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void put(CachedShortUrl item) {
        if (item == null) {
            return;
        }
        String key = buildKey(item.getDirType(), item.getShortCode());
        lock.writeLock().lock();
        try {
            map.put(key, item);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public CachedShortUrl remove(String dirType, String shortCode) {
        String key = buildKey(dirType, shortCode);
        lock.writeLock().lock();
        try {
            return map.remove(key);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean containsKey(String dirType, String shortCode) {
        String key = buildKey(dirType, shortCode);
        lock.readLock().lock();
        try {
            return map.containsKey(key);
        } finally {
            lock.readLock().unlock();
        }
    }

    public int size() {
        lock.readLock().lock();
        try {
            return map.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            map.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int getCapacity() {
        return capacity;
    }
}
