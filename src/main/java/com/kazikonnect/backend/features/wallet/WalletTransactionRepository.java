package com.kazikonnect.backend.features.wallet;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, UUID> {

    List<WalletTransaction> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
}
