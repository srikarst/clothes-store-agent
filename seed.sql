IF DB_ID('quickshop') IS NULL CREATE DATABASE quickshop;
GO
USE quickshop;
GO

-- Customers
DECLARE @c1 INT, @c2 INT, @c3 INT;
INSERT dbo.customers(name,email,created_at)
VALUES ('Alice','alice@example.com', DATEADD(DAY,-10, SYSUTCDATETIME()));  SET @c1 = SCOPE_IDENTITY();
INSERT dbo.customers(name,email,created_at)
VALUES ('Bob','bob@example.com',   DATEADD(DAY,-20, SYSUTCDATETIME()));    SET @c2 = SCOPE_IDENTITY();
INSERT dbo.customers(name,email,created_at)
VALUES ('Chloe','chloe@example.com', DATEADD(DAY,-30, SYSUTCDATETIME()));  SET @c3 = SCOPE_IDENTITY();
GO

-- Products
DECLARE @p1 INT, @p2 INT, @p3 INT, @p4 INT, @p5 INT;
INSERT dbo.products(name, category, price) VALUES ('Notebook', 'stationery', 4.50);  SET @p1 = SCOPE_IDENTITY();
INSERT dbo.products(name, category, price) VALUES ('Pen',      'stationery', 1.20);  SET @p2 = SCOPE_IDENTITY();
INSERT dbo.products(name, category, price) VALUES ('Mug',      'merch',     12.00);  SET @p3 = SCOPE_IDENTITY();
INSERT dbo.products(name, category, price) VALUES ('T-Shirt',  'merch',     18.00);  SET @p4 = SCOPE_IDENTITY();
INSERT dbo.products(name, category, price) VALUES ('Sticker',  'merch',      0.90);  SET @p5 = SCOPE_IDENTITY();
GO

-- Orders (some completed, some pending/cancelled)
DECLARE @o1 BIGINT, @o2 BIGINT, @o3 BIGINT, @o4 BIGINT;
INSERT dbo.orders(customer_id, created_at, status)
VALUES (@c1, DATEADD(DAY,-6, SYSUTCDATETIME()), 'completed');  SET @o1 = SCOPE_IDENTITY();
INSERT dbo.orders(customer_id, created_at, status)
VALUES (@c2, DATEADD(DAY,-3, SYSUTCDATETIME()), 'completed');  SET @o2 = SCOPE_IDENTITY();
INSERT dbo.orders(customer_id, created_at, status)
VALUES (@c2, DATEADD(DAY,-1, SYSUTCDATETIME()), 'pending');    SET @o3 = SCOPE_IDENTITY();
INSERT dbo.orders(customer_id, created_at, status)
VALUES (@c3, DATEADD(DAY,-15, SYSUTCDATETIME()), 'cancelled'); SET @o4 = SCOPE_IDENTITY();
GO

-- Order items (unit_price captured at purchase time)
INSERT dbo.order_items(order_id, product_id, qty, unit_price, discount)
VALUES
  (@o1, @p1, 3, 4.50, 0.0),
  (@o1, @p2, 2, 1.20, 0.0),
  (@o1, @p3, 1, 12.00, 0.10),       -- 10% off mug
  (@o2, @p4, 1, 18.00, 0.0),
  (@o2, @p5, 5, 0.90, 0.0),
  (@o3, @p2, 4, 1.20, 0.0);         -- pending; won’t count in 'completed' metrics
GO
