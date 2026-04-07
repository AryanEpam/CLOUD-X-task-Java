package com.chtrembl.petstoreapp.service;

import com.chtrembl.petstoreapp.model.Order;
import com.chtrembl.petstoreapp.model.Product;
import com.chtrembl.petstoreapp.model.User;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class OrderItemsReserverService {

    private final User sessionUser;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String functionKey;

    public OrderItemsReserverService(
            User sessionUser,
            @Value("${petstore.service.orderitemsreserver.url:}") String orderItemsReserverUrl,
            @Value("${petstore.service.orderitemsreserver.function-key:}") String functionKey) {
        this.sessionUser = sessionUser;
        this.functionKey = functionKey;
        this.webClient = WebClient.builder()
                .baseUrl(orderItemsReserverUrl)
                .build();
        this.objectMapper = new ObjectMapper()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }

    /**
     * Sends the current order to the OrderItemsReserver Azure Function.
     * The function will upload the order as a JSON file to Blob Storage,
     * using the session ID for file naming (overwriting on each update).
     */
    public void reserveOrderItems(Order order) {
        if (order == null || order.getProducts() == null || order.getProducts().isEmpty()) {
            log.debug("No products in order, skipping reservation");
            return;
        }

        try {
            Map<String, Object> reservation = buildReservationPayload(order);
            String jsonPayload = objectMapper.writeValueAsString(reservation);

            log.info("Sending order reservation for session: {}", sessionUser.getSessionId());

            WebClient.RequestBodySpec requestSpec = webClient.post()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/api/order/reserve");
                        if (functionKey != null && !functionKey.isBlank()) {
                            uriBuilder.queryParam("code", functionKey);
                        }
                        return uriBuilder.build();
                    })
                    .header("Content-Type", "application/json");

            requestSpec.bodyValue(jsonPayload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .subscribe(
                            response -> log.info("Order reservation response: {}", response),
                            error -> log.error("Failed to send order reservation for session {}: {}",
                                    sessionUser.getSessionId(), error.getMessage())
                    );

        } catch (Exception e) {
            log.error("Error preparing order reservation for session {}: {}",
                    sessionUser.getSessionId(), e.getMessage(), e);
        }
    }

    private Map<String, Object> buildReservationPayload(Order order) {
        Map<String, Object> reservation = new HashMap<>();
        reservation.put("id", order.getId());
        reservation.put("sessionId", sessionUser.getSessionId());
        reservation.put("email", sessionUser.getEmail());
        reservation.put("status", order.getStatus() != null ? order.getStatus().toString() : null);
        reservation.put("complete", order.isComplete());
        reservation.put("timestamp", Instant.now().toString());

        List<Map<String, Object>> productItems = new ArrayList<>();
        for (Product product : order.getProducts()) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", product.getId());
            item.put("name", product.getName());
            item.put("quantity", product.getQuantity());
            item.put("photoURL", product.getPhotoURL());
            productItems.add(item);
        }
        reservation.put("products", productItems);

        return reservation;
    }
}
