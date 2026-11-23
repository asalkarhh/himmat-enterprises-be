package com.asalkartechworks.himmat.document.controller;

import com.asalkartechworks.himmat.billing.model.InvoiceDisplayFormat;
import com.asalkartechworks.himmat.document.service.PdfDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "PDF Documents", description = "Printable PDF documents generation for invoices, payments, returns, ledgers, and vouchers")
@SecurityRequirement(name = "Bearer Authentication")
public class DocumentPdfController {

    private final PdfDocumentService pdfDocumentService;

    @GetMapping("/invoices/{invoiceId}/pdf")
    @PreAuthorize("hasAnyAuthority('SALES:READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Get invoice PDF", description = "Download printable PDF for sales invoice in NORMAL or GST format")
    public ResponseEntity<byte[]> getInvoicePdf(
            @PathVariable Long invoiceId,
            @RequestParam(required = false) InvoiceDisplayFormat format) {
        byte[] pdf = pdfDocumentService.generateInvoicePdf(invoiceId, format);
        return createPdfResponse(pdf, "invoice-" + invoiceId + ".pdf");
    }

    @GetMapping("/invoices/{invoiceId}/revisions/{revisionId}/pdf")
    @PreAuthorize("hasAnyAuthority('SALES:READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Get invoice revision PDF", description = "Download printable PDF for sales invoice revision")
    public ResponseEntity<byte[]> getInvoiceRevisionPdf(
            @PathVariable Long invoiceId,
            @PathVariable Long revisionId) {
        byte[] pdf = pdfDocumentService.generateInvoiceRevisionPdf(invoiceId, revisionId);
        return createPdfResponse(pdf, "invoice-" + invoiceId + "-revision-" + revisionId + ".pdf");
    }

    @GetMapping("/customer-receipts/{receiptId}/pdf")
    @PreAuthorize("hasAnyAuthority('SETTLEMENT:READ', 'SALES:READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Get customer receipt PDF", description = "Download printable receipt PDF for customer collection")
    public ResponseEntity<byte[]> getCustomerReceiptPdf(@PathVariable Long receiptId) {
        byte[] pdf = pdfDocumentService.generateCustomerReceiptPdf(receiptId);
        return createPdfResponse(pdf, "receipt-" + receiptId + ".pdf");
    }

    @GetMapping("/customer-returns/{returnId}/pdf")
    @PreAuthorize("hasAnyAuthority('SALES:READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Get customer return PDF", description = "Download printable return credit note PDF")
    public ResponseEntity<byte[]> getCustomerReturnPdf(@PathVariable Long returnId) {
        byte[] pdf = pdfDocumentService.generateCustomerReturnPdf(returnId);
        return createPdfResponse(pdf, "customer-return-" + returnId + ".pdf");
    }

    @GetMapping("/customer-refunds/{refundId}/pdf")
    @PreAuthorize("hasAnyAuthority('SETTLEMENT:READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Get customer refund PDF", description = "Download printable payment refund voucher PDF")
    public ResponseEntity<byte[]> getCustomerRefundPdf(@PathVariable Long refundId) {
        byte[] pdf = pdfDocumentService.generateCustomerRefundPdf(refundId);
        return createPdfResponse(pdf, "customer-refund-" + refundId + ".pdf");
    }

    @GetMapping("/purchases/{purchaseId}/pdf")
    @PreAuthorize("hasAnyAuthority('PURCHASE:READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Get purchase PDF", description = "Download printable inward purchase bill PDF")
    public ResponseEntity<byte[]> getPurchasePdf(@PathVariable Long purchaseId) {
        byte[] pdf = pdfDocumentService.generatePurchasePdf(purchaseId);
        return createPdfResponse(pdf, "purchase-" + purchaseId + ".pdf");
    }

    @GetMapping("/purchases/returns/{returnId}/pdf")
    @PreAuthorize("hasAnyAuthority('PURCHASE:READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Get purchase return PDF", description = "Download printable purchase return debit note PDF")
    public ResponseEntity<byte[]> getPurchaseReturnPdf(@PathVariable Long returnId) {
        byte[] pdf = pdfDocumentService.generatePurchaseReturnPdf(returnId);
        return createPdfResponse(pdf, "purchase-return-" + returnId + ".pdf");
    }

    @GetMapping("/supplier-payments/{paymentId}/pdf")
    @PreAuthorize("hasAnyAuthority('PURCHASE:READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Get supplier payment PDF", description = "Download printable supplier payment voucher PDF")
    public ResponseEntity<byte[]> getSupplierPaymentPdf(@PathVariable Long paymentId) {
        byte[] pdf = pdfDocumentService.generateSupplierPaymentPdf(paymentId);
        return createPdfResponse(pdf, "supplier-payment-" + paymentId + ".pdf");
    }

    @GetMapping("/supplier-refunds/{refundId}/pdf")
    @PreAuthorize("hasAnyAuthority('PURCHASE:READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Get supplier refund PDF", description = "Download printable supplier refund receipt PDF")
    public ResponseEntity<byte[]> getSupplierRefundPdf(@PathVariable Long refundId) {
        byte[] pdf = pdfDocumentService.generateSupplierRefundPdf(refundId);
        return createPdfResponse(pdf, "supplier-refund-" + refundId + ".pdf");
    }

    @GetMapping("/customers/{customerId}/ledger/pdf")
    @PreAuthorize("hasAnyAuthority('CUSTOMER:READ', 'SALES:READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Get customer ledger PDF", description = "Download customer statement of account PDF for a date range")
    public ResponseEntity<byte[]> getCustomerLedgerPdf(
            @PathVariable Long customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        byte[] pdf = pdfDocumentService.generateCustomerLedgerPdf(customerId, startDate, endDate);
        return createPdfResponse(pdf, "customer-" + customerId + "-statement.pdf");
    }

    @GetMapping("/suppliers/{supplierId}/ledger/pdf")
    @PreAuthorize("hasAnyAuthority('SUPPLIER:READ', 'PURCHASE:READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Get supplier ledger PDF", description = "Download supplier statement of account PDF for a date range")
    public ResponseEntity<byte[]> getSupplierLedgerPdf(
            @PathVariable Long supplierId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        byte[] pdf = pdfDocumentService.generateSupplierLedgerPdf(supplierId, startDate, endDate);
        return createPdfResponse(pdf, "supplier-" + supplierId + "-statement.pdf");
    }

    @GetMapping("/expenses/{expenseId}/pdf")
    @PreAuthorize("hasAnyAuthority('EXPENSE:READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Get operational expense PDF", description = "Download operational expense voucher PDF")
    public ResponseEntity<byte[]> getExpensePdf(@PathVariable Long expenseId) {
        byte[] pdf = pdfDocumentService.generateExpensePdf(expenseId);
        return createPdfResponse(pdf, "expense-" + expenseId + ".pdf");
    }

    private ResponseEntity<byte[]> createPdfResponse(byte[] pdf, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.inline().filename(filename).build());
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }
}
