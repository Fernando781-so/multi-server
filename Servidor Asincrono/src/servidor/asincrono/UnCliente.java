package servidor.asincrono;

import java.io.*;
import java.net.*;

public class UnCliente extends Thread {

    Socket socket;
    DataInputStream entrada;
    DataOutputStream salida;
    String nombre;
    boolean autenticado = false;
    boolean enPartida = false;
    String rival = null;
    int mensajesSinLogin = 0;

    public UnCliente(Socket s) {
        this.socket = s;
        try {
            entrada = new DataInputStream(s.getInputStream());
            salida = new DataOutputStream(s.getOutputStream());
            enviar("👋 Bienvenido al servidor Gato Chat. Usa /login <nombre> para iniciar sesión.");
        } catch (IOException e) {
            System.err.println("Error al conectar cliente: " + e.getMessage());
        }
    }

    void enviar(String msg) {
        try {
            salida.writeUTF(msg);
        } catch (IOException e) {
            System.out.println("No se pudo enviar a " + nombre + ": " + e.getMessage());
        }
    }

    @Override
    public void run() {
        try {
            while (true) {
                String msg = entrada.readUTF().trim();

                if (!autenticado) {
                    if (msg.startsWith("/login ")) {
                        String n = msg.substring(7).trim();
                        if (n.isEmpty() || ServidorAsincrono.Clientes.containsKey(n)) {
                            enviar("❌ Nombre inválido o ya en uso.");
                        } else {
                            nombre = n;
                            autenticado = true;
                            ServidorAsincrono.Clientes.put(nombre, this);
                            ServidorAsincrono.Ranking.putIfAbsent(nombre, new EstadisticasJugador(nombre));
                            enviar("✅ Sesión iniciada como " + nombre);
                            enviar("Usa /ayuda para ver los comandos disponibles.");
                        }
                        continue;
                    }

                    mensajesSinLogin++;
                    if (mensajesSinLogin > 3) {
                        enviar("🚫 Límite de mensajes sin iniciar sesión. Usa /login <nombre>.");
                    } else {
                        enviar("⚠️ Debes iniciar sesión con /login <nombre>");
                    }
                    continue;
                }

                procesarComando(msg);
            }
        } catch (IOException e) {
            System.out.println("Cliente desconectado: " + nombre);
        } finally {
            cerrarConexion();
        }
    }

    private void procesarComando(String msg) {
        if (msg.equals("/usuarios")) {
            enviar("👥 Usuarios conectados: " + ServidorAsincrono.Clientes.keySet());
        }

        else if (msg.startsWith("/jugar ")) {
            String objetivo = msg.substring(7).trim();
            if (objetivo.equals(nombre)) {
                enviar("⚠️ No puedes jugar contra ti mismo.");
                return;
            }
            UnCliente rival = ServidorAsincrono.Clientes.get(objetivo);
            if (rival == null) {
                enviar("❌ Usuario no encontrado.");
                return;
            }
            if (rival.enPartida) {
                enviar("⚠️ Ese jugador ya está en una partida.");
                return;
            }
            ServidorAsincrono.SolicitudesPendientes.put(objetivo, nombre);
            rival.enviar("🎮 " + nombre + " te ha retado a jugar. Usa /aceptar " + nombre);
            enviar("✅ Solicitud enviada a " + objetivo);
        }

        else if (msg.startsWith("/aceptar ")) {
            String rivalNombre = msg.substring(9).trim();
            String solicitante = ServidorAsincrono.SolicitudesPendientes.get(nombre);
            if (solicitante == null || !solicitante.equals(rivalNombre)) {
                enviar("⚠️ No tienes una solicitud pendiente de " + rivalNombre);
                return;
            }
            UnCliente j1 = ServidorAsincrono.Clientes.get(rivalNombre);
            if (j1 != null) {
                ServidorAsincrono.iniciarPartida(j1, this);
                ServidorAsincrono.SolicitudesPendientes.remove(nombre);
            } else {
                enviar("❌ El jugador ya no está disponible.");
            }
        }

        else if (msg.startsWith("/mover ")) {
            try {
                int pos = Integer.parseInt(msg.substring(7).trim());
                ServidorAsincrono.mover(nombre, pos);
            } catch (Exception e) {
                enviar("❌ Uso: /mover <1-9>");
            }
        }

        else if (msg.equals("/rendirse")) {
            ServidorAsincrono.rendirse(nombre);
        }

        else if (msg.equals("/ranking")) {
            enviar(ServidorAsincrono.mostrarRanking());
        }

        else if (msg.equals("/ayuda")) {
            enviar(ServidorAsincrono.mostrarAyuda());
        }

        else {
            enviar("❔ Comando no reconocido. Usa /ayuda para ver opciones.");
        }
    }

    private void cerrarConexion() {
        try {
            if (nombre != null) {
                ServidorAsincrono.Clientes.remove(nombre);
                System.out.println("🟡 Usuario desconectado: " + nombre);
            }
            socket.close();
        } catch (IOException ignored) {}
    }
}
