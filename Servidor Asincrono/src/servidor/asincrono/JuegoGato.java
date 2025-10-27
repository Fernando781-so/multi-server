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
        return jugador1.getNombre().equals(nombre) || jugador2.getNombre().equals(nombre);
    }

    public void iniciar() {
        activo = true;
        turno = (new Random().nextBoolean()) ? jugador1.getNombre() : jugador2.getNombre();
        enviarAmbos("🎲 Se inicia el juego entre " + jugador1.getNombre() + " y " + jugador2.getNombre());
        mostrarTablero();
        enviarAmbos("👉 Empieza: " + turno);
    }

    public synchronized void jugar(String jugador, int fila, int col) {
        if (!activo) return;
        if (!jugador.equals(turno)) {
            getJugador(jugador).enviar("⏳ No es tu turno.");
            return;
        }
        if (fila < 0 || fila > 2 || col < 0 || col > 2) {
            getJugador(jugador).enviar("❌ Coordenadas fuera de rango (0-2).");
            return;
        }
        if (tablero[fila][col] != '-') {
            getJugador(jugador).enviar("⚠️ Casilla ocupada.");
            return;
        }

        char simbolo = jugador.equals(jugador1.getNombre()) ? 'X' : 'O';
        tablero[fila][col] = simbolo;
        mostrarTablero();

        if (verificarGanador(simbolo)) {
            enviarAmbos("🏆 ¡" + jugador + " ha ganado!");
            activo = false;
            // Registrar resultado si tu servidor tiene la función registrarResultado (opcional)
            try {
                ServidorAsincrono.registrarResultadoIfExists(jugador1.getNombre(), jugador2.getNombre(), jugador);
            } catch (Throwable ignored) {}
            ServidorAsincrono.Partidas.remove(clave(jugador1.getNombre(), jugador2.getNombre()));
            return;
        }

        if (tableroLleno()) {
            enviarAmbos("🤝 Empate!");
            activo = false;
            try {
                ServidorAsincrono.registrarResultadoIfExistsDraw(jugador1.getNombre(), jugador2.getNombre());
            } catch (Throwable ignored) {}
            ServidorAsincrono.Partidas.remove(clave(jugador1.getNombre(), jugador2.getNombre()));
            return;
        }

        turno = jugador.equals(jugador1.getNombre()) ? jugador2.getNombre() : jugador1.getNombre();
        enviarAmbos("👉 Turno de: " + turno);
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
        // usa el método público enviar(...) que debe existir en UnCliente
        jugador1.enviar(msg);
        jugador2.enviar(msg);
    }

    private UnCliente getJugador(String nombre) {
        return jugador1.getNombre().equals(nombre) ? jugador1 : jugador2;
    }

    public void rendirse(String nombre) {
        if (!activo) return;
        String ganador = jugador1.getNombre().equals(nombre) ? jugador2.getNombre() : jugador1.getNombre();
        enviarAmbos("🏳️ " + nombre + " se ha rendido. ¡" + ganador + " gana!");
        activo = false;
        // Registrar resultado si existe la función en ServidorAsincrono (opcional)
        try {
            ServidorAsincrono.registrarResultadoIfExists( (jugador1.getNombre()), (jugador2.getNombre()), ganador);
        } catch (Throwable ignored) {}
        ServidorAsincrono.Partidas.remove(clave(jugador1.getNombre(), jugador2.getNombre()));
    }
}
