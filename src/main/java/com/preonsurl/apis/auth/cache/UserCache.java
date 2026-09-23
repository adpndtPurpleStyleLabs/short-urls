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
    private final Map<Long, User> usersById;
    private final Map<String, Long> userIdsByEmail;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public UserCache(@Value("${preonsurl.cache.user.capacity:10000}") int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Cache capacity must be positive");
        }

        this.capacity = capacity;
        this.usersById = new LinkedHashMap<>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, User> eldest) {
                boolean remove = size() > UserCache.this.capacity;
                if (remove) {
                    User eldestUser = eldest.getValue();
                    if (eldestUser != null && eldestUser.getEmail() != null) {
                        userIdsByEmail.remove(normalizeEmail(eldestUser.getEmail()));
                    }
                }
                return remove;
            }
        };

        this.userIdsByEmail = new LinkedHashMap<>();
    }

    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    public User get(Long userId) {
        if (userId == null) {
            return null;
        }
        lock.writeLock().lock();
        try {
            return usersById.get(userId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public User getByEmail(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            return null;
        }
        lock.writeLock().lock();
        try {
            Long userId = userIdsByEmail.get(normalizedEmail);
            if (userId == null) {
                return null;
            }

            // This also updates LRU order.
            User user = usersById.get(userId);
            // Safety in case the indexes somehow become inconsistent.
            if (user == null) {
                userIdsByEmail.remove(normalizedEmail);
            }
            return user;

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
            putInternal(user);
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
            putInternal(user);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void putInternal(User user) {

        Long userId = user.getId();

        // Remove old email index if email changed.
        User existing = usersById.get(userId);

        if (existing != null
                && existing.getEmail() != null) {

            String oldEmail = normalizeEmail(existing.getEmail());

            if (!oldEmail.equals(
                    normalizeEmail(user.getEmail()))) {

                userIdsByEmail.remove(oldEmail);
            }
        }

        usersById.put(userId, user);

        if (user.getEmail() != null
                && !user.getEmail().isBlank()) {

            userIdsByEmail.put(
                    normalizeEmail(user.getEmail()),
                    userId
            );
        }
    }

    public User remove(Long userId) {

        if (userId == null) {
            return null;
        }

        lock.writeLock().lock();

        try {
            User removed = usersById.remove(userId);

            if (removed != null
                    && removed.getEmail() != null) {

                userIdsByEmail.remove(
                        normalizeEmail(removed.getEmail())
                );
            }

            return removed;

        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean containsKey(Long userId) {

        if (userId == null) {
            return false;
        }

        lock.writeLock().lock();

        try {
            return usersById.get(userId) != null;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean containsEmail(String email) {

        String normalizedEmail = normalizeEmail(email);

        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            return false;
        }

        lock.readLock().lock();

        try {
            return userIdsByEmail.containsKey(normalizedEmail);
        } finally {
            lock.readLock().unlock();
        }
    }

    public int size() {

        lock.readLock().lock();

        try {
            return usersById.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public void clear() {

        lock.writeLock().lock();

        try {
            usersById.clear();
            userIdsByEmail.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int getCapacity() {
        return capacity;
    }
}