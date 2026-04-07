#!/bin/bash

# ===================================================================
# Azure CLI script for deploying the OrderItemsReserver Azure Function
# and related resources (container-based deployment)
# ===================================================================

set -e

# Configuration - Update these variables before running
RESOURCE_GROUP="petstore-rg"
LOCATION="eastus"
STORAGE_ACCOUNT_NAME="petstoreorderstor"       # max 24 chars, lowercase/numbers only
FUNCTION_APP_NAME="petstore-order-reserver"
ACR_NAME="petstoreacr"
BLOB_CONTAINER_NAME="order-items-reserver"
APP_SERVICE_PLAN="petstore-function-plan"

echo "====================================================================="
echo "Deploying OrderItemsReserver Azure Function"
echo "====================================================================="

# ===================================================================
# 1. Create Resource Group (if not exists)
# ===================================================================
echo "[1/8] Creating resource group: $RESOURCE_GROUP..."
az group create --name $RESOURCE_GROUP --location $LOCATION --output none

# ===================================================================
# 2. Create Storage Account for Blob Storage
# ===================================================================
echo "[2/8] Creating storage account: $STORAGE_ACCOUNT_NAME..."
az storage account create \
    --name $STORAGE_ACCOUNT_NAME \
    --resource-group $RESOURCE_GROUP \
    --location $LOCATION \
    --sku Standard_LRS \
    --kind StorageV2 \
    --output none

BLOB_CONNECTION_STRING=$(az storage account show-connection-string \
    --name $STORAGE_ACCOUNT_NAME \
    --resource-group $RESOURCE_GROUP \
    --query connectionString -o tsv)

# ===================================================================
# 3. Create Blob Container
# ===================================================================
echo "[3/8] Creating blob container: $BLOB_CONTAINER_NAME..."
az storage container create \
    --name $BLOB_CONTAINER_NAME \
    --account-name $STORAGE_ACCOUNT_NAME \
    --connection-string "$BLOB_CONNECTION_STRING" \
    --output none

# ===================================================================
# 4. Create Azure Container Registry
# ===================================================================
echo "[4/8] Creating Azure Container Registry: $ACR_NAME..."
az acr create \
    --name $ACR_NAME \
    --resource-group $RESOURCE_GROUP \
    --sku Basic \
    --admin-enabled true \
    --output none

ACR_LOGIN_SERVER=$(az acr show --name $ACR_NAME --query loginServer -o tsv)
ACR_USERNAME=$(az acr credential show --name $ACR_NAME --query username -o tsv)
ACR_PASSWORD=$(az acr credential show --name $ACR_NAME --query "passwords[0].value" -o tsv)

# ===================================================================
# 5. Build and Push Docker Image to ACR
# ===================================================================
echo "[5/8] Building and pushing Docker image..."
az acr login --name $ACR_NAME

docker build -t ${ACR_LOGIN_SERVER}/orderitemsreserver:latest ./orderitemsreserver
docker push ${ACR_LOGIN_SERVER}/orderitemsreserver:latest

# ===================================================================
# 6. Create App Service Plan (Linux, for container deployment)
# ===================================================================
echo "[6/8] Creating App Service Plan: $APP_SERVICE_PLAN..."
az appservice plan create \
    --name $APP_SERVICE_PLAN \
    --resource-group $RESOURCE_GROUP \
    --location $LOCATION \
    --is-linux \
    --sku B1 \
    --output none

# ===================================================================
# 7. Create Azure Function App with container deployment
# ===================================================================
echo "[7/8] Creating Azure Function App: $FUNCTION_APP_NAME..."
az functionapp create \
    --name $FUNCTION_APP_NAME \
    --resource-group $RESOURCE_GROUP \
    --plan $APP_SERVICE_PLAN \
    --storage-account $STORAGE_ACCOUNT_NAME \
    --image ${ACR_LOGIN_SERVER}/orderitemsreserver:latest \
    --registry-server $ACR_LOGIN_SERVER \
    --registry-username $ACR_USERNAME \
    --registry-password $ACR_PASSWORD \
    --functions-version 4 \
    --output none

# ===================================================================
# 8. Configure Function App Settings
# ===================================================================
echo "[8/8] Configuring Function App settings..."
az functionapp config appsettings set \
    --name $FUNCTION_APP_NAME \
    --resource-group $RESOURCE_GROUP \
    --settings \
    "BLOB_STORAGE_CONNECTION_STRING=$BLOB_CONNECTION_STRING" \
    "BLOB_CONTAINER_NAME=$BLOB_CONTAINER_NAME" \
    "FUNCTIONS_WORKER_RUNTIME=java" \
    "FUNCTIONS_EXTENSION_VERSION=~4" \
    --output none

# ===================================================================
# Done! Print summary
# ===================================================================
FUNCTION_APP_URL="https://${FUNCTION_APP_NAME}.azurewebsites.net"
echo ""
echo "====================================================================="
echo "Deployment Complete!"
echo "====================================================================="
echo ""
echo "Function App URL:      $FUNCTION_APP_URL"
echo "Function Endpoint:     ${FUNCTION_APP_URL}/api/order/reserve"
echo "Storage Account:       $STORAGE_ACCOUNT_NAME"
echo "Blob Container:        $BLOB_CONTAINER_NAME"
echo "Container Registry:    $ACR_LOGIN_SERVER"
echo ""
echo "Next steps:"
echo "  1. Set these env vars on your PetStore App Service/Container App:"
echo "     ORDER_ITEMS_RESERVER_URL=$FUNCTION_APP_URL"
echo ""
echo "  2. To verify, list function keys:"
echo "     az functionapp keys list --name $FUNCTION_APP_NAME --resource-group $RESOURCE_GROUP"
echo ""
echo "  3. To view resources:"
echo "     az resource list --resource-group $RESOURCE_GROUP --output table"
echo "====================================================================="
