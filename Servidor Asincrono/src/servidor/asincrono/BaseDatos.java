package servidor.asincrono;

import java.sql.*;

/**
 * Helper para SQLite: inicializa tablas, guarda mensajes,
 * actualiza estadísticas y carga datos al iniciar.
 */
public class BaseDatos {

    private static final String URL = "jdbc:sqlite:gato.db";

    static {
        inicializar();
    }

    public static Connection conectar() throws SQLException {
        return DriverManager.getConnection(URL);
    }

    private static void inicializar() {
        try (Connection conn = conectar(); Statement st = conn.createStatement()) {

            st.execute("""
                CREATE TABLE IF NOT EXISTS jugadores (
                    nombre TEXT PRIMARY KEY,
                    victorias INTEGER NOT NULL DEFAULT 0,
                    derrotas INTEGER NOT NULL DEFAULT 0,
                    empates INTEGER NOT NULL DEFAULT 0
                );
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS versus (
                    jugador_a TEXT NOT NULL,
                    jugador_b TEXT NOT NULL,
                    victorias_a INTEGER NOT NULL DEFAULT 0,
                    victorias_b INTEGER NOT NULL DEFAULT 0,
                    empates INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (jugador_a, jugador_b)
                );
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS mensajes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    jugador TEXT NOT NULL,
                    mensaje TEXT NOT NULL,
                    fecha DATETIME DEFAULT CURRENT_TIMESTAMP
                );
            """);

            System.out.println("[BD] Tablas inicializadas correctamente.");
        } catch (SQLException e) {
            System.out.println("[BD] Error inicializando BD: " + e.getMessage());
        }
    }

    // Guarda un mensaje en la tabla mensajes
    public static void guardarMensaje(String jugador, String mensaje) {
        String sql = "INSERT INTO mensajes(jugador, mensaje) VALUES (?, ?)";
        try (Connection conn = conectar();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, jugador);
            ps.setString(2, mensaje);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("[BD] Error guardando mensaje: " + e.getMessage());
        }
    }

    // Cargar historial (puede enviarse al start o consultarse)
    public static void cargarHistorialEnConsole() {
        String sql = "SELECT id, jugador, mensaje, fecha FROM mensajes ORDER BY id";
        try (Connection conn = conectar();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            System.out.println("===== HISTORIAL (BD) =====");
            while (rs.next()) {
                System.out.printf("[%s] %s: %s%n", rs.getString("fecha"), rs.getString("jugador"), rs.getString("mensaje"));
            }
        } catch (SQLException e) {
            System.out.println("[BD] Error leyendo historial: " + e.getMessage());
        }
    }

    // Asegura que un jugador exista en la tabla jugadores
    public static void asegurarJugador(String jugador) {
        String sql = "INSERT OR IGNORE INTO jugadores(nombre) VALUES (?)";
        try (Connection conn = conectar();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, jugador);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("[BD] Error asegurando jugador: " + e.getMessage());
        }
    }

    // Registra resultado persistente y actualiza la tabla versus
    // resultado: "gana1", "gana2", "empate" where j1 is ganador in gana1
    public static synchronized void registrarResultado(String j1, String j2, String resultado) {
        // normalizar orden para la tabla versus: menor, mayor lexicográficamente
        String a = j1.compareTo(j2) <= 0 ? j1 : j2;
        String b = j1.compareTo(j2) <= 0 ? j2 : j1;
        boolean j1esA = a.equals(j1);

        asegurarJugador(j1);
        asegurarJugador(j2);

        try (Connection conn = conectar()) {
            conn.setAutoCommit(false);

            // actualizar jugadores generales
            switch (resultado.toLowerCase()) {
                case "gana1" -> {
                    try (PreparedStatement ps1 = conn.prepareStatement("UPDATE jugadores SET victorias = victorias + 1 WHERE nombre = ?")) {
                        ps1.setString(1, j1);
                        ps1.executeUpdate();
                    }
                    try (PreparedStatement ps2 = conn.prepareStatement("UPDATE jugadores SET derrotas = derrotas + 1 WHERE nombre = ?")) {
                        ps2.setString(1, j2);
                        ps2.executeUpdate();
                    }
                }
                case "gana2" -> {
                    try (PreparedStatement ps1 = conn.prepareStatement("UPDATE jugadores SET victorias = victorias + 1 WHERE nombre = ?")) {
                        ps1.setString(1, j2);
                        ps1.executeUpdate();
                    }
                    try (PreparedStatement ps2 = conn.prepareStatement("UPDATE jugadores SET derrotas = derrotas + 1 WHERE nombre = ?")) {
                        ps2.setString(1, j1);
                        ps2.executeUpdate();
                    }
                }
                case "empate" -> {
                    try (PreparedStatement ps1 = conn.prepareStatement("UPDATE jugadores SET empates = empates + 1 WHERE nombre = ?")) {
                        ps1.setString(1, j1);
                        ps1.executeUpdate();
                    }
                    try (PreparedStatement ps2 = conn.prepareStatement("UPDATE jugadores SET empates = empates + 1 WHERE nombre = ?")) {
                        ps2.setString(1, j2);
                        ps2.executeUpdate();
                    }
                }
            }

            // actualizar versus tabla (fila única a,b)
            String sel = "SELECT victorias_a, victorias_b, empates FROM versus WHERE jugador_a = ? AND jugador_b = ?";
            try (PreparedStatement psSel = conn.prepareStatement(sel)) {
                psSel.setString(1, a);
                psSel.setString(2, b);
                try (ResultSet rs = psSel.executeQuery()) {
                    if (!rs.next()) {
                        // insertar
                        try (PreparedStatement psIns = conn.prepareStatement(
                                "INSERT INTO versus(jugador_a, jugador_b, victorias_a, victorias_b, empates) VALUES(?,?,?,?,?)")) {
                            int va = 0, vb = 0, e = 0;
                            if (resultado.equalsIgnoreCase("empate")) e = 1;
                            else if (resultado.equalsIgnoreCase("gana1")) {
                                if (j1esA) va = 1; else vb = 1;
                            } else if (resultado.equalsIgnoreCase("gana2")) {
                                if (j1esA) vb = 1; else va = 1;
                            }
                            psIns.setString(1, a);
                            psIns.setString(2, b);
                            psIns.setInt(3, va);
                            psIns.setInt(4, vb);
                            psIns.setInt(5, e);
                            psIns.executeUpdate();
                        }
                    } else {
                        int va = rs.getInt("victorias_a");
                        int vb = rs.getInt("victorias_b");
                        int e = rs.getInt("empates");
                        if (resultado.equalsIgnoreCase("empate")) e++;
                        else if (resultado.equalsIgnoreCase("gana1")) {
                            if (j1esA) va++; else vb++;
                        } else if (resultado.equalsIgnoreCase("gana2")) {
                            if (j1esA) vb++; else va++;
                        }
                        try (PreparedStatement psUpd = conn.prepareStatement(
                                "UPDATE versus SET victorias_a = ?, victorias_b = ?, empates = ? WHERE jugador_a = ? AND jugador_b = ?")) {
                            psUpd.setInt(1, va);
                            psUpd.setInt(2, vb);
                            psUpd.setInt(3, e);
                            psUpd.setString(4, a);
                            psUpd.setString(5, b);
                            psUpd.executeUpdate();
                        }
                    }
                }
            }

            conn.commit();
        } catch (SQLException ex) {
            System.out.println("[BD] Error registrarResultado: " + ex.getMessage());
        }
    }

    // Carga jugadores y vs en memoria (para inicializar Ranking y enfrentamientos)
    public static void cargarDatosEnMemoria() {
        // cargar jugadores
        try (Connection conn = conectar();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT nombre, victorias, derrotas, empates FROM jugadores")) {
            while (rs.next()) {
                String nombre = rs.getString("nombre");
                EstadisticasJugador ej = new EstadisticasJugador();
                // llenar campos privados vía métodos públicos: usar registrarVictory/Derrota/Empate repetidamente
                int v = rs.getInt("victorias");
                int d = rs.getInt("derrotas");
                int e = rs.getInt("empates");
                for (int i=0;i<v;i++) ej.registrarVictoria();
                for (int i=0;i<d;i++) ej.registrarDerrota();
                for (int i=0;i<e;i++) ej.registrarEmpate();
                ServidorAsincrono.Ranking.put(nombre, ej);
            }
        } catch (SQLException ex) {
            System.out.println("[BD] Error cargando jugadores: " + ex.getMessage());
        }

        // cargar versus
        try (Connection conn = conectar();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT jugador_a, jugador_b, victorias_a, victorias_b, empates FROM versus")) {
            while (rs.next()) {
                String a = rs.getString("jugador_a");
                String b = rs.getString("jugador_b");
                int va = rs.getInt("victorias_a");
                int vb = rs.getInt("victorias_b");
                int e = rs.getInt("empates");

                // actualizar los objetos EstadisticasJugador en memoria (si existen)
                EstadisticasJugador ea = ServidorAsincrono.Ranking.computeIfAbsent(a, k -> new EstadisticasJugador());
                EstadisticasJugador eb = ServidorAsincrono.Ranking.computeIfAbsent(b, k -> new EstadisticasJugador());

                // registrar enfrentamientos en ambos
                for (int i=0;i<va;i++) ea.registrarContra(b, "victoria");
                for (int i=0;i<vb;i++) ea.registrarContra(b, "derrota"); // not ideal pero la API guarda desde la perspectiva de ea
                for (int i=0;i<e;i++) ea.registrarContra(b, "empate");

                for (int i=0;i<vb;i++) eb.registrarContra(a, "victoria");
                for (int i=0;i<va;i++) eb.registrarContra(a, "derrota");
                for (int i=0;i<e;i++) eb.registrarContra(a, "empate");
            }
        } catch (SQLException ex) {
            System.out.println("[BD] Error cargando versus: " + ex.getMessage());
        }
    }

    // Obtener resumen versus desde BD (más exacto que memoria si hubo reinicio)
    public static String obtenerVsDesdeBD(String j1, String j2) {
        if (j1 == null || j2 == null) return "❌ Nombres inválidos.";
        if (!j1.equals(j2)) {
            String a = j1.compareTo(j2) <= 0 ? j1 : j2;
            String b = j1.compareTo(j2) <= 0 ? j2 : j1;
            String sql = "SELECT victorias_a, victorias_b, empates FROM versus WHERE jugador_a = ? AND jugador_b = ?";
            try (Connection conn = conectar();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, a);
                ps.setString(2, b);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return "⚠️ No hay partidas registradas entre " + j1 + " y " + j2;
                    int va = rs.getInt("victorias_a");
                    int vb = rs.getInt("victorias_b");
                    int e = rs.getInt("empates");
                    // interpretar va/vb según orden
                    int j1wins = a.equals(j1) ? va : vb;
                    int j2wins = a.equals(j1) ? vb : va;
                    int total = j1wins + j2wins;
                    double winRate = total == 0 ? 0.0 : (100.0 * j1wins / total);
                    return String.format("📊 %s vs %s → %d victorias / %d derrotas / %d empates (%.1f%% win rate para %s)",
                            j1, j2, j1wins, j2wins, e, winRate, j1);
                }
            } catch (SQLException ex) {
                return "[BD] Error obtenerVs: " + ex.getMessage();
            }
        } else {
            return "⚠️ Debes especificar dos jugadores distintos.";
        }
    }
}
