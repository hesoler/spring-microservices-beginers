package com.microservice.student;

import com.microservice.student.entities.Student;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class StudentControllerTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("studentDb")
            .withUsername("student")
            .withPassword("student");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    private TestRestTemplate rest;

    @Test
    void createsAndReadsBackAStudent() {
        Student student = Student.builder()
                .name("Ada").lastName("Lovelace").email("ada@example.com").courseId(7L)
                .build();

        ResponseEntity<Void> created = rest.postForEntity("/api/student/create", student, Void.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Student stored = storedByEmail("ada@example.com");
        Student found = rest.getForObject("/api/student/search/{id}", Student.class, stored.getId());

        assertThat(found.getName()).isEqualTo("Ada");
        assertThat(found.getLastName()).isEqualTo("Lovelace");
        assertThat(found.getEmail()).isEqualTo("ada@example.com");
        assertThat(found.getCourseId()).isEqualTo(7L);
    }

    @Test
    void filtersStudentsByCourse() {
        rest.postForEntity("/api/student/create", Student.builder()
                .name("In").lastName("Course").email("in@example.com").courseId(999L).build(), Void.class);
        rest.postForEntity("/api/student/create", Student.builder()
                .name("Out").lastName("OfCourse").email("out@example.com").courseId(998L).build(), Void.class);

        Student[] filtered = rest.getForObject(
                "/api/student/search-by-course/{courseId}", Student[].class, 999L);

        assertThat(filtered).extracting(Student::getEmail).containsExactly("in@example.com");
    }

    private Student storedByEmail(String email) {
        Student[] all = rest.getForObject("/api/student/all", Student[].class);
        return Arrays.stream(all)
                .filter(s -> email.equals(s.getEmail()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("student " + email + " was not persisted"));
    }
}
