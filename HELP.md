# Spring Microservices - Guía de Arquitectura y Dependencias

## Tabla de Contenidos

1. [Descripción del Proyecto](#descripción-del-proyecto)
2. [Arquitectura General](#arquitectura-general)
3. [Estructura del Proyecto](#estructura-del-proyecto)
4. [Microservicios](#microservicios)
    - [microservice-config](#microservice-config)
    - [microservice-eureka](#microservice-eureka)
    - [microservice-gateway](#microservice-gateway)
    - [microservice-student](#microservice-student)
    - [microservice-course](#microservice-course)
5. [Dependencias Comunes](#dependencias-comunes)
6. [Configuración Centralizada](#configuración-centralizada)
7. [Ejecución del Proyecto](#ejecución-del-proyecto)

---

## Descripción del Proyecto

Este proyecto es una implementación de arquitectura de microservicios utilizando **Spring Boot 3.2.4** y **Spring Cloud
2023.0.0**. El sistema gestiona estudiantes y cursos, demostrando patrones de microservicios como:

- **Service Discovery** con Eureka
- **API Gateway** con Spring Cloud Gateway
- **Configuration Server** centralizado
- **Comunicación entre microservicios** con Feign Client
- **Persistencia** con JPA (MySQL y PostgreSQL)

---

## Arquitectura General

```
                  ┌────────────────────────────────────────┐
                  │                 CLIENTE                │
                  └────────────────────┬───────────────────┘
                                       │
                                       ▼
            ┌──────────────────────────────────────────────────────┐
            │                     API GATEWAY                      │
            │                (microservice-gateway)                │
            │                      Puerto: 8080                    │
            └──────────────────────────┬───────────────────────────┘
                                       │
               ┌───────────────────────┼───────────────────────┐
               │                       │                       │
               ▼                       ▼                       ▼
 ┌───────────────────────┐ ┌──────────────────────┐ ┌───────────────────────┐
 │  Service Discovery    │ │ microservice-student │ │  microservice-course  │
 │ (microservice-eureka) │ │     Puerto: 8090     │ │      Puerto: 9090     │
 │     Puerto: 8761      │ │    Base: MySQL       │ │   Base: PostgreSQL    │
 └───────────────────────┘ └──────────────────────┘ └───────────────────────┘
                                       ▲                        ▲
                                       │                        │
                                       └────────────┬───────────┘
                                                    │
                                    ┌───────────────┴───────────────┐
                                    │      Configuration Server     │
                                    │     (microservice-config)     │
                                    │         Puerto: 8888          │
                                    └───────────────────────────────┘
```

### Flujo de Comunicación

1. El cliente envía requests al **API Gateway** (puerto 8080)
2. El Gateway enruta las peticiones a los microservicios correspondientes
3. Todos los microservicios se registran en **Eureka** para descubrimiento
4. La configuración centralizada se gestiona desde **Config Server**
5. **microservice-course** se comunica con **microservice-student** mediante **Feign Client**

---

## Estructura del Proyecto

```
SpringMicroservices/
├── pom.xml                          # POM padre (proyecto Maven multi-módulo)
├── microservice-config/             # Servidor de configuración centralizada
├── microservice-eureka/             # Servidor de descubrimiento (Eureka)
├── microservice-gateway/            # API Gateway (Spring Cloud Gateway)
├── microservice-student/            # Microservicio de estudiantes
│   └── src/main/java/com/microservice/student/
│       ├── entities/Student.java    # Entidad JPA
│       ├── controller/StudentController.java
│       ├── service/IStudentService.java
│       ├── service/StudentServiceImpl.java
│       └── persistence/StudentRepository.java
└── microservice-course/             # Microservicio de cursos
    └── src/main/java/com/microservice/course/
        ├── entity/Course.java       # Entidad JPA
        ├── controller/CourseController.java
        ├── service/ICourseService.java
        ├── service/CourseServiceImpl.java
        ├── client/StudentClient.java # Feign Client para comunicación
        ├── persistence/ICourseRepository.java
        └── http/response/StudentByCouseResponse.java
```

---

## Microservicios

### microservice-config

**Propósito**: Servidor de configuración centralizada que permite gestionar las propiedades de todos los microservicios
en un único lugar.

**Puerto**: 8888

**Anotaciones clave**:

```java
@EnableConfigServer
@SpringBootApplication
```

#### Dependencias

| Dependencia                  | Propósito                                                                                                     |
|------------------------------|---------------------------------------------------------------------------------------------------------------|
| `spring-cloud-config-server` | Habilita el servidor de configuración centralizado que sirve archivos de configuración a otros microservicios |
| `spring-boot-starter-test`   | Framework de pruebas para tests unitarios                                                                     |
| `lombok`                     | Generación automática de boilerplate code (getters, setters, constructores)                                   |

#### Configuración (`application.yml`)

```yaml
spring:
  application:
    name: microservice-config
  profiles:
    active: native  # Usa el filesystem en lugar de git
  cloud:
    config:
      server:
        native:
          search-locations: classpath:/configurations  # Busca configs en esta carpeta
```

#### Configuraciones centralizadas (`/configurations/`)

- `msvc-eureka.yml` - Configuración del servidor Eureka
- `msvc-gateway.yml` - Configuración del API Gateway
- `msvc-student.yml` - Configuración del microservicio de estudiantes
- `msvc-course.yml` - Configuración del microservicio de cursos

---

### microservice-eureka

**Propósito**: Servidor de Service Discovery que permite a los microservicios registrarse y descubrirse entre sí
dinámicamente.

**Puerto**: 8761

**Anotaciones clave**:

```java
@EnableEurekaServer
@SpringBootApplication
```

#### Dependencias

| Dependencia                                  | Propósito                                                                    |
|----------------------------------------------|------------------------------------------------------------------------------|
| `spring-boot-starter-actuator`               | Expone endpoints de monitoring y salud del servicio (importante para Eureka) |
| `spring-cloud-starter-config`                | Permite consumir configuración del Config Server                             |
| `spring-cloud-starter-netflix-eureka-server` | Implementación del servidor Eureka para service discovery                    |
| `spring-boot-starter-test`                   | Framework de pruebas                                                         |
| `lombok`                                     | Reducción de boilerplate code                                                |

#### Configuración centralizada (`msvc-eureka.yml`)

```yaml
server:
  port: 8761

eureka:
  instance:
    hostname: localhost
  client:
    register-with-eureka: false  # No se registra en otro Eureka
    fetch-registry: false        # No necesita caché de servicios
    serverUrl:
      defaultZone: http://${eureka.instance.hostname}:${server.port}/eureka/
```

**Nota importante**: Las configuraciones `register-with-eureka: false` y `fetch-registry: false` indican que es el
servidor principal y no necesita registrarse consigo mismo.

---

### microservice-gateway

**Propósito**: API Gateway que actúa como punto de entrada único para todas las peticiones de los clientes, enrutándolas
a los microservicios correspondientes.

**Puerto**: 8080

**Anotaciones clave**:

```java
@EnableDiscoveryClient
@SpringBootApplication
```

#### Dependencias

| Dependencia                                  | Propósito                                                  |
|----------------------------------------------|------------------------------------------------------------|
| `spring-boot-starter-actuator`               | Monitoring y health checks                                 |
| `spring-cloud-starter-config`                | Consumo de configuración centralizada                      |
| `spring-cloud-starter-gateway`               | Implementación del API Gateway con Spring WebFlux reactivo |
| `spring-cloud-starter-netflix-eureka-client` | Registro en Eureka para descubrir servicios dinámicamente  |
| `spring-boot-starter-test`                   | Testing                                                    |

#### Configuración centralizada (`msvc-gateway.yml`)

```yaml
server:
  port: 8080

spring:
  application:
    name: msvc-gateway
  cloud:
    gateway:
      discovery:
        locator:
          enabled: true  # Permite descubrimiento dinámico de rutas
      routes:
        - id: students
          uri: 'http://localhost:8090'  # Ruta al servicio de estudiantes
          predicates:
            - Path=/api/student/**
        - id: courses
          uri: 'http://localhost:9090'  # Ruta al servicio de cursos
          predicates:
            - Path=/api/course/**

eureka:
  client:
    register-with-eureka: false  # Gateway no necesita registrarse
```

#### Cómo funciona el routing

- `Path=/api/student/**` → Redirige a `http://localhost:8090`
- `Path=/api/course/**` → Redirige a `http://localhost:9090`

---

### microservice-student

**Propósito**: Microservicio CRUD para gestión de estudiantes. Almacena datos en MySQL.

**Puerto**: 8090

**Anotaciones clave**:

```java
@EnableDiscoveryClient
@SpringBootApplication
```

#### Dependencias

| Dependencia                                  | Propósito                                                                |
|----------------------------------------------|--------------------------------------------------------------------------|
| `spring-boot-starter-actuator`               | Endpoints de monitoreo (salud, métricas)                                 |
| `spring-boot-starter-data-jpa`               | Implementación de Repository Pattern con JPA/Hibernate para persistencia |
| `spring-boot-starter-web`                    | Controladores REST, servlet web (no reactivo)                            |
| `spring-cloud-starter-config`                | Conexión al Config Server para obtener configuración                     |
| `spring-cloud-starter-netflix-eureka-client` | Registro dinámico en Eureka para descubrimiento                          |
| `mysql-connector-j`                          | Driver JDBC para MySQL                                                   |
| `spring-boot-starter-test`                   | Testing                                                                  |
| `lombok`                                     | Generación automática de código                                          |

#### Estructura de capas

```
controller/      → Capa de presentación (REST APIs)
service/         → Capa de lógica de negocio
persistence/     → Capa de acceso a datos (Repository)
entities/        → Entidades JPA (modelo de dominio)
```

#### Configuración centralizada (`msvc-student.yml`)

```yaml
server:
  port: 8090

spring:
  application:
    name: msvc-student
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/studentDb
    username: root
    password: 150919
  jpa:
    hibernate:
      ddl-auto: create-drop  # Crear tablas automáticamente
    database: mysql
    database-platform: org.hibernate.dialect.MySQLDialect

eureka:
  instance:
    hostname: localhost
  client:
    serviceUrl:
      defaultZone: http://localhost:8761/eureka  # Registro en Eureka
```

#### Modelo de datos (Entidad Student)

```java

@Entity
@Table(name = "students")
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    @Column(name = "last_name")
    private String lastName;
    private String email;
    @Column(name = "course_id")
    private Long courseId;  // Relación con Course
}
```

---

### microservice-course

**Propósito**: Microservicio CRUD para gestión de cursos. Se comunica con microservice-student mediante Feign Client.
Usa PostgreSQL.

**Puerto**: 9090

**Anotaciones clave**:

```java
@EnableFeignClients
@EnableDiscoveryClient
@SpringBootApplication
```

#### Dependencias

| Dependencia                                  | Propósito                                                         |
|----------------------------------------------|-------------------------------------------------------------------|
| `spring-boot-starter-actuator`               | Monitoreo                                                         |
| `spring-boot-starter-data-jpa`               | Persistencia JPA con Hibernate                                    |
| `spring-boot-starter-web`                    | APIs REST                                                         |
| `spring-cloud-starter-config`                | Configuración centralizada                                        |
| `spring-cloud-starter-netflix-eureka-client` | Registro en Eureka                                                |
| `spring-cloud-starter-openfeign`             | **Cliente Feign** para comunicación síncrona entre microservicios |
| `postgresql`                                 | Driver JDBC para PostgreSQL                                       |
| `spring-boot-starter-test`                   | Testing                                                           |
| `lombok`                                     | Reducción de boilerplate                                          |

#### Configuración centralizada (`msvc-course.yml`)

```yaml
server:
  port: 9090

spring:
  application:
    name: msvc-course
  datasource:
    driver-class-name: org.postgresql.Driver
    url: jdbc:postgresql://localhost:5432/coursesDb
    username: postgres
    password: 150919
  jpa:
    hibernate:
      ddl-auto: create-drop
    database: postgresql
    database-platform: org.hibernate.dialect.PostgreSQLDialect

eureka:
  instance:
    hostname: localhost
  client:
    serviceUrl:
      defaultZone: http://localhost:8761/eureka
```

#### Modelo de datos (Entidad Course)

```java

@Entity
@Table(name = "courses")
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String teacher;
}
```

#### Feign Client - Comunicación con Student

```java

@FeignClient(name = "msvc-student", url = "localhost:8090")
public interface StudentClient {

    @GetMapping("/api/student/search-by-course/{courseId}")
    List<StudentDTO> findAllStudentByCourse(@PathVariable Long courseId);
}
```

**¿Por qué Feign?**

- Permite comunicación HTTP declarativa entre microservicios
- Simplifica el código comparado con RestTemplate
- Se integra con Eureka para descubrimiento dinámico de servicios

---

## Dependencias Comunes

### Lombok (`org.projectlombok:lombok`)

**¿Qué es?**: Biblioteca que elimina el boilerplate code en Java mediante anotaciones.

**Anotaciones utilizadas**:

| Anotación             | Efecto                                              |
|-----------------------|-----------------------------------------------------|
| `@Getter`             | Genera getters automáticamente                      |
| `@Setter`             | Genera setters automáticamente                      |
| `@Builder`            | Permite patrón Builder para construcción de objetos |
| `@AllArgsConstructor` | Genera constructor con todos los argumentos         |
| `@NoArgsConstructor`  | Genera constructor sin argumentos                   |

### Spring Boot Starter Parent (`spring-boot-starter-parent`)

**¿Qué es?**: BOM (Bill of Materials) que proporciona:

- Configuración predeterminada de versiones
- Gestión de dependencias transitive
- Configuración del plugin de compilación
- Propiedades para encoding, Java version, etc.

### Spring Cloud Dependencies (`spring-cloud-dependencies`)

**¿Qué es?**: BOM que gestiona las versiones de todos los componentes de Spring Cloud de forma compatible.

**Versión**: `2023.0.0` (compatible con Spring Boot 3.2.x)

---

## Configuración Centralizada

### Flujo de configuración

```
microservice-config (Puerto 8888)
        │
        ├── msvc-eureka.yml
        ├── msvc-gateway.yml
        ├── msvc-student.yml
        └── msvc-course.yml
                │
                ▼
    Todos los microservicios importan desde:
    spring.config.import: optional:configserver:http://localhost:8888
```

### Perfil `native`

El Config Server usa el perfil `native` que significa:

- Busca configuraciones en el classpath (`classpath:/configurations/`)
- No requiere conexión a Git
- Ideal para desarrollo local

### Cómo referenciar configuraciones

Cada microservicio referencia su configuración por nombre:

```yaml
# application.yml del microservicio
spring:
  application:
    name: msvc-student  # Coincide con msvc-student.yml
  config:
    import: optional:configserver:http://localhost:8888
```

---

## Ejecución del Proyecto

### Prerrequisitos

- JDK 17+
- Maven 3.6+
- MySQL (puerto 3306)
- PostgreSQL (puerto 5432)

### Base de datos

**MySQL** (para microservice-student):

```sql
CREATE DATABASE studentDb;
```

**PostgreSQL** (para microservice-course):

```sql
CREATE DATABASE coursesDb;
```

### Orden de ejecución

1. **Config Server** (siempre primero):
   ```bash
   cd microservice-config
   mvn spring-boot:run
   ```

2. **Eureka Server**:
   ```bash
   cd microservice-eureka
   mvn spring-boot:run
   ```

3. **Microservicios** (en paralelo o en cualquier orden):
   ```bash
   cd microservice-student
   mvn spring-boot:run
   
   cd microservice-course
   mvn spring-boot:run
   ```

4. **API Gateway** (opcional, puede ejecutarse antes):
   ```bash
   cd microservice-gateway
   mvn spring-boot:run
   ```

### Verificación

| Servicio         | URL                               |
|------------------|-----------------------------------|
| Eureka Dashboard | http://localhost:8761             |
| Gateway          | http://localhost:8080             |
| Student API      | http://localhost:8090/api/student |
| Course API       | http://localhost:9090/api/course  |
| Config Server    | http://localhost:8888             |

---

## Patrones de Microservicios Implementados

| Patrón                           | Implementación                                              |
|----------------------------------|-------------------------------------------------------------|
| **Service Discovery**            | Netflix Eureka - permite registro y descubrimiento dinámico |
| **API Gateway**                  | Spring Cloud Gateway - punto de entrada único               |
| **Configuration Server**         | Spring Cloud Config - gestión centralizada                  |
| **Comunicación entre servicios** | OpenFeign - cliente HTTP declarativo                        |
| **Persistencia políglota**       | MySQL + PostgreSQL - cada servicio usa su BD                |
| **Patrón Repository**            | Spring Data JPA                                             |
| **Separación por capas**         | Controller → Service → Repository                           |
