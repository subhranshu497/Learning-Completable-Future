package org.learning.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Payment(
        Long id,
        Long orderId,
        BigDecimal amount,
        String status,          // PENDING, COMPLETED, FAILED, REFUNDED
        String paymentMethod,   // CARD, PAYPAL, BANK_TRANSFER
        LocalDateTime processedAt
) {
    public static Payment of(Long id, Long orderId, BigDecimal amount,
                             String status, String paymentMethod, LocalDateTime processedAt) {
        return new Payment(id, orderId, amount, status, paymentMethod, processedAt);
    }
}
