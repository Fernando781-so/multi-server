package servidor.asincrono;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ServidorAsincrono {

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
        // inicializar BD y cargar datos
        BaseDatos.inicializar();
        BaseDatos.cargarJugadores(Ranking);
        BaseDatos.cargarVersus(Ranking);

        inicializarGrupos();

        // hook para guardar al cerrar
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("💾 Guardando datos antes de cerrar...");
            for (var e : Ranking.entrySet()) {
                BaseDatos.guardarJugador(e.getKey(), e.getValue());
                // guardar todos sus versus
                for (var ve : e.getValue().getEnfrentamientos().entrySet()) {
                    BaseDatos.guardarVersus(e.getKey(), ve.getKey(), ve.getValue());
                }
            }
            System.out.println("💾 Guardado completado.");
        }));

        System.out.println("Servidor iniciado en puerto 8080");
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
        Grupos.putIfAbsent("Todos", new GrupoChat("Todos"));
    }

    public static void enviarAGrupo(UnCliente remitente, String mensaje) throws IOException {
        String grupo = GruposUsuarios.getOrDefault(remitente.nombre, "Todos");
        GrupoChat chat = Grupos.get(grupo);
        if (chat == null) return;
        synchronized (CLIENTE_LOCK) {
            for (String miembro : chat.getMiembros()) {
                UnCliente cli = Clientes.get(miembro);
                if (cli != null && !cli.bloqueados.contains(remitente.nombre)) {
                    cli.salida.writeUTF(mensaje);
                }
            }
        }
    }

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
        // marcar rivales activos en UnCliente
        j1.rivalesActivos.add(j2.nombre);
        j2.rivalesActivos.add(j1.nombre);
        g.iniciar();
    }

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
                // registrar resultado: ganador vs jugador
                registrarResultado(ganador, jugador, ganador.equals(partida.jugador1.nombre) ? "gana1" : "gana2");
                clavesEliminar.add(clave);
            }
        }
        for (String c : clavesEliminar) Partidas.remove(c);
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

        // Guardar inmediatamente en BD
        BaseDatos.guardarJugador(j1, e1);
        BaseDatos.guardarJugador(j2, e2);

        // guardar enfrentamientos (versus) para ambos sentidos
        var enf1 = e1.getEnfrentamiento(j2);
        var enf2 = e2.getEnfrentamiento(j1);
        if (enf1 != null) BaseDatos.guardarVersus(j1, j2, enf1);
        if (enf2 != null) BaseDatos.guardarVersus(j2, j1, enf2);

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
          /aceptar <retador>        - aceptar reto
          /mover <oponente> <1-9>   - mover contra oponente específico
          /rendirse <oponente>      - rendirse en partida contra oponente

        RANKING:
          /ranking                  - mostrar ranking
          /versus <jugador> <oponente> - win-rate entre dos jugadores
        """;
    }
}
