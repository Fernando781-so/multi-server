package servidor.asincrono;

import java.util.Random;

public class JuegoGato {
    final UnCliente jugador1;
    final UnCliente jugador2;
    private final char[][] tablero = new char[3][3];
    private String turno;
    private boolean activo = false;

    public JuegoGato(UnCliente j1, UnCliente j2) {
        this.jugador1 = j1;
        this.jugador2 = j2;
        inicializar();
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
        turno = new Random().nextBoolean() ? jugador1.nombre : jugador2.nombre;
        enviarAmbos("🎲 Comienza partida: " + jugador1.nombre + " vs " + jugador2.nombre);
        mostrarTablero();
        enviarAmbos("👉 Empieza: " + turno);
    }

    public void jugar(String jugador, int pos) {
        if (!activo) return;
        if (!jugador.equals(turno)) { enviarA(jugador, "⏳ No es tu turno."); return; }
        if (pos < 1 || pos > 9) { enviarA(jugador, "❌ Posición inválida (1-9)."); return; }
        int f = (pos-1)/3, c = (pos-1)%3;
        if (tablero[f][c] != '-') { enviarA(jugador, "⚠️ Casilla ocupada."); return; }
        tablero[f][c] = jugador.equals(jugador1.nombre) ? 'X' : 'O';
        mostrarTablero();

        char s = tablero[f][c];
        if (verificarGanador(s)) {
            enviarAmbos("🏆 " + jugador + " ha ganado!");
            // determinar ganador relativo a jugador1/jugador2
            if (jugador.equals(jugador1.nombre)) ServidorAsincrono.registrarResultado(jugador1.nombre, jugador2.nombre, "gana1");
            else ServidorAsincrono.registrarResultado(jugador1.nombre, jugador2.nombre, "gana2");
            finalizar();
            return;
        }

        if (tableroLleno()) {
            enviarAmbos("🤝 Empate!");
            ServidorAsincrono.registrarResultado(jugador1.nombre, jugador2.nombre, "empate");
            finalizar();
            return;
        }

        turno = jugador.equals(jugador1.nombre) ? jugador2.nombre : jugador1.nombre;
        enviarAmbos("👉 Turno de: " + turno);
    }

    public void rendirse(String nombre) {
        if (!activo) return;
        String ganador = jugador1.nombre.equals(nombre) ? jugador2.nombre : jugador1.nombre;
        enviarAmbos("🏳️ " + nombre + " se rindió. " + ganador + " gana.");
        // registrar: ganador debe ser "gana1" if jugador1 is winner relative first param
        if (ganador.equals(jugador1.nombre)) ServidorAsincrono.registrarResultado(jugador1.nombre, jugador2.nombre, "gana1");
        else ServidorAsincrono.registrarResultado(jugador1.nombre, jugador2.nombre, "gana2");
        finalizar();
    }

    private void finalizar() {
        activo = false;
        jugador1.enPartida = false;
        jugador2.enPartida = false;
        jugador1.rival = null;
        jugador2.rival = null;
        ServidorAsincrono.Partidas.remove(clave(jugador1.nombre, jugador2.nombre));
    }

    private void inicializar() {
        for (int i=0;i<3;i++) for (int j=0;j<3;j++) tablero[i][j]='-';
    }

    private boolean verificarGanador(char s) {
        for (int i=0;i<3;i++) {
            if (tablero[i][0]==s && tablero[i][1]==s && tablero[i][2]==s) return true;
            if (tablero[0][i]==s && tablero[1][i]==s && tablero[2][i]==s) return true;
        }
        return (tablero[0][0]==s && tablero[1][1]==s && tablero[2][2]==s)
            || (tablero[0][2]==s && tablero[1][1]==s && tablero[2][0]==s);
    }

    private boolean tableroLleno() {
        for (char[] fila : tablero) for (char ch: fila) if (ch=='-') return false;
        return true;
    }

    private void mostrarTablero() {
        StringBuilder sb = new StringBuilder("╔═════ TABLERO ═════╗\n");
        for (char[] fila : tablero) sb.append(" ").append(fila[0]).append(" | ").append(fila[1]).append(" | ").append(fila[2]).append("\n");
        sb.append("╚═══════════════════╝");
        enviarAmbos(sb.toString());
    }

    void enviarAmbos(String m) {
        enviarA(jugador1.nombre, m);
        enviarA(jugador2.nombre, m);
    }

    private void enviarA(String nombreJugador, String m) {
        UnCliente c = ServidorAsincrono.Clientes.get(nombreJugador);
        if (c != null) c.enviar(m);
    }
}
