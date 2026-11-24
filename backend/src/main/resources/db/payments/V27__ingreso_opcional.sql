-- El ingreso es opcional sin empresa para ocupaciones sin ingreso (la API aplica la regla).
-- ck_clientes_ingreso sigue exigiendo > 0 cuando hay valor.
ALTER TABLE clientes ALTER COLUMN ingreso_mensual DROP NOT NULL;
