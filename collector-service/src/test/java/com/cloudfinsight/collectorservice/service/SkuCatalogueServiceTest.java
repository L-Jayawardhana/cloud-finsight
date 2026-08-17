package com.cloudfinsight.collectorservice.service;

import com.cloudfinsight.collectorservice.entity.VmSkuCatalogueEntry;
import com.cloudfinsight.collectorservice.repository.VmSkuCatalogueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class SkuCatalogueServiceTest {

    private VmSkuCatalogueRepository repository;
    private SkuCatalogueService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(VmSkuCatalogueRepository.class);
        service = new SkuCatalogueService(repository);
    }

    @Test
    void findCandidates_returnsBothCurrentAndOlderGenerationOptions() {
        VmSkuCatalogueEntry current = entry("Standard_D2s_v5", "CURRENT", 2, "8.00");
        VmSkuCatalogueEntry older = entry("Standard_D2s_v3", "OLDER_SUPPORTED", 2, "8.00");

        when(repository.findByVcpuCountBetweenAndMemoryGbBetween(any(), any(), any(), any()))
            .thenReturn(List.of(current, older));

        List<VmSkuCatalogueEntry> candidates = service.findCandidates(2, 8);

        assertThat(candidates)
            .hasSize(2)
            .extracting(VmSkuCatalogueEntry::getGeneration)
            .containsExactlyInAnyOrder("CURRENT", "OLDER_SUPPORTED");
    }

    @Test
    void findCandidates_preservesCorrectMetadataPerCandidate() {
        VmSkuCatalogueEntry entry = entry("Standard_D2s_v5", "CURRENT", 2, "8.00");
        when(repository.findByVcpuCountBetweenAndMemoryGbBetween(any(), any(), any(), any()))
            .thenReturn(List.of(entry));

        List<VmSkuCatalogueEntry> candidates = service.findCandidates(2, 8);

        assertThat(candidates.get(0).getArmSkuName()).isEqualTo("Standard_D2s_v5");
        assertThat(candidates.get(0).getVcpuCount()).isEqualTo(2);
        assertThat(candidates.get(0).getMemoryGb()).isEqualByComparingTo(new BigDecimal("8.00"));
    }

    private VmSkuCatalogueEntry entry(String skuName, String generation, int vcpu, String memoryGb) {
        VmSkuCatalogueEntry e = new VmSkuCatalogueEntry();
        e.setArmSkuName(skuName);
        e.setVmFamily("test-family");
        e.setGeneration(generation);
        e.setVcpuCount(vcpu);
        e.setMemoryGb(new BigDecimal(memoryGb));
        e.setSupportStatus("SUPPORTED");
        return e;
    }
}
