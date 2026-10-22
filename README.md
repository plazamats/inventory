# Inventory and Order System

A Java 17 inventory and order management application backed by PostgreSQL. The project demonstrates JDBC data access, transaction boundaries, connection pooling with HikariCP, a Swing user interface, and three approaches to safe stock updates under concurrency.

## Requirements

- Java 17 or newer
- Maven 3.9 or newer
- PostgreSQL 14 or newer

## Configuration

The application uses these environment variables:

| Variable | Default |
| --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/inventory` |
| `DB_USER` | `postgres` |
| `DB_PASSWORD` | `postgres` |

Create the database and load the schema and sample data:

```bash
createdb -U postgres inventory
psql -U postgres -d inventory -f schema.sql
psql -U postgres -d inventory -f seed.sql
```

## Run

Compile and run the test suite:

```bash
mvn test
```

The database-backed concurrency test runs when PostgreSQL is available and is skipped when the configured database cannot be reached.

Launch the Swing application:

```bash
mvn exec:java -Dexec.mainClass=com.example.inventory.ui.InventoryFrame
```

Run the concurrency comparison demo:

```bash
mvn exec:java -Dexec.mainClass=com.example.inventory.ConcurrencyDemo
```

The demo compares pessimistic locking, atomic stock updates, and optimistic locking across 50 concurrent threads and 20 repetitions.

## Project structure

- `model`: Product, Customer, Order, and OrderItem classes
- `dao`: JDBC data access methods that receive a caller-owned `Connection`
- `service`: transaction ownership and concurrency strategy selection
- `strategy`: supported stock-update strategies
- `ui`: Swing product and order screens
- `src/test`: concurrency tests and a naive race-condition demonstration

## Concurrency design

A race condition occurs when the result depends on the timing of two transactions. The classic mistake is to read stock, decide that enough exists, and only later update it. Two customers can both read `1` before either update is committed. Both business decisions can then be accepted even though only one item existed. The database `CHECK` constraint remains a final defence against negative persisted stock.

The pessimistic strategy reads each product with `SELECT ... FOR UPDATE`. The first transaction obtains the row lock, checks and reduces stock, and commits. The second transaction waits for that lock and then sees that the stock is gone. This is simple and predictable, but locks can reduce throughput. Transactions holding several locks must use a consistent order to avoid deadlocks.

The atomic update strategy does the check and subtraction in one statement:

```sql
stock_quantity = stock_quantity - ?
...
AND stock_quantity >= ?
```

PostgreSQL evaluates this statement atomically, so competing transactions cannot both claim the same last unit. The service interprets zero updated rows as an out-of-stock failure.

The optimistic strategy reads a version and updates only when that version is still current. A competing update changes the version, so a stale update affects zero rows and the transaction retries. Serialization failures with SQLState `40001` are retried.

The first two strategies use `READ COMMITTED`. The optimistic strategy uses `REPEATABLE READ`. All items in one order are processed by ascending product ID to reduce deadlock risk. Any exception rolls back the entire transaction, so an order cannot reserve only part of its basket.

HikariCP keeps a bounded pool of reusable database connections. The pool is capped at ten connections, has a connection timeout, and returns connections after each service operation.
