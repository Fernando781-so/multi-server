package servidor.asincrono;

import java.sql.*;
import java.util.Map;

public class BaseDatos {
    private static final String URL = "jdbc:sqlite:servidor.db";

    public static void inicializar() {
        try (Connection conn = DriverManager.getConnection(URL);
             Statement st = conn.createStatement()) {

            st.execute("""
                CREATE TABLE IF NOT EXISTS jugadores (
                    nombre TEXT PRIMARY KEY,
                    victorias INTEGER,
                    derrotas INTEGER,
                    empates INTEGER,
                    puntos INTEGER
                );
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS versus (
                    jugador1 TEXT,
                    jugador2 TEXT,
                    victorias1 INTEGER,
                    victorias2 INTEGER,
                    empates INTEGER,
                    PRIMARY KEY (jugador1, jugador2)
                );
            """);

            System.out.println("📁 SQLite listo (servidor.db).");
        } catch (Exception e) {
            System.out.println("❌ Error inicializando SQLite: " + e.getMessage());
        }
    }

    public static void guardarJugador(String nombre, EstadisticasJugador e) {
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement ps = conn.prepareStatement(
                 """
                 INSERT INTO jugadores(nombre, victorias, derrotas, empates, puntos)
                 VALUES (?, ?, ?, ?, ?)
                 ON CONFLICT(nombre) DO UPDATE SET
                   victorias = excluded.victorias,
                   derrotas = excluded.derrotas,
                   empates = excluded.empates,
                   puntos = excluded.puntos;
                 """
             )) {

            ps.setString(1, nombre);
            ps.setInt(2, e.getVictorias());
            ps.setInt(3, e.getDerrotas());
            ps.setInt(4, e.getEmpates());
            ps.setInt(5, e.getPuntos());
            ps.executeUpdate();

        } catch (Exception ex) {
            System.out.println("❌ Error guardarJugador(" + nombre + "): " + ex.getMessage());
        }
    }

    public static void guardarVersus(String j1, String j2, EstadisticasJugador.Enfrentamiento enf) {
        if (enf == null) return;
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement ps = conn.prepareStatement(
                 """
                 INSERT INTO versus(jugador1, jugador2, victorias1, victorias2, empates)
                 VALUES (?, ?, ?, ?, ?)
                 ON CONFLICT(jugador1, jugador2) DO UPDATE SET
                   victorias1 = excluded.victorias1,
                   victorias2 = excluded.victorias2,
                   empates = excluded.empates;
                 """
             )) {

            ps.setString(1, j1);
            ps.setString(2, j2);
            ps.setInt(3, enf.victorias);
            ps.setInt(4, enf.derrotas);
            ps.setInt(5, enf.empates);
            ps.executeUpdate();

        } catch (Exception ex) {
            System.out.println("❌ Error guardarVersus(" + j1 + "," + j2 + "): " + ex.getMessage());
        }
    }

    public static void cargarJugadores(Map<String, EstadisticasJugador> ranking) {
        try (Connection conn = DriverManager.getConnection(URL);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM jugadores")) {

            while (rs.next()) {
                String nombre = rs.getString("nombre");
                EstadisticasJugador e = new EstadisticasJugador();
                e.setVictorias(rs.getInt("victorias"));
                e.setDerrotas(rs.getInt("derrotas"));
                e.setEmpates(rs.getInt("empates"));
                e.setPuntos(rs.getInt("puntos"));
                ranking.put(nombre, e);
            }

            System.out.println("📥 Jugadores cargados desde BD.");

        } catch (Exception e) {
            System.out.println("❌ Error cargarJugadores: " + e.getMessage());
        }
    }

    public static void cargarVersus(Map<String, EstadisticasJugador> ranking) {
        try (Connection conn = DriverManager.getConnection(URL);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM versus")) {

            while (rs.next()) {
                String j1 = rs.getString("jugador1");
                String j2 = rs.getString("jugador2");
                int v1 = rs.getInt("victorias1");
                int v2 = rs.getInt("victorias2");
                int emp = rs.getInt("empates");

                EstadisticasJugador ej = ranking.get(j1);
                if (ej != null) {
                    ej.cargarContra(j2, v1, v2, emp);
                }
                // opcionalmente también cargar la inversa (pero nuestra estructura guarda por jugador)
            }

            System.out.println("📥 Versus cargado desde BD.");

        } catch (Exception e) {
            System.out.println("❌ Error cargarVersus: " + e.getMessage());
        }
    }
}
