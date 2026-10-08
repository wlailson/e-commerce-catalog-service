# E-commerce Catalog Service

Serviço de catálogo responsável por consultar e manter produtos, categorias e estoque. Também consome eventos de pedidos para processar as reservas de estoque.

## Tecnologias

- Java 25, Maven e Spring Boot 4.1.1.
- Spring MVC, validação, Spring Security e OAuth2 Resource Server para validação JWT.
- Spring Data JPA, PostgreSQL e Flyway.
- Spring for Apache Kafka para consumo de eventos.
- Spring Boot Actuator e Micrometer Prometheus Registry.
- springdoc-openapi / Swagger UI.
- Testes com JUnit Jupiter, Mockito e Testcontainers para PostgreSQL e Kafka.

## Executar localmente

Pré-requisitos: JDK 25, PostgreSQL e Kafka acessíveis. O perfil de desenvolvimento é ativado por padrão e sua configuração está em `src/main/resources/application-dev.yaml`; ajuste os valores locais do banco antes de iniciar.

```bash
./mvnw spring-boot:run
```

Configurações importantes podem ser fornecidas por variáveis de ambiente:

| Variável | Uso |
|---|---|
| `SERVER_PORT` | Porta HTTP (padrão `8080`). |
| `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | Conexão com PostgreSQL. |
| `KAFKA_BOOTSTRAP_SERVERS` | Endereço do cluster Kafka (padrão local `localhost:9092`). |
| `JWT_PUBLIC_KEY` | Chave pública usada na validação dos tokens. |
| `CORS_ORIGINS` | Origens permitidas para CORS. |

Flyway executa as migrações do banco no início da aplicação. Para execução via Docker Compose, os arquivos do repositório esperam variáveis de ambiente fornecidas pelo operador; não há arquivo `.env` de exemplo incluído.

## Testes

```bash
./mvnw test
./mvnw verify
```

`verify` inclui a fase de integração configurada com Maven Failsafe. Os testes que inicializam PostgreSQL ou Kafka por Testcontainers precisam de Docker disponível.

## API e observabilidade

Os endpoints do catálogo estão sob `/products`. A interface Swagger local fica em `http://localhost:8080/swagger-ui/index.html` e o documento OpenAPI em `http://localhost:8080/v3/api-docs`.

- Métricas Prometheus: `http://localhost:8080/actuator/prometheus`
- Saúde: `http://localhost:8080/actuator/health`

O serviço consome os tópicos de pedidos `order-created-event` e `order-updated-event` por padrão; os nomes podem ser alterados na configuração Kafka da aplicação.

## Projeto

Veja a arquitetura e os demais serviços no [README central do BFF](https://github.com/wlailson/e-commerce-BFF-service#readme).
