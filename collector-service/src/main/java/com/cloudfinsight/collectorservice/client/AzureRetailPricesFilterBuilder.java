package com.cloudfinsight.collectorservice.client;

public class AzureRetailPricesFilterBuilder {

    public static String build(String armSkuName, String region) {
        return "armRegionName eq '" + region + "'"
            + " and armSkuName eq '" + armSkuName + "'"
            + " and priceType eq 'Consumption'"
            + " and serviceName eq 'Virtual Machines'";
    }
}