package cl.libmanage.repositorio;

import cl.libmanage.dominio.Libro;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio de libros sobre MySQL. Todas las consultas usan PreparedStatement (sin concatenar
 * datos de entrada) y la actualización de disponibles es atómica en la propia base de datos.
 */
public final class JdbcLibroRepositorio implements LibroRepositorio {
    private static final String COLUMNAS = "id, isbn, titulo, autor, categoria, anio, ejemplares, disponibles";
    private final String url;
    private final String usuario;
    private final String contrasena;

    public JdbcLibroRepositorio(String url, String usuario, String contrasena) {
        this.url = url;
        this.usuario = usuario;
        this.contrasena = contrasena;
    }

    private Connection conectar() throws SQLException {
        return DriverManager.getConnection(url, usuario, contrasena);
    }

    @Override
    public Libro guardar(Libro libro) {
        String sql = libro.id() == 0
                ? "INSERT INTO libros (isbn, titulo, autor, categoria, anio, ejemplares, disponibles) "
                  + "VALUES (?, ?, ?, ?, ?, ?, ?)"
                : "UPDATE libros SET isbn = ?, titulo = ?, autor = ?, categoria = ?, anio = ?, ejemplares = ?, "
                  + "disponibles = ? WHERE id = ?";
        try (Connection c = conectar(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, libro.isbn());
            ps.setString(2, libro.titulo());
            ps.setString(3, libro.autor());
            ps.setString(4, libro.categoria());
            ps.setInt(5, libro.anio());
            ps.setInt(6, libro.ejemplares());
            ps.setInt(7, libro.disponibles());
            if (libro.id() != 0) {
                ps.setLong(8, libro.id());
                ps.executeUpdate();
                return libro;
            }
            ps.executeUpdate();
            try (ResultSet claves = ps.getGeneratedKeys()) {
                claves.next();
                return libro.conId(claves.getLong(1));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Error al guardar el libro", e);
        }
    }

    @Override
    public Optional<Libro> porId(long id) {
        return uno("SELECT " + COLUMNAS + " FROM libros WHERE id = ?", ps -> ps.setLong(1, id));
    }

    @Override
    public Optional<Libro> porIsbn(String isbn) {
        return uno("SELECT " + COLUMNAS + " FROM libros WHERE isbn = ?", ps -> ps.setString(1, isbn));
    }

    @Override
    public List<Libro> todos() {
        List<Libro> libros = new ArrayList<>();
        try (Connection c = conectar(); PreparedStatement ps = c.prepareStatement("SELECT " + COLUMNAS + " FROM libros");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                libros.add(leer(rs));
            }
            return libros;
        } catch (SQLException e) {
            throw new IllegalStateException("Error al listar libros", e);
        }
    }

    @Override
    public boolean ajustarDisponibles(long id, int delta) {
        String sql = "UPDATE libros SET disponibles = disponibles + ? "
                     + "WHERE id = ? AND disponibles + ? >= 0 AND disponibles + ? <= ejemplares";
        try (Connection c = conectar(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, delta);
            ps.setLong(2, id);
            ps.setInt(3, delta);
            ps.setInt(4, delta);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new IllegalStateException("Error al actualizar disponibles", e);
        }
    }

    @Override
    public void eliminar(long id) {
        try (Connection c = conectar(); PreparedStatement ps = c.prepareStatement("DELETE FROM libros WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Error al eliminar el libro", e);
        }
    }

    private interface Parametros {
        void aplicar(PreparedStatement ps) throws SQLException;
    }

    private Optional<Libro> uno(String sql, Parametros parametros) {
        try (Connection c = conectar(); PreparedStatement ps = c.prepareStatement(sql)) {
            parametros.aplicar(ps);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(leer(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Error al consultar libros", e);
        }
    }

    private static Libro leer(ResultSet rs) throws SQLException {
        return new Libro(rs.getLong("id"), rs.getString("isbn"), rs.getString("titulo"), rs.getString("autor"),
                rs.getString("categoria"), rs.getInt("anio"), rs.getInt("ejemplares"), rs.getInt("disponibles"));
    }
}
