# Spring Microservices — demo ejecutable

Cinco microservicios Spring Boot (config server, Eureka, gateway, student con MySQL, course con
PostgreSQL) más dos bases de datos. Todo el stack se levanta con un comando y se comprueba de
punta a punta con un script.

## Prerrequisitos

Docker con Compose. Nada más: no hace falta JDK, ni Maven, ni MySQL, ni PostgreSQL instalados.

## Levantar el stack

```bash
docker compose up --build -d
```

La primera vez tarda varios minutos porque descarga las dependencias de Maven dentro de la
imagen; los arranques siguientes son rápidos.

## Comprobar que funciona

```bash
bash scripts/smoke.sh
```

Espera a que el gateway enrute a ambos servicios, crea un curso y un estudiante, y verifica que
ambos se leyeron de vuelta desde sus bases de datos. Termina con `OK` y salida 0, o falla
diciendo qué no llegó a funcionar.

## URLs

| Servicio         | URL                                |
|------------------|------------------------------------|
| Gateway          | http://localhost:8080              |
| Eureka Dashboard | http://localhost:8761              |
| Config Server    | http://localhost:8888              |
| Student API      | http://localhost:8090/api/student  |
| Course API       | http://localhost:9090/api/course   |

El gateway es el punto de entrada: enruta por nombre de servicio (`lb://msvc-student`,
`lb://msvc-course`) contra el registro de Eureka.

## Ejemplos

Crear un curso a través del gateway:

```bash
curl -X POST http://localhost:8080/api/course/create \
  -H 'Content-Type: application/json' \
  -d '{"name":"Arquitectura","teacher":"Hector"}'
```

Listar los estudiantes (los cinco de ejemplo, sembrados al arrancar):

```bash
curl http://localhost:8080/api/student/all
```

## Tests

```bash
mvn test
```

Solo necesita Docker. Cada test arranca su propia base de datos con Testcontainers y desactiva
el config server y Eureka, así que no hace falta tener nada corriendo.

## Notas

- Durante los primeros ~30 segundos el gateway responde **503**: todavía no hay servicios
  registrados en Eureka. Un servicio reiniciado tarda hasta ~80 segundos en reaparecer.
- Cada arranque recrea el esquema y vuelve a sembrar los datos de ejemplo (`import.sql`):
  reiniciar reinicia los datos.
- Si el puerto 8080 ya está ocupado, usa otro sin tocar el archivo:
  `GATEWAY_PORT=8081 docker compose up --build -d` (y `GATEWAY_PORT=8081 bash scripts/smoke.sh`).
- Para borrar las bases de datos y empezar de cero:

```bash
docker compose down -v
```

La arquitectura completa, módulo a módulo, está en [`HELP.md`](HELP.md).

---

Si el contenido te resulta útil, puedes apoyar el canal con un like y una estrella al
repositorio: https://www.youtube.com/channel/UCTXForyROk00E6jiaNKSV1A
