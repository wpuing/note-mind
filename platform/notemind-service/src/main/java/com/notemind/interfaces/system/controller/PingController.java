package com.notemind.interfaces.system.controller;

import com.notemind.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 服务存活探测接口，供网关/运维探活使用。
 */
@RestController
@RequestMapping("/api/v1")
public class PingController {

    /**
     * 返回服务名与 ok 状态，无需登录。
     *
     * @return 含 service、status 的探测结果
     */
    @GetMapping("/ping")
    public Result<Map<String, String>> ping() {
        return Result.ok(Map.of("service", "notemind-service", "status", "ok"));
    }
}
