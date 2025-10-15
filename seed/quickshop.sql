-- Drop if re-running
IF OBJECT_ID('dbo.order_items','U') IS NOT NULL DROP TABLE dbo.order_items;
IF OBJECT_ID('dbo.orders','U')      IS NOT NULL DROP TABLE dbo.orders;
IF OBJECT_ID('dbo.products','U')    IS NOT NULL DROP TABLE dbo.products;
IF OBJECT_ID('dbo.customers','U')   IS NOT NULL DROP TABLE dbo.customers;

-- Tables
CREATE TABLE dbo.customers (
  id INT IDENTITY(1,1) PRIMARY KEY,
  name NVARCHAR(100) NOT NULL,
  email NVARCHAR(255) NOT NULL,
  created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME()
);

CREATE TABLE dbo.products (
  id INT IDENTITY(1,1) PRIMARY KEY,
  name NVARCHAR(120) NOT NULL,
  category NVARCHAR(60) NOT NULL,
  price DECIMAL(10,2) NOT NULL
);

CREATE TABLE dbo.orders (
  id INT IDENTITY(1,1) PRIMARY KEY,
  customer_id INT NOT NULL,
  created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
  status NVARCHAR(20) NOT NULL,
  CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES dbo.customers(id)
);

CREATE TABLE dbo.order_items (
  order_id INT NOT NULL,
  product_id INT NOT NULL,
  qty INT NOT NULL,
  unit_price DECIMAL(10,2) NOT NULL,
  discount DECIMAL(5,4) NOT NULL DEFAULT 0,
  CONSTRAINT pk_order_items PRIMARY KEY (order_id, product_id),
  CONSTRAINT fk_oi_order FOREIGN KEY (order_id) REFERENCES dbo.orders(id),
  CONSTRAINT fk_oi_product FOREIGN KEY (product_id) REFERENCES dbo.products(id)
);

-- Seed (tiny)
INSERT INTO dbo.customers (name, email, created_at) VALUES
('Alice','alice@example.com', DATEADD(DAY, -40, SYSUTCDATETIME())),
('Bob','bob@example.com',   DATEADD(DAY, -10, SYSUTCDATETIME())),
('Cara','cara@example.com', DATEADD(DAY, -5,  SYSUTCDATETIME()));

INSERT INTO dbo.products (name, category, price) VALUES
('T-Shirt','Apparel',20.00),
('Mug','Home',12.50),
('Sticker Pack','Merch',5.00),
('Hoodie','Apparel',45.00),
('Notebook','Office',8.00);

-- Some last month & this week
INSERT INTO dbo.orders (customer_id, created_at, status) VALUES
(1, DATEADD(DAY, -35, SYSUTCDATETIME()), 'completed'),  -- last month
(2, DATEADD(DAY, -34, SYSUTCDATETIME()), 'completed'),
(2, DATEADD(DAY,  -3, SYSUTCDATETIME()), 'completed'),  -- this week
(3, DATEADD(DAY,  -2, SYSUTCDATETIME()), 'completed');

INSERT INTO dbo.order_items (order_id, product_id, qty, unit_price, discount) VALUES
(1, 1, 2, 20.00, 0.00),
(1, 2, 1, 12.50, 0.10),
(2, 4, 1, 45.00, 0.00),
(2, 3, 3, 5.00,  0.00),
(3, 5, 4, 8.00,  0.05),
(4, 1, 1, 20.00, 0.00),
(4, 2, 2, 12.50, 0.00);
