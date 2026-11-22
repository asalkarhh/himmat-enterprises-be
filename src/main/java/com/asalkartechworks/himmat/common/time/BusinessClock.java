package com.asalkartechworks.himmat.common.time;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Business clock ensuring consistent business date and timezone handling
 * anchored to Asia/Kolkata while maintaining UTC timestamps for audit fields.
 */
@Component
public class BusinessClock {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");
    public static final ZoneId UTC_ZONE = ZoneId.of("UTC");

    private final Clock clock;

    public BusinessClock() {
        this(Clock.systemUTC());
    }

    public BusinessClock(Clock clock) {
        this.clock = clock;
    }

    /**
     * Current instant in UTC for audit and timestamp persistence.
     */
    public Instant now() {
        return clock.instant();
    }

    /**
     * Current business date in Asia/Kolkata timezone.
     */
    public LocalDate today() {
        return LocalDate.ofInstant(now(), BUSINESS_ZONE);
    }

    /**
     * Current date-time in Asia/Kolkata timezone.
     */
    public LocalDateTime nowInBusinessZone() {
        return LocalDateTime.ofInstant(now(), BUSINESS_ZONE);
    }

    /**
     * ZonedDateTime in Asia/Kolkata timezone.
     */
    public ZonedDateTime zonedDateTime() {
        return now().atZone(BUSINESS_ZONE);
    }
}
