package com.microservice.course;

import com.microservice.course.client.StudentClient;
import com.microservice.course.controller.sto.StudentDTO;
import com.microservice.course.entity.Course;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class CourseControllerTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("coursesDb")
            .withUsername("course")
            .withPassword("course");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @MockBean
    private StudentClient studentClient;

    @Autowired
    private TestRestTemplate rest;

    @BeforeEach
    void stubStudentService() {
        when(studentClient.findAllStudentByCourse(anyLong())).thenAnswer(invocation -> List.of(
                StudentDTO.builder()
                        .name("Ada").lastName("Lovelace").email("ada@example.com")
                        .courseId(invocation.getArgument(0))
                        .build()));
    }

    @Test
    void createsAndReadsBackACourse() {
        Course course = Course.builder().name("Architecture").teacher("Hector").build();

        ResponseEntity<Void> created = rest.postForEntity("/api/course/create", course, Void.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Course stored = storedByName("Architecture");
        Course found = rest.getForObject("/api/course/search/{id}", Course.class, stored.getId());

        assertThat(found.getName()).isEqualTo("Architecture");
        assertThat(found.getTeacher()).isEqualTo("Hector");
    }

    @Test
    void composesCourseWithStudentsFromTheStudentService() {
        rest.postForEntity("/api/course/create",
                Course.builder().name("Distributed Systems").teacher("Hector").build(), Void.class);
        Course stored = storedByName("Distributed Systems");

        ResponseEntity<String> response = rest.getForEntity(
                "/api/course/search-student/{courseId}", String.class, stored.getId());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("Distributed Systems")
                .contains("Hector")
                .contains("ada@example.com");
    }

    private Course storedByName(String name) {
        Course[] all = rest.getForObject("/api/course/all", Course[].class);
        return Arrays.stream(all)
                .filter(c -> name.equals(c.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("course " + name + " was not persisted"));
    }
}
