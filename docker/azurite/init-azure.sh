#!/bin/bash

echo "================================================"
echo ">>> Iniciando configuração do Azurite..."
echo "================================================"

CONN_STR="DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OGTHqGMDtWJ9X+mVQmBXbMJ5v3WFHb3yLbQ==;BlobEndpoint=http://127.0.0.1:10000/devstoreaccount1;"
echo ">>> Criando container file-mover-dest..."
az storage container create \
  --name file-mover-dest \
  --connection-string "$CONN_STR"

echo ">>> Criando container file-mover-source..."
az storage container create \
  --name file-mover-source \
  --connection-string "$CONN_STR"

echo ">>> Criando container file-mover-error..."
az storage container create \
  --name file-mover-error \
  --connection-string "$CONN_STR"

echo ""
echo ">>> Containers disponíveis:"
az storage container list \
  --connection-string "$CONN_STR" \
  --output table

echo "================================================"
echo ">>> Azurite pronto!"
echo "================================================"