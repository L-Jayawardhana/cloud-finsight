package com.cloudfinsight.collectorservice.client;

import com.cloudfinsight.collectorservice.client.dto.RetailPricesResponse;
import com.cloudfinsight.collectorservice.client.dto.RetailPricingRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RetailPricingRecordMappingTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void parsesRealAzureRetailPricesResponseShape() throws Exception {
        String json = """
            {
                "Items": [
                    {
                        "armSkuName": "Standard_D2s_v4",
                        "armRegionName": "southeastasia",
                        "retailPrice": 0.12,
                        "currencyCode": "USD",
                        "productName": "Virtual Machines Dsv4 Series",
                        "meterName": "D2s v4",
                        "serviceName": "Virtual Machines",
                        "priceType": "Consumption",
                        "effectiveStartDate": "2021-11-01T00:00:00Z"
                    }
                ],
                "NextPageLink": null
            }
            """;

        RetailPricesResponse response = objectMapper.readValue(json, RetailPricesResponse.class);

        assertThat(response.items()).hasSize(1);
        assertThat(response.nextPageLink()).isNull();

        RetailPricingRecord record = response.items().get(0);
        assertThat(record.armSkuName()).isEqualTo("Standard_D2s_v4");
        assertThat(record.retailPrice()).isEqualByComparingTo(new BigDecimal("0.12"));
        assertThat(record.serviceName()).isEqualTo("Virtual Machines");
    }

    @Test
    void ignoresUnknownFieldsInResponse() throws Exception {
        String json = """
            {
                "Items": [
                    {
                        "armSkuName": "Standard_D2s_v3",
                        "armRegionName": "southeastasia",
                        "retailPrice": 0.125,
                        "currencyCode": "USD",
                        "productName": "Virtual Machines Dsv3 Series",
                        "meterName": "D2s v3",
                        "serviceName": "Virtual Machines",
                        "priceType": "Consumption",
                        "unitOfMeasure": "1 Hour",
                        "skuId": "DZH318Z0BQPS/0064"
                    }
                ],
                "NextPageLink": "https://prices.azure.com/api/retail/prices?$skip=100"
            }
            """;

        RetailPricesResponse response = objectMapper.readValue(json, RetailPricesResponse.class);

        assertThat(response.nextPageLink()).contains("$skip=100");
        assertThat(response.items().get(0).armSkuName()).isEqualTo("Standard_D2s_v3");
    }
}
