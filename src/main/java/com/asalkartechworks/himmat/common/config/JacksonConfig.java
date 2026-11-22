package com.asalkartechworks.himmat.common.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * Jackson customization to enforce API design conventions:
 * 1. BIGINT IDs serialized as strings to prevent browser integer precision loss.
 * 2. BigDecimals formatted as decimal strings.
 * 3. JSR-310 JavaTimeModule registered for ISO-8601 date-time serialization.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> {
            SimpleModule customModule = new SimpleModule();
            // Serialize Long / BigInteger as String
            customModule.addSerializer(Long.class, ToStringSerializer.instance);
            customModule.addSerializer(Long.TYPE, ToStringSerializer.instance);

            // Serialize BigDecimal as plain string
            customModule.addSerializer(BigDecimal.class, new JsonSerializer<>() {
                @Override
                public void serialize(BigDecimal value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                    if (value == null) {
                        gen.writeNull();
                    } else {
                        gen.writeString(value.toPlainString());
                    }
                }
            });

            builder.modulesToInstall(new JavaTimeModule(), customModule);
            builder.featuresToDisable(
                    SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                    com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_FLOAT_AS_INT
            );
        };
    }
}
