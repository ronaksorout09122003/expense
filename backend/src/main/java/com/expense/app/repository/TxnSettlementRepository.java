package com.expense.app.repository;

import com.expense.app.entity.TxnSettlement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TxnSettlementRepository extends JpaRepository<TxnSettlement, UUID> {

    List<TxnSettlement> findByUserId(UUID userId);

    List<TxnSettlement> findByUserIdAndBaseTxnId(UUID userId, UUID baseTxnId);

    List<TxnSettlement> findByUserIdAndSettlementTxnId(UUID userId, UUID settlementTxnId);

    Optional<TxnSettlement> findByBaseTxnIdAndSettlementTxnId(UUID baseTxnId, UUID settlementTxnId);
}
