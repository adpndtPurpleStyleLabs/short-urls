package com.preonsurl.apis.apikey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Component
public class APIKeyCache {

    private final int capacity;
    private final Map<String, ApiKey> map;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public APIKeyCache(@Value("${preonsurl.cache.apikey.capacity:10000}") int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Cache capacity must be positive");
        }
        this.capacity = capacity;
        this.map = new LinkedHashMap<>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, ApiKey> eldest) {
                return size() > APIKeyCache.this.capacity;
            }
        };
    }

    public ApiKey get(String apiKeyHash) {
        if (apiKeyHash == null) {
            return null;
        }
        lock.writeLock().lock();
        try {
            return map.get(apiKeyHash);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void put(ApiKey apiKey) {
        if (apiKey == null || apiKey.getApiKeyHash() == null) {
            return;
        }
        lock.writeLock().lock();
        try {
            map.put(apiKey.getApiKeyHash(), apiKey);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void put(String apiKeyHash, ApiKey apiKey) {
        if (apiKeyHash == null || apiKey == null) {
            return;
        }
        lock.writeLock().lock();
        try {
            map.put(apiKeyHash, apiKey);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public ApiKey remove(String apiKeyHash) {
        if (apiKeyHash == null) {
            return null;
        }
        lock.writeLock().lock();
        try {
            return map.remove(apiKeyHash);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean containsKey(String apiKeyHash) {
        if (apiKeyHash == null) {
            return false;
        }
        lock.readLock().lock();
        try {
            return map.containsKey(apiKeyHash);
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
