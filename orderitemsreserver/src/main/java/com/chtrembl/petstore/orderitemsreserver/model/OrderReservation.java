package com.chtrembl.petstore.orderitemsreserver.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderReservation {
    private String id;
    private String sessionId;
    private String email;
    private List<ProductItem> products;
    private String status;
    private Boolean complete;
    private String timestamp;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public List<ProductItem> getProducts() { return products; }
    public void setProducts(List<ProductItem> products) { this.products = products; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Boolean getComplete() { return complete; }
    public void setComplete(Boolean complete) { this.complete = complete; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
}
