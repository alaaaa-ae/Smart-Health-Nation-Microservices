package com.g1.serviceregistry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Central service registry of the platform.
 * <p>
 * All registry behaviour (registration, heartbeats, eviction, discovery, replication)
 * is provided by Netflix Eureka Server; this module only configures it.
 * Clients of any language register through Eureka's REST API under {@code /eureka/**}.
 */
@SpringBootApplication
@EnableEurekaServer
public class ServiceRegistryApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceRegistryApplication.class, args);
    }

}
