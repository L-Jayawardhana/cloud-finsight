package com.cloudfinsight.apiservice.controller;

import com.cloudfinsight.apiservice.dto.CostSummaryDto;
import com.cloudfinsight.apiservice.dto.VmSummaryDto;
import com.cloudfinsight.apiservice.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "VM inventory and aggregate cost endpoints")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/vms")
    @Operation(summary = "List VM inventory", description = "Returns all monitored VMs with current SKU, region, monthly price, and p95 CPU/memory utilisation.")
    public List<VmSummaryDto> getVms() {
        return dashboardService.getVmSummaries();
    }

    @GetMapping("/cost/summary")
    @Operation(summary = "Get aggregate cost summary", description = "Returns total monthly spend, total potential savings, VM count, and pending recommendation count.")
    public CostSummaryDto getCostSummary() {
        return dashboardService.getCostSummary();
    }
}