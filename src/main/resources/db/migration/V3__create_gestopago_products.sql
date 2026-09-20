CREATE TABLE IF NOT EXISTS gestopago_products (
    product_id               INTEGER PRIMARY KEY,
    service_id               INTEGER,
    category_service_type_id INTEGER,
    service_name             VARCHAR(200),
    product_name             VARCHAR(250) NOT NULL,
    front_type               INTEGER,
    has_check_digit          BOOLEAN,
    price                    NUMERIC(19, 4),
    show_help                BOOLEAN,
    reference_type           VARCHAR(20),
    legend                   TEXT,
    updated_at               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_gestopago_products_service_id
    ON gestopago_products (service_id);
