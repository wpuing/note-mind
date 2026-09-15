package com.notemind.interfaces.dashboard.controller;

import com.notemind.application.service.dashboard.DashboardAsvc;
import com.notemind.common.result.Result;
import com.notemind.interfaces.dashboard.vo.DashboardOverviewVo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作台概览接口：KPI、热门知识库、趋势与模型调用统计。
 */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    /** 工作台应用服务，聚合近 N 天运营指标。 */
    private final DashboardAsvc dashboardAsvc;

    /**
     * 构造注入工作台服务。
     *
     * @param dashboardAsvc 工作台应用服务
     */
    public DashboardController(DashboardAsvc dashboardAsvc) {
        this.dashboardAsvc = dashboardAsvc;
    }

    /**
     * 获取工作台概览数据。
     *
     * @param days 统计天数，默认 14
     * @return 概览 VO（KPI、图表数据等）
     */
    @GetMapping("/overview")
    public Result<DashboardOverviewVo> overview(@RequestParam(defaultValue = "14") int days) {
        // 按天数窗口聚合工作台各类指标
        return Result.ok(dashboardAsvc.overview(days));
    }
}
