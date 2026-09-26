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
    private final Map<String, Long> userIdsByUsername;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public UserCache(@Value("${preonsurl.cache.user.capacity:10000}") int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Cache capacity must be positive");
        }

        this.capacity = capacity;
        this.usersById =
                new LinkedHashMap<>(capacity, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<Long, User> eldest) {
                        boolean remove = size() > UserCache.this.capacity;
                        if (remove) {
                            User eldestUser = eldest.getValue();
                            if (eldestUser != null) {
                                if (eldestUser.getEmail() != null) {
                                    userIdsByEmail.remove(normalizeEmail(eldestUser.getEmail()));
                                }
                                if (eldestUser.getUsername() != null) {
                                    userIdsByUsername.remove(normalizeUsername(eldestUser.getUsername()));
                                }
                            }
                        }

                        return remove;
                    }
                };
        this.userIdsByEmail = new LinkedHashMap<>();
        this.userIdsByUsername = new LinkedHashMap<>();
    }


    // Normalization
    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private static String normalizeUsername(String username) {
        return username == null ? null : username.trim().toLowerCase();
    }


    // Get by ID
    public User get(Long userId) {
        if (userId == null) {
            return null;
        }

        lock.writeLock().lock();
        try {
            // Updates LRU order
            return usersById.get(userId);

        } finally {
            lock.writeLock().unlock();
        }
    }


    // Get by Email
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
            // Updates LRU order
            User user = usersById.get(userId);
            if (user == null) {
                userIdsByEmail.remove(normalizedEmail);
            }
            return user;

        } finally {
            lock.writeLock().unlock();
        }
    }


    // Get by Username
    public User getByUsername(String username) {
        String normalizedUsername = normalizeUsername(username);
        if (normalizedUsername == null || normalizedUsername.isBlank()) {
            return null;
        }
        lock.writeLock().lock();
        try {
            Long userId = userIdsByUsername.get(normalizedUsername);
            if (userId == null) {
                return null;
            }
            // Updates LRU order
            User user = usersById.get(userId);
            // Safety in case indexes become inconsistent
            if (user == null) {
                userIdsByUsername.remove(normalizedUsername);
            }
            return user;
        } finally {
            lock.writeLock().unlock();
        }
    }


    // Put
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
        User existing = usersById.get(userId);
        // =====================================================
        // Remove old email index if email changed
        // =====================================================
        if (existing != null && existing.getEmail() != null) {
            String oldEmail = normalizeEmail(existing.getEmail());

            String newEmail = normalizeEmail(user.getEmail());
            if (!oldEmail.equals(newEmail)) {
                userIdsByEmail.remove(oldEmail);
            }
        }


        // =====================================================
        // Remove old username index if username changed
        // =====================================================
        if (existing != null && existing.getUsername() != null) {
            String oldUsername = normalizeUsername(existing.getUsername());
            String newUsername = normalizeUsername(user.getUsername());
            if (!oldUsername.equals(newUsername)) {
                userIdsByUsername.remove(oldUsername);
            }
        }


        // =====================================================
        // Store user
        // =====================================================
        usersById.put(userId, user);

        // =====================================================
        // Email index
        // =====================================================
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            userIdsByEmail.put(normalizeEmail(user.getEmail()), userId);
        }


        // =====================================================
        // Username index
        // =====================================================

        if (user.getUsername() != null && !user.getUsername().isBlank()) {
            userIdsByUsername.put(normalizeUsername(user.getUsername()), userId);
        }
    }


    // Remove
    public User remove(Long userId) {
        if (userId == null) {
            return null;
        }
        lock.writeLock().lock();
        try {
            User removed = usersById.remove(userId);
            if (removed != null) {
                if (removed.getEmail() != null) {
                    userIdsByEmail.remove(normalizeEmail(removed.getEmail()));
                }
                if (removed.getUsername() != null) {
                    userIdsByUsername.remove(normalizeUsername(removed.getUsername()));
                }
            }
            return removed;
        } finally {
            lock.writeLock().unlock();
        }
    }


    // Contains ID
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


    // Contains Email
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

    // Contains Username
    public boolean containsUsername(String username) {
        String normalizedUsername = normalizeUsername(username);
        if (normalizedUsername == null || normalizedUsername.isBlank()) {
            return false;
        }
        lock.readLock().lock();
        try {
            return userIdsByUsername.containsKey(normalizedUsername);
        } finally {
            lock.readLock().unlock();
        }
    }


    // Size
    public int size() {
        lock.readLock().lock();
        try {
            return usersById.size();
        } finally {
            lock.readLock().unlock();
        }
    }


    // Clear
    public void clear() {
        lock.writeLock().lock();
        try {
            usersById.clear();
            userIdsByEmail.clear();
            userIdsByUsername.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }


    // Capacity
    public int getCapacity() {
        return capacity;
    }
}