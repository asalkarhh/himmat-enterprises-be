package com.asalkartechworks.himmat.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Standard money calculation and rounding policy across the application.
 * Persisted currency values use 2 decimal places (HALF_UP).
 * Intermediate unit rates support up to 4 decimal places.
 * Percentages use up to 4 decimal places.
 */
public final class MoneyPolicy {

    public static final int MONEY_SCALE = 2;
    public static final int UNIT_RATE_SCALE = 4;
    public static final int PERCENT_SCALE = 4;
    public static final RoundingMode DEFAULT_ROUNDING = RoundingMode.HALF_UP;

    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(MONEY_SCALE, DEFAULT_ROUNDING);

    private MoneyPolicy() {
    }

    public static BigDecimal roundMoney(BigDecimal amount) {
        if (amount == null) {
            return ZERO;
        }
        return amount.setScale(MONEY_SCALE, DEFAULT_ROUNDING);
    }

    public static BigDecimal roundUnitRate(BigDecimal rate) {
        if (rate == null) {
            return BigDecimal.ZERO.setScale(UNIT_RATE_SCALE, DEFAULT_ROUNDING);
        }
        return rate.setScale(UNIT_RATE_SCALE, DEFAULT_ROUNDING);
    }

    public static BigDecimal roundPercent(BigDecimal pct) {
        if (pct == null) {
            return BigDecimal.ZERO.setScale(PERCENT_SCALE, DEFAULT_ROUNDING);
        }
        return pct.setScale(PERCENT_SCALE, DEFAULT_ROUNDING);
    }

    public static boolean isPositive(BigDecimal amount) {
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
    }

    public static boolean isNonNegative(BigDecimal amount) {
        return amount != null && amount.compareTo(BigDecimal.ZERO) >= 0;
    }
}
