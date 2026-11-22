package com.asalkartechworks.himmat.purchase.service;

import com.asalkartechworks.himmat.common.errors.AppException;
import com.asalkartechworks.himmat.common.errors.ResourceNotFoundException;
import com.asalkartechworks.himmat.common.money.MoneyPolicy;
import com.asalkartechworks.himmat.common.time.BusinessClock;
import com.asalkartechworks.himmat.party.model.Supplier;
import com.asalkartechworks.himmat.party.repository.SupplierRepository;
import com.asalkartechworks.himmat.purchase.dto.CreateSupplierPaymentRequest;
import com.asalkartechworks.himmat.purchase.dto.PaymentAllocationDto;
import com.asalkartechworks.himmat.purchase.dto.PaymentAllocationRequest;
import com.asalkartechworks.himmat.purchase.dto.PaymentComponentDto;
import com.asalkartechworks.himmat.purchase.dto.SupplierPaymentResponseDto;
import com.asalkartechworks.himmat.purchase.model.PaymentAllocation;
import com.asalkartechworks.himmat.purchase.model.PaymentComponent;
import com.asalkartechworks.himmat.purchase.model.Purchase;
import com.asalkartechworks.himmat.purchase.model.PurchaseStatus;
import com.asalkartechworks.himmat.purchase.model.SupplierLedgerSourceType;
import com.asalkartechworks.himmat.purchase.model.SupplierPayment;
import com.asalkartechworks.himmat.purchase.repository.PaymentAllocationRepository;
import com.asalkartechworks.himmat.purchase.repository.PurchaseRepository;
import com.asalkartechworks.himmat.purchase.repository.SupplierPaymentRepository;
import com.asalkartechworks.himmat.shared.idempotency.model.BusinessEvent;
import com.asalkartechworks.himmat.shared.transaction.BusinessTransactionExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierPaymentService {

    private final SupplierPaymentRepository supplierPaymentRepository;
    private final PaymentAllocationRepository paymentAllocationRepository;
    private final PurchaseRepository purchaseRepository;
    private final SupplierRepository supplierRepository;
    private final SupplierLedgerService supplierLedgerService;
    private final BusinessTransactionExecutor businessTransactionExecutor;
    private final BusinessClock businessClock;

    public SupplierPaymentResponseDto createPayment(String idempotencyKey, Long supplierId, Long actorId, CreateSupplierPaymentRequest request) {
        return businessTransactionExecutor.executeTransaction(
                idempotencyKey,
                actorId,
                "SUPPLIER_PAYMENT",
                "Supplier payment recording",
                "POST",
                "/api/v1/suppliers/" + supplierId + "/payments",
                request,
                SupplierPaymentResponseDto.class,
                event -> doCreatePayment(event, supplierId, actorId, request)
        );
    }

    private SupplierPaymentResponseDto doCreatePayment(BusinessEvent event, Long supplierId, Long actorId, CreateSupplierPaymentRequest request) {
        // 1. Lock Supplier (Party Lock first)
        Supplier supplier = supplierRepository.findByIdWithLock(supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found with ID: " + supplierId));

        if (!supplier.isActive()) {
            throw new AppException("INACTIVE_SUPPLIER", "Cannot record payment for inactive supplier", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        SupplierPayment payment = processPaymentDirect(event, supplier, actorId, request);
        return toDto(payment);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public SupplierPayment processPaymentDirect(BusinessEvent event, Supplier supplier, Long actorId, CreateSupplierPaymentRequest request) {
        if (request.getComponents() == null || request.getComponents().isEmpty()) {
            throw new AppException("VALIDATION_FAILED", "At least one payment component is required", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        // Validate component total matches totalAmount
        BigDecimal componentTotal = request.getComponents().stream()
                .map(PaymentComponentDto::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (MoneyPolicy.roundMoney(componentTotal).compareTo(MoneyPolicy.roundMoney(request.getTotalAmount())) != 0) {
            throw new AppException("VALIDATION_FAILED",
                    "Payment components total (" + componentTotal + ") must equal totalAmount (" + request.getTotalAmount() + ")",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        Instant now = businessClock.now();
        LocalDate paymentDate = request.getPaymentDate() != null ? request.getPaymentDate() : businessClock.today();

        Long nextSeq = supplierPaymentRepository.getNextPaymentSequence();
        String paymentNumber = String.format("SPAY-%05d", nextSeq);

        BigDecimal totalAllocated = BigDecimal.ZERO;
        List<PaymentAllocation> allocationsToSave = new ArrayList<>();

        if (request.getAllocations() != null && !request.getAllocations().isEmpty()) {
            for (PaymentAllocationRequest allocReq : request.getAllocations()) {
                if (allocReq.getAmount() == null || allocReq.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new AppException("VALIDATION_FAILED", "Allocation amount must be greater than zero", HttpStatus.UNPROCESSABLE_ENTITY);
                }

                Purchase purchase = purchaseRepository.findByIdWithLock(allocReq.getPurchaseId())
                        .orElseThrow(() -> new ResourceNotFoundException("Purchase not found with ID: " + allocReq.getPurchaseId()));

                if (!purchase.getSupplier().getId().equals(supplier.getId())) {
                    throw new AppException("INVALID_PURCHASE_SUPPLIER", "Purchase does not belong to this supplier", HttpStatus.UNPROCESSABLE_ENTITY);
                }

                if (purchase.getStatus() != PurchaseStatus.POSTED) {
                    throw new AppException("INVALID_PURCHASE_STATUS", "Cannot allocate payment to non-posted purchase", HttpStatus.UNPROCESSABLE_ENTITY);
                }

                BigDecimal outstanding = purchase.getTotalPayableAmount().subtract(
                        purchase.getTotalAllocatedAmount() != null ? purchase.getTotalAllocatedAmount() : BigDecimal.ZERO
                );

                if (allocReq.getAmount().compareTo(outstanding) > 0) {
                    throw new AppException("EXCESS_ALLOCATION",
                            "Allocation amount (" + allocReq.getAmount() + ") exceeds purchase outstanding balance (" + outstanding + ")",
                            HttpStatus.UNPROCESSABLE_ENTITY);
                }

                totalAllocated = totalAllocated.add(allocReq.getAmount());
                purchase.setTotalAllocatedAmount(purchase.getTotalAllocatedAmount().add(allocReq.getAmount()));
                purchaseRepository.save(purchase);

                allocationsToSave.add(PaymentAllocation.builder()
                        .purchase(purchase)
                        .allocatedAmount(MoneyPolicy.roundMoney(allocReq.getAmount()))
                        .businessEvent(event)
                        .createdAt(now)
                        .build());
            }

            if (totalAllocated.compareTo(request.getTotalAmount()) > 0) {
                throw new AppException("EXCESS_TOTAL_ALLOCATION",
                        "Sum of allocations (" + totalAllocated + ") cannot exceed payment total amount (" + request.getTotalAmount() + ")",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }
        }

        BigDecimal unallocated = MoneyPolicy.roundMoney(request.getTotalAmount().subtract(totalAllocated));

        SupplierPayment payment = SupplierPayment.builder()
                .paymentNumber(paymentNumber)
                .supplier(supplier)
                .paymentDate(paymentDate)
                .totalAmount(MoneyPolicy.roundMoney(request.getTotalAmount()))
                .unallocatedAmount(unallocated)
                .notes(request.getNotes())
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
            payment.addComponent(comp);
        }

        SupplierPayment savedPayment = supplierPaymentRepository.save(payment);

        for (PaymentAllocation alloc : allocationsToSave) {
            alloc.setSupplierPayment(savedPayment);
            paymentAllocationRepository.save(alloc);
        }

        // Post to Supplier Ledger (-totalAmount, negative = reduces payable / creates credit)
        supplierLedgerService.appendLedgerEntry(
                supplier,
                event,
                SupplierLedgerSourceType.PAYMENT,
                savedPayment.getId(),
                savedPayment.getTotalAmount().negate(),
                paymentDate,
                "Supplier payment: " + paymentNumber
        );

        return savedPayment;
    }

    @Transactional(readOnly = true)
    public SupplierPaymentResponseDto getPaymentById(Long id) {
        SupplierPayment payment = supplierPaymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with ID: " + id));
        return toDto(payment);
    }

    @Transactional(readOnly = true)
    public Page<SupplierPaymentResponseDto> searchPayments(Long supplierId, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        return supplierPaymentRepository.searchPayments(supplierId, startDate, endDate, pageable)
                .map(this::toDto);
    }

    public SupplierPaymentResponseDto toDto(SupplierPayment payment) {
        List<PaymentComponentDto> compDtos = payment.getComponents().stream()
                .map(c -> PaymentComponentDto.builder()
                        .method(c.getMethod())
                        .amount(c.getAmount())
                        .onlineReference(c.getOnlineReference())
                        .build())
                .toList();

        List<PaymentAllocation> allocations = paymentAllocationRepository.findBySupplierPaymentId(payment.getId());
        List<PaymentAllocationDto> allocDtos = allocations.stream()
                .map(a -> PaymentAllocationDto.builder()
                        .id(a.getId())
                        .purchaseId(a.getPurchase().getId())
                        .purchaseNumber(a.getPurchase().getPurchaseNumber())
                        .allocatedAmount(a.getAllocatedAmount())
                        .createdAt(a.getCreatedAt())
                        .build())
                .toList();

        return SupplierPaymentResponseDto.builder()
                .id(payment.getId())
                .paymentNumber(payment.getPaymentNumber())
                .supplierId(payment.getSupplier().getId())
                .supplierName(payment.getSupplier().getName())
                .paymentDate(payment.getPaymentDate())
                .totalAmount(payment.getTotalAmount())
                .unallocatedAmount(payment.getUnallocatedAmount())
                .notes(payment.getNotes())
                .components(compDtos)
                .allocations(allocDtos)
                .version(payment.getVersion())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}
