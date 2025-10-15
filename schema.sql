IF DB_ID('quickshop') IS NULL CREATE DATABASE quickshop;
GO
USE quickshop;
GO
-- Customers
CREATE TABLE dbo.customers (
  id INT IDENTITY(1,1) PRIMARY KEY,
  name NVARCHAR(200) NOT NULL,
  email NVARCHAR(320) NULL UNIQUE,
  created_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME()
);
-- Products
CREATE TABLE dbo.products (
  id INT IDENTITY(1,1) PRIMARY KEY,
  name NVARCHAR(200) NOT NULL,
  category NVARCHAR(100) NOT NULL,
  price DECIMAL(12,2) NOT NULL CHECK (price >= 0)
);
-- Orders
CREATE TABLE dbo.orders (
  id BIGINT IDENTITY(1,1) PRIMARY KEY,
  customer_id INT NOT NULL FOREIGN KEY REFERENCES dbo.customers(id),
  created_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
  status NVARCHAR(20) NOT NULL CHECK (status IN ('pending','completed','cancelled'))
);
-- Order items
CREATE TABLE dbo.order_items (
  order_id BIGINT NOT NULL FOREIGN KEY REFERENCES dbo.orders(id),
  product_id INT NOT NULL FOREIGN KEY REFERENCES dbo.products(id),
  qty INT NOT NULL CHECK (qty > 0),
  unit_price DECIMAL(12,2) NOT NULL CHECK (unit_price >= 0),
  discount DECIMAL(5,4) NOT NULL DEFAULT 0 CHECK (discount BETWEEN 0 AND 0.99),
  CONSTRAINT PK_order_items PRIMARY KEY (order_id, product_id)
);
GO
CREATE VIEW dbo.v_order_revenue AS
SELECT o.id AS order_id,
       SUM(oi.qty * oi.unit_price * (1 - oi.discount)) AS revenue
FROM dbo.orders o
JOIN dbo.order_items oi ON oi.order_id = o.id
GROUP BY o.id;
GO
CREATE INDEX IX_orders_customer_created ON dbo.orders(customer_id, created_at DESC);
CREATE INDEX IX_order_items_product      ON dbo.order_items(product_id);
CREATE INDEX IX_products_category        ON dbo.products(category);
GO
