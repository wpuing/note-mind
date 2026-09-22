package com.notemind.interfaces.system.controller;

import com.notemind.application.service.system.SystemConfigAsvc;
import com.notemind.common.result.Result;
import com.notemind.interfaces.system.vo.SystemConfigBatchSaveRequest;
import com.notemind.interfaces.system.vo.SystemConfigVo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 系统配置接口：列表、分组查看、批量保存、热重载。
 */
@RestController
@RequestMapping("/api/v1/system/configs")
public class SystemConfigController {

    /** 系统配置应用服务，读写 t_system_config 并即时生效。 */
    private final SystemConfigAsvc systemConfigAsvc;

    /**
     * 构造注入系统配置服务。
     *
     * @param systemConfigAsvc 系统配置应用服务
     */
    public SystemConfigController(SystemConfigAsvc systemConfigAsvc) {
        this.systemConfigAsvc = systemConfigAsvc;
    }

    /**
     * 列出全部系统配置项。
     *
     * @return 配置项列表
     */
    @GetMapping
    public Result<List<SystemConfigVo>> list() {
        // 查询全部配置供管理端平铺展示
        return Result.ok(systemConfigAsvc.listAll());
    }

    /**
     * 按分组返回系统配置，便于管理端分组编辑。
     *
     * @return 分组名到配置列表的映射
     */
    @GetMapping("/grouped")
    public Result<Map<String, List<SystemConfigVo>>> grouped() {
        // 按业务分组聚合配置项
        return Result.ok(systemConfigAsvc.listGrouped());
    }

    /**
     * 批量保存系统配置，保存后即时生效。
     *
     * @param body 批量保存请求
     * @return 保存后的配置列表
     */
    @PutMapping
    public Result<List<SystemConfigVo>> save(@RequestBody SystemConfigBatchSaveRequest body) {
        // 批量落库并刷新内存缓存中的默认值
        return Result.ok(systemConfigAsvc.saveBatch(body));
    }

    /**
     * 强制从数据库重载配置缓存。
     *
     * @return 重载后配置条数
     */
    @PostMapping("/reload")
    public Result<Map<String, Integer>> reload() {
        // 清空并重新加载配置缓存
        systemConfigAsvc.reload();
        // 返回当前缓存规模便于确认重载成功
        return Result.ok(Map.of("size", systemConfigAsvc.listAll().size()));
    }
}
