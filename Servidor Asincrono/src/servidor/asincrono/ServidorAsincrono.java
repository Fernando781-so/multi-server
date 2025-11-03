package servidor.asincrono;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ServidorAsincrono {

    public static final int PUERTO = 6000;

    public static final Map<String, UnCliente> Clientes = new ConcurrentHashMap<>();
    public static final Map<String, JuegoGato> Partidas = new ConcurrentHashMap<>();
    public static final Map<String, EstadisticasJugador> Ranking = new ConcurrentHashMap<>();
    public static final Map<String, String> SolicitudesPendientes = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        System.out.println("🚀 Servidor iniciado en el puerto " + PUERTO);

        try (ServerSocket servidor = new ServerSocket(PUERTO)) {
            while (true) {
                Socket socket = servidor.accept();
                new UnCliente(socket).start();
            }
        } catch (IOException e) {
            System.err.println("❌ Error en el servidor: " + e.getMessage());
        }
    }

    // 📦 Métodos de gestión de partidas

    public static void iniciarPartida(UnCliente j1, UnCliente j2) {
        String clave = JuegoGato.clave(j1.nombre, j2.nombre);
        if (Partidas.containsKey(clave)) {
            j1.enviar("⚠️ Ya tienes una partida activa con " + j2.nombre);
            return;
        }

        JuegoGato juego = new JuegoGato(j1, j2);
        Partidas.put(clave, juego);
        juego.iniciar();
    }

    public static void mover(String jugador, int pos) {
        for (JuegoGato partida : Partidas.values()) {
            if (partida.contieneJugador(jugador)) {
                partida.jugar(jugador, pos);
                return;
            }
        }
        UnCliente cli = Clientes.get(jugador);
        if (cli != null) cli.enviar("⚠️ No estás en una partida activa.");
    }

    public static void rendirse(String jugador) {
        for (JuegoGato partida : Partidas.values()) {
            if (partida.contieneJugador(jugador)) {
                partida.rendirse(jugador);
                return;
            }
        }
        UnCliente cli = Clientes.get(jugador);
        if (cli != null) cli.enviar("⚠️ No estás en una partida activa.");
    }

    // 🏅 Mostrar ranking general
    public static String mostrarRanking() {
        if (Ranking.isEmpty()) return "📊 No hay jugadores registrados aún.";

        StringBuilder sb = new StringBuilder("🏅 RANKING GLOBAL 🏅\n");
        Ranking.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().getPuntos(), a.getValue().getPuntos()))
                .forEach(e -> {
                    sb.append(String.format("👤 %-10s | %2d pts | %dV %dE %dD\n",
                            e.getKey(),
                            e.getValue().getPuntos(),
                            e.getValue().victorias,
                            e.getValue().empates,
                            e.getValue().derrotas));
                });
        return sb.toString();
    }

    // 🧭 Mostrar comandos
    public static String mostrarAyuda() {
        return """
        💡 COMANDOS DISPONIBLES:
        /usuarios            → Ver usuarios conectados
        /jugar <nombre>      → Retar a otro jugador
        /aceptar <nombre>    → Aceptar reto
        /mover <1-9>         → Hacer movimiento
        /rendirse            → Rendirse y perder la partida
        /ranking             → Ver clasificación global
        /ayuda               → Mostrar este menú
        """;
    }
}
