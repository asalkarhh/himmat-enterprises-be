package com.asalkartechworks.himmat.billing.service;

import com.asalkartechworks.himmat.billing.dto.CreateCustomerRefundRequest;
import com.asalkartechworks.himmat.billing.dto.CustomerRefundResponseDto;
import com.asalkartechworks.himmat.billing.dto.CustomerRefundSourceDto;
import com.asalkartechworks.himmat.billing.model.CustomerLedgerEntry;
import com.asalkartechworks.himmat.billing.model.CustomerLedgerSourceType;
import com.asalkartechworks.himmat.billing.model.CustomerReceipt;
import com.asalkartechworks.himmat.billing.model.CustomerRefund;
import com.asalkartechworks.himmat.billing.model.CustomerRefundComponent;
import com.asalkartechworks.himmat.billing.model.CustomerRefundSource;
import com.asalkartechworks.himmat.billing.repository.CustomerLedgerEntryRepository;
import com.asalkartechworks.himmat.billing.repository.CustomerReceiptRepository;
import com.asalkartechworks.himmat.billing.repository.CustomerRefundRepository;
import com.asalkartechworks.himmat.common.dto.PageResponse;
import com.asalkartechworks.himmat.common.errors.AppException;
import com.asalkartechworks.himmat.common.errors.ResourceNotFoundException;
import com.asalkartechworks.himmat.common.money.MoneyPolicy;
import com.asalkartechworks.himmat.common.time.BusinessClock;
import com.asalkartechworks.himmat.party.model.Customer;
import com.asalkartechworks.himmat.party.repository.CustomerRepository;
import com.asalkartechworks.himmat.purchase.dto.PaymentComponentDto;
import com.asalkartechworks.himmat.shared.idempotency.model.BusinessEvent;
import com.asalkartechworks.himmat.shared.transaction.BusinessTransactionExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerRefundService {

    private final CustomerRepository customerRepository;
    private final CustomerRefundRepository customerRefundRepository;
    private final CustomerReceiptRepository customerReceiptRepository;
    private final CustomerLedgerEntryRepository customerLedgerEntryRepository;
    private final CustomerLedgerService customerLedgerService;
    private final BusinessTransactionExecutor businessTransactionExecutor;
    private final BusinessClock businessClock;

    public CustomerRefundResponseDto createRefund(String idempotencyKey, Long customerId, Long actorId, CreateCustomerRefundRequest request) {
        return businessTransactionExecutor.executeTransaction(
                idempotencyKey,
                actorId,
                "CUSTOMER_REFUND",
                "Customer refund: " + request.getReason(),
                "POST",
                "/api/v1/customers/" + customerId + "/refunds",
                request,
                CustomerRefundResponseDto.class,
                event -> doCreateRefund(event, customerId, actorId, request)
        );
    }

    private CustomerRefundResponseDto doCreateRefund(BusinessEvent event, Long customerId, Long actorId, CreateCustomerRefundRequest request) {
        // 1. Lock Customer (Party Lock first)
        Customer customer = customerRepository.findByIdWithLock(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + customerId));

        if (!customer.isActive()) {
            throw new AppException("INACTIVE_CUSTOMER", "Cannot record refund for inactive customer", HttpStatus.UNPROCESSABLE_ENTITY);
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

        // 2. Validate available customer credit
        Optional<CustomerLedgerEntry> lastEntryOpt = customerLedgerEntryRepository.findFirstByCustomerIdOrderByIdDesc(customerId);
        BigDecimal currentBalance = lastEntryOpt.map(CustomerLedgerEntry::getRunningBalance).orElse(BigDecimal.ZERO);

        // A negative balance means the shop owes the customer or holds advance money
        BigDecimal creditFromBalance = currentBalance.compareTo(BigDecimal.ZERO) < 0 ? currentBalance.abs() : BigDecimal.ZERO;

        List<CustomerReceipt> unallocatedReceipts = customerReceiptRepository.findUnallocatedReceiptsByCustomerWithLock(customerId);
        BigDecimal unallocatedReceiptTotal = unallocatedReceipts.stream()
                .map(CustomerReceipt::getUnallocatedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal availableCredit = creditFromBalance.max(unallocatedReceiptTotal);

        if (request.getTotalAmount().compareTo(availableCredit) > 0) {
            throw new AppException("INSUFFICIENT_CUSTOMER_CREDIT",
                    "Refund amount (" + request.getTotalAmount() + ") exceeds available customer credit (" + availableCredit + ")",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        Instant now = businessClock.now();
        LocalDate refundDate = request.getRefundDate() != null ? request.getRefundDate() : businessClock.today();

        // 3. Consume unallocated receipt credit if available
        BigDecimal remainingToConsume = request.getTotalAmount();
        List<CustomerRefundSource> sources = new ArrayList<>();

        for (CustomerReceipt receipt : unallocatedReceipts) {
            if (remainingToConsume.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal unalloc = receipt.getUnallocatedAmount();
            if (unalloc.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal consumeThis = remainingToConsume.min(unalloc);
                receipt.setUnallocatedAmount(MoneyPolicy.roundMoney(unalloc.subtract(consumeThis)));
                receipt.setUpdatedAt(now);
                receipt.setUpdatedBy(actorId);
                customerReceiptRepository.save(receipt);

                sources.add(CustomerRefundSource.builder()
                        .customerReceipt(receipt)
                        .amount(consumeThis)
                        .createdAt(now)
                        .build());

                remainingToConsume = remainingToConsume.subtract(consumeThis);
            }
        }

        // 4. Generate Refund Number (CRF-%05d)
        Long nextSeq = customerRefundRepository.getNextRefundSequence();
        String refundNumber = String.format("CRF-%05d", nextSeq);

        // 5. Save Refund Entity
        CustomerRefund refund = CustomerRefund.builder()
                .refundNumber(refundNumber)
                .customer(customer)
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
            CustomerRefundComponent comp = CustomerRefundComponent.builder()
                    .method(compDto.getMethod())
                    .amount(MoneyPolicy.roundMoney(compDto.getAmount()))
                    .onlineReference(compDto.getOnlineReference())
                    .build();
            refund.addComponent(comp);
        }

        for (CustomerRefundSource src : sources) {
            refund.addSource(src);
        }

        CustomerRefund savedRefund = customerRefundRepository.save(refund);

        // 6. Post to Customer Ledger (+totalAmount, positive = shop paid back customer, increasing running balance towards 0)
        customerLedgerService.appendLedgerEntry(
                customer,
                event,
                CustomerLedgerSourceType.REFUND,
                savedRefund.getId(),
                savedRefund.getTotalAmount(),
                refundDate,
                "Customer refund: " + refundNumber
        );

        return toDto(savedRefund);
    }

    @Transactional(readOnly = true)
    public CustomerRefundResponseDto getRefundById(Long id) {
        CustomerRefund refund = customerRefundRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer refund not found with ID: " + id));
        return toDto(refund);
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerRefundResponseDto> searchRefunds(Long customerId, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        Page<CustomerRefund> page = customerRefundRepository.searchRefunds(customerId, startDate, endDate, pageable);
        return PageResponse.from(page.map(this::toDto));
    }

    public CustomerRefundResponseDto toDto(CustomerRefund refund) {
        List<PaymentComponentDto> compDtos = refund.getComponents().stream()
                .map(c -> PaymentComponentDto.builder()
                        .method(c.getMethod())
                        .amount(c.getAmount())
                        .onlineReference(c.getOnlineReference())
                        .build())
                .toList();

        List<CustomerRefundSourceDto> srcDtos = refund.getSources().stream()
                .map(s -> CustomerRefundSourceDto.builder()
                        .id(s.getId())
                        .customerReceiptId(s.getCustomerReceipt().getId())
                        .receiptNumber(s.getCustomerReceipt().getReceiptNumber())
                        .amount(s.getAmount())
                        .createdAt(s.getCreatedAt())
                        .build())
                .toList();

        return CustomerRefundResponseDto.builder()
                .id(refund.getId())
                .refundNumber(refund.getRefundNumber())
                .customerId(refund.getCustomer().getId())
                .customerName(refund.getCustomer().getName())
                .refundDate(refund.getRefundDate())
                .totalAmount(refund.getTotalAmount())
                .reason(refund.getReason())
                .components(compDtos)
                .sources(srcDtos)
                .businessEventId(refund.getBusinessEvent().getId())
                .version(refund.getVersion())
                .createdAt(refund.getCreatedAt())
                .build();
    }
}
