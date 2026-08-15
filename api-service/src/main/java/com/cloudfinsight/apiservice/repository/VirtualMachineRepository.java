package com.cloudfinsight.apiservice.repository;

import com.cloudfinsight.apiservice.entity.VirtualMachine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VirtualMachineRepository extends JpaRepository<VirtualMachine, Long> {
    Optional<VirtualMachine> findByAzureResourceId(String azureResourceId);
}