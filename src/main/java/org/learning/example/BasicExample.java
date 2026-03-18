package org.learning.example;

import org.learning.model.User;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class BasicExample {
    //simulate DB call is a classic example for async
    private User fetchUSerFromDB(Long userId){
        stimulateLatency(100);
        return User.of(userId, "john_doe", "john@example.com", "PREMIUM");
    }

    /**
     * supplyAsync - the starting point
     * supplyAsync runs a supplier in the ForkJoinPool.commonPool() by default
     * It returns immidiately with completablefuture, not blocking the caller
     */
    public CompletableFuture<User> fetchUserAsync(Long userId){
        //this returns immidiately, actual work is done by separate thread
        return CompletableFuture.supplyAsync(()->{
            //this will run on ForkJoinPool.commonPool()
            fetchUSerFromDB(userId);
        });
    }

    /**
     * Blocking Vs Non-Blocking
     */

   public User getUser_blocking(Long uId){
       CompletableFuture<User> future = fetchUserAsync(uId);
       try {
           return future.get();
       }catch (InterruptedException | ExecutionException e){
           Thread.currentThread().interrupt();
           throw new RuntimeException("Failed to fetch user", e);
       }
   }

    /**
     * Non-Blocking
     */
    public CompletableFuture<User> getUser_nonblocking(Long uId){
        return fetchUserAsync(uId);
    }

    /**
     * thenAccept - Processing the result without blocking
     *
     */
    public CompletableFuture<Void> getAndLogUser(Long uId){
        return fetchUserAsync(uId)
                .thenAccept(user ->{
                    //this runs after fetchUserAsync completes, on the same thread
                    System.out.println("Retrieved User "+user.id());
                });
    }

    /**
     * Transforming the result
     *
     */
    public CompletableFuture<String> getUserEmail(Long uId){
        return fetchUserAsync(uId)
                .thenApply(u->u.email());
    }

    //creating already completed future
    public CompletableFuture<User> getUserWithCache(Long uId, User cachedUser){
        if(cachedUser !=null){
            return CompletableFuture.completedFuture(cachedUser);
        }
        return fetchUserAsync(uId);
    }

    //we can use join() or get() when blocking is necessary
    //join() is preferred over get(), as join throws unchecked completionException
    //get() throws checked InterruptedException and ExecutionException

    public User getUserWithJoin(Long uid){
        return fetchUserAsync(uid).join();
    }

    private void stimulateLatency(int milis) {
        try {
            Thread.sleep(milis);
        }catch (InterruptedException e){
            Thread.currentThread().interrupt();
        }
    }
}
