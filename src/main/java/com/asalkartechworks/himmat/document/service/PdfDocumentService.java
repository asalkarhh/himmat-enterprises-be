package com.asalkartechworks.himmat.document.service;

import com.asalkartechworks.himmat.billing.dto.CustomerLedgerStatementDto;
import com.asalkartechworks.himmat.billing.model.CustomerReceipt;
import com.asalkartechworks.himmat.billing.model.CustomerReceiptComponent;
import com.asalkartechworks.himmat.billing.model.CustomerRefund;
import com.asalkartechworks.himmat.billing.model.CustomerRefundComponent;
import com.asalkartechworks.himmat.billing.model.CustomerReturn;
import com.asalkartechworks.himmat.billing.model.CustomerReturnLine;
import com.asalkartechworks.himmat.billing.model.InvoiceDisplayFormat;
import com.asalkartechworks.himmat.billing.model.SalesInvoice;
import com.asalkartechworks.himmat.billing.model.SalesInvoiceLine;
import com.asalkartechworks.himmat.billing.model.SalesInvoiceRevision;
import com.asalkartechworks.himmat.billing.model.SalesInvoiceRevisionLine;
import com.asalkartechworks.himmat.billing.repository.CustomerReceiptRepository;
import com.asalkartechworks.himmat.billing.repository.CustomerRefundRepository;
import com.asalkartechworks.himmat.billing.repository.CustomerReturnRepository;
import com.asalkartechworks.himmat.billing.repository.SalesInvoiceLineRepository;
import com.asalkartechworks.himmat.billing.repository.SalesInvoiceRepository;
import com.asalkartechworks.himmat.billing.repository.SalesInvoiceRevisionRepository;
import com.asalkartechworks.himmat.billing.service.CustomerLedgerService;
import com.asalkartechworks.himmat.common.errors.ResourceNotFoundException;
import com.asalkartechworks.himmat.common.time.BusinessClock;
import com.asalkartechworks.himmat.expense.model.OperationalExpense;
import com.asalkartechworks.himmat.expense.model.OperationalExpenseComponent;
import com.asalkartechworks.himmat.expense.repository.OperationalExpenseRepository;
import com.asalkartechworks.himmat.party.model.Customer;
import com.asalkartechworks.himmat.party.model.Supplier;
import com.asalkartechworks.himmat.party.repository.CustomerRepository;
import com.asalkartechworks.himmat.party.repository.SupplierRepository;
import com.asalkartechworks.himmat.purchase.dto.SupplierLedgerStatementDto;
import com.asalkartechworks.himmat.purchase.model.PaymentComponent;
import com.asalkartechworks.himmat.purchase.model.Purchase;
import com.asalkartechworks.himmat.purchase.model.PurchaseLine;
import com.asalkartechworks.himmat.purchase.model.PurchaseReturn;
import com.asalkartechworks.himmat.purchase.model.PurchaseReturnLine;
import com.asalkartechworks.himmat.purchase.model.SupplierPayment;
import com.asalkartechworks.himmat.purchase.model.SupplierRefund;
import com.asalkartechworks.himmat.purchase.repository.PurchaseRepository;
import com.asalkartechworks.himmat.purchase.repository.PurchaseReturnRepository;
import com.asalkartechworks.himmat.purchase.repository.SupplierPaymentRepository;
import com.asalkartechworks.himmat.purchase.repository.SupplierRefundRepository;
import com.asalkartechworks.himmat.purchase.service.SupplierLedgerService;
import com.asalkartechworks.himmat.settings.model.ShopSettings;
import com.asalkartechworks.himmat.settings.repository.ShopSettingsRepository;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfDocumentService {

    private final ShopSettingsRepository shopSettingsRepository;
    private final SalesInvoiceRepository salesInvoiceRepository;
    private final SalesInvoiceLineRepository salesInvoiceLineRepository;
    private final SalesInvoiceRevisionRepository salesInvoiceRevisionRepository;
    private final CustomerReturnRepository customerReturnRepository;
    private final CustomerReceiptRepository customerReceiptRepository;
    private final CustomerRefundRepository customerRefundRepository;

    private final PurchaseRepository purchaseRepository;
    private final PurchaseReturnRepository purchaseReturnRepository;
    private final SupplierPaymentRepository supplierPaymentRepository;
    private final SupplierRefundRepository supplierRefundRepository;

    private final OperationalExpenseRepository operationalExpenseRepository;
    private final CustomerRepository customerRepository;
    private final SupplierRepository supplierRepository;
    private final CustomerLedgerService customerLedgerService;
    private final SupplierLedgerService supplierLedgerService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
    private static final Color HEADER_BG = new Color(240, 240, 240);
    private static final Color ALT_ROW_BG = new Color(250, 250, 250);

    // Font definitions
    private static final Font FONT_SHOP_NAME = new Font(Font.HELVETICA, 16, Font.BOLD, Color.BLACK);
    private static final Font FONT_DOC_TITLE = new Font(Font.HELVETICA, 13, Font.BOLD, new Color(40, 40, 40));
    private static final Font FONT_HEADER = new Font(Font.HELVETICA, 9, Font.BOLD, Color.BLACK);
    private static final Font FONT_BODY = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.DARK_GRAY);
    private static final Font FONT_BODY_BOLD = new Font(Font.HELVETICA, 9, Font.BOLD, Color.BLACK);
    private static final Font FONT_MUTED = new Font(Font.HELVETICA, 8, Font.NORMAL, Color.GRAY);

    @Transactional(readOnly = true)
    public byte[] generateInvoicePdf(Long invoiceId, InvoiceDisplayFormat requestedFormat) {
        SalesInvoice invoice = salesInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + invoiceId));
        List<SalesInvoiceLine> lines = salesInvoiceLineRepository.findByInvoiceId(invoiceId);
        ShopSettings settings = getShopSettings();

        InvoiceDisplayFormat format = requestedFormat != null ? requestedFormat : invoice.getDisplayFormat();
        boolean isGst = (format == InvoiceDisplayFormat.GST);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(document, out);
        document.open();

        addHeaderSection(document, settings, isGst ? "TAX INVOICE" : "INVOICE", invoice.getInvoiceNumber(), invoice.getInvoiceDate());
        addCustomerPartySection(document, invoice.getCustomer(), invoice.getCustomerName(), invoice.getInvoiceDate(), invoice.getDueDate());

        // Line items table
        if (isGst) {
            float[] widths = {1.5f, 5f, 2f, 1.5f, 2.5f, 2f, 2.5f};
            PdfPTable table = createTable(widths);
            addHeaderCell(table, "#");
            addHeaderCell(table, "Item Description");
            addHeaderCell(table, "Size");
            addHeaderCell(table, "Qty");
            addHeaderCell(table, "Taxable");
            addHeaderCell(table, "GST");
            addHeaderCell(table, "Total (₹)");

            int idx = 1;
            for (SalesInvoiceLine line : lines) {
                if (!line.isActiveInCurrentRevision() || line.getQuantity() == 0) continue;
                addCell(table, String.valueOf(idx++), Element.ALIGN_CENTER);
                addCell(table, line.getProductName(), Element.ALIGN_LEFT);
                addCell(table, line.getSizeLabel(), Element.ALIGN_LEFT);
                addCell(table, String.valueOf(line.getQuantity()), Element.ALIGN_CENTER);
                addCell(table, formatMoney(line.getTaxableAmount() != null ? line.getTaxableAmount() : line.getLineTotal()), Element.ALIGN_RIGHT);
                addCell(table, formatMoney(line.getTaxAmount() != null ? line.getTaxAmount() : BigDecimal.ZERO), Element.ALIGN_RIGHT);
                addCell(table, formatMoney(line.getLineTotal()), Element.ALIGN_RIGHT);
            }

            addSummaryRow(table, "Total Taxable Amount", formatMoney(invoice.getTotalTaxableAmount()), 6);
            addSummaryRow(table, "Total GST Amount", formatMoney(invoice.getTotalGstAmount()), 6);
            addGrandTotalRow(table, "Total Payable Amount", formatMoney(invoice.getTotalPayableAmount()), 6);
            document.add(table);
        } else {
            float[] widths = {1.5f, 6.5f, 2.5f, 1.5f, 2.5f, 2.5f};
            PdfPTable table = createTable(widths);
            addHeaderCell(table, "#");
            addHeaderCell(table, "Item Description");
            addHeaderCell(table, "Size");
            addHeaderCell(table, "Qty");
            addHeaderCell(table, "Unit Net (₹)");
            addHeaderCell(table, "Total (₹)");

            int idx = 1;
            for (SalesInvoiceLine line : lines) {
                if (!line.isActiveInCurrentRevision() || line.getQuantity() == 0) continue;
                addCell(table, String.valueOf(idx++), Element.ALIGN_CENTER);
                addCell(table, line.getProductName(), Element.ALIGN_LEFT);
                addCell(table, line.getSizeLabel(), Element.ALIGN_LEFT);
                addCell(table, String.valueOf(line.getQuantity()), Element.ALIGN_CENTER);
                addCell(table, formatMoney(line.getUnitNetPrice()), Element.ALIGN_RIGHT);
                addCell(table, formatMoney(line.getLineTotal()), Element.ALIGN_RIGHT);
            }

            if (invoice.getTotalDiscountAmount() != null && invoice.getTotalDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
                addSummaryRow(table, "Total Discount", "-" + formatMoney(invoice.getTotalDiscountAmount()), 5);
            }
            addGrandTotalRow(table, "Total Payable Amount", formatMoney(invoice.getTotalPayableAmount()), 5);
            document.add(table);
        }

        addFooterNote(document, "Thank you for your business!");
        document.close();
        return out.toByteArray();
    }

    @Transactional(readOnly = true)
    public byte[] generateInvoiceRevisionPdf(Long invoiceId, Long revisionId) {
        SalesInvoice invoice = salesInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + invoiceId));
        SalesInvoiceRevision revision = salesInvoiceRevisionRepository.findById(revisionId)
                .orElseThrow(() -> new ResourceNotFoundException("Revision not found: " + revisionId));
        ShopSettings settings = getShopSettings();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(document, out);
        document.open();

        LocalDate revDate = LocalDate.ofInstant(revision.getCreatedAt(), BusinessClock.BUSINESS_ZONE);
        addHeaderSection(document, settings, "INVOICE REVISION #" + revision.getRevisionNumber(),
                invoice.getInvoiceNumber() + "-R" + revision.getRevisionNumber(), revDate);
        addCustomerPartySection(document, invoice.getCustomer(), invoice.getCustomerName(), invoice.getInvoiceDate(), invoice.getDueDate());

        Paragraph reasonPara = new Paragraph("Revision Reason: " + revision.getReason(), FONT_BODY_BOLD);
        reasonPara.setSpacingAfter(10);
        document.add(reasonPara);

        float[] widths = {1f, 4.5f, 2f, 1.5f, 1.5f, 1.5f, 2.5f, 2.5f};
        PdfPTable table = createTable(widths);
        addHeaderCell(table, "#");
        addHeaderCell(table, "Item");
        addHeaderCell(table, "Change");
        addHeaderCell(table, "Prev Qty");
        addHeaderCell(table, "New Qty");
        addHeaderCell(table, "Delta Qty");
        addHeaderCell(table, "Prev Total (₹)");
        addHeaderCell(table, "New Total (₹)");

        int idx = 1;
        for (SalesInvoiceRevisionLine line : revision.getLines()) {
            addCell(table, String.valueOf(idx++), Element.ALIGN_CENTER);
            addCell(table, line.getInvoiceLine().getProductName(), Element.ALIGN_LEFT);
            addCell(table, line.getChangeType().name(), Element.ALIGN_CENTER);
            addCell(table, String.valueOf(line.getPreviousQuantity() != null ? line.getPreviousQuantity() : 0), Element.ALIGN_CENTER);
            addCell(table, String.valueOf(line.getNewQuantity() != null ? line.getNewQuantity() : 0), Element.ALIGN_CENTER);
            addCell(table, String.valueOf(line.getDeltaQuantity()), Element.ALIGN_CENTER);
            addCell(table, formatMoney(line.getPreviousLineTotal()), Element.ALIGN_RIGHT);
            addCell(table, formatMoney(line.getNewLineTotal()), Element.ALIGN_RIGHT);
        }

        addSummaryRow(table, "Previous Invoice Payable", formatMoney(revision.getPreviousTotalPayable()), 7);
        addSummaryRow(table, "Revision Delta Amount", formatMoney(revision.getDeltaPayableAmount()), 7);
        addGrandTotalRow(table, "New Invoice Payable", formatMoney(revision.getNewTotalPayable()), 7);
        document.add(table);

        addFooterNote(document, "Document reflects revision amendment to " + invoice.getInvoiceNumber());
        document.close();
        return out.toByteArray();
    }

    @Transactional(readOnly = true)
    public byte[] generateCustomerReceiptPdf(Long receiptId) {
        CustomerReceipt receipt = customerReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt not found: " + receiptId));
        ShopSettings settings = getShopSettings();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(document, out);
        document.open();

        addHeaderSection(document, settings, "PAYMENT RECEIPT", receipt.getReceiptNumber(), receipt.getReceiptDate());
        addCustomerPartySection(document, receipt.getCustomer(), receipt.getCustomer().getName(), receipt.getReceiptDate(), null);

        float[] widths = {1f, 3f, 4f, 4f};
        PdfPTable table = createTable(widths);
        addHeaderCell(table, "#");
        addHeaderCell(table, "Payment Method");
        addHeaderCell(table, "Online / Check Reference");
        addHeaderCell(table, "Amount (₹)");

        int idx = 1;
        for (CustomerReceiptComponent comp : receipt.getComponents()) {
            addCell(table, String.valueOf(idx++), Element.ALIGN_CENTER);
            addCell(table, comp.getMethod().name(), Element.ALIGN_LEFT);
            addCell(table, comp.getOnlineReference() != null ? comp.getOnlineReference() : "-", Element.ALIGN_LEFT);
            addCell(table, formatMoney(comp.getAmount()), Element.ALIGN_RIGHT);
        }

        addGrandTotalRow(table, "Total Received", formatMoney(receipt.getTotalAmount()), 3);
        addSummaryRow(table, "Allocated to Invoices", formatMoney(receipt.getTotalAmount().subtract(receipt.getUnallocatedAmount())), 3);
        addSummaryRow(table, "Available Advance Balance", formatMoney(receipt.getUnallocatedAmount()), 3);
        document.add(table);

        addFooterNote(document, "Official payment receipt. Thank you for your payment.");
        document.close();
        return out.toByteArray();
    }

    @Transactional(readOnly = true)
    public byte[] generateCustomerReturnPdf(Long returnId) {
        CustomerReturn customerReturn = customerReturnRepository.findById(returnId)
                .orElseThrow(() -> new ResourceNotFoundException("Return not found: " + returnId));
        ShopSettings settings = getShopSettings();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(document, out);
        document.open();

        addHeaderSection(document, settings, "CUSTOMER RETURN CREDIT NOTE", customerReturn.getReturnNumber(), customerReturn.getReturnDate());
        addCustomerPartySection(document, customerReturn.getCustomer(), customerReturn.getCustomer().getName(), customerReturn.getReturnDate(), null);

        Paragraph invRef = new Paragraph("Linked Invoice: " + customerReturn.getInvoice().getInvoiceNumber() +
                " | Reason: " + (customerReturn.getReason() != null ? customerReturn.getReason() : "None"), FONT_BODY_BOLD);
        invRef.setSpacingAfter(10);
        document.add(invRef);

        float[] widths = {1f, 5f, 2f, 1.5f, 2.5f, 2.5f};
        PdfPTable table = createTable(widths);
        addHeaderCell(table, "#");
        addHeaderCell(table, "Returned Item");
        addHeaderCell(table, "Size");
        addHeaderCell(table, "Returned Qty");
        addHeaderCell(table, "Unit Net (₹)");
        addHeaderCell(table, "Credit Total (₹)");

        int idx = 1;
        for (CustomerReturnLine line : customerReturn.getLines()) {
            addCell(table, String.valueOf(idx++), Element.ALIGN_CENTER);
            addCell(table, line.getInvoiceLine().getProductName(), Element.ALIGN_LEFT);
            addCell(table, line.getInvoiceLine().getSizeLabel(), Element.ALIGN_LEFT);
            addCell(table, String.valueOf(line.getReturnedQuantity()), Element.ALIGN_CENTER);
            addCell(table, formatMoney(line.getUnitPrice()), Element.ALIGN_RIGHT);
            addCell(table, formatMoney(line.getTotalAmount()), Element.ALIGN_RIGHT);
        }

        addGrandTotalRow(table, "Total Credit Amount", formatMoney(customerReturn.getTotalReturnAmount()), 5);
        document.add(table);

        addFooterNote(document, "Credit has been credited to customer ledger.");
        document.close();
        return out.toByteArray();
    }

    @Transactional(readOnly = true)
    public byte[] generateCustomerRefundPdf(Long refundId) {
        CustomerRefund refund = customerRefundRepository.findById(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("Refund not found: " + refundId));
        ShopSettings settings = getShopSettings();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(document, out);
        document.open();

        addHeaderSection(document, settings, "CUSTOMER REFUND VOUCHER", refund.getRefundNumber(), refund.getRefundDate());
        addCustomerPartySection(document, refund.getCustomer(), refund.getCustomer().getName(), refund.getRefundDate(), null);

        Paragraph reasonPara = new Paragraph("Refund Reason: " + refund.getReason(), FONT_BODY_BOLD);
        reasonPara.setSpacingAfter(10);
        document.add(reasonPara);

        float[] widths = {1f, 3f, 4f, 4f};
        PdfPTable table = createTable(widths);
        addHeaderCell(table, "#");
        addHeaderCell(table, "Payment Method");
        addHeaderCell(table, "Reference");
        addHeaderCell(table, "Refund Amount (₹)");

        int idx = 1;
        for (CustomerRefundComponent comp : refund.getComponents()) {
            addCell(table, String.valueOf(idx++), Element.ALIGN_CENTER);
            addCell(table, comp.getMethod().name(), Element.ALIGN_LEFT);
            addCell(table, comp.getOnlineReference() != null ? comp.getOnlineReference() : "-", Element.ALIGN_LEFT);
            addCell(table, formatMoney(comp.getAmount()), Element.ALIGN_RIGHT);
        }

        addGrandTotalRow(table, "Total Refund Paid Out", formatMoney(refund.getTotalAmount()), 3);
        document.add(table);

        addFooterNote(document, "Refund processed from available advances/credits.");
        document.close();
        return out.toByteArray();
    }

    @Transactional(readOnly = true)
    public byte[] generatePurchasePdf(Long purchaseId) {
        Purchase purchase = purchaseRepository.findById(purchaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase not found: " + purchaseId));
        ShopSettings settings = getShopSettings();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(document, out);
        document.open();

        addHeaderSection(document, settings, "INWARD PURCHASE BILL", purchase.getPurchaseNumber(), purchase.getPurchaseDate());
        addSupplierPartySection(document, purchase.getSupplier(), purchase.getSupplierName(), purchase.getPurchaseDate(), purchase.getSupplierInvoiceNumber());

        float[] widths = {1f, 5.5f, 2f, 1.5f, 2.5f, 2.5f};
        PdfPTable table = createTable(widths);
        addHeaderCell(table, "#");
        addHeaderCell(table, "Item Description");
        addHeaderCell(table, "Size");
        addHeaderCell(table, "Qty");
        addHeaderCell(table, "Cost Excl Tax (₹)");
        addHeaderCell(table, "Total (₹)");

        int idx = 1;
        for (PurchaseLine line : purchase.getLines()) {
            addCell(table, String.valueOf(idx++), Element.ALIGN_CENTER);
            addCell(table, line.getProductName(), Element.ALIGN_LEFT);
            addCell(table, line.getSizeLabel(), Element.ALIGN_LEFT);
            addCell(table, String.valueOf(line.getQuantity()), Element.ALIGN_CENTER);
            addCell(table, formatMoney(line.getNetUnitCost() != null ? line.getNetUnitCost() : line.getGrossUnitCost()), Element.ALIGN_RIGHT);
            addCell(table, formatMoney(line.getLineTotal()), Element.ALIGN_RIGHT);
        }

        if (purchase.getSubtotalExclTax() != null) {
            addSummaryRow(table, "Subtotal Excl Tax", formatMoney(purchase.getSubtotalExclTax()), 5);
        }
        if (purchase.getTotalGstAmount() != null) {
            addSummaryRow(table, "GST Amount", formatMoney(purchase.getTotalGstAmount()), 5);
        }
        addGrandTotalRow(table, "Total Payable", formatMoney(purchase.getTotalPayableAmount()), 5);
        document.add(table);

        addFooterNote(document, "Inward goods verified and received in inventory.");
        document.close();
        return out.toByteArray();
    }

    @Transactional(readOnly = true)
    public byte[] generatePurchaseReturnPdf(Long returnId) {
        PurchaseReturn purchaseReturn = purchaseReturnRepository.findById(returnId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase return not found: " + returnId));
        ShopSettings settings = getShopSettings();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(document, out);
        document.open();

        addHeaderSection(document, settings, "PURCHASE RETURN DEBIT NOTE", purchaseReturn.getReturnNumber(), purchaseReturn.getReturnDate());
        addSupplierPartySection(document, purchaseReturn.getSupplier(), purchaseReturn.getSupplier().getName(), purchaseReturn.getReturnDate(), null);

        Paragraph refPara = new Paragraph("Linked Purchase: " + purchaseReturn.getPurchase().getPurchaseNumber() +
                " | Reason: " + (purchaseReturn.getReason() != null ? purchaseReturn.getReason() : "None"), FONT_BODY_BOLD);
        refPara.setSpacingAfter(10);
        document.add(refPara);

        float[] widths = {1f, 5.5f, 2f, 1.5f, 2.5f, 2.5f};
        PdfPTable table = createTable(widths);
        addHeaderCell(table, "#");
        addHeaderCell(table, "Returned Item");
        addHeaderCell(table, "Size");
        addHeaderCell(table, "Qty");
        addHeaderCell(table, "Unit Cost (₹)");
        addHeaderCell(table, "Total (₹)");

        int idx = 1;
        for (PurchaseReturnLine line : purchaseReturn.getLines()) {
            addCell(table, String.valueOf(idx++), Element.ALIGN_CENTER);
            addCell(table, line.getPurchaseLine().getProductName(), Element.ALIGN_LEFT);
            addCell(table, line.getPurchaseLine().getSizeLabel(), Element.ALIGN_LEFT);
            addCell(table, String.valueOf(line.getReturnedQuantity()), Element.ALIGN_CENTER);
            addCell(table, formatMoney(line.getUnitCost()), Element.ALIGN_RIGHT);
            addCell(table, formatMoney(line.getTotalAmount()), Element.ALIGN_RIGHT);
        }

        addGrandTotalRow(table, "Total Debit Amount", formatMoney(purchaseReturn.getTotalReturnAmount()), 5);
        document.add(table);

        addFooterNote(document, "Debit note posted against supplier ledger.");
        document.close();
        return out.toByteArray();
    }

    @Transactional(readOnly = true)
    public byte[] generateSupplierPaymentPdf(Long paymentId) {
        SupplierPayment payment = supplierPaymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier payment not found: " + paymentId));
        ShopSettings settings = getShopSettings();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(document, out);
        document.open();

        addHeaderSection(document, settings, "SUPPLIER PAYMENT VOUCHER", payment.getPaymentNumber(), payment.getPaymentDate());
        addSupplierPartySection(document, payment.getSupplier(), payment.getSupplier().getName(), payment.getPaymentDate(), null);

        float[] widths = {1f, 3f, 4f, 4f};
        PdfPTable table = createTable(widths);
        addHeaderCell(table, "#");
        addHeaderCell(table, "Payment Method");
        addHeaderCell(table, "Reference");
        addHeaderCell(table, "Amount (₹)");

        int idx = 1;
        for (PaymentComponent comp : payment.getComponents()) {
            addCell(table, String.valueOf(idx++), Element.ALIGN_CENTER);
            addCell(table, comp.getMethod().name(), Element.ALIGN_LEFT);
            addCell(table, comp.getOnlineReference() != null ? comp.getOnlineReference() : "-", Element.ALIGN_LEFT);
            addCell(table, formatMoney(comp.getAmount()), Element.ALIGN_RIGHT);
        }

        addGrandTotalRow(table, "Total Payment", formatMoney(payment.getTotalAmount()), 3);
        addSummaryRow(table, "Allocated to Bills", formatMoney(payment.getTotalAmount().subtract(payment.getUnallocatedAmount())), 3);
        addSummaryRow(table, "Available Supplier Advance", formatMoney(payment.getUnallocatedAmount()), 3);
        document.add(table);

        addFooterNote(document, "Authorized signature payment voucher.");
        document.close();
        return out.toByteArray();
    }

    @Transactional(readOnly = true)
    public byte[] generateSupplierRefundPdf(Long refundId) {
        SupplierRefund refund = supplierRefundRepository.findById(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier refund not found: " + refundId));
        ShopSettings settings = getShopSettings();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(document, out);
        document.open();

        addHeaderSection(document, settings, "SUPPLIER REFUND RECEIPT", refund.getRefundNumber(), refund.getRefundDate());
        addSupplierPartySection(document, refund.getSupplier(), refund.getSupplier().getName(), refund.getRefundDate(), null);

        Paragraph reasonPara = new Paragraph("Refund Reason: " + refund.getReason(), FONT_BODY_BOLD);
        reasonPara.setSpacingAfter(10);
        document.add(reasonPara);

        float[] widths = {1f, 3f, 4f, 4f};
        PdfPTable table = createTable(widths);
        addHeaderCell(table, "#");
        addHeaderCell(table, "Payment Method");
        addHeaderCell(table, "Reference");
        addHeaderCell(table, "Refund Amount (₹)");

        int idx = 1;
        for (PaymentComponent comp : refund.getComponents()) {
            addCell(table, String.valueOf(idx++), Element.ALIGN_CENTER);
            addCell(table, comp.getMethod().name(), Element.ALIGN_LEFT);
            addCell(table, comp.getOnlineReference() != null ? comp.getOnlineReference() : "-", Element.ALIGN_LEFT);
            addCell(table, formatMoney(comp.getAmount()), Element.ALIGN_RIGHT);
        }

        addGrandTotalRow(table, "Total Refund Received", formatMoney(refund.getTotalAmount()), 3);
        document.add(table);

        addFooterNote(document, "Supplier refund credited to bank/cash.");
        document.close();
        return out.toByteArray();
    }

    @Transactional(readOnly = true)
    public byte[] generateCustomerLedgerPdf(Long customerId, LocalDate startDate, LocalDate endDate) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + customerId));
        CustomerLedgerStatementDto statement = customerLedgerService.getLedgerStatement(customerId, startDate, endDate, Pageable.unpaged());
        ShopSettings settings = getShopSettings();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(document, out);
        document.open();

        addHeaderSection(document, settings, "CUSTOMER STATEMENT OF ACCOUNT", customer.getCustomerCode(), LocalDate.now());
        addCustomerPartySection(document, customer, customer.getName(), statement.getStartDate(), statement.getEndDate());

        Paragraph periodPara = new Paragraph(String.format("Statement Period: %s to %s",
                statement.getStartDate().format(DATE_FMT), statement.getEndDate().format(DATE_FMT)), FONT_BODY_BOLD);
        periodPara.setSpacingAfter(10);
        document.add(periodPara);

        float[] widths = {2f, 3f, 4f, 2.5f, 2.5f};
        PdfPTable table = createTable(widths);
        addHeaderCell(table, "Date");
        addHeaderCell(table, "Type");
        addHeaderCell(table, "Notes");
        addHeaderCell(table, "Amount (₹)");
        addHeaderCell(table, "Balance (₹)");

        addSummaryRow(table, "Opening Balance", formatMoney(statement.getOpeningBalance()), 4);

        if (statement.getEntries() != null && statement.getEntries().getData() != null) {
            for (var entry : statement.getEntries().getData()) {
                addCell(table, entry.getEffectiveDate().format(DATE_FMT), Element.ALIGN_CENTER);
                addCell(table, entry.getSourceType().name(), Element.ALIGN_LEFT);
                addCell(table, entry.getNotes() != null ? entry.getNotes() : "-", Element.ALIGN_LEFT);
                addCell(table, formatMoney(entry.getAmount()), Element.ALIGN_RIGHT);
                addCell(table, formatMoney(entry.getRunningBalance()), Element.ALIGN_RIGHT);
            }
        }

        addGrandTotalRow(table, "Closing Balance", formatMoney(statement.getClosingBalance()), 4);
        document.add(table);

        addFooterNote(document, "Please reconcile this statement and report any discrepancies promptly.");
        document.close();
        return out.toByteArray();
    }

    @Transactional(readOnly = true)
    public byte[] generateSupplierLedgerPdf(Long supplierId, LocalDate startDate, LocalDate endDate) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found: " + supplierId));
        SupplierLedgerStatementDto statement = supplierLedgerService.getLedgerStatement(supplierId, startDate, endDate, Pageable.unpaged());
        ShopSettings settings = getShopSettings();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(document, out);
        document.open();

        addHeaderSection(document, settings, "SUPPLIER STATEMENT OF ACCOUNT", supplier.getSupplierCode(), LocalDate.now());
        addSupplierPartySection(document, supplier, supplier.getName(), statement.getStartDate(), null);

        Paragraph periodPara = new Paragraph(String.format("Statement Period: %s to %s",
                statement.getStartDate().format(DATE_FMT), statement.getEndDate().format(DATE_FMT)), FONT_BODY_BOLD);
        periodPara.setSpacingAfter(10);
        document.add(periodPara);

        float[] widths = {2f, 3f, 4f, 2.5f, 2.5f};
        PdfPTable table = createTable(widths);
        addHeaderCell(table, "Date");
        addHeaderCell(table, "Type");
        addHeaderCell(table, "Notes");
        addHeaderCell(table, "Amount (₹)");
        addHeaderCell(table, "Balance (₹)");

        addSummaryRow(table, "Opening Balance", formatMoney(statement.getOpeningBalance()), 4);

        if (statement.getEntries() != null && statement.getEntries().getData() != null) {
            for (var entry : statement.getEntries().getData()) {
                addCell(table, entry.getEffectiveDate().format(DATE_FMT), Element.ALIGN_CENTER);
                addCell(table, entry.getSourceType().name(), Element.ALIGN_LEFT);
                addCell(table, entry.getNotes() != null ? entry.getNotes() : "-", Element.ALIGN_LEFT);
                addCell(table, formatMoney(entry.getAmount()), Element.ALIGN_RIGHT);
                addCell(table, formatMoney(entry.getRunningBalance()), Element.ALIGN_RIGHT);
            }
        }

        addGrandTotalRow(table, "Closing Balance", formatMoney(statement.getClosingBalance()), 4);
        document.add(table);

        addFooterNote(document, "Official supplier statement of account.");
        document.close();
        return out.toByteArray();
    }

    @Transactional(readOnly = true)
    public byte[] generateExpensePdf(Long expenseId) {
        OperationalExpense expense = operationalExpenseRepository.findById(expenseId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found: " + expenseId));
        ShopSettings settings = getShopSettings();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(document, out);
        document.open();

        addHeaderSection(document, settings, "OPERATIONAL EXPENSE VOUCHER", expense.getExpenseNumber(), expense.getExpenseDate());

        PdfPTable meta = new PdfPTable(2);
        meta.setWidthPercentage(100);
        meta.setSpacingAfter(12);

        meta.addCell(createCleanCell("Category: " + expense.getCategory(), Element.ALIGN_LEFT, FONT_BODY_BOLD));
        meta.addCell(createCleanCell("Status: " + expense.getStatus(), Element.ALIGN_RIGHT, FONT_BODY_BOLD));
        meta.addCell(createCleanCell("Description: " + expense.getDescription(), Element.ALIGN_LEFT, FONT_BODY));
        meta.addCell(createCleanCell("Ext Ref: " + (expense.getExternalReference() != null ? expense.getExternalReference() : "-"), Element.ALIGN_RIGHT, FONT_BODY));
        document.add(meta);

        float[] widths = {1f, 4f, 4f, 3f};
        PdfPTable table = createTable(widths);
        addHeaderCell(table, "#");
        addHeaderCell(table, "Payment Method");
        addHeaderCell(table, "Reference");
        addHeaderCell(table, "Amount (₹)");

        int idx = 1;
        for (OperationalExpenseComponent comp : expense.getComponents()) {
            addCell(table, String.valueOf(idx++), Element.ALIGN_CENTER);
            addCell(table, comp.getMethod().name(), Element.ALIGN_LEFT);
            addCell(table, comp.getOnlineReference() != null ? comp.getOnlineReference() : "-", Element.ALIGN_LEFT);
            addCell(table, formatMoney(comp.getAmount()), Element.ALIGN_RIGHT);
        }

        addGrandTotalRow(table, "Total Expense Paid", formatMoney(expense.getAmount()), 3);
        document.add(table);

        if (expense.getNotes() != null && !expense.getNotes().isBlank()) {
            Paragraph notes = new Paragraph("Notes: " + expense.getNotes(), FONT_MUTED);
            notes.setSpacingBefore(8);
            document.add(notes);
        }

        addFooterNote(document, "Shop operational expense payment voucher.");
        document.close();
        return out.toByteArray();
    }

    private ShopSettings getShopSettings() {
        return shopSettingsRepository.findById(ShopSettings.SINGLETON_ID).orElse(null);
    }

    private void addHeaderSection(Document document, ShopSettings settings, String title, String docNumber, LocalDate docDate) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingAfter(12);

        String shopName = settings != null && settings.getName() != null ? settings.getName() : "Himmat Enterprises";
        String shopAddress = settings != null && settings.getAddress() != null ? settings.getAddress() : "";
        String shopPhone = settings != null && settings.getPhone() != null ? "Phone: " + settings.getPhone() : "";
        String shopGstin = settings != null && settings.getGstin() != null ? "GSTIN: " + settings.getGstin() : "";

        PdfPCell leftCell = new PdfPCell();
        leftCell.setBorder(PdfPCell.NO_BORDER);
        leftCell.addElement(new Paragraph(shopName, FONT_SHOP_NAME));
        if (!shopAddress.isBlank()) leftCell.addElement(new Paragraph(shopAddress, FONT_MUTED));
        if (!shopPhone.isBlank()) leftCell.addElement(new Paragraph(shopPhone, FONT_MUTED));
        if (!shopGstin.isBlank()) leftCell.addElement(new Paragraph(shopGstin, FONT_MUTED));
        table.addCell(leftCell);

        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(PdfPCell.NO_BORDER);
        rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph titlePara = new Paragraph(title, FONT_DOC_TITLE);
        titlePara.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(titlePara);

        Paragraph numPara = new Paragraph("Doc #: " + docNumber, FONT_BODY_BOLD);
        numPara.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(numPara);

        if (docDate != null) {
            Paragraph datePara = new Paragraph("Date: " + docDate.format(DATE_FMT), FONT_BODY);
            datePara.setAlignment(Element.ALIGN_RIGHT);
            rightCell.addElement(datePara);
        }
        table.addCell(rightCell);

        document.add(table);
    }

    private void addCustomerPartySection(Document document, Customer customer, String customerName, LocalDate invoiceDate, LocalDate dueDate) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingAfter(14);

        PdfPCell left = new PdfPCell();
        left.setBorder(PdfPCell.NO_BORDER);
        left.addElement(new Paragraph("BILL TO:", FONT_HEADER));
        left.addElement(new Paragraph(customerName != null ? customerName : (customer != null ? customer.getName() : "-"), FONT_BODY_BOLD));
        if (customer != null) {
            if (customer.getPhone() != null && !customer.getPhone().isBlank()) {
                left.addElement(new Paragraph("Phone: " + customer.getPhone(), FONT_BODY));
            }
            if (customer.getGstin() != null && !customer.getGstin().isBlank()) {
                left.addElement(new Paragraph("GSTIN: " + customer.getGstin(), FONT_BODY));
            }
        }
        table.addCell(left);

        PdfPCell right = new PdfPCell();
        right.setBorder(PdfPCell.NO_BORDER);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);
        if (dueDate != null) {
            Paragraph duePara = new Paragraph("Due Date: " + dueDate.format(DATE_FMT), FONT_BODY_BOLD);
            duePara.setAlignment(Element.ALIGN_RIGHT);
            right.addElement(duePara);
        }
        table.addCell(right);

        document.add(table);
    }

    private void addSupplierPartySection(Document document, Supplier supplier, String supplierName, LocalDate date, String supplierInvNum) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingAfter(14);

        PdfPCell left = new PdfPCell();
        left.setBorder(PdfPCell.NO_BORDER);
        left.addElement(new Paragraph("SUPPLIER / VENDOR:", FONT_HEADER));
        left.addElement(new Paragraph(supplierName != null ? supplierName : (supplier != null ? supplier.getName() : "-"), FONT_BODY_BOLD));
        if (supplier != null) {
            if (supplier.getPhone() != null && !supplier.getPhone().isBlank()) {
                left.addElement(new Paragraph("Phone: " + supplier.getPhone(), FONT_BODY));
            }
            if (supplier.getGstin() != null && !supplier.getGstin().isBlank()) {
                left.addElement(new Paragraph("GSTIN: " + supplier.getGstin(), FONT_BODY));
            }
        }
        table.addCell(left);

        PdfPCell right = new PdfPCell();
        right.setBorder(PdfPCell.NO_BORDER);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);
        if (supplierInvNum != null && !supplierInvNum.isBlank()) {
            Paragraph invPara = new Paragraph("Supplier Bill #: " + supplierInvNum, FONT_BODY_BOLD);
            invPara.setAlignment(Element.ALIGN_RIGHT);
            right.addElement(invPara);
        }
        table.addCell(right);

        document.add(table);
    }

    private PdfPTable createTable(float[] widths) {
        PdfPTable table = new PdfPTable(widths);
        table.setWidthPercentage(100);
        table.setSpacingBefore(6);
        table.setSpacingAfter(14);
        return table;
    }

    private void addHeaderCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FONT_HEADER));
        cell.setBackgroundColor(HEADER_BG);
        cell.setPadding(5);
        cell.setBorderColor(Color.LIGHT_GRAY);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }

    private void addCell(PdfPTable table, String text, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", FONT_BODY));
        cell.setPadding(5);
        cell.setBorderColor(Color.LIGHT_GRAY);
        cell.setHorizontalAlignment(alignment);
        table.addCell(cell);
    }

    private void addSummaryRow(PdfPTable table, String label, String value, int labelColspan) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, FONT_BODY_BOLD));
        labelCell.setColspan(labelColspan);
        labelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        labelCell.setPadding(5);
        labelCell.setBorderColor(Color.LIGHT_GRAY);
        table.addCell(labelCell);

        PdfPCell valCell = new PdfPCell(new Phrase(value, FONT_BODY_BOLD));
        valCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        valCell.setPadding(5);
        valCell.setBorderColor(Color.LIGHT_GRAY);
        table.addCell(valCell);
    }

    private void addGrandTotalRow(PdfPTable table, String label, String value, int labelColspan) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, FONT_HEADER));
        labelCell.setColspan(labelColspan);
        labelCell.setBackgroundColor(HEADER_BG);
        labelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        labelCell.setPadding(6);
        labelCell.setBorderColor(Color.LIGHT_GRAY);
        table.addCell(labelCell);

        PdfPCell valCell = new PdfPCell(new Phrase(value, FONT_HEADER));
        valCell.setBackgroundColor(HEADER_BG);
        valCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        valCell.setPadding(6);
        valCell.setBorderColor(Color.LIGHT_GRAY);
        table.addCell(valCell);
    }

    private PdfPCell createCleanCell(String text, int alignment, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorder(PdfPCell.NO_BORDER);
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(3);
        return cell;
    }

    private void addFooterNote(Document document, String note) {
        Paragraph p = new Paragraph(note, FONT_MUTED);
        p.setSpacingBefore(12);
        p.setAlignment(Element.ALIGN_CENTER);
        document.add(p);
    }

    private String formatMoney(BigDecimal amount) {
        if (amount == null) return "0.00";
        return amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
