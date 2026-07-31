package com.cloudfinsight.collectorservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PrometheusData(
    @JsonProperty("resultType") String resultType,
    @JsonProperty("result") List<PrometheusResult> result
) {}
