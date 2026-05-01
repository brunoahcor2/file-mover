# File Mover — Resumo do Projeto

> Java 21 · Spring Boot 3.3.4 · Apache Camel 4.8.0 · Clean Architecture

---

## O que é

Pipeline empresarial de movimentação de arquivos. Monitora um diretório (ou bucket/container/servidor) de origem, valida a empresa responsável pelo arquivo via REST e transfere para o destino configurado. Cada um dos três papéis (source, destination, error) é configurado independentemente por provider.

---

## Arquitetura — Clean Architecture

```
com.filemover/
│
├── domain/
│   ├── model/
│   │   ├── FileTransferRequest.java        ← dados do arquivo + empresa + status
│   │   ├── FileTransferStatus.java         ← enum: RECEIVED, TRANSFERRED, FAILED
│   │   └── CompanyValidationResult.java    ← resultado da validação (valid, reason)
│   └── exception/
│       ├── CompanyValidationException.java ← empresa rejeitada pela validação
│       └── FileTransferException.java      ← falha na leitura/gravação
│
├── application/
│   ├── port/
│   │   ├── in/
│   │   │   └── ProcessFileTransferUseCase.java   ← interface: execute(request)
│   │   └── out/
│   │       ├── StorageGateway.java               ← interface: read / write / delete / providerName
│   │       └── CompanyValidationGateway.java      ← interface: validate(companyId)
│   └── usecase/
│       └── ProcessFileTransferUseCaseImpl.java
│           ├── 1. valida empresa via CompanyValidationGateway
│           ├── 2. lê do sourceStorage
│           ├── 3. grava no destinationStorage
│           └── 4. remove da origem (não crítico se falhar)
│
└── infrastructure/
    ├── camel/
    │   ├── route/
    │   │   └── FileMoverRoute.java
    │   │       ├── source URI → FileTransferProcessor (rota principal)
    │   │       └── direct:fileError → error URI (Dead Letter Channel)
    │   └── processor/
    │       └── FileTransferProcessor.java
    │           ├── resolve headers multi-provider (LOCAL/S3/SFTP/Azure)
    │           ├── extrai companyId do prefixo do filename
    │           ├── timing granular por fase (metadata/build-request/use-case/TOTAL)
    │           └── métricas Micrometer (counters success/failure + timer)
    │
    ├── storage/
    │   ├── StorageType.java               ← enum: LOCAL, S3, AZURE_BLOB, SFTP
    │   ├── StorageGatewayFactory.java     ← @Bean sourceStorage / destinationStorage / errorStorage
    │   ├── StorageRouteUriBuilder.java    ← monta URIs Camel por provider e role
    │   ├── AzureStorageInitializer.java   ← cria containers Azure no startup
    │   └── adapter/
    │       ├── LocalStorageAdapter.java
    │       ├── S3StorageAdapter.java
    │       ├── AzureBlobStorageAdapter.java
    │       └── SftpStorageAdapter.java
    │           ├── write() → mkdir defensivo + put via JSch
    │           ├── read()  → FilterInputStream que fecha session/channel no close()
    │           └── delete() → rm via withChannel (finally garante desconexão)
    │
    ├── adapter/
    │   └── fake/
    │       ├── FakeCompanyValidationAdapter.java  ← REST client para validação
    │       └── FakeValidationController.java      ← endpoint /api/v1/validate/{id}
    │
    └── config/
        ├── FileMoverProperties.java       ← @ConfigurationProperties prefix=file-mover
        ├── RestClientConfig.java          ← timeouts + interceptor HTTP timing
        ├── AzureBlobClientConfig.java     ← beans BlobServiceClient por role
        ├── CamelConfig.java
        ├── GlobalExceptionHandler.java
        └── logging/
            └── MdcLoggingFilter.java      ← injeta traceId em todos os logs via MDC
```

---

## Fluxo de negócio

```
[source] ──poll (Camel)──► FileTransferProcessor
                                    │
                     ┌──────────────▼──────────────┐
                     │  ProcessFileTransferUseCase  │
                     │  1. validate(companyId)      │
                     │  2. sourceStorage.read()     │
                     │  3. destStorage.write()      │
                     │  4. sourceStorage.delete()   │
                     └──────────────────────────────┘
                                    │
                    ┌───────────────┴───────────────┐
                aprovado                        rejeitado / falha (3x retry + backoff)
                    │                               │
              [destination]                      [error]
```

**Importante:** o use case é dono de todo o I/O. O Camel é responsável apenas por:
- Pollear o source (via URI nativa por provider)
- Encaminhar para o `FileTransferProcessor`
- Rotear falhas para o Dead Letter Channel → error storage

O `.to(destinationUri)` foi removido da rota principal para evitar dupla gravação.

---

## Storage — Multi-provider por role

Cada um dos três papéis (source, destination, error) é configurado independentemente:

| Provider   | Source | Destination | Error |
|------------|--------|-------------|-------|
| LOCAL      | ✅     | ✅          | ✅    |
| S3         | ✅     | ✅          | ✅    |
| AZURE_BLOB | ✅     | ✅          | ✅    |
| SFTP       | ✅     | ✅          | ✅    |

**Cenários testados:**

| Cenário            | Status |
|--------------------|--------|
| LOCAL → LOCAL      | ✅ ok  |
| LOCAL → S3         | ✅ ok (LocalStack) |
| S3 → LOCAL         | ✅ ok  |
| LOCAL → Azure Blob | ✅ ok (Azurite) |
| LOCAL → SFTP       | ✅ ok (atmoz/sftp) |

---

## URIs Camel por provider

| Provider   | Componente Camel         | Exemplo de URI (destination)                              |
|------------|--------------------------|-----------------------------------------------------------|
| LOCAL      | `file:`                  | `file:/data/dest?autoCreate=true`                         |
| S3         | `aws2-s3:`               | `aws2-s3://bucket?region=...&accessKey=...`               |
| SFTP       | `sftp:`                  | `sftp://user@host:22//upload?password=...&knownHostsFile=/dev/null` |
| AZURE_BLOB | `azure-storage-blob:`    | `azure-storage-blob://container?serviceClient=#bean_dest` |

**Nota SFTP:** path com barra dupla (`//upload`) força path absoluto no servidor. `knownHostsFile=/dev/null` evita o prompt de `known_hosts` que fazia o Camel responder "no" e abortar.

---

## Observabilidade

| Ferramenta | Função                   | URL                   |
|------------|--------------------------|-----------------------|
| Actuator   | Health, info, métricas   | localhost:8080/actuator |
| Prometheus | Coleta de métricas       | localhost:9090        |
| Grafana    | Dashboards               | localhost:3000        |
| Loki       | Agregação de logs        | localhost:3100        |
| Promtail   | Coleta logs do arquivo   | —                     |

**Métricas customizadas:**
- `file_mover_transfers_total` com tag `status=success|failure`
- `file_mover_processing_duration_seconds`

**Logs com timing granular por fase:**
```
[TIMING] fase=metadata       elapsed=0ms
[TIMING] fase=build-request  elapsed=0ms
[TIMING] fase=validation-*   elapsed=75ms
[TIMING] fase=use-case       elapsed=774ms
[TIMING] fase=TOTAL          elapsed=775ms  status=SUCCESS
```

Todos os logs carregam `traceId` via MDC (gerado no `FileTransferProcessor`, propagado pelo `MdcLoggingFilter`).

---

## Configuração — variáveis de ambiente

```bash
# Diretórios locais
FILE_MOVER_SOURCE_DIR, FILE_MOVER_DEST_DIR, FILE_MOVER_ERROR_DIR
FILE_MOVER_POLL_DELAY

# Provider por role
STORAGE_SOURCE_TYPE          # LOCAL | S3 | AZURE_BLOB | SFTP
STORAGE_DEST_TYPE
STORAGE_ERROR_TYPE

# SFTP source
SOURCE_SFTP_HOST, SOURCE_SFTP_PORT, SOURCE_SFTP_USER, SOURCE_SFTP_PASS, SOURCE_SFTP_DIR

# SFTP destination
DEST_SFTP_HOST, DEST_SFTP_PORT, DEST_SFTP_USER, DEST_SFTP_PASS, DEST_SFTP_DIR

# SFTP error
ERROR_SFTP_HOST, ERROR_SFTP_PORT, ERROR_SFTP_USER, ERROR_SFTP_PASS, ERROR_SFTP_DIR

# S3 source / destination / error
SOURCE_S3_BUCKET, SOURCE_S3_REGION, SOURCE_S3_ENDPOINT, SOURCE_S3_ACCESS_KEY, SOURCE_S3_SECRET_KEY
DEST_S3_BUCKET, DEST_S3_REGION, DEST_S3_ENDPOINT, DEST_S3_ACCESS_KEY, DEST_S3_SECRET_KEY
ERROR_S3_BUCKET ...

# Azure source / destination / error
SOURCE_AZURE_CONN_STR, SOURCE_AZURE_CONTAINER, SOURCE_AZURE_PREFIX
DEST_AZURE_CONN_STR, DEST_AZURE_CONTAINER, DEST_AZURE_PREFIX
ERROR_AZURE_CONN_STR, ERROR_AZURE_CONTAINER

# Validação
VALIDATION_SERVICE_URL, VALIDATION_SERVICE_PATH
```

---

## Stack de containers — docker-compose.yml

| Container        | Imagem                              | Porta  | Função                      |
|------------------|-------------------------------------|--------|-----------------------------|
| file-mover-app   | build local                         | 8080   | Aplicação Java              |
| sftp-server      | atmoz/sftp                          | 2222   | Emulador SFTP               |
| azurite          | mcr.microsoft.com/azure-storage/... | 10000  | Emulador Azure Blob         |
| localstack       | localstack/localstack:3.4           | 4566   | Emulador AWS S3             |
| prometheus       | prom/prometheus:v2.53.0             | 9090   | Coleta métricas             |
| loki             | grafana/loki:3.1.0                  | 3100   | Agregação de logs           |
| promtail         | grafana/promtail:3.1.0              | —      | Coleta logs do arquivo      |
| grafana          | grafana/grafana:11.1.0              | 3000   | Dashboards                  |

---

## Decisões de design relevantes

**Use case como dono do I/O**
O `ProcessFileTransferUseCaseImpl` faz leitura, validação, gravação e remoção. O Camel só poleia e roteia erros. Isso permite trocar o provider sem tocar na rota.

**SftpStorageAdapter com FilterInputStream**
O `read()` retorna um `FilterInputStream` que fecha `ChannelSftp` e `Session` quando o stream é fechado pelo chamador, evitando vazamento de conexão.

**mkdir defensivo no write()**
Antes do `channel.put()`, o adapter verifica se o diretório remoto existe via `channel.stat()` e cria com `channel.mkdir()` se necessário.

**StorageGatewayFactory com @Qualifier**
Três beans independentes (`sourceStorage`, `destinationStorage`, `errorStorage`) injetados com `@Qualifier` no use case — sem acoplamento ao tipo concreto.

**companyId extraído do prefixo do filename**
`ACME_relatorio.txt` → `companyId=ACME`. Fallback para header `X-Company-Id` se presente.
