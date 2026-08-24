package com.cloudfinsight.collectorservice.model;

public record ScoredCandidate(
    CandidateSku candidate,
    double costScore,
    double reliabilityScore,
    double performanceScore,
    double compositeScore,
    String confidenceLevel
) {
    public static final String HIGH = "HIGH";
    public static final String MEDIUM = "MEDIUM";
    public static final String LOW = "LOW";
}
