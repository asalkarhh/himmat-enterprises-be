package com.asalkartechworks.himmat.purchase.service;

import com.asalkartechworks.himmat.common.errors.AppException;
import com.asalkartechworks.himmat.common.errors.ResourceNotFoundException;
import com.asalkartechworks.himmat.common.money.MoneyPolicy;
import com.asalkartechworks.himmat.common.time.BusinessClock;
import com.asalkartechworks.himmat.party.model.Supplier;
import com.asalkartechworks.himmat.party.repository.SupplierRepository;
import com.asalkartechworks.himmat.purchase.dto.CreateSupplierRefundRequest;
import com.asalkartechworks.himmat.purchase.dto.PaymentComponentDto;
import com.asalkartechworks.himmat.purchase.dto.SupplierRefundResponseDto;
import com.asalkartechworks.himmat.purchase.model.PaymentComponent;
import com.asalkartechworks.himmat.purchase.model.SupplierLedgerEntry;
import com.asalkartechworks.himmat.purchase.model.SupplierLedgerSourceType;
import com.asalkartechworks.himmat.purchase.model.SupplierPayment;
import com.asalkartechworks.himmat.purchase.model.SupplierRefund;
import com.asalkartechworks.himmat.purchase.repository.SupplierLedgerEntryRepository;
import com.asalkartechworks.himmat.purchase.repository.SupplierPaymentRepository;
import com.asalkartechworks.himmat.purchase.repository.SupplierRefundRepository;
import com.asalkartechworks.himmat.shared.idempotency.model.BusinessEvent;
import com.asalkartechworks.himmat.shared.transaction.BusinessTransactionExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierRefundService {

    private final SupplierRepository supplierRepository;
    private final SupplierRefundRepository supplierRefundRepository;
    private final SupplierPaymentRepository supplierPaymentRepository;
    private final SupplierLedgerEntryRepository supplierLedgerEntryRepository;
    private final SupplierLedgerService supplierLedgerService;
    private final BusinessTransactionExecutor businessTransactionExecutor;
    private final BusinessClock businessClock;

    public SupplierRefundResponseDto createRefund(String idempotencyKey, Long supplierId, Long actorId, CreateSupplierRefundRequest request) {
        return businessTransactionExecutor.executeTransaction(
                idempotencyKey,
                actorId,
                "SUPPLIER_REFUND",
                "Supplier refund processing",
                "POST",
                "/api/v1/suppliers/" + supplierId + "/refunds",
                request,
                SupplierRefundResponseDto.class,
                event -> doCreateRefund(event, supplierId, actorId, request)
        );
    }

    private SupplierRefundResponseDto doCreateRefund(BusinessEvent event, Long supplierId, Long actorId, CreateSupplierRefundRequest request) {
        // 1. Lock Supplier (Party Lock first)
        Supplier supplier = supplierRepository.findByIdWithLock(supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found with ID: " + supplierId));

        if (!supplier.isActive()) {
            throw new AppException("INACTIVE_SUPPLIER", "Cannot record refund for inactive supplier", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        if (request.getComponents() == null || request.getComponents().isEmpty()) {
            throw new AppException("VALIDATION_FAILED", "At least one refund component is required", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        // Validate component total matches totalAmount
        BigDecimal componentTotal = request.getComponents().stream()
                .map(PaymentComponentDto::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (MoneyPolicy.roundMoney(componentTotal).compareTo(MoneyPolicy.roundMoney(request.getTotalAmount())) != 0) {
            throw new AppException("VALIDATION_FAILED",
                    "Refund components total (" + componentTotal + ") must equal totalAmount (" + request.getTotalAmount() + ")",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        // 2. Validate available supplier credit / negative ledger balance
        Optional<SupplierLedgerEntry> lastEntryOpt = supplierLedgerEntryRepository.findFirstBySupplierIdOrderByIdDesc(supplierId);
        BigDecimal currentBalance = lastEntryOpt.map(SupplierLedgerEntry::getRunningBalance).orElse(BigDecimal.ZERO);

        // A negative balance means the supplier owes the shop or holds shop advance
        BigDecimal creditFromBalance = currentBalance.compareTo(BigDecimal.ZERO) < 0 ? currentBalance.abs() : BigDecimal.ZERO;

        List<SupplierPayment> unallocatedPayments = supplierPaymentRepository.findUnallocatedPaymentsBySupplierWithLock(supplierId);
        BigDecimal unallocatedPaymentTotal = unallocatedPayments.stream()
                .map(SupplierPayment::getUnallocatedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal availableCredit = creditFromBalance.max(unallocatedPaymentTotal);

        if (request.getTotalAmount().compareTo(availableCredit) > 0) {
            throw new AppException("INSUFFICIENT_SUPPLIER_CREDIT",
                    "Refund amount (" + request.getTotalAmount() + ") exceeds available supplier credit (" + availableCredit + ")",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        // 3. Consume unallocated payment amounts if available
        BigDecimal remainingToConsume = request.getTotalAmount();
        for (SupplierPayment payment : unallocatedPayments) {
            if (remainingToConsume.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal unalloc = payment.getUnallocatedAmount();
            if (unalloc.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal consumeThis = remainingToConsume.min(unalloc);
                payment.setUnallocatedAmount(unalloc.subtract(consumeThis));
                supplierPaymentRepository.save(payment);
                remainingToConsume = remainingToConsume.subtract(consumeThis);
            }
        }

        Instant now = businessClock.now();
        LocalDate refundDate = request.getRefundDate() != null ? request.getRefundDate() : businessClock.today();

        // 4. Generate Refund Number (SRF-%05d)
        Long nextSeq = supplierRefundRepository.getNextRefundSequence();
        String refundNumber = String.format("SRF-%05d", nextSeq);

        // 5. Save Refund Entity
        SupplierRefund refund = SupplierRefund.builder()
                .refundNumber(refundNumber)
                .supplier(supplier)
                .refundDate(refundDate)
                .totalAmount(MoneyPolicy.roundMoney(request.getTotalAmount()))
                .reason(request.getReason())
                .businessEvent(event)
                .createdAt(now)
                .updatedAt(now)
                .createdBy(actorId)
                .updatedBy(actorId)
                .build();

        for (PaymentComponentDto compDto : request.getComponents()) {
            PaymentComponent comp = PaymentComponent.builder()
                    .method(compDto.getMethod())
                    .amount(MoneyPolicy.roundMoney(compDto.getAmount()))
                    .onlineReference(compDto.getOnlineReference())
                    .build();
            refund.addComponent(comp);
        }

        SupplierRefund savedRefund = supplierRefundRepository.save(refund);

        // 6. Post to Supplier Ledger (+totalAmount, positive = shop received refund, reducing credit towards 0)
        supplierLedgerService.appendLedgerEntry(
                supplier,
                event,
                SupplierLedgerSourceType.REFUND,
                savedRefund.getId(),
                savedRefund.getTotalAmount(),
                refundDate,
                "Supplier refund: " + refundNumber
        );

        return toDto(savedRefund);
    }

    @Transactional(readOnly = true)
    public SupplierRefundResponseDto getRefundById(Long id) {
        SupplierRefund refund = supplierRefundRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Refund not found with ID: " + id));
        return toDto(refund);
    }

    public SupplierRefundResponseDto toDto(SupplierRefund refund) {
        List<PaymentComponentDto> compDtos = refund.getComponents().stream()
                .map(c -> PaymentComponentDto.builder()
                        .method(c.getMethod())
                        .amount(c.getAmount())
                        .onlineReference(c.getOnlineReference())
                        .build())
                .toList();

        return SupplierRefundResponseDto.builder()
                .id(refund.getId())
                .refundNumber(refund.getRefundNumber())
                .supplierId(refund.getSupplier().getId())
                .supplierName(refund.getSupplier().getName())
                .refundDate(refund.getRefundDate())
                .totalAmount(refund.getTotalAmount())
                .reason(refund.getReason())
                .components(compDtos)
                .version(refund.getVersion())
                .createdAt(refund.getCreatedAt())
                .build();
    }
}
