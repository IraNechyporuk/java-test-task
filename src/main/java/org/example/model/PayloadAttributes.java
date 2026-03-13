package org.example.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PayloadAttributes {
    private String correlationId;
    private String tenantName;
}