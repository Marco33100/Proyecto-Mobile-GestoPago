-- La empresa es opcional: el registro propio admite personas sin empleador.
ALTER TABLE clientes ALTER COLUMN empresa DROP NOT NULL;
