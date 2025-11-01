package servidor.asincrono;

import java.util.Random;

public class JuegoGato {
    private final UnCliente jugador1;
    private final UnCliente jugador2;
    private final char[][] tablero = new char[3][3];
    private String turno;
    private boolean activo = false;

    public JuegoGato(UnCliente j1, UnCliente j2) {
        this.jugador1 = j1;
        this.jugador2 = j2;
        inicializarTablero();
    }

    public static String clave(String a, String b) {
        return (a.compareTo(b) < 0) ? a + "|" + b : b + "|" + a;
    }

    public boolean contieneJugador(String nombre) {
        return jugador1.nombre.equals(nombre) || jugador2.nombre.equals(nombre);
    }

    public void iniciar() {
        activo = true;
        turno = (new Random().nextBoolean()) ? jugador1.nombre : jugador2.nombre;
        enviarAmbos("🎲 Se inicia el juego entre " + jugador1.nombre + " y " + jugador2.nombre);
        mostrarTablero();
        enviarAmbos("👉 Empieza: " + turno);
    }

    public void jugar(String jugador, int fila, int col) {
        if (!activo) return;
        if (!jugador.equals(turno)) {
            invokeEnviar(getJugador(jugador), "⏳ No es tu turno.");
            return;
        }
        if (fila < 0 || fila > 2 || col < 0 || col > 2) {
            invokeEnviar(getJugador(jugador), "❌ Coordenadas fuera de rango (0-2).");
            return;
        }
        if (tablero[fila][col] != '-') {
            invokeEnviar(getJugador(jugador), "⚠️ Casilla ocupada.");
            return;
        }

        char simbolo = jugador.equals(jugador1.nombre) ? 'X' : 'O';
        tablero[fila][col] = simbolo;
        mostrarTablero();

        if (verificarGanador(simbolo)) {
            enviarAmbos("🏆 ¡" + jugador + " ha ganado!");
            actualizarRanking(jugador, true);
            activo = false;
            ServidorAsincrono.Partidas.remove(clave(jugador1.nombre, jugador2.nombre));
            return;
        }

        if (tableroLleno()) {
            enviarAmbos("🤝 Empate!");
            actualizarRanking(null, false);
            activo = false;
            ServidorAsincrono.Partidas.remove(clave(jugador1.nombre, jugador2.nombre));
            return;
        }

        turno = jugador.equals(jugador1.nombre) ? jugador2.nombre : jugador1.nombre;
        enviarAmbos("👉 Turno de: " + turno);
    }

    private void actualizarRanking(String ganador, boolean victoria) {
        EstadisticasJugador est1 = ServidorAsincrono.Ranking.get(jugador1.nombre);
        EstadisticasJugador est2 = ServidorAsincrono.Ranking.get(jugador2.nombre);

        if (victoria) {
            if (ganador.equals(jugador1.nombre)) {
                est1.registrarVictoria();
                est2.registrarDerrota();
            } else {
                est2.registrarVictoria();
                est1.registrarDerrota();
            }
        } else {
            est1.registrarEmpate();
            est2.registrarEmpate();
        }
    }

    private void inicializarTablero() {
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                tablero[i][j] = '-';
    }

    private boolean verificarGanador(char simbolo) {
        for (int i = 0; i < 3; i++) {
            if (tablero[i][0] == simbolo && tablero[i][1] == simbolo && tablero[i][2] == simbolo) return true;
            if (tablero[0][i] == simbolo && tablero[1][i] == simbolo && tablero[2][i] == simbolo) return true;
        }
        return (tablero[0][0] == simbolo && tablero[1][1] == simbolo && tablero[2][2] == simbolo)
                || (tablero[0][2] == simbolo && tablero[1][1] == simbolo && tablero[2][0] == simbolo);
    }

    private boolean tableroLleno() {
        for (char[] fila : tablero)
            for (char c : fila)
                if (c == '-') return false;
        return true;
    }

    private void mostrarTablero() {
        StringBuilder sb = new StringBuilder("╔═════ GATO ═════╗\n");
        for (char[] fila : tablero) {
            sb.append(" ").append(fila[0]).append(" | ").append(fila[1]).append(" | ").append(fila[2]).append("\n");
        }
        sb.append("╚════════════════╝");
        enviarAmbos(sb.toString());
    }

    private void enviarAmbos(String msg) {
        invokeEnviar(jugador1, msg);
        invokeEnviar(jugador2, msg);
    }

    @SuppressWarnings("UseSpecificCatch")
    private void invokeEnviar(UnCliente cliente, String msg) {
        if (cliente == null) return;
        try {
            java.lang.reflect.Method m = cliente.getClass().getMethod("enviar", String.class);
            m.invoke(cliente, msg);
            return;
        } catch (Exception ignored) {
        }
        try {
            java.lang.reflect.Method m = cliente.getClass().getMethod("send", String.class);
            m.invoke(cliente, msg);
            return;
        } catch (Exception ignored) {
        }
        // Fallback: print to console if no suitable method exists
        System.out.println("Mensaje para " + (cliente.nombre != null ? cliente.nombre : "cliente") + ": " + msg);
    }

    private UnCliente getJugador(String nombre) {
        return jugador1.nombre.equals(nombre) ? jugador1 : jugador2;
    }

    public void rendirse(String nombre) {
        if (!activo) return;
        String ganador = jugador1.nombre.equals(nombre) ? jugador2.nombre : jugador1.nombre;
        enviarAmbos("🏳️ " + nombre + " se ha rendido. ¡" + ganador + " gana!");
        actualizarRanking(ganador, true);
        activo = false;
    }
}
