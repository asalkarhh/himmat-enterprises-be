package com.asalkartechworks.himmat.billing.service;

import com.asalkartechworks.himmat.billing.dto.DueInvoiceDto;
import com.asalkartechworks.himmat.billing.dto.DuesSummaryDto;
import com.asalkartechworks.himmat.billing.model.SalesInvoice;
import com.asalkartechworks.himmat.billing.repository.SalesInvoiceRepository;
import com.asalkartechworks.himmat.common.dto.PageResponse;
import com.asalkartechworks.himmat.common.money.MoneyPolicy;
import com.asalkartechworks.himmat.common.time.BusinessClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DuesService {

    private final SalesInvoiceRepository salesInvoiceRepository;
    private final BusinessClock businessClock;

    @Transactional(readOnly = true)
    public PageResponse<DueInvoiceDto> searchDueInvoices(Long customerId,
                                                         Long siteId,
                                                         LocalDate dueStartDate,
                                                         LocalDate dueEndDate,
                                                         Boolean overdueOnly,
                                                         Pageable pageable) {
        LocalDate today = businessClock.today();
        Page<SalesInvoice> page = salesInvoiceRepository.searchDueInvoices(
                customerId, siteId, dueStartDate, dueEndDate, overdueOnly, today, pageable
        );

        Page<DueInvoiceDto> dtoPage = page.map(si -> toDueInvoiceDto(si, today));
        return PageResponse.from(dtoPage);
    }

    @Transactional(readOnly = true)
    public DuesSummaryDto getDuesSummary(Long customerId,
                                        Long siteId,
                                        LocalDate dueStartDate,
                                        LocalDate dueEndDate,
                                        Boolean overdueOnly) {
        LocalDate today = businessClock.today();
        List<SalesInvoice> invoices = salesInvoiceRepository.findAllDueInvoicesForSummary(
                customerId, siteId, dueStartDate, dueEndDate, overdueOnly, today
        );

        BigDecimal totalOutstanding = BigDecimal.ZERO;
        BigDecimal totalOverdue = BigDecimal.ZERO;
        long outstandingCount = 0;
        long overdueCount = 0;

        for (SalesInvoice si : invoices) {
            BigDecimal outstanding = si.getOutstandingAmount();

            if (outstanding.compareTo(BigDecimal.ZERO) > 0) {
                totalOutstanding = totalOutstanding.add(outstanding);
                outstandingCount++;

                if (today.isAfter(si.getDueDate())) {
                    totalOverdue = totalOverdue.add(outstanding);
                    overdueCount++;
                }
            }
        }

        return DuesSummaryDto.builder()
                .totalOutstandingAmount(MoneyPolicy.roundMoney(totalOutstanding))
                .totalOverdueAmount(MoneyPolicy.roundMoney(totalOverdue))
                .outstandingInvoiceCount(outstandingCount)
                .overdueInvoiceCount(overdueCount)
                .build();
    }

    private DueInvoiceDto toDueInvoiceDto(SalesInvoice si, LocalDate today) {
        BigDecimal outstanding = MoneyPolicy.roundMoney(si.getOutstandingAmount());

        boolean isOverdue = today.isAfter(si.getDueDate());
        long daysOverdue = isOverdue ? ChronoUnit.DAYS.between(si.getDueDate(), today) : 0;

        return DueInvoiceDto.builder()
                .invoiceId(si.getId())
                .invoiceNumber(si.getInvoiceNumber())
                .customerId(si.getCustomer().getId())
                .customerName(si.getCustomerName())
                .siteId(si.getCustomerSite() != null ? si.getCustomerSite().getId() : null)
                .siteName(si.getSiteName())
                .invoiceDate(si.getInvoiceDate())
                .dueDate(si.getDueDate())
                .totalPayableAmount(si.getTotalPayableAmount())
                .totalAllocatedAmount(si.getTotalAllocatedAmount())
                .outstandingAmount(outstanding)
                .isOverdue(isOverdue)
                .daysOverdue(daysOverdue)
                .build();
    }
}
