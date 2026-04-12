package org.springframework.http.client.reactive;

/**
 * Compatibility shim for libraries that still reference the Spring Boot 3 / Spring Framework 6
 * ReactorResourceFactory package name after upgrading the application to Spring Framework 7.
 */
public class ReactorResourceFactory extends org.springframework.http.client.ReactorResourceFactory {}
