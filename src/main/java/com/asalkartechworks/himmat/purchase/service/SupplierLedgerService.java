package com.asalkartechworks.himmat.purchase.service;

import com.asalkartechworks.himmat.common.dto.PageResponse;
import com.asalkartechworks.himmat.common.errors.ResourceNotFoundException;
import com.asalkartechworks.himmat.common.money.MoneyPolicy;
import com.asalkartechworks.himmat.common.time.BusinessClock;
import com.asalkartechworks.himmat.party.model.Supplier;
import com.asalkartechworks.himmat.party.repository.SupplierRepository;
import com.asalkartechworks.himmat.purchase.dto.SupplierAccountSummaryDto;
import com.asalkartechworks.himmat.purchase.dto.SupplierLedgerEntryDto;
import com.asalkartechworks.himmat.purchase.dto.SupplierLedgerStatementDto;
import com.asalkartechworks.himmat.purchase.model.Purchase;
import com.asalkartechworks.himmat.purchase.model.SupplierLedgerEntry;
import com.asalkartechworks.himmat.purchase.model.SupplierLedgerSourceType;
import com.asalkartechworks.himmat.purchase.model.SupplierPayment;
import com.asalkartechworks.himmat.purchase.repository.PurchaseRepository;
import com.asalkartechworks.himmat.purchase.repository.SupplierLedgerEntryRepository;
import com.asalkartechworks.himmat.purchase.repository.SupplierPaymentRepository;
import com.asalkartechworks.himmat.shared.idempotency.model.BusinessEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierLedgerService {

    private final SupplierRepository supplierRepository;
    private final SupplierLedgerEntryRepository supplierLedgerEntryRepository;
    private final SupplierPaymentRepository supplierPaymentRepository;
    private final PurchaseRepository purchaseRepository;
    private final BusinessClock businessClock;

    @Transactional(propagation = Propagation.MANDATORY)
    public SupplierLedgerEntry appendLedgerEntry(Supplier supplier,
                                                BusinessEvent event,
                                                SupplierLedgerSourceType sourceType,
                                                Long sourceId,
                                                BigDecimal signedAmount,
                                                LocalDate effectiveDate,
                                                String notes) {
        BigDecimal roundedAmount = MoneyPolicy.roundMoney(signedAmount);

        Optional<SupplierLedgerEntry> lastEntryOpt = supplierLedgerEntryRepository.findFirstBySupplierIdOrderByIdDesc(supplier.getId());
        BigDecimal previousBalance = lastEntryOpt.map(SupplierLedgerEntry::getRunningBalance).orElse(BigDecimal.ZERO);
        BigDecimal newRunningBalance = MoneyPolicy.roundMoney(previousBalance.add(roundedAmount));

        SupplierLedgerEntry entry = SupplierLedgerEntry.builder()
                .supplier(supplier)
                .businessEvent(event)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .amount(roundedAmount)
                .runningBalance(newRunningBalance)
                .effectiveDate(effectiveDate != null ? effectiveDate : businessClock.today())
                .notes(notes)
                .createdAt(businessClock.now())
                .build();

        return supplierLedgerEntryRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public SupplierAccountSummaryDto getAccountSummary(Long supplierId) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found with ID: " + supplierId));

        Optional<SupplierLedgerEntry> lastEntryOpt = supplierLedgerEntryRepository.findFirstBySupplierIdOrderByIdDesc(supplierId);
        BigDecimal currentBalance = lastEntryOpt.map(SupplierLedgerEntry::getRunningBalance).orElse(BigDecimal.ZERO);

        BigDecimal totalPurchases = supplierLedgerEntryRepository.calculateTotalPurchases(supplierId);
        BigDecimal totalPaid = supplierLedgerEntryRepository.calculateTotalPayments(supplierId).abs();
        BigDecimal totalReturned = supplierLedgerEntryRepository.calculateTotalReturns(supplierId).abs();
        BigDecimal totalRefunded = supplierLedgerEntryRepository.calculateTotalRefunds(supplierId);

        // Sum unallocated amounts from supplier payments
        List<SupplierPayment> unallocatedPayments = supplierPaymentRepository.findUnallocatedPaymentsBySupplier(supplierId);
        BigDecimal unallocatedCredit = unallocatedPayments.stream()
                .map(SupplierPayment::getUnallocatedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Sum outstanding payable from purchases
        List<Purchase> unpaidPurchases = purchaseRepository.findUnpaidPurchasesBySupplier(supplierId);
        BigDecimal outstandingPayable = unpaidPurchases.stream()
                .map(p -> p.getTotalPayableAmount().subtract(p.getTotalAllocatedAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return SupplierAccountSummaryDto.builder()
                .supplierId(supplier.getId())
                .supplierCode(supplier.getSupplierCode())
                .supplierName(supplier.getName())
                .currentBalance(MoneyPolicy.roundMoney(currentBalance))
                .totalPurchases(MoneyPolicy.roundMoney(totalPurchases))
                .totalPaid(MoneyPolicy.roundMoney(totalPaid))
                .totalReturned(MoneyPolicy.roundMoney(totalReturned))
                .totalRefunded(MoneyPolicy.roundMoney(totalRefunded))
                .unallocatedCredit(MoneyPolicy.roundMoney(unallocatedCredit))
                .outstandingPayable(MoneyPolicy.roundMoney(outstandingPayable))
                .build();
    }

    @Transactional(readOnly = true)
    public SupplierLedgerStatementDto getLedgerStatement(Long supplierId, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found with ID: " + supplierId));

        LocalDate effectiveStart = startDate != null ? startDate : businessClock.today().withDayOfMonth(1);
        LocalDate effectiveEnd = endDate != null ? endDate : businessClock.today();

        BigDecimal openingBalance = MoneyPolicy.roundMoney(
                supplierLedgerEntryRepository.calculateOpeningBalance(supplierId, effectiveStart)
        );

        Page<SupplierLedgerEntry> entryPage = supplierLedgerEntryRepository.findBySupplierIdAndDateRange(
                supplierId, effectiveStart, effectiveEnd, pageable
        );

        Page<SupplierLedgerEntryDto> dtoPage = entryPage.map(this::toDto);

        BigDecimal closingBalance = MoneyPolicy.roundMoney(
                supplierLedgerEntryRepository.calculateOpeningBalance(supplierId, effectiveEnd.plusDays(1))
        );

        return SupplierLedgerStatementDto.builder()
                .supplierId(supplier.getId())
                .supplierCode(supplier.getSupplierCode())
                .supplierName(supplier.getName())
                .startDate(effectiveStart)
                .endDate(effectiveEnd)
                .openingBalance(openingBalance)
                .closingBalance(closingBalance)
                .entries(PageResponse.from(dtoPage))
                .build();
    }

    public SupplierLedgerEntryDto toDto(SupplierLedgerEntry entry) {
        return SupplierLedgerEntryDto.builder()
                .id(entry.getId())
                .businessEventId(entry.getBusinessEvent().getId())
                .sourceType(entry.getSourceType())
                .sourceId(entry.getSourceId())
                .amount(entry.getAmount())
                .runningBalance(entry.getRunningBalance())
                .effectiveDate(entry.getEffectiveDate())
                .notes(entry.getNotes())
                .createdAt(entry.getCreatedAt())
                .build();
    }
}
