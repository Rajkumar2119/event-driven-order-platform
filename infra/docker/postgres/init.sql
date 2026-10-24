-- One PostgreSQL server, one database per service (each service owns its data).
CREATE USER orders WITH PASSWORD 'orders';
CREATE DATABASE orders_db OWNER orders;
CREATE USER payments WITH PASSWORD 'payments';
CREATE DATABASE payments_db OWNER payments;
