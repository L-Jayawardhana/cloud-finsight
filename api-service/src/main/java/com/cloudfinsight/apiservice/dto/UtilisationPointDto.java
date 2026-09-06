package com.cloudfinsight.apiservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UtilisationPointDto(
    LocalDate date,
    BigDecimal cpuP50,
    BigDecimal cpuP95,
    BigDecimal cpuMax,
    BigDecimal memP50,
    BigDecimal memP95,
    BigDecimal memMax
) {}
