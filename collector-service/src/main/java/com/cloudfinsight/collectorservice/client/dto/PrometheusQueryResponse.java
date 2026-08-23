package com.cloudfinsight.collectorservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PrometheusQueryResponse(
    @JsonProperty("status") String status,
    @JsonProperty("data") PrometheusData data
) {}
