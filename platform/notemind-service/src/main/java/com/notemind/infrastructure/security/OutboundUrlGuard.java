package com.notemind.infrastructure.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

/**
 * 出站 URL 校验：降低模型 baseUrl / Embedding 等配置引发的 SSRF 风险。
 */
public final class OutboundUrlGuard {

    private static final Set<String> DEFAULT_HOST_ALLOW = Set.of(
            "dashscope.aliyuncs.com",
            "dashscope-intl.aliyuncs.com");

    private OutboundUrlGuard() {
    }

    /**
     * 校验并规范化 base（可含 path）。默认仅允许百炼域名；本地调试可 http://127.0.0.1。
     */
    public static String requireSafeHttpUrl(String rawUrl, boolean allowLocalHttp) {
        // 始终白名单主机（*.aliyuncs.com）；弱默认仅放宽本机 http，不放开任意公网
        return requireSafeHttpUrl(rawUrl, allowLocalHttp, true);
    }

    /**
     * @param allowLocalHttp 允许 http://127.0.0.1
     * @param allowlistOnly  true=仅白名单主机；false=任意公网 https（仍禁私网）
     */
    public static String requireSafeHttpUrl(String rawUrl, boolean allowLocalHttp, boolean allowlistOnly) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL 不能为空");
        }
        String trimmed = rawUrl.trim().replaceAll("/+$", "");
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL 非法");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL 缺少主机名");
        }
        host = host.toLowerCase(Locale.ROOT);

        boolean localHost = "127.0.0.1".equals(host)
                || "localhost".equals(host)
                || "[::1]".equals(host)
                || "::1".equals(host);

        if ("https".equals(scheme)) {
            // ok
        } else if ("http".equals(scheme) && allowLocalHttp && localHost) {
            // ok
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅允许 https 出站（本地调试可 http://127.0.0.1）");
        }

        if (localHost) {
            if (!allowLocalHttp) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "禁止访问本机地址");
            }
            return trimmed;
        }

        boolean allowlisted = DEFAULT_HOST_ALLOW.contains(host) || host.endsWith(".aliyuncs.com");
        if (allowlistOnly && !allowlisted) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "出站主机不在白名单（默认仅 *.aliyuncs.com）");
        }
        assertPublicResolved(host);
        return trimmed;
    }

    private static void assertPublicResolved(String host) {
        try {
            for (InetAddress addr : InetAddress.getAllByName(host)) {
                if (isBlockedAddress(addr)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "禁止访问内网/保留地址: " + host);
                }
            }
        } catch (UnknownHostException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无法解析主机: " + host);
        }
    }

    static boolean isBlockedAddress(InetAddress addr) {
        if (addr.isAnyLocalAddress()
                || addr.isLoopbackAddress()
                || addr.isLinkLocalAddress()
                || addr.isSiteLocalAddress()
                || addr.isMulticastAddress()) {
            return true;
        }
        byte[] b = addr.getAddress();
        if (b.length == 4) {
            int a0 = b[0] & 0xff;
            int a1 = b[1] & 0xff;
            // 0.0.0.0/8
            if (a0 == 0) {
                return true;
            }
            // CGNAT 100.64.0.0/10
            if (a0 == 100 && a1 >= 64 && a1 <= 127) {
                return true;
            }
        }
        if (b.length == 16) {
            // fc00::/7 unique local
            int a0 = b[0] & 0xff;
            if ((a0 & 0xfe) == 0xfc) {
                return true;
            }
        }
        return false;
    }
}
