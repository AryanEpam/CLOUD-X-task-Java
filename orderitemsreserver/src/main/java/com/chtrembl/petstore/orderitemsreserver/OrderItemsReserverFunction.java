package com.chtrembl.petstore.orderitemsreserver;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.chtrembl.petstore.orderitemsreserver.model.OrderReservation;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OrderItemsReserverFunction {

    private static final Logger logger = Logger.getLogger(OrderItemsReserverFunction.class.getName());
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @FunctionName("orderItemsReserver")
    public HttpResponseMessage run(
            @HttpTrigger(
                    name = "req",
                    methods = {HttpMethod.POST},
                    authLevel = AuthorizationLevel.ANONYMOUS,
                    route = "order/reserve"
            ) HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        context.getLogger().info("OrderItemsReserver function triggered.");

        try {
            String requestBody = request.getBody().orElse(null);
            if (requestBody == null || requestBody.isBlank()) {
                return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                        .body("{\"error\": \"Request body is required\"}")
                        .header("Content-Type", "application/json")
                        .build();
            }

            // Deserialize the order reservation request
            OrderReservation reservation = objectMapper.readValue(requestBody, OrderReservation.class);

            // Validate session ID
            String sessionId = reservation.getSessionId();
            if (sessionId == null || sessionId.isBlank()) {
                return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                        .body("{\"error\": \"Session ID is required\"}")
                        .header("Content-Type", "application/json")
                        .build();
            }

            // Set timestamp
            reservation.setTimestamp(Instant.now().toString());

            // Upload to Blob Storage
            String jsonContent = objectMapper.writeValueAsString(reservation);
            uploadToBlob(sessionId, jsonContent, context);

            context.getLogger().info("Order reservation uploaded for session: " + sessionId);

            return request.createResponseBuilder(HttpStatus.OK)
                    .body("{\"message\": \"Order reservation saved successfully\", \"sessionId\": \"" + sessionId + "\"}")
                    .header("Content-Type", "application/json")
                    .build();

        } catch (Exception e) {
            context.getLogger().log(Level.SEVERE, "Error processing order reservation", e);
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"Failed to process order reservation: " + e.getMessage() + "\"}")
                    .header("Content-Type", "application/json")
                    .build();
        }
    }

    private void uploadToBlob(String sessionId, String jsonContent, ExecutionContext context) {
        String connectionString = System.getenv("BLOB_STORAGE_CONNECTION_STRING");
        String containerName = System.getenv("BLOB_CONTAINER_NAME");

        if (containerName == null || containerName.isBlank()) {
            containerName = "order-items-reserver";
        }

        if (connectionString == null || connectionString.isBlank()) {
            throw new IllegalStateException("BLOB_STORAGE_CONNECTION_STRING environment variable is not set");
        }

        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                .connectionString(connectionString)
                .buildClient();

        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);

        // Create the container if it doesn't exist
        if (!containerClient.exists()) {
            containerClient.create();
            context.getLogger().info("Created blob container: " + containerName);
        }

        // Use session ID for file naming - overwrites existing file for same session
        String blobName = sessionId + ".json";
        BlobClient blobClient = containerClient.getBlobClient(blobName);

        byte[] data = jsonContent.getBytes(StandardCharsets.UTF_8);
        blobClient.upload(new ByteArrayInputStream(data), data.length, true); // true = overwrite

        // Set content type to JSON
        BlobHttpHeaders headers = new BlobHttpHeaders().setContentType("application/json");
        blobClient.setHttpHeaders(headers);

        context.getLogger().info("Uploaded order reservation to blob: " + blobName);
    }
}
