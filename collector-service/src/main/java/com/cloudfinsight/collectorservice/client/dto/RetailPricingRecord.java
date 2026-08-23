package com.cloudfinsight.collectorservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RetailPricingRecord(
    @JsonProperty("armSkuName") String armSkuName,
    @JsonProperty("armRegionName") String armRegionName,
    @JsonProperty("retailPrice") BigDecimal retailPrice,
    @JsonProperty("currencyCode") String currencyCode,
    @JsonProperty("productName") String productName,
    @JsonProperty("meterName") String meterName,
    @JsonProperty("serviceName") String serviceName,
    @JsonProperty("priceType") String priceType,
    @JsonProperty("effectiveStartDate") OffsetDateTime effectiveStartDate
) {}