-- Esquema MySQL 8 de LibManage
CREATE TABLE IF NOT EXISTS libros (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    isbn        CHAR(13)     NOT NULL UNIQUE,
    titulo      VARCHAR(200) NOT NULL,
    autor       VARCHAR(200) NOT NULL,
    categoria   VARCHAR(80)  NOT NULL,
    anio        INT          NOT NULL,
    ejemplares  INT          NOT NULL,
    disponibles INT          NOT NULL,
    CONSTRAINT chk_disponibles CHECK (disponibles BETWEEN 0 AND ejemplares),
    CONSTRAINT chk_ejemplares CHECK (ejemplares BETWEEN 1 AND 100),
    FULLTEXT INDEX ft_libros (titulo, autor)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Datos personales cifrados en la aplicación (AES-256-GCM); el email se busca por índice ciego (HMAC).
CREATE TABLE IF NOT EXISTS usuarios (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre          VARCHAR(120) NOT NULL,
    email_cifrado   VARCHAR(400) NOT NULL,
    rut_cifrado     VARCHAR(200) NOT NULL,
    indice_email    CHAR(64)     NOT NULL UNIQUE,
    hash_contrasena VARCHAR(200) NOT NULL,
    rol             VARCHAR(20)  NOT NULL,
    activo          BOOLEAN      NOT NULL DEFAULT TRUE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS prestamos (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    libro_id   BIGINT NOT NULL,
    inicio     DATE   NOT NULL,
    limite     DATE   NOT NULL,
    devolucion DATE   NULL,
    INDEX idx_prestamos_usuario (usuario_id, devolucion),
    CONSTRAINT fk_prestamo_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id),
    CONSTRAINT fk_prestamo_libro FOREIGN KEY (libro_id) REFERENCES libros (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
