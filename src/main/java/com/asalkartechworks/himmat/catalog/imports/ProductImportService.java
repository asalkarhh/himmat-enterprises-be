package com.asalkartechworks.himmat.catalog.imports;

import com.asalkartechworks.himmat.catalog.model.Brand;
import com.asalkartechworks.himmat.catalog.model.CatalogImportRow;
import com.asalkartechworks.himmat.catalog.model.CatalogImportRun;
import com.asalkartechworks.himmat.catalog.model.Category;
import com.asalkartechworks.himmat.catalog.model.ImportMode;
import com.asalkartechworks.himmat.catalog.model.ImportRowAction;
import com.asalkartechworks.himmat.catalog.model.ImportStatus;
import com.asalkartechworks.himmat.catalog.model.InventoryBalance;
import com.asalkartechworks.himmat.catalog.model.PricingMethod;
import com.asalkartechworks.himmat.catalog.model.Product;
import com.asalkartechworks.himmat.catalog.model.ProductVariant;
import com.asalkartechworks.himmat.catalog.model.Subcategory;
import com.asalkartechworks.himmat.catalog.repository.BrandRepository;
import com.asalkartechworks.himmat.catalog.repository.CatalogImportRowRepository;
import com.asalkartechworks.himmat.catalog.repository.CatalogImportRunRepository;
import com.asalkartechworks.himmat.catalog.repository.CategoryRepository;
import com.asalkartechworks.himmat.catalog.repository.InventoryBalanceRepository;
import com.asalkartechworks.himmat.catalog.repository.ProductRepository;
import com.asalkartechworks.himmat.catalog.repository.ProductVariantRepository;
import com.asalkartechworks.himmat.catalog.repository.SubcategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductImportService {

    private final ProductImportParser parser;
    private final CatalogImportRunRepository importRunRepository;
    private final CatalogImportRowRepository importRowRepository;
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;
    private final SubcategoryRepository subcategoryRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryBalanceRepository inventoryBalanceRepository;

    public ProductImportResult runImport(File file, ImportMode mode, String operator, ProductImportConfig config) {
        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("Import file does not exist: " + file.getAbsolutePath());
        }

        try (InputStream is = new FileInputStream(file)) {
            byte[] fileBytes = is.readAllBytes();
            String checksum = computeSha256(fileBytes);
            return executeImport(file.getName(), fileBytes, checksum, mode, operator, config);
        } catch (Exception e) {
            log.error("Failed executing import for file {}", file.getName(), e);
            throw new RuntimeException("Import failed: " + e.getMessage(), e);
        }
    }

    public ProductImportResult runImportBytes(String filename, byte[] fileBytes, ImportMode mode, String operator, ProductImportConfig config) {
        String checksum = computeSha256(fileBytes);
        return executeImport(filename, fileBytes, checksum, mode, operator, config);
    }

    @Transactional
    protected ProductImportResult executeImport(
            String filename,
            byte[] fileBytes,
            String checksum,
            ImportMode mode,
            String operator,
            ProductImportConfig config) {

        Instant start = Instant.now();
        Long nextSeq = importRunRepository.getNextRunSequenceNumber();
        String runCode = String.format("IMP-%05d", nextSeq);
        String operatorIdentity = (operator != null && !operator.isBlank()) ? operator.trim() : "system:developer-cli";
        ProductImportConfig activeConfig = config != null ? config : ProductImportConfig.defaultFinolexConfig();

        log.info("Starting product import [{}] in mode [{}] for file [{}] (checksum: {}) by [{}]",
                runCode, mode, filename, checksum, operatorIdentity);

        // Parse file
        ProductImportParser.ParseResult parseResult = parser.parse(new ByteArrayInputStream(fileBytes), activeConfig);

        CatalogImportRun run = CatalogImportRun.builder()
                .runCode(runCode)
                .sourceFilename(filename)
                .sourceFileChecksum(checksum)
                .mappingVersion(activeConfig.getMappingVersion())
                .importMode(mode)
                .operatorIdentity(operatorIdentity)
                .status(parseResult.hasErrors() ? ImportStatus.FAILED : ImportStatus.IN_PROGRESS)
                .startedAt(start)
                .build();
        run = importRunRepository.save(run);

        if (parseResult.hasErrors()) {
            String errorSummary = String.join("\n", parseResult.getParseErrors());
            run.setStatus(ImportStatus.FAILED);
            run.setCompletedAt(Instant.now());
            run.setErrorSummary(errorSummary);
            run.setRejectedRows(parseResult.getParseErrors().size());
            importRunRepository.save(run);

            return ProductImportResult.builder()
                    .runCode(runCode)
                    .sourceFilename(filename)
                    .sourceChecksum(checksum)
                    .mode(mode)
                    .status(ImportStatus.FAILED)
                    .operatorIdentity(operatorIdentity)
                    .totalRows(0)
                    .rejectedRows(parseResult.getParseErrors().size())
                    .startedAt(start)
                    .completedAt(run.getCompletedAt())
                    .durationMs(Instant.now().toEpochMilli() - start.toEpochMilli())
                    .errorSummary(errorSummary)
                    .build();
        }

        List<ProductImportRow> rows = parseResult.getRows();
        List<ProductImportRowOutcome> outcomes = new ArrayList<>();
        int createdCount = 0;
        int updatedCount = 0;
        int unchangedCount = 0;
        int rejectedCount = 0;
        StringBuilder conflicts = new StringBuilder();

        Instant now = Instant.now();

        // If not DRY_RUN, ensure Brand exists
        Brand brand = null;
        if (mode != ImportMode.DRY_RUN) {
            brand = brandRepository.findByNameIgnoreCase(activeConfig.getBrandName())
                    .orElseGet(() -> brandRepository.save(Brand.builder()
                            .name(activeConfig.getBrandName())
                            .isActive(true)
                            .version(0L)
                            .createdAt(now)
                            .updatedAt(now)
                            .build()));
        }
        final Brand targetBrand = brand;

        for (ProductImportRow row : rows) {
            int rowNum = row.getRowNumber();
            String identity = row.getSourceIdentity();

            if (mode == ImportMode.DRY_RUN) {
                // Read-only resolution
                Optional<Brand> bOpt = brandRepository.findByNameIgnoreCase(activeConfig.getBrandName());
                Optional<Category> cOpt = categoryRepository.findByNameIgnoreCase(row.getCategory());
                Optional<Subcategory> sOpt = cOpt.flatMap(c -> subcategoryRepository.findByCategoryIdAndNameIgnoreCase(c.getId(), row.getSubcategory()));
                Optional<Product> pOpt = (bOpt.isPresent() && sOpt.isPresent())
                        ? productRepository.findByBrandIdAndSubcategoryIdAndNameIgnoreCase(bOpt.get().getId(), sOpt.get().getId(), row.getProductName())
                        : Optional.empty();
                Optional<ProductVariant> vOpt = pOpt.flatMap(p -> productVariantRepository.findByProductIdAndSizeLabelIgnoreCase(p.getId(), row.getSizeLabel()));

                if (vOpt.isPresent()) {
                    ProductVariant v = vOpt.get();
                    if (v.getMrpInclTax() != null && v.getMrpInclTax().compareTo(row.getPrice()) == 0) {
                        unchangedCount++;
                        outcomes.add(ProductImportRowOutcome.builder()
                                .rowNumber(rowNum)
                                .sheetName(row.getSheetName())
                                .sourceIdentity(identity)
                                .action(ImportRowAction.UNCHANGED)
                                .variantId(v.getId())
                                .sku(v.getSku())
                                .previousVersion(v.getVersion())
                                .resultingVersion(v.getVersion())
                                .build());
                    } else {
                        updatedCount++;
                        outcomes.add(ProductImportRowOutcome.builder()
                                .rowNumber(rowNum)
                                .sheetName(row.getSheetName())
                                .sourceIdentity(identity)
                                .action(ImportRowAction.DRY_RUN_PLAN)
                                .variantId(v.getId())
                                .sku(v.getSku())
                                .previousVersion(v.getVersion())
                                .resultingVersion(v.getVersion())
                                .errorMessage("Plan: Update price from " + v.getMrpInclTax() + " to " + row.getPrice())
                                .build());
                    }
                } else {
                    createdCount++;
                    outcomes.add(ProductImportRowOutcome.builder()
                                .rowNumber(rowNum)
                                .sheetName(row.getSheetName())
                                .sourceIdentity(identity)
                                .action(ImportRowAction.DRY_RUN_PLAN)
                                .errorMessage("Plan: Create new variant with MRP " + row.getPrice())
                                .build());
                }

            } else {
                // Actual modification modes: CREATE_ONLY or UPDATE
                Category category = categoryRepository.findByNameIgnoreCase(row.getCategory())
                        .orElseGet(() -> categoryRepository.save(Category.builder()
                                .name(row.getCategory())
                                .purchaseDiscountPct(BigDecimal.ZERO)
                                .salesDiscountPct(BigDecimal.ZERO)
                                .isActive(true)
                                .version(0L)
                                .createdAt(now)
                                .updatedAt(now)
                                .build()));

                Subcategory subcategory = subcategoryRepository.findByCategoryIdAndNameIgnoreCase(category.getId(), row.getSubcategory())
                        .orElseGet(() -> subcategoryRepository.save(Subcategory.builder()
                                .category(category)
                                .name(row.getSubcategory())
                                .isActive(true)
                                .version(0L)
                                .createdAt(now)
                                .updatedAt(now)
                                .build()));

                Product product = productRepository.findByBrandIdAndSubcategoryIdAndNameIgnoreCase(targetBrand.getId(), subcategory.getId(), row.getProductName())
                        .orElseGet(() -> productRepository.save(Product.builder()
                                .brand(targetBrand)
                                .subcategory(subcategory)
                                .name(row.getProductName())
                                .isActive(true)
                                .version(0L)
                                .createdAt(now)
                                .updatedAt(now)
                                .build()));

                Optional<ProductVariant> existingVariantOpt = productVariantRepository.findByProductIdAndSizeLabelIgnoreCase(product.getId(), row.getSizeLabel());

                if (existingVariantOpt.isPresent()) {
                    ProductVariant existing = existingVariantOpt.get();
                    if (existing.getMrpInclTax() != null && existing.getMrpInclTax().compareTo(row.getPrice()) == 0) {
                        unchangedCount++;
                        outcomes.add(ProductImportRowOutcome.builder()
                                .rowNumber(rowNum)
                                .sheetName(row.getSheetName())
                                .sourceIdentity(identity)
                                .action(ImportRowAction.UNCHANGED)
                                .variantId(existing.getId())
                                .sku(existing.getSku())
                                .previousVersion(existing.getVersion())
                                .resultingVersion(existing.getVersion())
                                .build());
                    } else if (mode == ImportMode.UPDATE) {
                        // Update allowed mapped field (mrpInclTax) only
                        Long prevVer = existing.getVersion();
                        existing.setMrpInclTax(row.getPrice());
                        existing.setUpdatedAt(now);
                        productVariantRepository.save(existing);
                        updatedCount++;
                        outcomes.add(ProductImportRowOutcome.builder()
                                .rowNumber(rowNum)
                                .sheetName(row.getSheetName())
                                .sourceIdentity(identity)
                                .action(ImportRowAction.UPDATED)
                                .variantId(existing.getId())
                                .sku(existing.getSku())
                                .previousVersion(prevVer)
                                .resultingVersion(existing.getVersion())
                                .build());
                    } else {
                        // CREATE_ONLY mode with conflicting price
                        rejectedCount++;
                        String conflictMsg = String.format("Row %d: Variant exists with different MRP (DB: %s, File: %s). Update mode required.",
                                rowNum, existing.getMrpInclTax(), row.getPrice());
                        conflicts.append(conflictMsg).append("\n");
                        outcomes.add(ProductImportRowOutcome.builder()
                                .rowNumber(rowNum)
                                .sheetName(row.getSheetName())
                                .sourceIdentity(identity)
                                .action(ImportRowAction.REJECTED)
                                .variantId(existing.getId())
                                .sku(existing.getSku())
                                .previousVersion(existing.getVersion())
                                .resultingVersion(existing.getVersion())
                                .errorMessage(conflictMsg)
                                .build());
                    }
                } else {
                    // Create new variant and 1:1 inventory balance
                    String deterministicSku = generateSku(activeConfig.getSkuPrefix(), row);
                    // Ensure SKU is globally unique (fallback slug in rare case)
                    if (productVariantRepository.existsBySku(deterministicSku)) {
                        deterministicSku = deterministicSku + "-" + rowNum;
                    }

                    ProductVariant newVariant = ProductVariant.builder()
                            .product(product)
                            .sku(deterministicSku)
                            .sizeLabel(row.getSizeLabel())
                            .pricingMethod(PricingMethod.MRP_DISCOUNT)
                            .mrpInclTax(row.getPrice())
                            .purchaseCostExclTax(null) // Unknown
                            .gstRatePct(null)          // Unknown
                            .profitPct(null)           // Unknown
                            .fixedPriceInclTax(null)
                            .lowStockThreshold(0)
                            .isActive(true)
                            .version(0L)
                            .createdAt(now)
                            .updatedAt(now)
                            .build();
                    newVariant = productVariantRepository.save(newVariant);

                    // 1:1 inventory balance initialization (quantity = 0, unconfirmed)
                    InventoryBalance initialBalance = InventoryBalance.builder()
                            .variantId(newVariant.getId())
                            .variant(newVariant)
                            .quantityOnHand(0)
                            .openingConfirmed(false)
                            .version(0L)
                            .createdAt(now)
                            .updatedAt(now)
                            .isNew(true)
                            .build();
                    inventoryBalanceRepository.save(initialBalance);

                    createdCount++;
                    outcomes.add(ProductImportRowOutcome.builder()
                            .rowNumber(rowNum)
                            .sheetName(row.getSheetName())
                            .sourceIdentity(identity)
                            .action(ImportRowAction.CREATED)
                            .variantId(newVariant.getId())
                            .sku(newVariant.getSku())
                            .previousVersion(null)
                            .resultingVersion(0L)
                            .build());
                }
            }
        }

        // Persist row audit records
        List<CatalogImportRow> dbRows = new ArrayList<>();
        for (ProductImportRowOutcome o : outcomes) {
            dbRows.add(CatalogImportRow.builder()
                    .importRun(run)
                    .sheetName(o.getSheetName())
                    .rowNumber(o.getRowNumber())
                    .sourceIdentity(o.getSourceIdentity())
                    .targetVariantId(o.getVariantId())
                    .actionTaken(o.getAction())
                    .previousVersion(o.getPreviousVersion())
                    .resultingVersion(o.getResultingVersion())
                    .errorMessage(o.getErrorMessage())
                    .build());
        }
        importRowRepository.saveAll(dbRows);

        // Update run status
        ImportStatus finalStatus = (rejectedCount > 0 && mode == ImportMode.CREATE_ONLY)
                ? ImportStatus.CONFLICT
                : ImportStatus.COMPLETED;

        Instant finish = Instant.now();
        run.setStatus(finalStatus);
        run.setCompletedAt(finish);
        run.setTotalRows(rows.size());
        run.setCreatedRows(createdCount);
        run.setUpdatedRows(updatedCount);
        run.setUnchangedRows(unchangedCount);
        run.setRejectedRows(rejectedCount);
        run.setErrorSummary(conflicts.length() > 0 ? conflicts.toString().trim() : null);
        importRunRepository.save(run);

        ProductImportResult importResult = ProductImportResult.builder()
                .runCode(runCode)
                .sourceFilename(filename)
                .sourceChecksum(checksum)
                .mode(mode)
                .status(finalStatus)
                .operatorIdentity(operatorIdentity)
                .totalRows(rows.size())
                .createdRows(createdCount)
                .updatedRows(updatedCount)
                .unchangedRows(unchangedCount)
                .rejectedRows(rejectedCount)
                .startedAt(start)
                .completedAt(finish)
                .durationMs(finish.toEpochMilli() - start.toEpochMilli())
                .errorSummary(run.getErrorSummary())
                .rowOutcomes(outcomes)
                .build();

        log.info("Product import [{}] finished with status [{}] in {} ms. Creates: {}, Updates: {}, Unchanged: {}, Rejected: {}",
                runCode, finalStatus, importResult.getDurationMs(), createdCount, updatedCount, unchangedCount, rejectedCount);

        return importResult;
    }

    private String generateSku(String prefix, ProductImportRow row) {
        String base = (prefix != null ? prefix.trim() : "FIN-").toUpperCase();
        // Compute short hash of composite key
        String composite = String.format("%s|%s|%s|%s",
                row.getCategory().toLowerCase().trim(),
                row.getSubcategory().toLowerCase().trim(),
                row.getProductName().toLowerCase().trim(),
                row.getSizeLabel().toLowerCase().trim());

        String hash = computeMd5Short(composite);

        // Produce a clean readable slug snippet
        String productSnippet = row.getProductName().replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
        if (productSnippet.length() > 15) {
            productSnippet = productSnippet.substring(0, 15);
        }
        String sizeSnippet = row.getSizeLabel().replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
        if (sizeSnippet.length() > 10) {
            sizeSnippet = sizeSnippet.substring(0, 10);
        }

        String candidate = String.format("%s%s-%s-%s", base, productSnippet, sizeSnippet, hash);
        if (candidate.length() > 100) {
            candidate = candidate.substring(0, 100);
        }
        return candidate;
    }

    private String computeSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 algorithm unavailable", e);
        }
    }

    private String computeMd5Short(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 8).toUpperCase();
        } catch (Exception e) {
            return Integer.toHexString(data.hashCode()).toUpperCase();
        }
    }
}
