package org.learning.model;

import java.time.LocalDateTime;
import java.util.List;

public record UserProfile(
        User user,
        List<String> preferences,
        LocalDateTime lastLoginAt,
        int loyaltyPoints,
        String recommendedPlan
) {
    public static UserProfile of(User user, List<String> preferences,
                                 LocalDateTime lastLoginAt, int loyaltyPoints,
                                 String recommendedPlan) {
        return new UserProfile(user, preferences, lastLoginAt, loyaltyPoints, recommendedPlan);
    }
}
