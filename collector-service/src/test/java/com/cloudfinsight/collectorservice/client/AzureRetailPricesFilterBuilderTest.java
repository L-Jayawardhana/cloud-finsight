package com.cloudfinsight.collectorservice.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AzureRetailPricesFilterBuilderTest {

    @Test
    void build_includesRegionAndSkuAndConsumptionAndServiceFilters() {
        String filter = AzureRetailPricesFilterBuilder.build("Standard_D2s_v4", "southeastasia");

        assertThat(filter)
            .contains("armRegionName eq 'southeastasia'")
            .contains("armSkuName eq 'Standard_D2s_v4'")
            .contains("priceType eq 'Consumption'")
            .contains("serviceName eq 'Virtual Machines'");
    }

    @Test
    void build_producesDifferentFiltersForDifferentSkus() {
        String filter1 = AzureRetailPricesFilterBuilder.build("Standard_D2s_v4", "southeastasia");
        String filter2 = AzureRetailPricesFilterBuilder.build("Standard_D2s_v3", "southeastasia");

        assertThat(filter1).isNotEqualTo(filter2);
    }
}
