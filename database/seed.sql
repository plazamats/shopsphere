INSERT INTO roles (id, name) VALUES (1, 'CUSTOMER'), (2, 'ADMIN'), (3, 'SUPER_ADMIN');

INSERT INTO system_settings (setting_key, setting_value, description) VALUES
 ('vat_rate',                '0.15',   'VAT rate added to the discounted subtotal'),
 ('discount_threshold',      '1000.00','Subtotal at or above which the discount applies (ZAR)'),
 ('discount_percent',        '5',      'Discount percentage for large orders'),
 ('shipping_flat',           '60.00',  'Flat shipping fee (ZAR)'),
 ('shipping_free_threshold', '1500.00','Discounted subtotal at or above which shipping is free (ZAR)'),
 ('max_item_quantity',       '20',     'Maximum quantity of one product in a cart'),
 ('default_low_stock',       '5',      'Low stock threshold for new products');

INSERT INTO categories (name, description) VALUES
 ('Electronics',     'Phones, audio and accessories'),
 ('Clothing',        'Everyday clothing and footwear'),
 ('Home & Kitchen',  'Appliances and homeware'),
 ('Books',           'Study and leisure reading'),
 ('Sports & Outdoors','Fitness and outdoor gear');

INSERT INTO products (sku, name, description, price, category_id) VALUES
 ('ELEC-001','Wireless Earbuds Pro','Bluetooth 5.3 earbuds with noise cancelling and 24 hour battery case.',899.00,(SELECT id FROM categories WHERE name='Electronics')),
 ('ELEC-002','Smartphone 128GB','6.5 inch display, dual camera, 128GB storage.',4999.00,(SELECT id FROM categories WHERE name='Electronics')),
 ('ELEC-003','Portable Power Bank 20000mAh','Fast charging power bank with two USB ports.',549.00,(SELECT id FROM categories WHERE name='Electronics')),
 ('ELEC-004','Bluetooth Speaker','Water resistant speaker with 12 hour playtime.',699.00,(SELECT id FROM categories WHERE name='Electronics')),
 ('ELEC-005','USB-C Charging Cable 2m','Braided cable, supports 60W charging.',99.00,(SELECT id FROM categories WHERE name='Electronics')),
 ('CLOT-001','Cotton T-Shirt','100 percent cotton crew neck t-shirt.',199.00,(SELECT id FROM categories WHERE name='Clothing')),
 ('CLOT-002','Denim Jeans','Classic straight fit denim jeans.',549.00,(SELECT id FROM categories WHERE name='Clothing')),
 ('CLOT-003','Running Shoes','Lightweight running shoes with breathable mesh.',1199.00,(SELECT id FROM categories WHERE name='Clothing')),
 ('CLOT-004','Hooded Sweatshirt','Warm fleece hoodie with front pocket.',459.00,(SELECT id FROM categories WHERE name='Clothing')),
 ('HOME-001','Stainless Steel Kettle 1.7L','Fast boil electric kettle with auto shut off.',349.00,(SELECT id FROM categories WHERE name='Home & Kitchen')),
 ('HOME-002','Non-stick Pan Set (3 piece)','Aluminium non-stick frying pans in three sizes.',799.00,(SELECT id FROM categories WHERE name='Home & Kitchen')),
 ('HOME-003','Air Fryer 4L','Digital air fryer with eight presets.',1499.00,(SELECT id FROM categories WHERE name='Home & Kitchen')),
 ('HOME-004','Cotton Bath Towel Set','Set of four soft cotton towels.',399.00,(SELECT id FROM categories WHERE name='Home & Kitchen')),
 ('BOOK-001','Java: How to Program','Introductory Java programming textbook.',899.00,(SELECT id FROM categories WHERE name='Books')),
 ('BOOK-002','Database System Concepts','Relational databases, SQL and design.',949.00,(SELECT id FROM categories WHERE name='Books')),
 ('BOOK-003','Clean Code','A handbook of agile software craftsmanship.',529.00,(SELECT id FROM categories WHERE name='Books')),
 ('SPRT-001','Yoga Mat','Non-slip 6mm yoga mat.',249.00,(SELECT id FROM categories WHERE name='Sports & Outdoors')),
 ('SPRT-002','Adjustable Dumbbell Set','Pair of adjustable dumbbells up to 20kg each.',1299.00,(SELECT id FROM categories WHERE name='Sports & Outdoors')),
 ('SPRT-003','Insulated Water Bottle 750ml','Keeps drinks cold for 24 hours.',189.00,(SELECT id FROM categories WHERE name='Sports & Outdoors')),
 ('SPRT-004','Hiking Backpack 40L','Water resistant backpack with rain cover.',899.00,(SELECT id FROM categories WHERE name='Sports & Outdoors'));

/* One placeholder image reference per product. Images are references only, no files are stored. */
INSERT INTO product_images (product_id, image_url, sort_order)
SELECT id, 'images/' || lower(sku) || '.png', 0 FROM products;

/* Starting stock for every product, with a matching stock movement. */
INSERT INTO inventory (product_id, quantity, low_stock_threshold)
SELECT id, 25 + (id * 7 % 40), 5 FROM products;
UPDATE inventory SET quantity = 3 WHERE product_id = (SELECT id FROM products WHERE sku = 'ELEC-002');
INSERT INTO stock_movements (product_id, change_qty, resulting_qty, reason, note)
SELECT product_id, quantity, quantity, 'INITIAL', 'Seed data' FROM inventory;

/* Demo accounts (development only). Passwords are stored as PBKDF2 hashes. */
/* customer@shopsphere.test / Customer#123 */
/* admin@shopsphere.test    / Admin#12345 */
/* The SUPER_ADMIN is not seeded. The server creates it from SHOPSPHERE_SUPERADMIN_EMAIL and */
/* SHOPSPHERE_SUPERADMIN_PASSWORD on first start. */
INSERT INTO users (email, first_name, last_name, phone, password_hash) VALUES
 ('customer@shopsphere.test', 'Demo', 'Customer', '0821234567',
  'pbkdf2_sha256$120000$LCe4QQTGmZDnwt2qGp8aiw==$/W98s026u/j5sdFhgpWIcYYZo91pV/LXfDOaLTDVb7Q='),
 ('admin@shopsphere.test', 'Demo', 'Admin', '0827654321',
  'pbkdf2_sha256$120000$d+LGGvPhPhQ9UB0pu+Geng==$7Xox0uT1QJiVWyo+cGeGWKrkAOoIusP1uirZYk0B8Bo=');
INSERT INTO user_roles (user_id, role_id) SELECT id, 1 FROM users WHERE email = 'customer@shopsphere.test';
INSERT INTO user_roles (user_id, role_id) SELECT id, 2 FROM users WHERE email = 'admin@shopsphere.test';
INSERT INTO addresses (user_id, recipient, line1, city, province, postal_code, phone)
SELECT id, 'Demo Customer', '12 Long Street', 'Cape Town', 'Western Cape', '8001', '0821234567'
FROM users WHERE email = 'customer@shopsphere.test';
