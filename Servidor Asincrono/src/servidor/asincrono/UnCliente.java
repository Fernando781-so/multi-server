package servidor.asincrono;

import java.io.*;
import java.net.*;

public class UnCliente implements Runnable {
    private final Socket socket;
    private final DataInputStream entrada;
    private final DataOutputStream salida;
    private final String nombre;

    public UnCliente(Socket socket, String nombre, DataInputStream entrada, DataOutputStream salida) {
        this.socket = socket;
        this.nombre = nombre;
        this.entrada = entrada;
        this.salida = salida;
    }

    public String getNombre() { return nombre; }

    public void enviar(String msg) {
        try { salida.writeUTF(msg); } catch (IOException ignored) {}
    }

    @Override
    public void run() {
        try {
            enviar("👋 Bienvenido al chat, " + nombre + "! Escribe 'ayuda' para ver comandos.");

            while (true) {
                String msg = entrada.readUTF();
                if (msg == null) break;
                msg = msg.trim();

                if (msg.equalsIgnoreCase("salir")) {
                    enviar("👋 Desconectando...");
                    break;
                }

                if (procesarComando(msg)) continue;
                reenviar(nombre + ": " + msg);
            }
        } catch (IOException e) {
            System.out.println("❌ Cliente desconectado: " + nombre);
        } finally {
            cerrar();
        }
    }

    private boolean procesarComando(String msg) {
        try {
            if (msg.equalsIgnoreCase("ayuda")) {
                enviar("""
                        🧭 Comandos disponibles:
                        ──────────────────────
                        gato @usuario       → Proponer jugar al gato.
                        aceptar @usuario    → Aceptar propuesta de juego.
                        marcar f c          → Marcar casilla (0-2 filas, 0-2 columnas).
                        rendirse @usuario   → Rendirse en una partida.
                        salir               → Salir del chat.
                        """);
                return true;
            }

            if (msg.startsWith("gato @")) {
                String rival = msg.substring(6).trim();
                return proponerJuego(rival);
            }

            if (msg.startsWith("aceptar @")) {
                String rival = msg.substring(9).trim();
                return aceptarJuego(rival);
            }

            if (msg.startsWith("marcar ")) {
                String[] partes = msg.split(" ");
                if (partes.length != 3) {
                    enviar("Uso: marcar fila columna (0-2)");
                    return true;
                }
                int f = Integer.parseInt(partes[1]);
                int c = Integer.parseInt(partes[2]);
                return marcar(f, c);
            }

            if (msg.startsWith("rendirse @")) {
                String rival = msg.substring(11).trim();
                return rendirse(rival);
            }

        } catch (Exception e) {
            enviar("⚠️ Error en comando: " + e.getMessage());
        }
        return false;
    }

    private boolean proponerJuego(String rival) {
        synchronized (ServidorAsincrono.CLIENTE_LOCK) {
            UnCliente otro = ServidorAsincrono.Cliente.get(rival);
            if (otro == null) {
                enviar("❌ El usuario no existe o no está conectado.");
                return true;
            }
            if (rival.equals(nombre)) {
                enviar("❌ No puedes jugar contigo mismo.");
                return true;
            }

            String clave = JuegoGato.clave(nombre, rival);
            if (ServidorAsincrono.Partidas.containsKey(clave)) {
                enviar("⚠️ Ya tienes una partida activa con " + rival);
                return true;
            }

            otro.enviar("🎮 " + nombre + " te ha propuesto jugar al gato. Usa 'aceptar @" + nombre + "' para aceptar.");
            enviar("✅ Propuesta enviada a " + rival);
        }
        return true;
    }

    private boolean aceptarJuego(String rival) {
        synchronized (ServidorAsincrono.CLIENTE_LOCK) {
            UnCliente otro = ServidorAsincrono.Cliente.get(rival);
            if (otro == null) {
                enviar("❌ El usuario no existe o no está conectado.");
                return true;
            }

            String clave = JuegoGato.clave(nombre, rival);
            if (ServidorAsincrono.Partidas.containsKey(clave)) {
                enviar("⚠️ Ya hay una partida activa con " + rival);
                return true;
            }

            JuegoGato partida = new JuegoGato(this, otro);
            ServidorAsincrono.Partidas.put(clave, partida);
            partida.iniciar();
        }
        return true;
    }

    private boolean marcar(int fila, int col) {
        synchronized (ServidorAsincrono.CLIENTE_LOCK) {
            for (JuegoGato partida : ServidorAsincrono.Partidas.values()) {
                if (partida.contieneJugador(nombre)) {
                    partida.jugar(nombre, fila, col);
                    return true;
                }
            }
        }
        enviar("⚠️ No estás en ninguna partida activa.");
        return true;
    }

    private boolean rendirse(String rival) {
        String clave = JuegoGato.clave(nombre, rival);
        synchronized (ServidorAsincrono.CLIENTE_LOCK) {
            JuegoGato p = ServidorAsincrono.Partidas.get(clave);
            if (p == null) {
                enviar("⚠️ No tienes partida activa con " + rival);
                return true;
            }
            p.rendirse(nombre);
            ServidorAsincrono.Partidas.remove(clave);
        }
        return true;
    }

    private void reenviar(String msg) {
        synchronized (ServidorAsincrono.CLIENTE_LOCK) {
            for (UnCliente c : ServidorAsincrono.Cliente.values()) {
                if (!c.nombre.equals(this.nombre)) c.enviar(msg);
            }
        }
    }

    private void cerrar() {
        try {
            entrada.close();
            salida.close();
            socket.close();
        } catch (IOException ignored) {}

        synchronized (ServidorAsincrono.CLIENTE_LOCK) {
            ServidorAsincrono.Cliente.remove(nombre);
            for (JuegoGato g : ServidorAsincrono.Partidas.values()) {
                if (g.contieneJugador(nombre)) {
                    g.rendirse(nombre);
                }
            }
        }
        System.out.println("🔴 Desconectado: " + nombre);
    }
}
