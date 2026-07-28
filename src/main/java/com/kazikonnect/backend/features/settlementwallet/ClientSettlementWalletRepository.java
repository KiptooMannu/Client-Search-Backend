package com.kazikonnect.backend.features.settlementwallet;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientSettlementWalletRepository extends JpaRepository<ClientSettlementWallet, UUID> {
    
    Optional<ClientSettlementWallet> findByUserId(UUID userId);
    
    Optional<ClientSettlementWallet> findByUser_Id(UUID userId);
    
    boolean existsByUserId(UUID userId);
}
