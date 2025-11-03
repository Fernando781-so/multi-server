package servidor.asincrono;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ServidorAsincrono {
    public static final int PUERTO = 6000;

    // Estado global
    public static final Map<String, UnCliente> Clientes = new ConcurrentHashMap<>();
    public static final Map<String, JuegoGato> Partidas = new ConcurrentHashMap<>();
    public static final Map<String, EstadisticasJugador> Ranking = new ConcurrentHashMap<>();
    public static final Map<String, String> SolicitudesPendientes = new ConcurrentHashMap<>();
    public static final Map<String, GrupoChat> Grupos = new ConcurrentHashMap<>();

    static {
        // Grupo "Todos" siempre existe
        Grupos.put("Todos", new GrupoChat("Todos"));
    }

    public static void main(String[] args) {
        System.out.println("Servidor iniciado en puerto " + PUERTO);
        try (ServerSocket ss = new ServerSocket(PUERTO)) {
            while (true) {
                Socket s = ss.accept();
                // Crear hilo para cada conexión
                UnCliente cliente = new UnCliente(s);
                cliente.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ---------------- Partidas ----------------

    // Inicia una partida privada entre j1 (retador) y j2 (aceptante)
    public static synchronized void iniciarPartida(UnCliente j1, UnCliente j2) {
        String clave = JuegoGato.clave(j1.nombre, j2.nombre);
        if (Partidas.containsKey(clave)) {
            j1.enviar("⚠️ Ya existe una partida entre vosotros.");
            return;
        }
        JuegoGato g = new JuegoGato(j1, j2);
        Partidas.put(clave, g);
        g.iniciar();
    }

    // Ejecuta un movimiento
    public static void mover(String jugador, int pos) {
        for (JuegoGato g : Partidas.values()) {
            if (g.contieneJugador(jugador)) {
                g.jugar(jugador, pos);
                return;
            }
        }
        UnCliente c = Clientes.get(jugador);
        if (c != null) c.enviar("⚠️ No estás en una partida activa.");
    }

    // Rendirse
    public static void rendirse(String jugador) {
        for (JuegoGato g : Partidas.values()) {
            if (g.contieneJugador(jugador)) {
                g.rendirse(jugador);
                return;
            }
        }
        UnCliente c = Clientes.get(jugador);
        if (c != null) c.enviar("⚠️ No estás en una partida activa.");
    }

    // Finalizar partida por desconexión: ganador es 'ganador', perdedor 'perdedor'
public static void finalizarPartidaPorDesconexion(String jugador) {
    String clave = null;
    JuegoGato partida = null;

    synchronized (Partidas) {
        for (Map.Entry<String, JuegoGato> entry : Partidas.entrySet()) {
            if (entry.getValue().contieneJugador(jugador)) {
                clave = entry.getKey();
                partida = entry.getValue();
                break;
            }
        }
    }

    if (clave == null || partida == null) return;

    String ganador = partida.jugador1.nombre.equals(jugador)
            ? partida.jugador2.nombre
            : partida.jugador1.nombre;

    partida.enviarAmbos("⚠️ " + jugador + " se ha desconectado. ¡" + ganador + " gana por abandono!");
    registrarResultado(ganador, jugador, "gana1");

    Partidas.remove(clave);
}


 public static void registrarResultado(String j1, String j2, String resultado) {
    EstadisticasJugador e1 = Ranking.computeIfAbsent(j1, k -> new EstadisticasJugador());
    EstadisticasJugador e2 = Ranking.computeIfAbsent(j2, k -> new EstadisticasJugador());

    switch (resultado.toLowerCase()) {
        case "gana1" -> {
            e1.registrarVictoria();
            e2.registrarDerrota();
            e1.registrarContra(j2, "victoria");
            e2.registrarContra(j1, "derrota");
        }
        case "gana2" -> {
            e2.registrarVictoria();
            e1.registrarDerrota();
            e2.registrarContra(j1, "victoria");
            e1.registrarContra(j2, "derrota");
        }
        case "empate" -> {
            e1.registrarEmpate();
            e2.registrarEmpate();
            e1.registrarContra(j2, "empate");
            e2.registrarContra(j1, "empate");
        }
    }

    System.out.printf("📊 Resultado registrado: %s vs %s → %s%n", j1, j2, resultado);
}

    public static String obtenerRanking() {
        StringBuilder sb = new StringBuilder("📊 RANKING GENERAL:\n");
        Ranking.entrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getValue().getPuntos(), a.getValue().getPuntos()))
            .forEach(e -> sb.append("• ").append(e.getKey()).append(": ").append(e.getValue()).append("\n"));
        return sb.toString();
    }

    public static String obtenerVs(String j1, String j2) {
        EstadisticasJugador e1 = Ranking.get(j1);
        EstadisticasJugador e2 = Ranking.get(j2);
        if (e1 == null || e2 == null) return "❌ Uno o ambos jugadores no existen.";
        return e1.getResumenContra(j1, j2);
    }

    // ---------------- Ayuda ----------------

    public static String ayuda() {
        return """
        COMANDOS (agrupados):
        SESIÓN:
          /login <nombre>           - iniciar sesión (invitados pueden usar hasta 3 mensajes antes)
          /salir                    - desconectarse (no permitido durante partida; rendirse primero)

        GRUPOS:
          /grupos                   - listar grupos
          /creargrupo <nombre>      - crear grupo (no 'Todos')
          /unir <nombre>            - unirse a grupo
          /salirgrupo               - salir del grupo actual (vuelve a Todos)
          /borrargrupo <nombre>     - borrar grupo vacío (no 'Todos')
          /miembros                 - listar miembros del grupo actual

        CHAT:
          (mensaje normal)          - envía al grupo actual (invitados solo en Todos)
          /bloquear <usuario>       - bloquear (no recibir mensajes de ese usuario)
          /desbloquear <usuario>    - desbloquear

        GATO:
          /jugar <usuario>          - retar a usuario
          /aceptar <usuario>        - aceptar reto
          /mover <1-9>              - hacer movimiento en la partida
          /rendirse                 - rendirse (pierde la partida)

        RANKING:
          /ranking                  - ranking general (puntos)
          /versus <a> <b>           - win-rate entre dos jugadores

        /ayuda                      - mostrar este menú
        """;
    }
}
