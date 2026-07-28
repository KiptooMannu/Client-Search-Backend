package com.kazikonnect.backend.features.platformwallet;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlatformWalletRepository extends JpaRepository<PlatformWallet, UUID> {
    
    Optional<PlatformWallet> findFirstByOrderByIdAsc();
}
