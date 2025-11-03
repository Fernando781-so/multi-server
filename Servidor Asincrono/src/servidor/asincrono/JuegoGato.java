package servidor.asincrono;

import java.io.IOException;
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
        jugador1.enPartida = true;
        jugador2.enPartida = true;
        jugador1.rival = jugador2.nombre;
        jugador2.rival = jugador1.nombre;

        turno = (new Random().nextBoolean()) ? jugador1.nombre : jugador2.nombre;
        enviarAmbos("🎮 ¡Comienza el juego del Gato!");
        enviarAmbos("🔹 " + jugador1.nombre + " = X");
        enviarAmbos("🔸 " + jugador2.nombre + " = O");
        mostrarTablero();
        enviarAmbos("👉 Turno inicial: " + turno);
    }

    public void jugar(String jugador, int pos) {
        if (!activo) return;
        if (!jugador.equals(turno)) {
            enviarA(jugador, "⏳ No es tu turno.");
            return;
        }

        if (pos < 1 || pos > 9) {
            enviarA(jugador, "❌ Posición inválida. Usa /mover <1-9>");
            return;
        }

        int fila = (pos - 1) / 3;
        int col = (pos - 1) % 3;

        if (tablero[fila][col] != '-') {
            enviarA(jugador, "⚠️ Casilla ocupada.");
            return;
        }

        char simbolo = jugador.equals(jugador1.nombre) ? 'X' : 'O';
        tablero[fila][col] = simbolo;
        mostrarTablero();

        if (verificarGanador(simbolo)) {
            enviarAmbos("🏆 ¡" + jugador + " ha ganado la partida!");
            actualizarRanking(jugador, true);
            finalizar();
            return;
        }

        if (tableroLleno()) {
            enviarAmbos("🤝 ¡Empate!");
            actualizarRanking(null, false);
            finalizar();
            return;
        }

        turno = jugador.equals(jugador1.nombre) ? jugador2.nombre : jugador1.nombre;
        enviarAmbos("👉 Turno de: " + turno);
    }

    private void finalizar() {
        activo = false;
        jugador1.enPartida = false;
        jugador2.enPartida = false;
        jugador1.rival = null;
        jugador2.rival = null;
        ServidorAsincrono.Partidas.remove(clave(jugador1.nombre, jugador2.nombre));
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
        StringBuilder sb = new StringBuilder("╔═════ TABLERO ═════╗\n");
        for (char[] fila : tablero) {
            sb.append(" ").append(fila[0]).append(" | ").append(fila[1]).append(" | ").append(fila[2]).append("\n");
        }
        sb.append("╚═══════════════════╝");
        enviarAmbos(sb.toString());
    }

    private void enviarAmbos(String msg) {
        enviarA(jugador1.nombre, msg);
        enviarA(jugador2.nombre, msg);
    }

    private void enviarA(String nombreJugador, String msg) {
        UnCliente cli = ServidorAsincrono.Clientes.get(nombreJugador);
        if (cli != null) {
            try {
                cli.salida.writeUTF(msg);
            } catch (IOException e) {
                System.out.println("Error enviando a " + nombreJugador + ": " + e.getMessage());
            }
        }
    }

    public void rendirse(String nombre) {
        if (!activo) return;
        String ganador = jugador1.nombre.equals(nombre) ? jugador2.nombre : jugador1.nombre;
        enviarAmbos("🏳️ " + nombre + " se ha rendido. ¡" + ganador + " gana!");
        actualizarRanking(ganador, true);
        finalizar();
    }
}
