package com.cloudfinsight.apiservice.controller;

import com.cloudfinsight.apiservice.dto.CostSummaryDto;
import com.cloudfinsight.apiservice.dto.RecommendationHistoryDto;
import com.cloudfinsight.apiservice.dto.VmSummaryDto;
import com.cloudfinsight.apiservice.service.DashboardService;
import com.cloudfinsight.apiservice.service.RecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "VM inventory and aggregate cost endpoints")
public class DashboardController {

    private final DashboardService dashboardService;
    private final RecommendationService recommendationService;

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

    @GetMapping("/vms/{vmId}/recommendations/history")
    @Operation(summary = "Get a VM's recommendation history",
        description = "Returns all past recommendations for a VM, most recent first, paginated. Each entry is flagged isCurrent if it's the VM's active pending recommendation.")
    public ResponseEntity<Page<RecommendationHistoryDto>> getRecommendationHistory(
        @PathVariable Long vmId,
        Pageable pageable
    ) {
        return recommendationService.getRecommendationHistory(vmId, pageable)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
