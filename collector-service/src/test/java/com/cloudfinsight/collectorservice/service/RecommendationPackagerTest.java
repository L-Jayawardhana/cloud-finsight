package com.cloudfinsight.collectorservice.service;

import com.cloudfinsight.collectorservice.entity.Recommendation;
import com.cloudfinsight.collectorservice.entity.RecommendationCandidate;
import com.cloudfinsight.collectorservice.entity.VirtualMachine;
import com.cloudfinsight.collectorservice.entity.VmSkuCatalogueEntry;
import com.cloudfinsight.collectorservice.model.CandidateSavings;
import com.cloudfinsight.collectorservice.model.CandidateSku;
import com.cloudfinsight.collectorservice.model.SavingsEstimate;
import com.cloudfinsight.collectorservice.model.ScoredCandidate;
import com.cloudfinsight.collectorservice.repository.RecommendationCandidateRepository;
import com.cloudfinsight.collectorservice.repository.RecommendationRepository;
import com.cloudfinsight.collectorservice.repository.VirtualMachineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecommendationPackagerTest {

    private VirtualMachineRepository virtualMachineRepository;
    private RecommendationRepository recommendationRepository;
    private RecommendationCandidateRepository recommendationCandidateRepository;
    private RecommendationPackager packager;

    @BeforeEach
    void setUp() {
        virtualMachineRepository = Mockito.mock(VirtualMachineRepository.class);
        recommendationRepository = Mockito.mock(RecommendationRepository.class);
        recommendationCandidateRepository = Mockito.mock(RecommendationCandidateRepository.class);
        packager = new RecommendationPackager(
            virtualMachineRepository, recommendationRepository, recommendationCandidateRepository);
    }

    @Test
    void packageRecommendation_selectsFirstOfTiedScores_deterministically() {
        VirtualMachine vm = vm(1L);
        when(virtualMachineRepository.findById(1L)).thenReturn(Optional.of(vm));

        ScoredCandidate tiedFirst = scored("Standard_B1ms", CandidateSku.DOWNSIZE, "CURRENT", 0.75, "HIGH");
        ScoredCandidate tiedSecond = scored("Standard_D2s_v3", CandidateSku.CROSS_GENERATION, "OLDER_SUPPORTED", 0.75, "HIGH");

        List<ScoredCandidate> scoredCandidates = List.of(tiedFirst, tiedSecond); // equal composite scores
        List<CandidateSavings> savingsList = List.of(
            savingsFor(tiedFirst, 40.0),
            savingsFor(tiedSecond, 30.0)
        );

        when(recommendationRepository.save(Mockito.any(Recommendation.class)))
            .thenAnswer(inv -> {
                Recommendation r = inv.getArgument(0);
                r.setId(100L);
                return r;
            });

        Optional<Recommendation> result = packager.packageRecommendation(1L, scoredCandidates, savingsList);

        assertThat(result).isPresent();
        assertThat(result.get().getRecommendationType()).isEqualTo(CandidateSku.DOWNSIZE);

        ArgumentCaptor<RecommendationCandidate> captor = ArgumentCaptor.forClass(RecommendationCandidate.class);
        verify(recommendationCandidateRepository, times(2)).save(captor.capture());

        List<RecommendationCandidate> saved = captor.getAllValues();
        assertThat(saved.get(0).getCandidateSku()).isEqualTo("Standard_B1ms");
        assertThat(saved.get(0).isSelected()).isTrue();
        assertThat(saved.get(1).getCandidateSku()).isEqualTo("Standard_D2s_v3");
        assertThat(saved.get(1).isSelected()).isFalse();
    }

    @Test
    void packageRecommendation_singleCandidate_persistsWithNoRunnerUp() {
        VirtualMachine vm = vm(1L);
        when(virtualMachineRepository.findById(1L)).thenReturn(Optional.of(vm));

        ScoredCandidate only = scored("Standard_B1ms", CandidateSku.DOWNSIZE, "CURRENT", 0.80, "HIGH");
        List<CandidateSavings> savingsList = List.of(savingsFor(only, 40.0));

        when(recommendationRepository.save(Mockito.any(Recommendation.class)))
            .thenAnswer(inv -> {
                Recommendation r = inv.getArgument(0);
                r.setId(101L);
                return r;
            });

        Optional<Recommendation> result = packager.packageRecommendation(1L, List.of(only), savingsList);

        assertThat(result).isPresent();
        verify(recommendationCandidateRepository, times(1)).save(Mockito.any(RecommendationCandidate.class));
    }

    @Test
    void packageRecommendation_populatesProsAndConsFromScoresAndSavings() {
        VirtualMachine vm = vm(1L);
        when(virtualMachineRepository.findById(1L)).thenReturn(Optional.of(vm));

        ScoredCandidate crossGen = scored("Standard_D2s_v3", CandidateSku.CROSS_GENERATION, "OLDER_SUPPORTED", 0.70, "LOW");
        CandidateSavings savings = savingsFor(crossGen, 40.0);

        when(recommendationRepository.save(Mockito.any(Recommendation.class)))
            .thenAnswer(inv -> {
                Recommendation r = inv.getArgument(0);
                r.setId(102L);
                return r;
            });

        packager.packageRecommendation(1L, List.of(crossGen), List.of(savings));

        ArgumentCaptor<RecommendationCandidate> captor = ArgumentCaptor.forClass(RecommendationCandidate.class);
        verify(recommendationCandidateRepository).save(captor.capture());

        RecommendationCandidate saved = captor.getValue();
        assertThat(saved.getCons()).contains("Older-generation hardware");
        assertThat(saved.getCons()).contains("LOW confidence");
        assertThat(saved.getPros()).contains("Estimated saving");
    }

    @Test
    void packageRecommendation_noCandidates_returnsEmpty() {
        Optional<Recommendation> result = packager.packageRecommendation(1L, List.of(), List.of());

        assertThat(result).isEmpty();
    }

    @Test
    void packageRecommendation_unknownVm_returnsEmpty() {
        when(virtualMachineRepository.findById(1L)).thenReturn(Optional.empty());

        ScoredCandidate candidate = scored("Standard_B1ms", CandidateSku.DOWNSIZE, "CURRENT", 0.80, "HIGH");
        Optional<Recommendation> result = packager.packageRecommendation(
            1L, List.of(candidate), List.of(savingsFor(candidate, 40.0)));

        assertThat(result).isEmpty();
    }

    private VirtualMachine vm(Long id) {
        VirtualMachine vm = new VirtualMachine();
        vm.setId(id);
        return vm;
    }

    private ScoredCandidate scored(String skuName, String type, String generation, double compositeScore,
                                    String confidenceLevel) {
        VmSkuCatalogueEntry sku = new VmSkuCatalogueEntry();
        sku.setArmSkuName(skuName);
        sku.setVmFamily("test");
        sku.setGeneration(generation);
        sku.setVcpuCount(2);
        sku.setMemoryGb(new BigDecimal("8.00"));
        sku.setSupportStatus("SUPPORTED");

        CandidateSku candidateSku = new CandidateSku(sku, type, 50.0, 50.0);
        return new ScoredCandidate(candidateSku, 1.5, 1.0, 0.6, compositeScore, confidenceLevel);
    }

    private CandidateSavings savingsFor(ScoredCandidate scored, double candidateMonthlyPrice) {
        SavingsEstimate estimate = new SavingsEstimate(
            "USD", 100.0, candidateMonthlyPrice, 100.0 - candidateMonthlyPrice,
            ((100.0 - candidateMonthlyPrice) / 100.0) * 100.0,
            false, 2 * candidateMonthlyPrice, 100.0 - (2 * candidateMonthlyPrice));
        return new CandidateSavings(scored.candidate(), estimate);
    }
}
