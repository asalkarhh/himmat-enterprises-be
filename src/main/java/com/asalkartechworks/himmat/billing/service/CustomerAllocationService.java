package com.asalkartechworks.himmat.billing.service;

import com.asalkartechworks.himmat.billing.dto.ApplyCustomerAllocationRequest;
import com.asalkartechworks.himmat.billing.dto.CustomerAllocationItemRequest;
import com.asalkartechworks.himmat.billing.dto.ReceiptAllocationDto;
import com.asalkartechworks.himmat.billing.model.CustomerReceipt;
import com.asalkartechworks.himmat.billing.model.InvoiceStatus;
import com.asalkartechworks.himmat.billing.model.ReceiptAllocation;
import com.asalkartechworks.himmat.billing.model.SalesInvoice;
import com.asalkartechworks.himmat.billing.model.SettlementStatus;
import com.asalkartechworks.himmat.billing.repository.CustomerReceiptRepository;
import com.asalkartechworks.himmat.billing.repository.ReceiptAllocationRepository;
import com.asalkartechworks.himmat.billing.repository.SalesInvoiceRepository;
import com.asalkartechworks.himmat.common.errors.AppException;
import com.asalkartechworks.himmat.common.errors.ResourceNotFoundException;
import com.asalkartechworks.himmat.common.money.MoneyPolicy;
import com.asalkartechworks.himmat.common.time.BusinessClock;
import com.asalkartechworks.himmat.party.model.Customer;
import com.asalkartechworks.himmat.party.repository.CustomerRepository;
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
public class CustomerAllocationService {

    private final CustomerRepository customerRepository;
    private final CustomerReceiptRepository customerReceiptRepository;
    private final SalesInvoiceRepository salesInvoiceRepository;
    private final ReceiptAllocationRepository receiptAllocationRepository;
    private final BusinessTransactionExecutor businessTransactionExecutor;
    private final BusinessClock businessClock;

    public List<ReceiptAllocationDto> applyAllocations(String idempotencyKey, Long customerId, Long actorId, ApplyCustomerAllocationRequest request) {
        @SuppressWarnings("unchecked")
        Class<List<ReceiptAllocationDto>> respType = (Class<List<ReceiptAllocationDto>>) (Class<?>) List.class;

        return businessTransactionExecutor.executeTransaction(
                idempotencyKey,
                actorId,
                "CUSTOMER_ALLOCATION_APPLY",
                "Apply unallocated customer credit to sales invoices",
                "POST",
                "/api/v1/customers/" + customerId + "/allocations",
                request,
                respType,
                event -> doApplyAllocations(event, customerId, actorId, request)
        );
    }

    private List<ReceiptAllocationDto> doApplyAllocations(BusinessEvent event, Long customerId, Long actorId, ApplyCustomerAllocationRequest request) {
        // 1. Lock Customer (Party lock first)
        Customer customer = customerRepository.findByIdWithLock(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + customerId));

        if (!customer.isActive()) {
            throw new AppException("INACTIVE_CUSTOMER", "Cannot allocate credit for inactive customer", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        Instant now = businessClock.now();
        List<ReceiptAllocationDto> result = new ArrayList<>();

        for (CustomerAllocationItemRequest item : request.getAllocations()) {
            CustomerReceipt receipt = customerReceiptRepository.findByIdWithLock(item.getReceiptId())
                    .orElseThrow(() -> new ResourceNotFoundException("CustomerReceipt not found with ID: " + item.getReceiptId()));

            if (!receipt.getCustomer().getId().equals(customer.getId())) {
                throw new AppException("INVALID_RECEIPT_CUSTOMER", "Receipt does not belong to this customer", HttpStatus.UNPROCESSABLE_ENTITY);
            }

            if (item.getAmount().compareTo(receipt.getUnallocatedAmount()) > 0) {
                throw new AppException("INSUFFICIENT_UNALLOCATED_CREDIT",
                        "Requested allocation (" + item.getAmount() + ") exceeds available receipt credit (" + receipt.getUnallocatedAmount() + ")",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }

            SalesInvoice invoice = salesInvoiceRepository.findByIdWithLock(item.getInvoiceId())
                    .orElseThrow(() -> new ResourceNotFoundException("SalesInvoice not found with ID: " + item.getInvoiceId()));

            if (!invoice.getCustomer().getId().equals(customer.getId())) {
                throw new AppException("INVALID_INVOICE_CUSTOMER", "Invoice does not belong to this customer", HttpStatus.UNPROCESSABLE_ENTITY);
            }

            if (invoice.getStatus() != InvoiceStatus.POSTED) {
                throw new AppException("INVALID_INVOICE_STATUS", "Cannot allocate receipt to non-posted invoice", HttpStatus.UNPROCESSABLE_ENTITY);
            }

            BigDecimal outstanding = invoice.getTotalPayableAmount().subtract(
                    invoice.getTotalAllocatedAmount() != null ? invoice.getTotalAllocatedAmount() : BigDecimal.ZERO
            );

            if (item.getAmount().compareTo(outstanding) > 0) {
                throw new AppException("EXCESS_ALLOCATION",
                        "Allocation amount (" + item.getAmount() + ") exceeds invoice outstanding balance (" + outstanding + ")",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }

            // Deduct from receipt credit
            receipt.setUnallocatedAmount(MoneyPolicy.roundMoney(receipt.getUnallocatedAmount().subtract(item.getAmount())));
            receipt.setUpdatedAt(now);
            receipt.setUpdatedBy(actorId);
            customerReceiptRepository.save(receipt);

            // Add to invoice allocation
            invoice.setTotalAllocatedAmount(MoneyPolicy.roundMoney(invoice.getTotalAllocatedAmount().add(item.getAmount())));
            if (invoice.getTotalAllocatedAmount().compareTo(invoice.getTotalPayableAmount()) >= 0) {
                invoice.setSettlementStatus(SettlementStatus.PAID);
            } else if (invoice.getTotalAllocatedAmount().compareTo(BigDecimal.ZERO) > 0) {
                invoice.setSettlementStatus(SettlementStatus.PARTIALLY_PAID);
            } else {
                invoice.setSettlementStatus(SettlementStatus.UNPAID);
            }
            invoice.setUpdatedAt(now);
            invoice.setUpdatedBy(actorId);
            salesInvoiceRepository.save(invoice);

            // Record allocation
            ReceiptAllocation allocation = ReceiptAllocation.builder()
                    .customerReceipt(receipt)
                    .invoice(invoice)
                    .allocatedAmount(MoneyPolicy.roundMoney(item.getAmount()))
                    .businessEvent(event)
                    .createdAt(now)
                    .build();

            ReceiptAllocation savedAlloc = receiptAllocationRepository.save(allocation);

            result.add(ReceiptAllocationDto.builder()
                    .id(savedAlloc.getId())
                    .receiptId(receipt.getId())
                    .receiptNumber(receipt.getReceiptNumber())
                    .invoiceId(invoice.getId())
                    .invoiceNumber(invoice.getInvoiceNumber())
                    .allocatedAmount(savedAlloc.getAllocatedAmount())
                    .createdAt(savedAlloc.getCreatedAt())
                    .build());
        }

        // NOTE: Internal allocation between unallocated advance credit and invoice does NOT change
        // net money owed, so no new ledger entry is posted.

        return result;
    }
}
