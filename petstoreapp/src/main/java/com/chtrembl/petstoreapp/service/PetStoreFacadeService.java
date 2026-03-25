package com.chtrembl.petstoreapp.service;

import com.chtrembl.petstoreapp.client.ProductServiceClient;
import com.chtrembl.petstoreapp.model.Order;
import com.chtrembl.petstoreapp.model.Pet;
import com.chtrembl.petstoreapp.model.Product;
import com.chtrembl.petstoreapp.model.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

import static com.chtrembl.petstoreapp.model.Status.AVAILABLE;

@Service
@RequiredArgsConstructor
@Slf4j
public class PetStoreFacadeService {

    private final PetManagementService petManagementService;
    private final ProductManagementService productManagementService;
    private final OrderManagementService orderManagementService;
    private final ProductServiceClient productServiceClient;

    public Collection<Pet> getPets(String category) {
        return petManagementService.getPetsByCategory(category);
    }

    public Collection<Product> getProducts(String category, List<Tag> tags) {
        return productManagementService.getProductsByCategory(category, tags);
    }

    public void updateOrder(long productId, int quantity, boolean completeOrder) {
        orderManagementService.updateOrder(productId, quantity, completeOrder);
    }

    public Order retrieveOrder(String orderId) {
        Order order = orderManagementService.retrieveOrder(orderId);
        enrichOrderProducts(order);
        return order;
    }

    private void enrichOrderProducts(Order order) {
        if (order == null || order.getProducts() == null || order.getProducts().isEmpty()) {
            return;
        }
        try {
            List<Product> availableProducts = productServiceClient.getProductsByStatus(AVAILABLE.getValue());
            if (availableProducts != null && !availableProducts.isEmpty()) {
                for (Product orderProduct : order.getProducts()) {
                    availableProducts.stream()
                            .filter(p -> p.getId() != null && p.getId().equals(orderProduct.getId()))
                            .findFirst()
                            .ifPresent(ap -> {
                                orderProduct.setName(ap.getName());
                                orderProduct.setPhotoURL(ap.getPhotoURL());
                            });
                }
            }
        } catch (Exception e) {
            log.warn("Could not enrich order products with product details: {}", e.getMessage());
        }
    }
}