#!/bin/bash

echo "================================================"
echo ">>> Iniciando configuração do LocalStack S3..."
echo "================================================"

echo ">>> Criando bucket file-mover-source..."
awslocal s3 mb s3://file-mover-source --region us-east-1
awslocal s3api put-bucket-versioning \
  --bucket file-mover-source \
  --versioning-configuration Status=Enabled

echo ">>> Criando bucket file-mover-dest..."
awslocal s3 mb s3://file-mover-dest --region us-east-1
awslocal s3api put-bucket-versioning \
  --bucket file-mover-dest \
  --versioning-configuration Status=Enabled

echo ">>> Criando bucket file-mover-error..."
awslocal s3 mb s3://file-mover-error --region us-east-1
awslocal s3api put-bucket-versioning \
  --bucket file-mover-error \
  --versioning-configuration Status=Enabled

echo ""
echo "================================================"
echo ">>> Buckets disponíveis:"
awslocal s3 ls
echo "================================================"
echo ">>> LocalStack S3 pronto!"
echo "================================================"