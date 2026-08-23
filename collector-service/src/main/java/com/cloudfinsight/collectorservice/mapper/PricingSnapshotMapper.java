package com.cloudfinsight.collectorservice.mapper;

import com.cloudfinsight.collectorservice.client.dto.RetailPricingRecord;
import com.cloudfinsight.collectorservice.entity.PricingSnapshot;
import org.springframework.stereotype.Component;

@Component
public class PricingSnapshotMapper {

    public PricingSnapshot toEntity(RetailPricingRecord record) {
        PricingSnapshot snapshot = new PricingSnapshot();
        snapshot.setArmSkuName(record.armSkuName());
        snapshot.setRegion(record.armRegionName());
        snapshot.setOsType("Linux"); // filtered to exclude Windows in the client
        snapshot.setRetailPrice(record.retailPrice());
        snapshot.setCurrency(record.currencyCode());
        snapshot.setEffectiveStartDate(record.effectiveStartDate());
        return snapshot;
    }
}