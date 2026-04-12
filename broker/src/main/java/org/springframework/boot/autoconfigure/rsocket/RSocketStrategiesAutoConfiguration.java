package org.springframework.boot.autoconfigure.rsocket;

/**
 * Compatibility shim for libraries that still reference the Spring Boot 3.x
 * RSocket strategies auto-configuration type by its old package name.
 *
 * <p>Spring Boot 4 moved this auto-configuration to
 * {@code org.springframework.boot.rsocket.autoconfigure}. The broker still
 * depends on {@code rsocket-broker-spring}, which references the previous
 * package in annotation metadata.
 */
public final class RSocketStrategiesAutoConfiguration {}
