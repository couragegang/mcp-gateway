package com.couragegang.mcp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

@Factory
public class JacksonFactory {

    @Singleton
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
