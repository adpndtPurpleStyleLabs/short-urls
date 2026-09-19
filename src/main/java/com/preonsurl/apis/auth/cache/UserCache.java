package com.preonsurl.apis.auth.cache;

import com.preonsurl.apis.auth.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Component
public class UserCache {

    private final int capacity;
    private final Map<Long, User> map;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public UserCache(@Value("${preonsurl.cache.user.capacity:10000}") int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Cache capacity must be positive");
        }
        this.capacity = capacity;
        this.map = new LinkedHashMap<>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, User> eldest) {
                return size() > UserCache.this.capacity;
            }
        };
    }

    public User get(Long userId) {
        if (userId == null) {
            return null;
        }
        lock.writeLock().lock();
        try {
            return map.get(userId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void put(User user) {
        if (user == null || user.getId() == null) {
            return;
        }
        lock.writeLock().lock();
        try {
            map.put(user.getId(), user);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void put(Long userId, User user) {
        if (userId == null || user == null) {
            return;
        }
        lock.writeLock().lock();
        try {
            map.put(userId, user);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public User remove(Long userId) {
        if (userId == null) {
            return null;
        }
        lock.writeLock().lock();
        try {
            return map.remove(userId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean containsKey(Long userId) {
        if (userId == null) {
            return false;
        }
        lock.readLock().lock();
        try {
            return map.containsKey(userId);
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
