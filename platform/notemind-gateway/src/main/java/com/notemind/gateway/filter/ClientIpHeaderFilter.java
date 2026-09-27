package com.notemind.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * 在剥离客户端伪造头之后，写入网关所见真实对端 IP 到 X-Real-IP，供 Service 登录限流使用。
 */
@Component
public class ClientIpHeaderFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String ip = resolveRemoteIp(exchange);
        ServerHttpRequest req = exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove("X-Forwarded-For");
                    h.remove("X-Real-IP");
                    if (ip != null && !ip.isBlank()) {
                        h.set("X-Real-IP", ip);
                    }
                })
                .build();
        return chain.filter(exchange.mutate().request(req).build());
    }

    @Override
    public int getOrder() {
        // 早于路由，晚于或并列于 RemoveRequestHeader 默认过滤器均可（此处再强制覆盖）
        return Ordered.HIGHEST_PRECEDENCE + 50;
    }

    private static String resolveRemoteIp(ServerWebExchange exchange) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        if (remote == null || remote.getAddress() == null) {
            return "unknown";
        }
        return remote.getAddress().getHostAddress();
    }
}
