package org.learning.chaining;

import org.learning.model.User;
import org.learning.model.UserProfile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ChainingExample {
    private void stimulateLatency(int milis) {
        try {
            Thread.sleep(milis);
        }catch (InterruptedException e){
            Thread.currentThread().interrupt();
        }
    }
    //chaining and composition
    /**
     * thenApply:  Takes a Function<T, U> -> returns CompletableFuture<U>
     *     it makes the transformation synchronous
     *
     * thenCompose: Takes a Function<T, CompletableFuture<U>> -> returns CompletableFuture<U>
     *     use when the transformation is Async
     *
     *   Analogy - > thenapply : - stream.map()
     *              thenCompose : - stream.flatMap()
     */

    //simulate DB call is a classic example for async
   private CompletableFuture<User> fetchUser(Long uid){
       return CompletableFuture.supplyAsync(()->{
           stimulateLatency(100);
           return User.of(uid, "jane_doe", "jane@example.com", "PREMIUM");

       });
   }
   private CompletableFuture<List<String>> fetchUserPreferences(Long uid){
       return CompletableFuture.supplyAsync(()->{
           stimulateLatency(30);
           return List.of("dark_mode", "email_notifications", "weekly_digest");
       });
   }
   private CompletableFuture<Integer> fetchLoyaltyPoints(Long uid){
      return CompletableFuture.completedFuture(2500);
   }
    private CompletableFuture<String> fetchGreetingTemplate(String username) {
        return CompletableFuture.supplyAsync(() -> {
            stimulateLatency(20);
            return "Hello, " + username + "! Welcome to our platform.";
        });
    }
    private String calculateRecommendedPlan(String currentTier, int loyaltyPoints) {
        if (loyaltyPoints > 5000) return "VIP";
        if (loyaltyPoints > 2000 && "BASIC".equals(currentTier)) return "PREMIUM";
        return currentTier;
    }

    /**
     * ThenApply - sync transformation
     * to get user tier of the provided user
     */
    private CompletableFuture<String> getUserTier(Long uid){
        return fetchUser(uid)
                .thenApply(user->user.tier());
    }

    /**
     * ThenCompose - Async way
     *
     */
    private CompletableFuture<List<String>> getUserPreferencesForUser(Long uid){
        return fetchUser(uid)
                .thenCompose(user -> fetchUserPreferences(user.id()));
    }
    /**
     * ─────────────────────────────────────────────────────────────────────────
     * EXAMPLE 4: Real-World Scenario - User Profile Enrichment
     * ─────────────────────────────────────────────────────────────────────────
     *
     * Common pattern: Fetch user, then enrich with additional data.
     * This demonstrates chaining async calls in a realistic scenario.
     */
    public CompletableFuture<UserProfile> enrichUserProfile(Long uid){
        return fetchUser(uid)
                .thenCompose(user ->
                 //after getting user fetch their preference
                 fetchUserPreferences(user.id())
                         .thenCompose(pref ->
                                 //now fetch their loyality points
                                         fetchLoyaltyPoints(user.id())
                                                 .thenApply(points ->
                                                         //combine everything into userProfile
                                                         //this is sync - object construction
                                                         UserProfile.of(
                                                                 user,
                                                                 pref,
                                                                 LocalDateTime.now(),
                                                                 points,
                                                                 calculateRecommendedPlan(user.tier(), points)
                                                         )
                                                 )
                                 )
                        );
    }

    /**
     * ─────────────────────────────────────────────────────────────────────────
     * EXAMPLE 5: Better Pattern - Combining Independent Futures
     * ─────────────────────────────────────────────────────────────────────────
     *
     * The nested approach above works but is sequential (slower).
     * If preferences and points don't depend on each other, run them in parallel!
     * We'll explore this more in Level 3.
     */
    public CompletableFuture<UserProfile> enrichUserProfileParallel(Long uid){
        return fetchUser(uid)
                .thenCompose(user ->{
                    //declare two separate completableFuture
                    CompletableFuture<List<String>> prefFuture = fetchUserPreferences(user.id());
                    CompletableFuture<Integer> pointFuture = fetchLoyaltyPoints(user.id());
                    //use thenCombine - thencombine will wait for both
                    return prefFuture.thenCombine(pointFuture,(pref, point)->
                                    UserProfile.of(
                                            user,
                                            pref,
                                            LocalDateTime.now(),
                                            point,
                                            calculateRecommendedPlan(user.tier(), point)
                                    )
                            );
                });
    }

    /**
     * ─────────────────────────────────────────────────────────────────────────
     * EXAMPLE 6: Mixing thenApply and thenCompose in a Chain
     * ─────────────────────────────────────────────────────────────────────────
     *
     * Real code often mixes both - just pick the right one for each step.
     */

    public CompletableFuture<String> getPersonalizedGreeting(Long uid){
        return fetchUser(uid)
                .thenApply(user->user.username()) // sync
                .thenCompose(this::fetchGreetingTemplate) // async
                .thenApply(String::toUpperCase); // sync
    }
}
