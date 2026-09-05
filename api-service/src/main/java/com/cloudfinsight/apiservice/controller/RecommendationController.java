package com.cloudfinsight.apiservice.controller;

import com.cloudfinsight.apiservice.dto.RecommendationDetailDto;
import com.cloudfinsight.apiservice.dto.RecommendationSummaryDto;
import com.cloudfinsight.apiservice.service.RecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recommendations")
@RequiredArgsConstructor
@Tag(name = "Recommendations", description = "Recommendation list and detail endpoints")
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping
    @Operation(summary = "List current recommendations",
        description = "Returns the latest pending recommendation per VM, optionally filtered by vmId or type, paginated.")
    public Page<RecommendationSummaryDto> listRecommendations(
        @Parameter(description = "Filter to a specific VM's recommendation") @RequestParam(required = false) Long vmId,
        @Parameter(description = "Filter by recommendation type, e.g. DOWNSIZE") @RequestParam(required = false) String type,
        Pageable pageable
    ) {
        return recommendationService.listRecommendations(vmId, type, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get recommendation detail",
        description = "Returns full recommendation detail including all candidates, pros/cons, savings, and confidence.")
    public ResponseEntity<RecommendationDetailDto> getRecommendation(@PathVariable Long id) {
        return recommendationService.getRecommendationDetail(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
