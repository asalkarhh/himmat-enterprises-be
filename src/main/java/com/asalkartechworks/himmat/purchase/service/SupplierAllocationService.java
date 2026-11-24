package com.asalkartechworks.himmat.purchase.service;

import com.asalkartechworks.himmat.common.errors.AppException;
import com.asalkartechworks.himmat.common.errors.ResourceNotFoundException;
import com.asalkartechworks.himmat.common.money.MoneyPolicy;
import com.asalkartechworks.himmat.common.time.BusinessClock;
import com.asalkartechworks.himmat.party.model.Supplier;
import com.asalkartechworks.himmat.party.repository.SupplierRepository;
import com.asalkartechworks.himmat.purchase.dto.AllocationItemRequest;
import com.asalkartechworks.himmat.purchase.dto.ApplySupplierAllocationRequest;
import com.asalkartechworks.himmat.purchase.dto.PaymentAllocationDto;
import com.asalkartechworks.himmat.purchase.model.PaymentAllocation;
import com.asalkartechworks.himmat.purchase.model.Purchase;
import com.asalkartechworks.himmat.purchase.model.PurchaseStatus;
import com.asalkartechworks.himmat.purchase.model.SupplierPayment;
import com.asalkartechworks.himmat.purchase.repository.PaymentAllocationRepository;
import com.asalkartechworks.himmat.purchase.repository.PurchaseRepository;
import com.asalkartechworks.himmat.purchase.repository.SupplierPaymentRepository;
import com.asalkartechworks.himmat.shared.idempotency.model.BusinessEvent;
import com.asalkartechworks.himmat.shared.transaction.BusinessTransactionExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierAllocationService {

    private final SupplierRepository supplierRepository;
    private final SupplierPaymentRepository supplierPaymentRepository;
    private final PurchaseRepository purchaseRepository;
    private final PaymentAllocationRepository paymentAllocationRepository;
    private final BusinessTransactionExecutor businessTransactionExecutor;
    private final BusinessClock businessClock;

    public List<PaymentAllocationDto> applyAllocations(String idempotencyKey, Long supplierId, Long actorId, ApplySupplierAllocationRequest request) {
        @SuppressWarnings("unchecked")
        Class<List<PaymentAllocationDto>> respType = (Class<List<PaymentAllocationDto>>) (Class<?>) List.class;

        return businessTransactionExecutor.executeTransaction(
                idempotencyKey,
                actorId,
                "SUPPLIER_ALLOCATION_APPLY",
                "Apply unallocated supplier credit to purchases",
                "POST",
                "/api/v1/suppliers/" + supplierId + "/allocations",
                request,
                respType,
                event -> doApplyAllocations(event, supplierId, actorId, request)
        );
    }

    private List<PaymentAllocationDto> doApplyAllocations(BusinessEvent event, Long supplierId, Long actorId, ApplySupplierAllocationRequest request) {
        // 1. Lock Supplier (Party Lock first)
        Supplier supplier = supplierRepository.findByIdWithLock(supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found with ID: " + supplierId));

        if (!supplier.isActive()) {
            throw new AppException("INACTIVE_SUPPLIER", "Cannot allocate credit for inactive supplier", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        Instant now = businessClock.now();
        List<PaymentAllocationDto> result = new ArrayList<>();

        for (AllocationItemRequest item : request.getAllocations()) {
            SupplierPayment payment = supplierPaymentRepository.findByIdWithLock(item.getPaymentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Payment not found with ID: " + item.getPaymentId()));

            if (!payment.getSupplier().getId().equals(supplier.getId())) {
                throw new AppException("INVALID_PAYMENT_SUPPLIER", "Payment does not belong to this supplier", HttpStatus.UNPROCESSABLE_ENTITY);
            }

            if (item.getAmount().compareTo(payment.getUnallocatedAmount()) > 0) {
                throw new AppException("INSUFFICIENT_UNALLOCATED_CREDIT",
                        "Requested allocation (" + item.getAmount() + ") exceeds available payment credit (" + payment.getUnallocatedAmount() + ")",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }

            Purchase purchase = purchaseRepository.findByIdWithLock(item.getPurchaseId())
                    .orElseThrow(() -> new ResourceNotFoundException("Purchase not found with ID: " + item.getPurchaseId()));

            if (!purchase.getSupplier().getId().equals(supplier.getId())) {
                throw new AppException("INVALID_PURCHASE_SUPPLIER", "Purchase does not belong to this supplier", HttpStatus.UNPROCESSABLE_ENTITY);
            }

            if (purchase.getStatus() != PurchaseStatus.POSTED) {
                throw new AppException("INVALID_PURCHASE_STATUS", "Cannot allocate payment to non-posted purchase", HttpStatus.UNPROCESSABLE_ENTITY);
            }

            BigDecimal outstanding = purchase.getOutstandingAmount();

            if (item.getAmount().compareTo(outstanding) > 0) {
                throw new AppException("EXCESS_ALLOCATION",
                        "Allocation amount (" + item.getAmount() + ") exceeds purchase outstanding balance (" + outstanding + ")",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }

            // Deduct from payment credit
            payment.setUnallocatedAmount(MoneyPolicy.roundMoney(payment.getUnallocatedAmount().subtract(item.getAmount())));
            payment.setUpdatedAt(now);
            payment.setUpdatedBy(actorId);
            supplierPaymentRepository.save(payment);

            // Add to purchase allocation
            purchase.setTotalAllocatedAmount(MoneyPolicy.roundMoney(purchase.getTotalAllocatedAmount().add(item.getAmount())));
            purchase.setUpdatedAt(now);
            purchase.setUpdatedBy(actorId);
            purchaseRepository.save(purchase);

            // Record allocation
            PaymentAllocation allocation = PaymentAllocation.builder()
                    .supplierPayment(payment)
                    .purchase(purchase)
                    .allocatedAmount(MoneyPolicy.roundMoney(item.getAmount()))
                    .businessEvent(event)
                    .createdAt(now)
                    .build();

            PaymentAllocation savedAlloc = paymentAllocationRepository.save(allocation);

            result.add(PaymentAllocationDto.builder()
                    .id(savedAlloc.getId())
                    .purchaseId(purchase.getId())
                    .purchaseNumber(purchase.getPurchaseNumber())
                    .allocatedAmount(savedAlloc.getAllocatedAmount())
                    .createdAt(savedAlloc.getCreatedAt())
                    .build());
        }

        // NOTE: Internal allocation between unallocated advance and purchase document does NOT change
        // net money owed, so no new ledger entry is posted.

        return result;
    }
}
