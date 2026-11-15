CREATE TABLE IF NOT EXISTS personas (
    id               SERIAL PRIMARY KEY,
    nombre           VARCHAR(150) NOT NULL,
    apellido_paterno VARCHAR(150),
    apellido_materno VARCHAR(150)
);
