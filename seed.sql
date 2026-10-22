INSERT INTO products (name, price, stock_quantity)
VALUES ('USB-C Cable', 9.99, 20)
ON CONFLICT DO NOTHING;

INSERT INTO customers (name, email)
VALUES ('Demo Customer', 'demo@example.com')
ON CONFLICT (email) DO NOTHING;
