DROP TABLE candle_data;

DROP TABLE order_details;

DROP TABLE order_details_seq;

ALTER TABLE currencies
    DROP COLUMN currencyType;

ALTER TABLE currencies
    DROP COLUMN fractionalDigits;

ALTER TABLE currencies
    DROP COLUMN fullDisplayName;

ALTER TABLE currencies
    DROP COLUMN shortDisplayName;

ALTER TABLE orders
    DROP COLUMN order_id;