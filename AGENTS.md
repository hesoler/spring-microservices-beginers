# AGENTS.md

Spring Boot 3.2.4 / Spring Cloud 2023.0.0 / Java 17 microservices demo in 5 Maven modules.
`README.md` is the quickstart (Spanish). `HELP.md` is the architecture doc (Spanish).

## Running the stack

`docker compose up --build -d` brings up both databases, config server, Eureka, student, course
and gateway; `bash scripts/smoke.sh` is the end-to-end check (gateway → both services → both
databases, by service name). Both are the supported path; nothing needs to be installed on the
host but Docker.

All published ports are overridable (`GATEWAY_PORT`, `STUDENT_PORT`, `COURSE_PORT`,
`EUREKA_PORT`, `CONFIG_PORT`, `MYSQL_PORT`, `POSTGRES_PORT`) with the documented values as
defaults. `smoke.sh` honours `GATEWAY_PORT`. This exists because host port 8080 is frequently
taken; on this machine a WildFly instance owns it.

One image serves every module: `docker build --build-arg MODULE=microservice-student .`, from a
single `Dockerfile` that copies the poms before the sources to keep the dependency layer cached.

## Build, test, run

- There is **no root wrapper**. System `mvn` at the repo root builds the whole reactor; each module has its own `mvnw`, but the file is not executable — use `sh mvnw` or `mvn`.
- `mvn -pl <module>` from the root still requires all 5 module directories to exist, because the root `pom.xml` lists them as modules.
- Per module: `(cd microservice-student && sh mvnw test)`.
- Start order without containers: config (8888) → eureka (8761) → student/course → gateway (8080).
- No CI, no lint/formatter/typecheck config. Verification is `mvn test` at the root (or per module).

## Configuration lives in the config server

Each service's own `application.yml` holds only `spring.application.name` plus
`configserver:http://${CONFIG_HOST:localhost}:8888`. Ports, datasources, credentials, Eureka
URLs and gateway routes live in `microservice-config/src/main/resources/configurations/msvc-*.yml`
(served from the classpath by `spring.profiles.active: native`).

- The service name (`msvc-student`) is what selects `msvc-student.yml`; renaming one side silently drops all config.
- The import is **not** `optional:`. Without the config server a service fails fast with `I/O error on GET request for "http://localhost:8888/msvc-student/default": Connection refused` instead of booting with no port and no datasource.
- The served values are **placeholders resolved client-side**: the config server hands out `${STUDENT_DB_HOST:localhost}` verbatim, and each service resolves it against its own environment. Setting the variable on the config server container changes nothing.
- Credentials are `${STUDENT_DB_USER:student}` / `${STUDENT_DB_PASSWORD:student}` and `${COURSE_DB_USER:course}` / `${COURSE_DB_PASSWORD:course}` — dev defaults only, overridden by compose.
- `ddl-auto: create` in both DB configs, and it must stay `create`: `microservice-{student,course}/src/main/resources/import.sql` seeds 5 demo rows each, and Hibernate executes that file **only** when it creates the schema. Switching to `update` silently stops the seed; `create` also resets the data on every restart.

## Tests

`mvn test` is green with nothing running: Docker only, no config server, no Eureka, no installed
databases. Each module's `src/test/resources/application.yml` shadows the config import and
disables the Eureka client, and the two controller tests start their own database with
Testcontainers.

- `microservice-config`: `ConfigServerTest` — asserts the served student port and that the gateway routes are `lb://msvc-student` / `lb://msvc-course`, not `localhost` URLs.
- `microservice-student`: `StudentControllerTest` — Testcontainers `mysql:8.0`, create → 201 → read back → filter by course.
- `microservice-course`: `CourseControllerTest` — Testcontainers `postgres:16-alpine`; the Feign boundary is a `@MockBean`, because a `@TestConfiguration` + `@Primary` bean cannot win against `@FeignClient`, whose bean is itself primary.
- `microservice-gateway` and `microservice-eureka`: the original `contextLoads` tests, now isolated from config/Eureka.
- Shadow YAMLs must set `spring.cloud.config.enabled: false`: with the import shadowed but the client enabled, `spring-cloud-starter-config` fails with `No spring.config.import set`.
- Root surefire sets `api.version=1.41` because docker-java defaults to Docker API 1.32 and Docker 23+ rejects anything below 1.40 (`client version 1.32 is too old`).
- Root pom pins `lombok.version` to `1.18.46` and declares `annotationProcessorPaths`: JDK 23+ stops running annotation processors found on the classpath, and Boot's managed Lombok 1.18.30 cannot process JDK 25 at all — either way every Lombok getter and builder silently disappears. Do not remove either line, and do not "fix" it with a `maven-enforcer` JDK guard.
- Surefire logs `Surefire is going to kill self fork JVM. The exit has elapsed 30 seconds after System.exit(0)` and the run still passes; the fork does not exit cleanly after Testcontainers shuts down.
- Tests run ~40-60s per module, and `@Testcontainers` pulls images on first use.

## Modules

| Module | Port | Role |
| --- | --- | --- |
| microservice-config | 8888 | Config server, `native` profile, configs in `src/main/resources/configurations` |
| microservice-eureka | 8761 | Eureka registry |
| microservice-gateway | 8080 | Routes `/api/student/**` → `lb://msvc-student` and `/api/course/**` → `lb://msvc-course`, resolved through Eureka; returns 503 when a service is not registered |
| microservice-student | 8090 | Student CRUD, MySQL |
| microservice-course | 9090 | Course CRUD, PostgreSQL, calls student through Feign |

## Conventions that differ from Spring defaults

- Package roots are inconsistent: eureka uses `com.microservice`, the others `com.microservice.<module>`.
- Look like typos but are existing names: `controller/sto/StudentDTO`, `http/response/StudentByCouseResponse`. Do not rename them in an unrelated change.
- Repositories extend `CrudRepository` and services cast `findAll()` to `List<...>`; follow that shape instead of converting to `JpaRepository`.
- Lombok is declared once in the root `pom.xml` (no `provided` scope) and inherited by every module.
- The Spring Cloud BOM import and `spring-cloud.version` are duplicated in every module `pom.xml`; add Cloud starters to the module, never the root.
- Never pin a version on a dependency the Boot parent manages: with parent 3.2.4, pinning `spring-boot-starter-actuator` to `4.0.4` breaks context loading with `EndpointCondition must be used on @Bean methods`.
- Commit messages: Conventional Commits, no AI attribution, no co-author trailers.

## API surface

- student: `POST /api/student/create`, `GET /api/student/all`, `GET /api/student/search/{id}`, `GET /api/student/search-by-course/{courseId}`
- course: `POST /api/course/create`, `GET /api/course/all`, `GET /api/course/search/{id}`, `GET /api/course/search-student/{courseId}` (Feign hop to student)
- `StudentClient` is `@FeignClient(name = "msvc-student")` with no `url`, so course→student resolves through Eureka and needs `spring-cloud-starter-loadbalancer`. Changing student's port no longer requires an annotation change.
- With student down, `/api/course/search-student/{id}` returns **500** (unhandled Feign failure), not 503 — a known gap, not a design choice.
- `Student.courseId` is a plain `Long` column, not a JPA relation — there are no joins to `Course`.

## Repo hygiene

- `.idea/misc.xml` is tracked and edited by IntelliJ; keep it out of unrelated commits.
- `.agents/skills` and `skills-lock.json` are untracked skill installs; the index is `.atl/skill-registry.md`.
- `.superpowers/`, `.engram/` and `odd/` are local working artifacts and are not part of the build.
