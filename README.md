# 📁 File Mover — Apache Camel + Spring Boot 3 + Java 21

Pipeline de movimentação de arquivos com validação de empresa, arquitetura limpa e observabilidade completa.

---

## 🏗️ Arquitetura

```
┌──────────────────────────────────────────────────────────────────┐
│                        CAMEL ROUTE                               │
│  [Dir A] ──poll──► FileTransferProcessor ──► [Dir B]            │
│                           │                                      │
│                    (erro) ▼                                      │
│                       [Dir Error]                                │
└──────────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌──────────────────────────────────────────────────────────────────┐
│                   APPLICATION LAYER (Use Case)                   │
│   ProcessFileTransferUseCaseImpl                                 │
│        │                         │                               │
│        ▼                         ▼                               │
│  CompanyValidationGateway   FileStorageGateway                   │
│        (port/out)               (port/out)                       │
└──────────────────────────────────────────────────────────────────┘
                │                         │
                ▼                         ▼
┌─────────────────────────┐   ┌───────────────────────────────┐
│ FakeCompanyValidation   │   │ LocalFileStorageAdapter       │
│ Adapter (HTTP REST)     │   │ (java.nio Files.move)         │
└─────────────────────────┘   └───────────────────────────────┘
```

### Camadas (Clean Architecture)

| Camada | Pacote | Responsabilidade |
|---|---|---|
| **Domain** | `domain/model`, `domain/exception` | Entidades, regras de negócio puras |
| **Application** | `application/usecase`, `application/port` | Casos de uso, contratos (ports) |
| **Infrastructure** | `infrastructure/` | Camel, adapters HTTP/filesystem, config |

---

## 🚀 Como executar

### Pré-requisitos
- Java 21+
- Maven 3.9+
- Docker + Docker Compose

### 1. Subir stack de observabilidade

```bash
docker compose up -d prometheus loki grafana promtail
```

### 2. Executar a aplicação

```bash
mvn spring-boot:run
```

Ou com variáveis de ambiente customizadas:

```bash
FILE_MOVER_SOURCE_DIR=/meu/dir/A \
FILE_MOVER_DEST_DIR=/meu/dir/B \
FILE_MOVER_POLL_DELAY=3000 \
mvn spring-boot:run
```

### 3. Testar o fluxo

```bash
# Criar diretórios de teste
mkdir -p /tmp/file-mover/{source,destination,error}

# Criar arquivo de teste (prefixo = companyId)
echo "conteúdo do arquivo" > /tmp/file-mover/source/ACME_relatorio.txt

# Acompanhar logs
tail -f logs/file-mover.log
```

---

## ⚙️ Variáveis de Ambiente

| Variável | Padrão | Descrição |
|---|---|---|
| `FILE_MOVER_SOURCE_DIR` | `/tmp/file-mover/source` | Diretório de origem (Dir A) |
| `FILE_MOVER_DEST_DIR` | `/tmp/file-mover/destination` | Diretório de destino (Dir B) |
| `FILE_MOVER_ERROR_DIR` | `/tmp/file-mover/error` | Diretório para arquivos com erro |
| `FILE_MOVER_POLL_DELAY` | `5000` | Intervalo de polling em ms |
| `FILE_MOVER_FILE_FILTER` | `.*\.txt` | Filtro de arquivos (regex) |
| `VALIDATION_SERVICE_URL` | `http://localhost:8080` | URL do serviço de validação |
| `VALIDATION_SERVICE_PATH` | `/api/v1/validate` | Path do endpoint de validação |
| `VALIDATION_SERVICE_TIMEOUT` | `5000` | Timeout de leitura em ms |
| `VALIDATION_SERVICE_CONNECT_TIMEOUT` | `2000` | Timeout de conexão em ms |
| `LOKI_URL` | `http://localhost:3100/loki/api/v1/push` | URL do Loki para logs |
| `LOG_FILE_PATH` | `logs/file-mover.log` | Caminho do arquivo de log |

---

## 🔍 Observabilidade

### Endpoints Actuator

| Endpoint | Descrição |
|---|---|
| `GET /actuator/health` | Health check com detalhes |
| `GET /actuator/prometheus` | Métricas no formato Prometheus |
| `GET /actuator/metrics` | Lista de métricas disponíveis |
| `GET /actuator/camel` | Status das rotas Camel |
| `GET /actuator/info` | Informações da aplicação |

### Métricas customizadas

| Métrica | Labels | Descrição |
|---|---|---|
| `file_mover_transfers_total` | `status=success\|failure` | Contador de transferências |
| `file_mover_processing_duration_seconds` | — | Histograma de tempo de processamento |

### Acessos

- **Grafana**: http://localhost:3000 (admin/admin)
- **Prometheus**: http://localhost:9090
- **Loki**: http://localhost:3100

---

## 📋 Regras do Serviço Fake de Validação

O `FakeValidationController` (embutido na própria aplicação) simula o serviço externo:

- **Aprovado**: qualquer companyId que **não** esteja na lista de bloqueados
- **Rejeitado**: `BLOCKED`, `SUSPENDED`, `UNKNOWN`

O `companyId` é extraído do **prefixo do nome do arquivo** antes do `_`:
```
ACME_relatorio-2024.txt   →  companyId = "ACME"      ✅ aprovado
BLOCKED_dados.txt         →  companyId = "BLOCKED"   ❌ rejeitado
```

---

## 🔄 Fluxo de Retry (Dead Letter Channel)

```
Tentativa 1  ──falha──► espera 2s
Tentativa 2  ──falha──► espera 4s
Tentativa 3  ──falha──► espera 8s
                              │
                              ▼
                    Move para /error
                    Log nível ERROR
```

---

## 🧪 Testes

```bash
# Apenas testes unitários
mvn test

# Build completo com testes
mvn verify
```

---

## 📦 Build Docker

```bash
# Build da imagem
docker build -t file-mover:latest .

# Subir tudo com Docker Compose
docker compose up -d
```

---

## 📁 Estrutura do Projeto

```
src/main/java/com/filemover/
├── FileMoverApplication.java
├── domain/
│   ├── model/
│   │   ├── FileTransferRequest.java
│   │   ├── FileTransferStatus.java
│   │   └── CompanyValidationResult.java
│   └── exception/
│       ├── CompanyValidationException.java
│       └── FileTransferException.java
├── application/
│   ├── port/
│   │   ├── in/  ProcessFileTransferUseCase.java
│   │   └── out/ CompanyValidationGateway.java
│   │            FileStorageGateway.java
│   └── usecase/
│       └── ProcessFileTransferUseCaseImpl.java
└── infrastructure/
    ├── camel/
    │   ├── route/     FileMoverRoute.java
    │   └── processor/ FileTransferProcessor.java
    ├── adapter/
    │   ├── fake/       FakeCompanyValidationAdapter.java
    │   │               FakeValidationController.java
    │   └── filesystem/ LocalFileStorageAdapter.java
    ├── config/
    │   ├── FileMoverProperties.java
    │   ├── CamelConfig.java
    │   ├── RestClientConfig.java
    │   └── GlobalExceptionHandler.java
    └── logging/
        └── MdcLoggingFilter.java
```
