package org.learning.parallel;

import org.learning.model.DashboardData;
import org.learning.model.Order;
import org.learning.model.Payment;
import org.learning.model.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ParallelExecution {
    private final ExecutorService ioExecutor;

    public ParallelExecution() {
        int poolSize = calculatePoolSizeOptimally();
        this.ioExecutor = Executors.newFixedThreadPool(poolSize, new NamedThreadFactory("io-pool"));

    }

    /**
     * Calculate optimal thread pool size for I/O-bound operations.
     *
     * Formula: N_threads = N_cpu × (1 + W/C)
     * Where:
     *   N_cpu = number of CPU cores
     *   W = wait time (time spent waiting for I/O)
     *   C = compute time (time spent doing actual computation)
     *
     * For I/O-heavy work, W/C is typically high (10-100), so we use more threads.
     * Rule of thumb: 2-4× CPU cores for I/O-bound, equal to cores for CPU-bound.
     */
    private int calculatePoolSizeOptimally() {
        int cpuCores = Runtime.getRuntime().availableProcessors();
        // For I/O-bound work with ~90% wait time, multiply by ~2-4
        // Being conservative here; in production, tune based on metrics
        return cpuCores * 2;
    }

    //stimulated Async svc
    public CompletableFuture<User> fetchUser(Long uid, Executor executor){
        return CompletableFuture.supplyAsync(()->{
            simulateLatency(100);
            return User.of(uid, "alice", "alice@example.com", "PREMIUM");
        },executor);
    }
    public CompletableFuture<List<Order>> fetchOrders(Long userId, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            simulateLatency(150);
            return List.of(
                    Order.of(1L, userId, List.of("Laptop", "Mouse"), new BigDecimal("1299.99"), "DELIVERED", LocalDateTime.now().minusDays(5)),
                    Order.of(2L, userId, List.of("Keyboard"), new BigDecimal("149.99"), "SHIPPED", LocalDateTime.now().minusDays(1))
            );
        }, executor);
    }

    public CompletableFuture<List<Payment>> fetchPayments(Long userId, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            simulateLatency(120);
            return List.of(
                    Payment.of(1L, 1L, new BigDecimal("1299.99"), "COMPLETED", "CARD", LocalDateTime.now().minusDays(5)),
                    Payment.of(2L, 2L, new BigDecimal("149.99"), "PENDING", "PAYPAL", LocalDateTime.now().minusDays(1))
            );
        }, executor);
    }

    // Intermediate record for type-safe combining
    private record UserWithOrders(User user, List<Order> orders) {}

    /**
     * ─────────────────────────────────────────────────────────────────────────
     * EXAMPLE 1: allOf - Run All Operations in Parallel
     * ─────────────────────────────────────────────────────────────────────────
     *
     * allOf() returns a CompletableFuture<Void> that completes when ALL
     * input futures complete. It doesn't give you the results directly -
     * you need to extract them from the original futures.
     */
    public CompletableFuture<DashboardData> loadDashboardWithAllOf(Long uid){
        //Launch all three operations in parallel
        CompletableFuture<User> userFuture = fetchUser(uid, ioExecutor);
        CompletableFuture<List<Order>> orderFuture = fetchOrders(uid, ioExecutor);
        CompletableFuture<List<Payment>> paymentFuture = fetchPayments(uid, ioExecutor);

        // all wait to complete
        return CompletableFuture.allOf(userFuture, orderFuture, paymentFuture)
                .thenApply(x->{
                    User user = userFuture.join();
                    List<Order> orders = orderFuture.join();
                    List<Payment> payments = paymentFuture.join();
                    return DashboardData.of(
                            user,
                            orders,
                            payments,
                            orders.size(),
                            calculateAccountHealth(payments)
                    );
                });
    }

    /**
     * ─────────────────────────────────────────────────────────────────────────
     * EXAMPLE 2: thenCombine - Combine Exactly Two Futures
     * ─────────────────────────────────────────────────────────────────────────
     *
     * thenCombine is cleaner when combining exactly two futures.
     * For more than two, use allOf or chain multiple thenCombine calls.
     */
    public CompletableFuture<String> getUserOrderSummary(Long uid){
        CompletableFuture<User> userFuture = fetchUser(uid, ioExecutor);
        CompletableFuture<List<Order>> orderSummaryFuture = fetchOrders(uid, ioExecutor);

        return userFuture.thenCombine(orderSummaryFuture,(user, order)->
                String.format("%s has %d orders", user.username(), order.size())
                );
    }

    /**
     * ─────────────────────────────────────────────────────────────────────────
     * EXAMPLE 3: Chaining thenCombine for Three+ Futures
     * ─────────────────────────────────────────────────────────────────────────
     *
     * Alternative to allOf when you want type-safe combination.
     */

    public CompletableFuture<DashboardData> loadDashboardWithCombine(Long uid){
        CompletableFuture<User> userFuture = fetchUser(uid, ioExecutor);
        CompletableFuture<List<Order>> orderFuture = fetchOrders(uid, ioExecutor);
        CompletableFuture<List<Payment>> paymentList = fetchPayments(uid, ioExecutor);

        return userFuture
                .thenCombine(orderFuture, (user, order)->
                        new UserWithOrders(user,order)
                )
                    .thenCombine(paymentList, (userWithOrders, payment)->
                        DashboardData.of(
                                userWithOrders.user(),
                                userWithOrders.orders(),
                                payment,
                                userWithOrders.orders().size(),
                                calculateAccountHealth(payment)
                            )
                        );
    }

    /**
     * ─────────────────────────────────────────────────────────────────────────
     * EXAMPLE 4: anyOf - First Result Wins (Racing Pattern)
     * ─────────────────────────────────────────────────────────────────────────
     *
     * anyOf() completes as soon as ANY input future completes.
     * Useful for:
     * - Redundant requests to multiple services (first response wins)
     * - Timeout implementation (race with a delayed timeout future)
     * - Cache-aside pattern (race cache lookup vs database query)
     */

    public CompletableFuture<User> fetchUserFromFastestSource(Long uid){
        //race between primary and backup services
        CompletableFuture<User> primarySvc = fetchUser(uid, ioExecutor);
        CompletableFuture<User> secondarySvc = fetchUserFromBackup(uid);

        return CompletableFuture.anyOf(primarySvc,secondarySvc)
                .thenApply(res->(User) res);
    }

    private CompletableFuture<User> fetchUserFromBackup(Long uid) {
        return CompletableFuture.supplyAsync(()->{
            simulateLatency(120);
            return User.of(uid, "alice_backup", "alice@backup.com", "PREMIUM");
        },ioExecutor);
    }

    /**
     * ─────────────────────────────────────────────────────────────────────────
     * EXAMPLE 5: Using Virtual Threads (Java 21+)
     * ─────────────────────────────────────────────────────────────────────────
     *
     * Java 21's virtual threads are perfect for I/O-bound async operations.
     * They're lightweight and can scale to millions of concurrent tasks.
     *
     * Key benefits:
     * - No thread pool sizing headaches
     * - Near-zero memory overhead per thread
     * - Perfect for high-concurrency I/O workloads
     */
    public CompletableFuture<DashboardData> loadDashboardWithVirtualThreads(Long uid){
        // Virtual thread executor - creates new virtual thread per task
        Executor virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
        CompletableFuture<User> userFuture = fetchUser(uid, virtualExecutor);
        CompletableFuture<List<Order>> orderFuture = fetchOrders(uid, virtualExecutor);
        CompletableFuture<List<Payment>> paymentFuture = fetchPayments(uid, virtualExecutor);

        return CompletableFuture.allOf(userFuture, orderFuture, paymentFuture)
                .thenApply(x->DashboardData.of(
                        userFuture.join(),
                        orderFuture.join(),
                        paymentFuture.join(),
                        orderFuture.join().size(),
                        calculateAccountHealth(paymentFuture.join())
                ));
    }

    private String calculateAccountHealth(List<Payment> payments) {
        long failedPayments = payments.stream()
                .filter(p -> "FAILED".equals(p.status()))
                .count();

        if (failedPayments > 2) return "CRITICAL";
        if (failedPayments > 0) return "WARNING";
        return "GOOD";
    }

    private void simulateLatency(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
/**
 * Custom ThreadFactory for meaningful thread names.
 * This is CRITICAL for debugging - you'll thank yourself in production.
 */
class NamedThreadFactory implements ThreadFactory {
    private final AtomicInteger counter = new AtomicInteger(0);
    private final String prefix;

    NamedThreadFactory(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread thread = new Thread(r, prefix + "-" + counter.incrementAndGet());
        thread.setDaemon(true); // Don't prevent JVM shutdown
        return thread;
    }
}
