package servidor.asincrono;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ServidorAsincrono {

    // Estado global
    public static final Object CLIENTE_LOCK = new Object();
    public static final Map<String, String> SolicitudesPendientes = new HashMap<>();
    public static Map<String, UnCliente> Clientes = new ConcurrentHashMap<>();
    public static Map<String, EstadisticasJugador> Ranking = new ConcurrentHashMap<>();
    public static Map<String, JuegoGato> Partidas = new ConcurrentHashMap<>();
    public static Map<String, String> GruposUsuarios = new ConcurrentHashMap<>();
    public static Map<String, Set<String>> Bloqueos = new ConcurrentHashMap<>();
    public static Map<String, String> Usuarios = new ConcurrentHashMap<>();
    public static Map<String, GrupoChat> Grupos = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        inicializarGrupos();
        System.out.println("Iniciando Servidor");
        try (ServerSocket ss = new ServerSocket(8080)) {
            while (true) {
                Socket s = ss.accept();
                UnCliente cliente = new UnCliente(s);
                cliente.start();
            }
        } catch (IOException e) {
            System.out.println("Error al iniciar el servidor: " + e.getMessage());
        }
    }

    public static void inicializarGrupos() {
        Grupos.put("Todos", new GrupoChat("Todos"));
    }

    public static void enviarAGrupo(UnCliente remitente, String mensaje) throws IOException {
        String grupo = GruposUsuarios.getOrDefault(remitente.nombre, "Todos");
        GrupoChat chat = Grupos.get(grupo);
        synchronized (CLIENTE_LOCK) {
            for (String miembro : chat.getMiembros()) {
                UnCliente cli = Clientes.get(miembro);
                if (cli != null && !cli.bloqueados.contains(remitente.nombre)) {
                    cli.salida.writeUTF(mensaje);
                }
            }
        }
    }

    // 🔹 Inicia una partida privada entre j1 (retador) y j2 (aceptante)
    public static synchronized void iniciarPartida(UnCliente j1, UnCliente j2) {
        if (j1.nombre.equals(j2.nombre)) {
            j1.enviar("⚠️ No puedes jugar contra ti mismo.");
            return;
        }

        String clave = JuegoGato.clave(j1.nombre, j2.nombre);
        if (Partidas.containsKey(clave)) {
            j1.enviar("⚠️ Ya tienes una partida activa con " + j2.nombre);
            return;
        }

        JuegoGato g = new JuegoGato(j1, j2);
        Partidas.put(clave, g);
        g.iniciar();
    }

    // 🔹 Ejecuta un movimiento dentro de una partida específica
    public static void mover(String jugador, String rival, int pos) {
        String clave = JuegoGato.clave(jugador, rival);
        JuegoGato partida = Partidas.get(clave);
        if (partida == null) {
            UnCliente c = Clientes.get(jugador);
            if (c != null) c.enviar("⚠️ No tienes una partida activa con " + rival);
            return;
        }
        partida.jugar(jugador, pos);
    }

    // 🔹 Rendirse en una partida específica
    public static void rendirse(String jugador, String rival) {
        String clave = JuegoGato.clave(jugador, rival);
        JuegoGato partida = Partidas.get(clave);
        if (partida == null) {
            UnCliente c = Clientes.get(jugador);
            if (c != null) c.enviar("⚠️ No tienes una partida activa con " + rival);
            return;
        }
        partida.rendirse(jugador);
    }

    // 🔹 Finalizar partida por desconexión
    public static void finalizarPartidaPorDesconexion(String jugador) {
        List<String> clavesEliminar = new ArrayList<>();
        for (Map.Entry<String, JuegoGato> entry : Partidas.entrySet()) {
            JuegoGato partida = entry.getValue();
            if (partida.contieneJugador(jugador)) {
                String clave = entry.getKey();
                String ganador = partida.jugador1.nombre.equals(jugador)
                        ? partida.jugador2.nombre
                        : partida.jugador1.nombre;

                partida.enviarAmbos("⚠️ " + jugador + " se ha desconectado. ¡" + ganador + " gana por abandono!");
                registrarResultado(ganador, jugador, "gana1");
                clavesEliminar.add(clave);
            }
        }
        for (String c : clavesEliminar) Partidas.remove(c);
    }

    // 🔹 Registrar resultados
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

    // 🔹 Ayuda
    public static String ayuda() {
        return """
        COMANDOS:
        SESIÓN:
          /login <nombre>           - iniciar sesión
          /desconectar              - salir del servidor (no en partida)

        CHAT:
          /conectados               - usuarios conectados
          /bloquear <usuario>       - bloquear usuario
          /desbloquear <usuario>    - desbloquear usuario
          /ayuda                    - mostrar este menú

        GRUPOS:
          /grupos                   - listar grupos
          /creargrupo <nombre>      - crear grupo
          /unir <nombre>            - unirse a grupo
          /salirgrupo               - salir al grupo 'Todos'
          /borrargrupo <nombre>     - borrar grupo vacío
          /miembros                 - listar miembros del grupo actual

        GATO:
          /jugar <usuario>          - retar a otro jugador
          /aceptar <usuario>        - aceptar reto
          /mover <usuario> <1-9>    - hacer movimiento contra usuario específico
          /rendirse <usuario>       - rendirse en partida contra usuario

        RANKING:
          /ranking                  - mostrar ranking
          /versus <a> <b>           - win-rate entre dos jugadores
        """;
    }
}
