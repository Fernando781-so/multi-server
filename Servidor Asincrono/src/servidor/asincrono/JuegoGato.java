package servidor.asincrono;

import java.util.*;

public class JuegoGato {

    final UnCliente jugador1;
    final UnCliente jugador2;
    private final char[] tablero = new char[9];
    private String turno;

    private boolean enCurso = false;

    public JuegoGato(UnCliente j1, UnCliente j2) {
        this.jugador1 = j1;
        this.jugador2 = j2;
        Arrays.fill(tablero, ' ');
    }

    public static String clave(String j1, String j2) {
        List<String> l = Arrays.asList(j1, j2);
        Collections.sort(l);
        return l.get(0) + "_" + l.get(1);
    }

    // 🔹 Iniciar partida
    public void iniciar() {
        enCurso = true;
        turno = new Random().nextBoolean() ? jugador1.nombre : jugador2.nombre;
        enviarAmbos("🎮 Nueva partida entre " + jugador1.nombre + " (X) y " + jugador2.nombre + " (O)");
        enviarAmbos("🌀 Empieza: " + turno);
        enviarAmbos(dibujarTablero());
    }

    // 🔹 Verifica si contiene a un jugador
    public boolean contieneJugador(String nombre) {
        return jugador1.nombre.equals(nombre) || jugador2.nombre.equals(nombre);
    }

    // 🔹 Jugar movimiento
    public synchronized void jugar(String jugador, int pos) {
        if (!enCurso) {
            enviar(jugador, "⚠️ La partida ya terminó.");
            return;
        }

        if (pos < 1 || pos > 9) {
            enviar(jugador, "⚠️ Movimiento inválido (1-9).");
            return;
        }

        if (!jugador.equals(turno)) {
            enviar(jugador, "⏳ No es tu turno.");
            return;
        }

        int idx = pos - 1;
        if (tablero[idx] != ' ') {
            enviar(jugador, "🚫 Esa casilla ya está ocupada.");
            return;
        }

        char marca = jugador.equals(jugador1.nombre) ? 'X' : 'O';
        tablero[idx] = marca;

        enviarAmbos("🎯 " + jugador + " marcó posición " + pos);
        enviarAmbos(dibujarTablero());

        if (hayGanador(marca)) {
            enCurso = false;
            enviarAmbos("🏆 " + jugador + " ha ganado la partida.");
            String perdedor = jugador.equals(jugador1.nombre) ? jugador2.nombre : jugador1.nombre;
            ServidorAsincrono.registrarResultado(jugador, perdedor, jugador.equals(jugador1.nombre) ? "gana1" : "gana2");
            limpiarPartida();
            return;
        }

        if (tableroLleno()) {
            enCurso = false;
            enviarAmbos("🤝 La partida ha terminado en empate.");
            ServidorAsincrono.registrarResultado(jugador1.nombre, jugador2.nombre, "empate");
            limpiarPartida();
            return;
        }

        turno = jugador.equals(jugador1.nombre) ? jugador2.nombre : jugador1.nombre;
        enviarAmbos("➡️ Turno de: " + turno);
    }

    // 🔹 Rendirse
    public synchronized void rendirse(String jugador) {
        if (!enCurso) {
            enviar(jugador, "⚠️ No hay partida activa.");
            return;
        }
        String ganador = jugador.equals(jugador1.nombre) ? jugador2.nombre : jugador1.nombre;
        enviarAmbos("🏳️ " + jugador + " se ha rendido. Gana " + ganador + ".");
        ServidorAsincrono.registrarResultado(ganador, jugador, ganador.equals(jugador1.nombre) ? "gana1" : "gana2");
        limpiarPartida();
    }

    // 🔹 Enviar mensajes
    public void enviar(String jugador, String msg) {
        UnCliente c = ServidorAsincrono.Clientes.get(jugador);
        if (c != null) c.enviar("[GATO] " + msg);
    }

    public void enviarAmbos(String msg) {
        if (jugador1 != null) jugador1.enviar("[GATO] " + msg);
        if (jugador2 != null) jugador2.enviar("[GATO] " + msg);
    }

    // 🔹 Dibujar tablero
// 🔹 Dibujar tablero
public String dibujarTablero() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 9; i++) {
        sb.append(tablero[i] == ' ' ? String.valueOf(i + 1) : String.valueOf(tablero[i]));
        if (i % 3 == 2 && i != 8) sb.append("\n───┼───┼───\n");
        else if (i % 3 != 2) sb.append(" │ ");
    }
    return sb.toString();
}


    // 🔹 Verificar ganador
    private boolean hayGanador(char m) {
        int[][] lineas = {
            {0,1,2},{3,4,5},{6,7,8}, // filas
            {0,3,6},{1,4,7},{2,5,8}, // columnas
            {0,4,8},{2,4,6}          // diagonales
        };
        for (int[] l : lineas)
            if (tablero[l[0]] == m && tablero[l[1]] == m && tablero[l[2]] == m)
                return true;
        return false;
    }

    // 🔹 Verificar empate
    private boolean tableroLleno() {
        for (char c : tablero)
            if (c == ' ')
                return false;
        return true;
    }

    // 🔹 Limpieza al finalizar partida
    private void limpiarPartida() {
        jugador1.rivalesActivos.remove(jugador2.nombre);
        jugador2.rivalesActivos.remove(jugador1.nombre);
        ServidorAsincrono.Partidas.remove(clave(jugador1.nombre, jugador2.nombre));
    }
}
