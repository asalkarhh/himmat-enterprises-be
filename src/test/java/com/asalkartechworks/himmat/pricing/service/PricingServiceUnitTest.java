package com.asalkartechworks.himmat.pricing.service;

import com.asalkartechworks.himmat.catalog.model.Category;
import com.asalkartechworks.himmat.catalog.model.PricingMethod;
import com.asalkartechworks.himmat.catalog.model.Product;
import com.asalkartechworks.himmat.catalog.model.ProductVariant;
import com.asalkartechworks.himmat.catalog.model.Subcategory;
import com.asalkartechworks.himmat.catalog.repository.ProductVariantRepository;
import com.asalkartechworks.himmat.common.errors.ValidationException;
import com.asalkartechworks.himmat.pricing.dto.PricingPreviewLineRequest;
import com.asalkartechworks.himmat.pricing.dto.PricingPreviewLineResponse;
import com.asalkartechworks.himmat.pricing.dto.PricingPreviewRequest;
import com.asalkartechworks.himmat.pricing.dto.PricingPreviewResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PricingServiceUnitTest {

    @Mock
    private ProductVariantRepository productVariantRepository;

    @InjectMocks
    private PricingService pricingService;

    private Category testCategory;
    private Subcategory testSubcategory;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        testCategory = Category.builder()
                .id(1L)
                .name("Pipes & Fittings")
                .purchaseDiscountPct(new BigDecimal("15.0000"))
                .salesDiscountPct(new BigDecimal("10.0000"))
                .version(1L)
                .build();

        testSubcategory = Subcategory.builder()
                .id(2L)
                .category(testCategory)
                .name("CPVC Fittings")
                .build();

        testProduct = Product.builder()
                .id(3L)
                .subcategory(testSubcategory)
                .name("SCH40 Plain Pipe 6M")
                .build();
    }

    @Test
    @DisplayName("Discounted method pricing calculations")
    void testDiscountedPricing() {
        ProductVariant variant = ProductVariant.builder()
                .id(101L)
                .product(testProduct)
                .sku("PIPE-SCH40-12")
                .sizeLabel("1/2 inch")
                .pricingMethod(PricingMethod.MRP_DISCOUNT)
                .mrpInclTax(new BigDecimal("500.00"))
                .gstRatePct(new BigDecimal("18.0000"))
                .isActive(true)
                .version(1L)
                .build();

        when(productVariantRepository.findByIdWithHierarchy(101L)).thenReturn(Optional.of(variant));

        PricingPreviewRequest request = PricingPreviewRequest.builder()
                .lines(List.of(
                        PricingPreviewLineRequest.builder()
                                .variantId(101L)
                                .quantity(2)
                                .discountPct(new BigDecimal("10.0000"))
                                .build()
                ))
                .build();

        PricingPreviewResponse response = pricingService.calculatePreview(request, true);

        assertNotNull(response);
        assertEquals(1, response.getLines().size());
        PricingPreviewLineResponse line = response.getLines().get(0);

        assertEquals(new BigDecimal("500.0000"), line.getBaseInclusivePrice());
        assertEquals(new BigDecimal("10.0000"), line.getDiscountPct());
        assertEquals("EXPLICIT_LINE_OVERRIDE", line.getDiscountSource());
        assertEquals(new BigDecimal("450.0000"), line.getUnitNetInclusive());
        assertEquals(new BigDecimal("900.00"), line.getLineTotal());
        assertEquals(new BigDecimal("900.00"), response.getGrossTotal());
    }

    @Test
    @DisplayName("Cost-plus method calculations")
    void testCostPlusCalculations() {
        ProductVariant variant = ProductVariant.builder()
                .id(102L)
                .product(testProduct)
                .sku("PAINT-WHITE-1L")
                .sizeLabel("1 Litre")
                .pricingMethod(PricingMethod.COST_PLUS)
                .purchaseCostExclTax(new BigDecimal("100.0000"))
                .profitPct(new BigDecimal("20.0000"))
                .gstRatePct(new BigDecimal("18.0000"))
                .isActive(true)
                .version(1L)
                .build();

        when(productVariantRepository.findByIdWithHierarchy(102L)).thenReturn(Optional.of(variant));

        PricingPreviewRequest request = PricingPreviewRequest.builder()
                .lines(List.of(
                        PricingPreviewLineRequest.builder()
                                .variantId(102L)
                                .quantity(1)
                                .build()
                ))
                .build();

        PricingPreviewResponse response = pricingService.calculatePreview(request, true);

        assertNotNull(response);
        PricingPreviewLineResponse line = response.getLines().get(0);

        // 100 * (1 + 0.20) * (1 + 0.18) = 141.60
        assertEquals(new BigDecimal("141.6000"), line.getBaseInclusivePrice());
        assertEquals(new BigDecimal("0.0000"), line.getDiscountPct());
        assertEquals("NONE", line.getDiscountSource());
        assertEquals(new BigDecimal("141.6000"), line.getUnitNetInclusive());
        assertEquals(new BigDecimal("141.60"), line.getLineTotal());
    }

    @Test
    @DisplayName("Tax extraction calculations")
    void testTaxExtraction() {
        ProductVariant variant = ProductVariant.builder()
                .id(103L)
                .product(testProduct)
                .sku("FIXED-ITEM-1")
                .sizeLabel("Standard")
                .pricingMethod(PricingMethod.FIXED)
                .fixedPriceInclTax(new BigDecimal("118.00"))
                .gstRatePct(new BigDecimal("18.0000"))
                .isActive(true)
                .version(1L)
                .build();

        when(productVariantRepository.findByIdWithHierarchy(103L)).thenReturn(Optional.of(variant));

        PricingPreviewRequest request = PricingPreviewRequest.builder()
                .lines(List.of(
                        PricingPreviewLineRequest.builder()
                                .variantId(103L)
                                .quantity(1)
                                .build()
                ))
                .build();

        PricingPreviewResponse response = pricingService.calculatePreview(request, true);

        assertNotNull(response);
        PricingPreviewLineResponse line = response.getLines().get(0);

        assertEquals(new BigDecimal("118.00"), line.getLineTotal());
        assertTrue(line.isTaxBreakdownAvailable());
        assertEquals(new BigDecimal("100.00"), line.getTaxableAmount());
        assertEquals(new BigDecimal("18.00"), line.getTaxAmount());
        assertEquals(new BigDecimal("100.00"), response.getTotalTaxableAmount());
        assertEquals(new BigDecimal("18.00"), response.getTotalTaxAmount());
    }

    @Test
    @DisplayName("Fixed price calculations")
    void testFixedPricingCalculations() {
        ProductVariant variant = ProductVariant.builder()
                .id(104L)
                .product(testProduct)
                .sku("FIXED-FITTING-2")
                .sizeLabel("3/4 inch")
                .pricingMethod(PricingMethod.FIXED)
                .fixedPriceInclTax(new BigDecimal("125.50"))
                .gstRatePct(new BigDecimal("18.0000"))
                .isActive(true)
                .version(1L)
                .build();

        when(productVariantRepository.findByIdWithHierarchy(104L)).thenReturn(Optional.of(variant));

        PricingPreviewRequest request = PricingPreviewRequest.builder()
                .lines(List.of(
                        PricingPreviewLineRequest.builder()
                                .variantId(104L)
                                .quantity(2)
                                .build()
                ))
                .build();

        PricingPreviewResponse response = pricingService.calculatePreview(request, true);

        assertNotNull(response);
        PricingPreviewLineResponse line = response.getLines().get(0);

        assertEquals(new BigDecimal("125.5000"), line.getBaseInclusivePrice());
        assertEquals(new BigDecimal("0.0000"), line.getDiscountPct());
        assertEquals("NONE", line.getDiscountSource());
        assertEquals(new BigDecimal("125.5000"), line.getUnitNetInclusive());
        assertEquals(new BigDecimal("251.00"), line.getLineTotal());
        assertEquals(new BigDecimal("251.00"), response.getGrossTotal());
    }

    @Test
    @DisplayName("COST_PLUS does NOT inherit category sales discount")
    void testCostPlus_DoesNotInheritCategorySalesDiscount() {
        ProductVariant variant = ProductVariant.builder()
                .id(105L)
                .product(testProduct)
                .sku("PAINT-RED-500ML")
                .sizeLabel("500ml")
                .pricingMethod(PricingMethod.COST_PLUS)
                .purchaseCostExclTax(new BigDecimal("100.0000"))
                .profitPct(new BigDecimal("20.0000"))
                .gstRatePct(new BigDecimal("18.0000"))
                .isActive(true)
                .version(1L)
                .build();

        when(productVariantRepository.findByIdWithHierarchy(105L)).thenReturn(Optional.of(variant));

        // Category sales discount is 10%, but request does not specify discount
        PricingPreviewRequest request = PricingPreviewRequest.builder()
                .lines(List.of(
                        PricingPreviewLineRequest.builder()
                                .variantId(105L)
                                .quantity(1)
                                .build()
                ))
                .build();

        PricingPreviewResponse response = pricingService.calculatePreview(request, true);
        PricingPreviewLineResponse line = response.getLines().get(0);

        // Discount must remain 0 and source NONE, NOT 10%
        assertEquals(new BigDecimal("0.0000"), line.getDiscountPct());
        assertEquals("NONE", line.getDiscountSource());
        assertEquals(new BigDecimal("141.6000"), line.getUnitNetInclusive());
    }

    @Test
    @DisplayName("MRP_DISCOUNT automatically inherits category sales discount when no line discount supplied")
    void testMrpDiscount_InheritsCategorySalesDiscount() {
        ProductVariant variant = ProductVariant.builder()
                .id(106L)
                .product(testProduct)
                .sku("VALVE-12")
                .sizeLabel("1/2 inch")
                .pricingMethod(PricingMethod.MRP_DISCOUNT)
                .mrpInclTax(new BigDecimal("200.00"))
                .isActive(true)
                .version(1L)
                .build();

        when(productVariantRepository.findByIdWithHierarchy(106L)).thenReturn(Optional.of(variant));

        PricingPreviewRequest request = PricingPreviewRequest.builder()
                .lines(List.of(
                        PricingPreviewLineRequest.builder()
                                .variantId(106L)
                                .quantity(1)
                                .build()
                ))
                .build();

        PricingPreviewResponse response = pricingService.calculatePreview(request, true);
        PricingPreviewLineResponse line = response.getLines().get(0);

        // Category sales discount is 10%
        assertEquals(new BigDecimal("10.0000"), line.getDiscountPct());
        assertEquals("CATEGORY_SALES_DISCOUNT", line.getDiscountSource());
        assertEquals(new BigDecimal("180.0000"), line.getUnitNetInclusive());
        assertEquals(new BigDecimal("180.00"), line.getLineTotal());
    }

    @Test
    @DisplayName("Missing GST produces no fabricated tax breakdown")
    void testMissingGst_NoFabricatedTaxBreakdown() {
        ProductVariant variant = ProductVariant.builder()
                .id(107L)
                .product(testProduct)
                .sku("NO-GST-ITEM")
                .sizeLabel("Standard")
                .pricingMethod(PricingMethod.FIXED)
                .fixedPriceInclTax(new BigDecimal("100.00"))
                .gstRatePct(null) // Unconfigured GST
                .isActive(true)
                .version(1L)
                .build();

        when(productVariantRepository.findByIdWithHierarchy(107L)).thenReturn(Optional.of(variant));

        PricingPreviewRequest request = PricingPreviewRequest.builder()
                .lines(List.of(
                        PricingPreviewLineRequest.builder()
                                .variantId(107L)
                                .quantity(1)
                                .build()
                ))
                .build();

        PricingPreviewResponse response = pricingService.calculatePreview(request, true);

        assertFalse(response.isTaxBreakdownAvailable());
        assertNull(response.getTotalTaxableAmount());
        assertNull(response.getTotalTaxAmount());
        assertFalse(response.getWarnings().isEmpty());

        PricingPreviewLineResponse line = response.getLines().get(0);
        assertFalse(line.isTaxBreakdownAvailable());
        assertNull(line.getTaxableAmount());
        assertNull(line.getTaxAmount());
    }

    @Test
    @DisplayName("Quantity must be positive integer")
    void testZeroQuantity_ThrowsValidationException() {
        ProductVariant variant = ProductVariant.builder()
                .id(108L)
                .product(testProduct)
                .sku("TEST-ITEM")
                .sizeLabel("Standard")
                .pricingMethod(PricingMethod.FIXED)
                .fixedPriceInclTax(new BigDecimal("100.00"))
                .isActive(true)
                .build();

        when(productVariantRepository.findByIdWithHierarchy(108L)).thenReturn(Optional.of(variant));

        PricingPreviewRequest request = PricingPreviewRequest.builder()
                .lines(List.of(
                        PricingPreviewLineRequest.builder()
                                .variantId(108L)
                                .quantity(0)
                                .build()
                ))
                .build();

        assertThrows(ValidationException.class, () -> pricingService.calculatePreview(request, true));
    }
}
