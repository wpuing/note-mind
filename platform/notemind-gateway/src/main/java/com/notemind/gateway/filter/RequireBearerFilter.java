package com.notemind.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Gateway 层粗拦截过滤器：除登录/健康探测/OPTIONS 外，/api/** 必须携带 Bearer。
 * <p>
 * 仅校验 Authorization 头形态，JWT 签名与权限仍在 Service 侧完成。
 * 同时配合网关配置剥离伪造的用户头，避免浏览器直连下游。
 */
@Component
public class RequireBearerFilter implements GlobalFilter, Ordered {

    /**
     * 对进入网关的请求做 Bearer 粗校验，不满足则直接 401。
     *
     * @param exchange 当前 Web 交换上下文
     * @param chain    后续过滤器链
     * @return 放行或结束响应的 Mono
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // OPTIONS 预检直接放行，交由 CORS 处理
        if (HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod())) {
            // 继续过滤器链，不做鉴权
            return chain.filter(exchange);
        }
        String path = exchange.getRequest().getURI().getPath();
        // 路径为空时归一为空串，避免后续 NPE
        if (path == null) {
            path = "";
        }
        // 公开路径（登录、ping、actuator）跳过 Bearer 校验
        if (isPublic(path)) {
            // 公开接口直接放行
            return chain.filter(exchange);
        }
        // 非 /api/ 前缀的资源不强制 Bearer（静态或其它路由）
        if (!path.startsWith("/api/")) {
            // 非 API 路径直接放行
            return chain.filter(exchange);
        }
        String auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        // Authorization 缺失或非 Bearer 前缀则拒绝
        if (auth == null || auth.length() < 8 || !auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            // 结束响应，不再向下游转发
            return exchange.getResponse().setComplete();
        }
        // 形态合法则放行，由 Service 校验 JWT
        return chain.filter(exchange);
    }

    /**
     * 判断是否为网关层公开路径（无需 Bearer）。
     *
     * @param path 请求路径
     * @return true 表示公开
     */
    private static boolean isPublic(String path) {
        return "/api/v1/auth/login".equals(path)
                || "/api/v1/ping".equals(path)
                || path.startsWith("/actuator/");
    }

    /**
     * 过滤器执行顺序：数值越小越靠前。
     *
     * @return 顺序值 -100，尽量靠前拦截
     */
    @Override
    public int getOrder() {
        return -100;
    }
}
