package com.asalkartechworks.himmat.common.money;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MoneyPolicyTest {

    @Test
    @DisplayName("roundMoney formats to 2 decimals using HALF_UP")
    void testRoundMoney() {
        assertEquals(new BigDecimal("100.00"), MoneyPolicy.roundMoney(new BigDecimal("100")));
        assertEquals(new BigDecimal("141.60"), MoneyPolicy.roundMoney(new BigDecimal("141.6000")));
        assertEquals(new BigDecimal("12.55"), MoneyPolicy.roundMoney(new BigDecimal("12.545")));
        assertEquals(new BigDecimal("12.54"), MoneyPolicy.roundMoney(new BigDecimal("12.544")));
        assertEquals(MoneyPolicy.ZERO, MoneyPolicy.roundMoney(null));
    }

    @Test
    @DisplayName("roundUnitRate formats to 4 decimals using HALF_UP")
    void testRoundUnitRate() {
        assertEquals(new BigDecimal("141.6000"), MoneyPolicy.roundUnitRate(new BigDecimal("141.6")));
        assertEquals(new BigDecimal("12.3457"), MoneyPolicy.roundUnitRate(new BigDecimal("12.34567")));
        assertEquals(new BigDecimal("0.0000"), MoneyPolicy.roundUnitRate(null));
    }

    @Test
    @DisplayName("roundPercent formats to 4 decimals using HALF_UP")
    void testRoundPercent() {
        assertEquals(new BigDecimal("18.0000"), MoneyPolicy.roundPercent(new BigDecimal("18")));
        assertEquals(new BigDecimal("5.2500"), MoneyPolicy.roundPercent(new BigDecimal("5.25")));
        assertEquals(new BigDecimal("0.0000"), MoneyPolicy.roundPercent(null));
    }
}
