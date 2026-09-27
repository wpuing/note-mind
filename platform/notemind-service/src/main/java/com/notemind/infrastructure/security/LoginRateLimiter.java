package com.notemind.infrastructure.security;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录限流：按 IP 与用户名分别统计失败次数；超限锁定一段时间。
 * <p>进程内内存实现，多实例各自计数（MVP 足够；生产可换 Redis）。
 */
@Component
public class LoginRateLimiter {

    private final int maxFailures;
    private final int ipMaxFailures;
    private final long windowMs;
    private final long lockoutMs;
    private final Object lock = new Object();

    private final ConcurrentHashMap<String, ArrayDeque<Long>> failures = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lockedUntil = new ConcurrentHashMap<>();

    public LoginRateLimiter(
            @Value("${notemind.auth.login-max-failures:5}") int maxFailures,
            @Value("${notemind.auth.login-ip-max-failures:30}") int ipMaxFailures,
            @Value("${notemind.auth.login-window-seconds:300}") int windowSeconds,
            @Value("${notemind.auth.login-lockout-seconds:900}") int lockoutSeconds) {
        this.maxFailures = Math.max(1, maxFailures);
        this.ipMaxFailures = Math.max(this.maxFailures, ipMaxFailures);
        this.windowMs = Math.max(1, windowSeconds) * 1000L;
        this.lockoutMs = Math.max(1, lockoutSeconds) * 1000L;
    }

    /** 登录前检查：已锁定则 429。 */
    public void checkAllowed(String clientIp, String username) {
        synchronized (lock) {
            long now = System.currentTimeMillis();
            purgeExpiredLocks(now);
            if (isLocked("ip:" + normalizeIp(clientIp), now)
                    || isLocked("user:" + normalizeUser(username), now)) {
                throw locked();
            }
        }
    }

    /** 登录失败：累计窗口内失败；达阈值则锁定；若本次已触发锁定则立即 429。 */
    public void recordFailure(String clientIp, String username) {
        synchronized (lock) {
            long now = System.currentTimeMillis();
            boolean locked = bump("ip:" + normalizeIp(clientIp), now, ipMaxFailures)
                    | bump("user:" + normalizeUser(username), now, maxFailures);
            if (locked) {
                throw locked();
            }
        }
    }

    /**
     * 登录成功：清除该用户名锁定；IP 桶只做衰减式清理（不整清，避免 NAT 误伤）。
     */
    public void recordSuccess(String clientIp, String username) {
        synchronized (lock) {
            String userKey = "user:" + normalizeUser(username);
            failures.remove(userKey);
            lockedUntil.remove(userKey);
        }
    }

    /** @return true 若本次 bump 触发了锁定 */
    private boolean bump(String key, long now, int limit) {
        ArrayDeque<Long> q = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        prune(q, now);
        q.addLast(now);
        if (q.size() >= limit) {
            lockedUntil.put(key, now + lockoutMs);
            q.clear();
            failures.remove(key, q);
            return true;
        }
        return false;
    }

    private boolean isLocked(String key, long now) {
        Long until = lockedUntil.get(key);
        if (until == null) {
            return false;
        }
        if (until <= now) {
            lockedUntil.remove(key, until);
            return false;
        }
        return true;
    }

    private void prune(ArrayDeque<Long> q, long now) {
        long cutoff = now - windowMs;
        while (!q.isEmpty() && q.peekFirst() < cutoff) {
            q.removeFirst();
        }
        if (q.isEmpty()) {
            // 空队列由调用方 remove；此处仅 prune
        }
    }

    private void purgeExpiredLocks(long now) {
        Iterator<Map.Entry<String, Long>> it = lockedUntil.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> e = it.next();
            if (e.getValue() <= now) {
                it.remove();
            }
        }
        // 顺带清理空失败队列，避免用户名扫描撑内存
        failures.entrySet().removeIf(e -> e.getValue() == null || e.getValue().isEmpty());
    }

    private static ResponseStatusException locked() {
        return new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS, "登录尝试过于频繁，请稍后再试");
    }

    private static String normalizeIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return "unknown";
        }
        String t = ip.trim();
        return t.length() > 64 ? t.substring(0, 64) : t;
    }

    private static String normalizeUser(String username) {
        if (username == null || username.isBlank()) {
            return "_";
        }
        String t = username.trim().toLowerCase();
        return t.length() > 64 ? t.substring(0, 64) : t;
    }
}
