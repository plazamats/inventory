package com.example.inventory.strategy;

public enum ConcurrencyStrategy {
    PESSIMISTIC,
    ATOMIC_UPDATE,
    OPTIMISTIC
}
