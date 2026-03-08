package com.flipkart.drift.sdk.model.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder

@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderDetail implements Serializable {
    private String orderId;
    private String orderItemId;
    private String orderItemUnitId;
    private String trackingId;
}