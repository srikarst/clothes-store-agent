SET SCHEMA dbo;

DELETE FROM dbo.address;
DELETE FROM dbo.person;

DELETE FROM dbo.order_items;
DELETE FROM dbo.orders;
DELETE FROM dbo.products;
DELETE FROM dbo.customers;

INSERT INTO dbo.customers (id, name, email, created_at) VALUES
  (1, 'Alice', 'alice@example.com', DATEADD('DAY', -40, CURRENT_TIMESTAMP)),
  (2, 'Bob',   'bob@example.com',   DATEADD('DAY', -10, CURRENT_TIMESTAMP)),
  (3, 'Cara',  'cara@example.com',  DATEADD('DAY',  -5, CURRENT_TIMESTAMP));

INSERT INTO dbo.products (id, name, category, price) VALUES
  (1, 'T-Shirt',  'Apparel Tops',      20.00),
  (2, 'Hoodie',   'Apparel Outerwear', 45.00),
  (3, 'Jeans',    'Apparel Bottoms',   55.00),
  (4, 'Jacket',   'Apparel Outerwear', 85.00),
  (5, 'Sneakers', 'Apparel Footwear',  65.00);

INSERT INTO dbo.orders (id, customer_id, created_at, status) VALUES
  (1, 1, DATEADD('DAY', -35, CURRENT_TIMESTAMP), 'completed'),
  (2, 2, DATEADD('DAY', -34, CURRENT_TIMESTAMP), 'completed'),
  (3, 2, DATEADD('DAY',  -3, CURRENT_TIMESTAMP), 'completed'),
  (4, 3, DATEADD('DAY',  -2, CURRENT_TIMESTAMP), 'completed');

INSERT INTO dbo.order_items (order_id, product_id, qty, unit_price, discount) VALUES
  (1, 1, 2, 20.00, 0.00),
  (1, 2, 1, 45.00, 0.10),
  (2, 4, 1, 85.00, 0.00),
  (2, 3, 1, 55.00, 0.15),
  (3, 5, 2, 65.00, 0.05),
  (4, 1, 1, 20.00, 0.00),
  (4, 2, 1, 45.00, 0.00);

INSERT INTO dbo.person (id, name, age) VALUES
  (1, 'Devon', 29),
  (2, 'Mina',  34);

INSERT INTO dbo.address (id, person_id, city) VALUES
  (1, 1, 'Seattle'),
  (2, 1, 'Portland'),
  (3, 2, 'San Francisco');

ALTER TABLE dbo.customers ALTER COLUMN id RESTART WITH 4;
ALTER TABLE dbo.products  ALTER COLUMN id RESTART WITH 6;
ALTER TABLE dbo.orders    ALTER COLUMN id RESTART WITH 5;

ALTER TABLE dbo.person   ALTER COLUMN id RESTART WITH 3;
ALTER TABLE dbo.address  ALTER COLUMN id RESTART WITH 4;
