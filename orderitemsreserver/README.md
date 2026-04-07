# OrderItemsReserver Azure Function

Azure Function service that receives order reservation requests from the PetStore application and uploads them as JSON files to Azure Blob Storage.

## Overview

- **Trigger**: HTTP POST
- **Runtime**: Java 21
- **Deployment**: Container-based Azure Function
- **Storage**: Azure Blob Storage

## Endpoint

```
POST /api/order/reserve?code={function-key}
```

### Request Body

```json
{
  "id": "order-id",
  "sessionId": "session-id",
  "email": "user@example.com",
  "products": [
    {
      "id": 1,
      "name": "Product Name",
      "quantity": 2,
      "photoURL": "https://..."
    }
  ],
  "status": "placed",
  "complete": false
}
```

### Response

```json
{
  "message": "Order reservation saved successfully",
  "sessionId": "session-id"
}
```

## Blob Storage

- **Container**: `order-items-reserver`
- **File naming**: `{sessionId}.json`
- Files are overwritten on each cart update within the same session

## Environment Variables

| Variable | Description |
|---|---|
| `BLOB_STORAGE_CONNECTION_STRING` | Azure Blob Storage connection string |
| `BLOB_CONTAINER_NAME` | Blob container name (default: `order-items-reserver`) |
| `AzureWebJobsStorage` | Azure Functions storage connection string |

## Local Development

```bash
mvn clean package
# Run with Azure Functions Core Tools
func start
```

## Docker Build

```bash
docker build -t orderitemsreserver .
```
