package com.preonsurl.apis.link.cache;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Component
public class NewUrlLruCache {
    private final int capacity;
    private final Map<String, CachedNewUrlDto> map;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public NewUrlLruCache(@Value("${preonsurl.cache.lru.capacity:10000}") int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Cache capacity must be positive");
        }

        this.capacity = capacity;
        this.map = new LinkedHashMap<>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CachedNewUrlDto> eldest) {
                return size() > NewUrlLruCache.this.capacity;
            }
        };
    }

    public CachedNewUrlDto get(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        lock.writeLock().lock();

        try {
            return map.get(normalizePath(path));
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void put(CachedNewUrlDto item) {
        if (item == null || item.getFullShortUrl() == null) {
            return;
        }
        lock.writeLock().lock();
        try {
            map.put(normalizePath(item.getFullShortUrl()), item);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public CachedNewUrlDto remove(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        lock.writeLock().lock();
        try {
            return map.remove(normalizePath(path));
        } finally {
            lock.writeLock().unlock();
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

    private static String normalizePath(String path) {
        String normalized = path.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return normalized;
    }
}