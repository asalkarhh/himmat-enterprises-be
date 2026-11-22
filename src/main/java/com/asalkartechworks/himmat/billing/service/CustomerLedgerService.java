package com.asalkartechworks.himmat.billing.service;

import com.asalkartechworks.himmat.billing.dto.CustomerAccountSummaryDto;
import com.asalkartechworks.himmat.billing.dto.CustomerLedgerEntryDto;
import com.asalkartechworks.himmat.billing.dto.CustomerLedgerStatementDto;
import com.asalkartechworks.himmat.billing.model.CustomerLedgerEntry;
import com.asalkartechworks.himmat.billing.model.CustomerLedgerSourceType;
import com.asalkartechworks.himmat.billing.model.CustomerReceipt;
import com.asalkartechworks.himmat.billing.model.SalesInvoice;
import com.asalkartechworks.himmat.billing.repository.CustomerLedgerEntryRepository;
import com.asalkartechworks.himmat.billing.repository.CustomerReceiptRepository;
import com.asalkartechworks.himmat.billing.repository.SalesInvoiceRepository;
import com.asalkartechworks.himmat.common.dto.PageResponse;
import com.asalkartechworks.himmat.common.errors.ResourceNotFoundException;
import com.asalkartechworks.himmat.common.money.MoneyPolicy;
import com.asalkartechworks.himmat.common.time.BusinessClock;
import com.asalkartechworks.himmat.party.model.Customer;
import com.asalkartechworks.himmat.party.repository.CustomerRepository;
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
public class CustomerLedgerService {

    private final CustomerRepository customerRepository;
    private final CustomerLedgerEntryRepository customerLedgerEntryRepository;
    private final CustomerReceiptRepository customerReceiptRepository;
    private final SalesInvoiceRepository salesInvoiceRepository;
    private final BusinessClock businessClock;

    @Transactional(propagation = Propagation.MANDATORY)
    public CustomerLedgerEntry appendLedgerEntry(Customer customer,
                                                 BusinessEvent event,
                                                 CustomerLedgerSourceType sourceType,
                                                 Long sourceId,
                                                 BigDecimal signedAmount,
                                                 LocalDate effectiveDate,
                                                 String notes) {
        BigDecimal roundedAmount = MoneyPolicy.roundMoney(signedAmount);

        Optional<CustomerLedgerEntry> lastEntryOpt = customerLedgerEntryRepository.findFirstByCustomerIdOrderByIdDesc(customer.getId());
        BigDecimal previousBalance = lastEntryOpt.map(CustomerLedgerEntry::getRunningBalance).orElse(BigDecimal.ZERO);
        BigDecimal newRunningBalance = MoneyPolicy.roundMoney(previousBalance.add(roundedAmount));

        CustomerLedgerEntry entry = CustomerLedgerEntry.builder()
                .customer(customer)
                .businessEvent(event)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .amount(roundedAmount)
                .runningBalance(newRunningBalance)
                .effectiveDate(effectiveDate != null ? effectiveDate : businessClock.today())
                .notes(notes)
                .createdAt(businessClock.now())
                .build();

        return customerLedgerEntryRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public CustomerAccountSummaryDto getAccountSummary(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + customerId));

        Optional<CustomerLedgerEntry> lastEntryOpt = customerLedgerEntryRepository.findFirstByCustomerIdOrderByIdDesc(customerId);
        BigDecimal currentBalance = lastEntryOpt.map(CustomerLedgerEntry::getRunningBalance).orElse(BigDecimal.ZERO);

        BigDecimal totalInvoiced = customerLedgerEntryRepository.calculateTotalInvoiced(customerId);
        BigDecimal totalReceipts = customerLedgerEntryRepository.calculateTotalReceipts(customerId).abs();
        BigDecimal totalReturned = customerLedgerEntryRepository.calculateTotalReturns(customerId).abs();
        BigDecimal totalRefunded = customerLedgerEntryRepository.calculateTotalRefunds(customerId);

        // Sum unallocated credit from unallocated customer receipts
        List<CustomerReceipt> unallocatedReceipts = customerReceiptRepository.findUnallocatedReceiptsByCustomer(customerId);
        BigDecimal unallocatedCredit = unallocatedReceipts.stream()
                .map(CustomerReceipt::getUnallocatedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Sum outstanding dues from unpaid posted invoices
        List<SalesInvoice> unpaidInvoices = salesInvoiceRepository.findUnpaidInvoicesByCustomer(customerId);
        BigDecimal outstandingDues = unpaidInvoices.stream()
                .map(si -> si.getTotalPayableAmount().subtract(si.getTotalAllocatedAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return CustomerAccountSummaryDto.builder()
                .customerId(customer.getId())
                .customerCode(customer.getCustomerCode())
                .customerName(customer.getName())
                .currentBalance(MoneyPolicy.roundMoney(currentBalance))
                .totalInvoiced(MoneyPolicy.roundMoney(totalInvoiced))
                .totalReceipts(MoneyPolicy.roundMoney(totalReceipts))
                .totalReturned(MoneyPolicy.roundMoney(totalReturned))
                .totalRefunded(MoneyPolicy.roundMoney(totalRefunded))
                .unallocatedCredit(MoneyPolicy.roundMoney(unallocatedCredit))
                .outstandingDues(MoneyPolicy.roundMoney(outstandingDues))
                .build();
    }

    @Transactional(readOnly = true)
    public CustomerLedgerStatementDto getLedgerStatement(Long customerId, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + customerId));

        LocalDate effectiveStart = startDate != null ? startDate : businessClock.today().withDayOfMonth(1);
        LocalDate effectiveEnd = endDate != null ? endDate : businessClock.today();

        BigDecimal openingBalance = MoneyPolicy.roundMoney(
                customerLedgerEntryRepository.calculateOpeningBalance(customerId, effectiveStart)
        );

        Page<CustomerLedgerEntry> entryPage = customerLedgerEntryRepository.findByCustomerIdAndDateRange(
                customerId, effectiveStart, effectiveEnd, pageable
        );

        Page<CustomerLedgerEntryDto> dtoPage = entryPage.map(this::toDto);

        BigDecimal closingBalance = MoneyPolicy.roundMoney(
                customerLedgerEntryRepository.calculateOpeningBalance(customerId, effectiveEnd.plusDays(1))
        );

        return CustomerLedgerStatementDto.builder()
                .customerId(customer.getId())
                .customerCode(customer.getCustomerCode())
                .customerName(customer.getName())
                .startDate(effectiveStart)
                .endDate(effectiveEnd)
                .openingBalance(openingBalance)
                .closingBalance(closingBalance)
                .entries(PageResponse.from(dtoPage))
                .build();
    }

    public CustomerLedgerEntryDto toDto(CustomerLedgerEntry entry) {
        return CustomerLedgerEntryDto.builder()
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
