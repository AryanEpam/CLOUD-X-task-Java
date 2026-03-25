package com.chtrembl.petstoreapp.service;

import com.chtrembl.petstoreapp.client.ProductServiceClient;
import com.chtrembl.petstoreapp.exception.ProductServiceException;
import com.chtrembl.petstoreapp.model.ContainerEnvironment;
import com.chtrembl.petstoreapp.model.Product;
import com.chtrembl.petstoreapp.model.Tag;
import com.chtrembl.petstoreapp.model.User;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

import static com.chtrembl.petstoreapp.config.Constants.CATEGORY;
import static com.chtrembl.petstoreapp.config.Constants.OPERATION;
import static com.chtrembl.petstoreapp.config.Constants.REQUEST_ID;
import static com.chtrembl.petstoreapp.config.Constants.TRACE_ID;
import static com.chtrembl.petstoreapp.model.Status.AVAILABLE;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductManagementService {

        private final User sessionUser;
        private final ContainerEnvironment containerEnvironment;
        private final ProductServiceClient productServiceClient;

        public Collection<Product> getProductsByCategory(String category, List<Tag> tags) {
                List<Product> products;

                MDC.put(OPERATION, "getProducts");
                MDC.put(CATEGORY, category);

                String requestId = MDC.get(REQUEST_ID);
                String traceId = MDC.get(TRACE_ID);

                // TASK 5.1: Get username and session ID for custom event
                String username = this.sessionUser.getName();
                String sessionId = this.sessionUser.getSessionId();

                log.info("Starting product retrieval operation [RequestID: {}, TraceID: {}, Category: {}, User: {}, Session: {}]",
                                requestId, traceId, category, username, sessionId);

                // TASK 5.1: Track custom event with username and session information
                java.util.Map<String, String> customProperties = new java.util.HashMap<>(
                                this.sessionUser.getCustomEventProperties());
                customProperties.put("category", category);
                customProperties.put("username", username);
                customProperties.put("sessionId", sessionId);

                this.sessionUser.getTelemetryClient().trackEvent("GetProductsByCategory", customProperties, null);

                log.info("User '{}' with session '{}' is requesting products for category '{}'",
                                username, sessionId, category);

                try {
                        this.sessionUser.getTelemetryClient().trackEvent(
                                        String.format("PetStoreApp user %s is requesting to retrieve products from the ProductService",
                                                        this.sessionUser.getName()),
                                        this.sessionUser.getCustomEventProperties(), null);

                        products = productServiceClient.getProductsByStatus(AVAILABLE.getValue());
                        this.sessionUser.setProducts(products);

                        if (tags.stream().anyMatch(t -> t.getName().equals("large"))) {
                                products = products.stream()
                                                .filter(product -> category.equals(product.getCategory().getName())
                                                                && product.getTags().toString().contains("large"))
                                                .toList();
                        } else {
                                products = products.stream()
                                                .filter(product -> category.equals(product.getCategory().getName())
                                                                && product.getTags().toString().contains("small"))
                                                .toList();
                        }

                        // TASK 5.2: Log and track custom metric for number of products returned
                        int productCount = products.size();

                        log.info("Successfully retrieved {} products for category {} with tags {} [RequestID: {}, TraceID: {}, User: {}, Session: {}]",
                                        productCount, category, tags, requestId, traceId, username, sessionId);

                        // Track custom metric - convert int to double
                        this.sessionUser.getTelemetryClient().trackMetric("ProductsReturned", (double) productCount);

                        // Track additional metric information as a custom event
                        java.util.Map<String, String> metricProperties = new java.util.HashMap<>(
                                        this.sessionUser.getCustomEventProperties());
                        metricProperties.put("category", category);
                        metricProperties.put("productCount", String.valueOf(productCount));
                        metricProperties.put("username", username);
                        metricProperties.put("sessionId", sessionId);

                        // Use custom metrics map for trackEvent
                        java.util.Map<String, Double> metrics = new java.util.HashMap<>();
                        metrics.put("productCount", (double) productCount);

                        this.sessionUser.getTelemetryClient().trackEvent("ProductCountByCategory", metricProperties,
                                        metrics);

                        return products;
                } catch (FeignException fe) {
                        log.error("Feign error retrieving products [RequestID: {}, TraceID: {}, Category: {}, HTTP: {}, Message: {}]",
                                        requestId, traceId, category, fe.status(), fe.getMessage(), fe);

                        this.sessionUser.getTelemetryClient().trackException(fe);
                        this.sessionUser.getTelemetryClient().trackEvent(
                                        String.format("PetStoreApp %s received Feign error %s (HTTP %d), container host: %s",
                                                        this.sessionUser.getName(),
                                                        fe.getMessage(),
                                                        fe.status(),
                                                        this.containerEnvironment.getContainerHostName()));
                        log.error("Failed to retrieve products from ProductService via Feign client", fe);
                        throw new ProductServiceException("Unable to retrieve products from product service", fe);
                } finally {
                        MDC.remove(OPERATION);
                        MDC.remove(CATEGORY);
                }
        }

}
