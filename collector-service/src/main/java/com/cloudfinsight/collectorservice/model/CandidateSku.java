package com.cloudfinsight.collectorservice.model;

import com.cloudfinsight.collectorservice.entity.VmSkuCatalogueEntry;

public record CandidateSku(
    VmSkuCatalogueEntry sku,
    String recommendationType,
    double cpuHeadroomPercent,
    double memoryHeadroomPercent
) {
    public static final String DOWNSIZE = "DOWNSIZE";
    public static final String UPSIZE = "UPSIZE";
    public static final String CROSS_GENERATION = "CROSS_GENERATION";
}
