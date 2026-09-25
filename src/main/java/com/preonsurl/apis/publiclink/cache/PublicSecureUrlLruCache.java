package com.preonsurl.apis.publiclink.cache;

import com.preonsurl.apis.publiclink.dto.CachedPublicSecureUrlDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Component
public class PublicSecureUrlLruCache {

    private final int capacity;
    private final Map<String, CachedPublicSecureUrlDto> map;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public PublicSecureUrlLruCache(@Value("${preonsurl.psecure.cache.capacity:10000}") int capacity) {
        this.capacity = capacity > 0 ? capacity : 10000;
        this.map = new LinkedHashMap<>(this.capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CachedPublicSecureUrlDto> eldest) {
                return size() > PublicSecureUrlLruCache.this.capacity;
            }
        };
    }

    public CachedPublicSecureUrlDto get(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String normalized = normalizeKey(key);
        lock.writeLock().lock();
        try {
            return map.get(normalized);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void put(CachedPublicSecureUrlDto item) {
        if (item == null) {
            return;
        }
        lock.writeLock().lock();
        try {
            if (item.getShortKey() != null) {
                map.put(normalizeKey(item.getShortKey()), item);
            }
            if (item.getPsecureUrl() != null) {
                map.put(normalizeKey(item.getPsecureUrl()), item);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public CachedPublicSecureUrlDto remove(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String normalized = normalizeKey(key);
        lock.writeLock().lock();
        try {
            return map.remove(normalized);
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

    private static String normalizeKey(String key) {
        String trimmed = key.trim();
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
