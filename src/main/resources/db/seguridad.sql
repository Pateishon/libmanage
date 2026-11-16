-- Protocolo de seguridad de la base de datos (se ejecuta como administrador al desplegar).
-- 1. Usuario de la aplicación con mínimo privilegio: solo lectura/escritura de datos, sin DDL ni GRANT.
-- 2. Conexión cifrada obligatoria (REQUIRE SSL).
-- 3. La contraseña se inyecta desde un secreto (variable APP_DB_PASSWORD), nunca desde el código.
CREATE USER IF NOT EXISTS 'libmanage_app'@'%' IDENTIFIED BY '${APP_DB_PASSWORD}' REQUIRE SSL;
GRANT SELECT, INSERT, UPDATE, DELETE ON libmanage.* TO 'libmanage_app'@'%';
FLUSH PRIVILEGES;
-- Complementos operativos: respaldos diarios cifrados, binlog para recuperación a un punto en el tiempo
-- y auditoría de accesos administrativos (ver docs/PLAN_DE_PRUEBAS.md).
