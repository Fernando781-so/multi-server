package servidor.asincrono;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ServidorAsincrono {
    public static final Object CLIENTE_LOCK = new Object();

    public static final Map<String, UnCliente> Clientes = new ConcurrentHashMap<>();
    public static final Map<String, JuegoGato> Partidas = new ConcurrentHashMap<>();
    public static final Map<String, EstadisticasJugador> Ranking = new ConcurrentHashMap<>();

    // === NUEVO: sistema de grupos persistentes ===
    public static final Map<String, GrupoChat> Grupos = new ConcurrentHashMap<>();

    static {
        Grupos.put("Todos", new GrupoChat("Todos"));
    }

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(8080)) {
            System.out.println("🟢 Servidor iniciado en puerto 8080");

            while (true) {
                Socket socket = serverSocket.accept();
                java.io.DataInputStream entrada = new java.io.DataInputStream(socket.getInputStream());
                java.io.DataOutputStream salida = new java.io.DataOutputStream(socket.getOutputStream());

                salida.writeUTF("Ingrese su nombre:");
                String nombre = entrada.readUTF();

                UnCliente nuevoCliente = new UnCliente(socket, nombre);
                Clientes.put(nombre, nuevoCliente);

                Ranking.putIfAbsent(nombre, new EstadisticasJugador(nombre));

                // Al conectar, automáticamente se unen al grupo “Todos”
                Grupos.get("Todos").unir(nombre);

                new Thread(nuevoCliente).start();
                System.out.println("👤 Cliente conectado: " + nombre);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // === Métodos de ranking (ya usados por UnCliente) ===
    public static void registrarResultado(String j1, String j2, String resultado) {
        Ranking.putIfAbsent(j1, new EstadisticasJugador(j1));
        Ranking.putIfAbsent(j2, new EstadisticasJugador(j2));

        EstadisticasJugador p1 = Ranking.get(j1);
        EstadisticasJugador p2 = Ranking.get(j2);

        switch (resultado) {
            case "gana1" -> { p1.victoria(); p2.derrota(); }
            case "gana2" -> { p2.victoria(); p1.derrota(); }
            case "empate" -> { p1.empate(); p2.empate(); }
        }
    }

    public static String obtenerRanking() {
        StringBuilder sb = new StringBuilder("📊 RANKING GENERAL:\n");
        Ranking.values().stream()
            .sorted((a, b) -> Integer.compare(b.getPuntos(), a.getPuntos()))
            .forEach(est -> sb.append(est.toString()).append("\n"));
        return sb.toString();
    }

    public static String obtenerVs(String j1, String j2) {
        EstadisticasJugador e1 = Ranking.get(j1);
        EstadisticasJugador e2 = Ranking.get(j2);
        if (e1 == null || e2 == null)
            return "❌ Uno o ambos jugadores no existen.";

        int total = e1.getVictorias() + e2.getVictorias();
        if (total == 0)
            return "⚠️ No hay partidas registradas entre ellos.";

        double porc1 = (100.0 * e1.getVictorias()) / total;
        double porc2 = (100.0 * e2.getVictorias()) / total;

        return String.format("📈 %s vs %s → %.1f%% / %.1f%%", j1, j2, porc1, porc2);
    }
}
