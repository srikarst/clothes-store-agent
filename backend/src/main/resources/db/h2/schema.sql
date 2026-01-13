CREATE SCHEMA IF NOT EXISTS dbo;
SET SCHEMA dbo;

DROP VIEW IF EXISTS dbo.v_order_revenue;

DROP TABLE IF EXISTS dbo.order_items;
DROP TABLE IF EXISTS dbo.orders;
DROP TABLE IF EXISTS dbo.products;
DROP TABLE IF EXISTS dbo.customers;

CREATE TABLE dbo.customers (
  id INT IDENTITY PRIMARY KEY,
  name VARCHAR(200) NOT NULL,
  email VARCHAR(320) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT UQ_customers_email UNIQUE (email)
);

CREATE TABLE dbo.products (
  id INT IDENTITY PRIMARY KEY,
  name VARCHAR(200) NOT NULL,
  category VARCHAR(100) NOT NULL,
  price DECIMAL(12,2) NOT NULL,
  CONSTRAINT CK_products_price CHECK (price >= 0)
);

CREATE TABLE dbo.orders (
  id BIGINT IDENTITY PRIMARY KEY,
  customer_id INT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  status VARCHAR(20) NOT NULL,
  CONSTRAINT FK_orders_customer FOREIGN KEY (customer_id) REFERENCES dbo.customers(id),
  CONSTRAINT CK_orders_status CHECK (status IN ('pending','completed','cancelled'))
);

CREATE TABLE dbo.order_items (
  order_id BIGINT NOT NULL,
  product_id INT NOT NULL,
  qty INT NOT NULL,
  unit_price DECIMAL(12,2) NOT NULL,
  discount DECIMAL(5,4) NOT NULL DEFAULT 0,
  CONSTRAINT PK_order_items PRIMARY KEY (order_id, product_id),
  CONSTRAINT FK_order_items_order FOREIGN KEY (order_id) REFERENCES dbo.orders(id),
  CONSTRAINT FK_order_items_product FOREIGN KEY (product_id) REFERENCES dbo.products(id),
  CONSTRAINT CK_order_items_qty CHECK (qty > 0),
  CONSTRAINT CK_order_items_unit_price CHECK (unit_price >= 0),
  CONSTRAINT CK_order_items_discount CHECK (discount BETWEEN 0 AND 0.99)
);

CREATE VIEW dbo.v_order_revenue AS
SELECT o.id AS order_id,
       SUM(oi.qty * oi.unit_price * (1 - oi.discount)) AS revenue
FROM dbo.orders o
JOIN dbo.order_items oi ON oi.order_id = o.id
GROUP BY o.id;

CREATE INDEX IF NOT EXISTS IX_orders_customer_created ON dbo.orders(customer_id, created_at);
CREATE INDEX IF NOT EXISTS IX_order_items_product ON dbo.order_items(product_id);
CREATE INDEX IF NOT EXISTS IX_products_category ON dbo.products(category);
