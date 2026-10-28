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
          enabled: false  # Las rutas son explícitas, no autogeneradas
      routes:
        - id: students
          uri: lb://msvc-student  # Se resuelve vía Eureka, no es una URL fija
          predicates:
            - Path=/api/student/**
        - id: courses
          uri: lb://msvc-course
          predicates:
            - Path=/api/course/**

eureka:
  client:
    register-with-eureka: false  # Gateway no necesita registrarse
    fetch-registry: true         # Pero sí necesita leer el registro
    serviceUrl:
      defaultZone: http://${EUREKA_HOST:localhost}:8761/eureka/
```

#### Cómo funciona el routing

- `Path=/api/student/**` → `lb://msvc-student`, resuelto contra el registro de Eureka
- `Path=/api/course/**` → `lb://msvc-course`, resuelto contra el registro de Eureka

`lb://` requiere `spring-cloud-starter-loadbalancer` y una instancia registrada. Si el servicio
destino está caído, el gateway responde **503**; no hay URL fija a la que recurrir.

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
    url: jdbc:mysql://${STUDENT_DB_HOST:localhost}:${STUDENT_DB_PORT:3306}/${STUDENT_DB_NAME:studentDb}
    username: ${STUDENT_DB_USER:student}
    password: ${STUDENT_DB_PASSWORD:student}
  jpa:
    hibernate:
      ddl-auto: create  # Recrea el esquema en cada arranque
    database: mysql
    database-platform: org.hibernate.dialect.MySQLDialect

eureka:
  instance:
    prefer-ip-address: true
  client:
    serviceUrl:
      defaultZone: http://${EUREKA_HOST:localhost}:8761/eureka/
```

Las credenciales y el host son *placeholders*: el Config Server los entrega sin resolver y cada
servicio los resuelve contra sus propias variables de entorno. Los valores por defecto sirven
para ejecutar en local, y `docker-compose.yml` los sobrescribe (`STUDENT_DB_HOST: mysql`).

`ddl-auto: create` recrea el esquema en cada arranque y vuelve a ejecutar
`src/main/resources/import.sql`, que siembra cinco estudiantes de ejemplo. Reiniciar el servicio
**reinicia los datos**: es un demo, no una base persistente.

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
    url: jdbc:postgresql://${COURSE_DB_HOST:localhost}:${COURSE_DB_PORT:5432}/${COURSE_DB_NAME:coursesDb}
    username: ${COURSE_DB_USER:course}
    password: ${COURSE_DB_PASSWORD:course}
  jpa:
    hibernate:
      ddl-auto: create
    database: postgresql
    database-platform: org.hibernate.dialect.PostgreSQLDialect

eureka:
  instance:
    prefer-ip-address: true
  client:
    serviceUrl:
      defaultZone: http://${EUREKA_HOST:localhost}:8761/eureka/
```

Igual que en student, `ddl-auto: create` recrea el esquema y vuelve a sembrar
`src/main/resources/import.sql` con cinco cursos de ejemplo en cada arranque.

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

@FeignClient(name = "msvc-student")  // sin url: se resuelve vía Eureka
public interface StudentClient {

    @GetMapping("/api/student/search-by-course/{courseId}")
    List<StudentDTO> findAllStudentByCourse(@PathVariable Long courseId);
}
```

Sin `url`, Feign busca `msvc-student` en el registro de Eureka y reparte la llamada con
`spring-cloud-starter-loadbalancer`. Si student no está registrado, la llamada falla y el
endpoint responde 500.

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
    spring.config.import: configserver:http://${CONFIG_HOST:localhost}:8888
```

El import **no** es `optional:`. Si el Config Server no está arriba, el servicio no arranca y
falla con el mensaje que dice exactamente qué falta:

```
I/O error on GET request for "http://localhost:8888/msvc-student/default": Connection refused
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
    import: 'configserver:http://${CONFIG_HOST:localhost}:8888'
```

---

## Ejecución del Proyecto

### Prerrequisitos

Docker con Compose. Nada más: no hace falta JDK, ni Maven, ni MySQL, ni PostgreSQL instalados.

### Un solo comando

```bash
docker compose up --build -d
```

La primera vez tarda varios minutos porque descarga las dependencias de Maven dentro de la
imagen. Después, los arranques son rápidos. Levanta las siete piezas: `mysql`, `postgres`,
`config`, `eureka`, `student`, `course` y `gateway`.

Espera un poco y comprueba el sistema de punta a punta:

```bash
bash scripts/smoke.sh
```

El script espera a que el gateway enrute a ambos servicios, crea un curso y un estudiante, y
comprueba que ambos se leyeron de vuelta desde sus bases de datos.

El stack levanta el esquema y siembra los datos de ejemplo (`import.sql`) en cada arranque, así
que `docker compose down -v` y otro `up` te deja las bases limpias y sembradas de nuevo.

### Si el puerto 8080 ya está ocupado

Todos los puertos publicados se pueden cambiar sin tocar el archivo:

```bash
GATEWAY_PORT=8081 docker compose up --build -d
GATEWAY_PORT=8081 bash scripts/smoke.sh
```

Lo mismo con `STUDENT_PORT`, `COURSE_PORT`, `EUREKA_PORT`, `CONFIG_PORT`, `MYSQL_PORT` y
`POSTGRES_PORT`.

### Ejecución desde Maven (sin contenedores)

El Config Server es obligatorio y debe arrancar primero; si no está, el servicio falla con
`Connection refused` contra el puerto 8888 en lugar de arrancar a medias. Además necesitas MySQL
en 3306 y PostgreSQL en 5432 con las credenciales por defecto (`student/student` y
`course/course`) o las variables `STUDENT_DB_*` / `COURSE_DB_*` correspondientes.

```bash
cd microservice-config && mvn spring-boot:run     # primero, siempre
cd microservice-eureka  && mvn spring-boot:run
cd microservice-student && mvn spring-boot:run
cd microservice-course  && mvn spring-boot:run
cd microservice-gateway && mvn spring-boot:run
```

### Verificación

| Servicio         | URL                               |
|------------------|-----------------------------------|
| Eureka Dashboard | http://localhost:8761             |
| Gateway          | http://localhost:8080             |
| Student API      | http://localhost:8090/api/student |
| Course API       | http://localhost:9090/api/course  |
| Config Server    | http://localhost:8888             |

Durante los primeros ~30 segundos el gateway responde **503**: todavía no hay servicios
registrados en Eureka. Un servicio reiniciado tarda hasta ~80 segundos en volver a aparecer.

### Tests

```bash
mvn test
```

Solo necesita Docker: cada test arranca su propia base con Testcontainers y desactiva la
importación del Config Server y el cliente de Eureka. No hace falta tener nada corriendo.

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
