package com.example.inventory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class NaiveRaceTest {
    @Test
    void naiveReadThenDecideOversellsBeforeTheDatabaseUpdate() throws Exception {
        AtomicInteger stock = new AtomicInteger(1);
        AtomicInteger acceptedOrders = new AtomicInteger();
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Callable<Void> naiveBuyer = () -> {
            int observedStock = stock.get();
            barrier.await();
            if (observedStock >= 1) {
                acceptedOrders.incrementAndGet();
                stock.decrementAndGet();
            }
            return null;
        };
        Future<Void> first = executor.submit(naiveBuyer);
        Future<Void> second = executor.submit(naiveBuyer);
        first.get();
        second.get();
        executor.shutdown();
        assertEquals(2, acceptedOrders.get());
        assertEquals(-1, stock.get());
    }
}
