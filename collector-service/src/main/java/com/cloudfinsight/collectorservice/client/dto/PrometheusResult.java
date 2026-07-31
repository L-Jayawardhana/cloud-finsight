package com.cloudfinsight.collectorservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PrometheusResult(
    @JsonProperty("metric") Map<String, String> metric,
    @JsonProperty("value") List<Object> value
) {}
