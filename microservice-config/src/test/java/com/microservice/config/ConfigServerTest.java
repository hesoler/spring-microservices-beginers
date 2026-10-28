package com.microservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConfigServerTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void servesStudentConfigUnderItsNameAndPort() {
        String body = rest.getForObject("/msvc-student/default", String.class);

        assertThat(body).contains("\"server.port\"").contains("8090");
    }

    @Test
    void gatewayRoutesResolveServicesByDiscoveryName() {
        String body = rest.getForObject("/msvc-gateway/default", String.class);

        assertThat(body).contains("lb://msvc-student", "lb://msvc-course");
        assertThat(body).doesNotContain("http://localhost:8090", "http://localhost:9090");
    }
}
