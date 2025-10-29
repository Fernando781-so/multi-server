package servidor.asincrono;

import java.io.*;
import java.net.*;
import java.util.*;

public class UnCliente implements Runnable {

    final Socket socket;
    final DataInputStream entrada;
    final DataOutputStream salida;
    final String nombre;
    private final Set<String> bloqueados = new HashSet<>();
    private String rival = null;
    private boolean enPartida = false;

    public UnCliente(Socket s, String nombre) throws IOException {
        this.socket = s;
        this.nombre = nombre;
        entrada = new DataInputStream(s.getInputStream());
        salida = new DataOutputStream(s.getOutputStream());
    }

    @Override
    public void run() {
        try {
            String mensaje;
            while (true) {
                mensaje = entrada.readUTF();
                if (mensaje == null) break;

                if (mensaje.startsWith("/")) {
                    procesarComando(mensaje);
                } else {
                    if (enPartida && rival != null) {
                        UnCliente otro = buscarUsuario(rival);
                        if (otro != null) otro.salida.writeUTF(nombre + " (Gato): " + mensaje);
                    } else {
                        broadcast(nombre + ": " + mensaje);
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Cliente desconectado: " + nombre);
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
            synchronized (ServidorAsincrono.CLIENTE_LOCK) {
                ServidorAsincrono.Clientes.remove(socket.getRemoteSocketAddress().toString());
            }
        }
    }

    private void procesarComando(String mensaje) throws IOException {
        String[] partes = mensaje.split(" ");
        String comando = partes[0].toUpperCase();

        switch (comando) {

            // === CONSULTAR RANKING GENERAL ===
            case "/RANKING" -> {
                if (enPartida) {
                    salida.writeUTF("🚫 No puedes consultar el ranking mientras estás jugando.");
                    return;
                }
                salida.writeUTF(ServidorAsincrono.obtenerRanking());
            }

            // === CONSULTAR WINRATE ENTRE DOS JUGADORES ===
            case "/VERSUS" -> {
                if (enPartida) {
                    salida.writeUTF("🚫 No puedes consultar el winrate mientras estás jugando.");
                    return;
                }
                if (partes.length < 3) {
                    salida.writeUTF("Uso: /versus <jugador1> <jugador2>");
                    return;
                }
                salida.writeUTF(ServidorAsincrono.obtenerVs(partes[1], partes[2]));
            }

            // === BLOQUEAR USUARIO ===
            case "/BLOQUEAR" -> {
                if (nombre == null) {
                    salida.writeUTF("No puedes bloquear sin iniciar sesión.");
                    return;
                }
                if (partes.length < 2) {
                    salida.writeUTF("Uso: /bloquear <usuario>");
                    return;
                }
                String user = partes[1];
                if (user.equals(nombre)) {
                    salida.writeUTF("No puedes bloquearte a ti mismo.");
                    return;
                }

                UnCliente target = buscarUsuario(user);
                if (target == null) {
                    salida.writeUTF("Usuario no encontrado.");
                    return;
                }
                if (enPartida && rival != null && rival.equals(user)) {
                    salida.writeUTF("No puedes bloquear a tu rival mientras juegas.");
                    return;
                }

                bloqueados.add(user);
                salida.writeUTF("Has bloqueado a " + user);
            }

            // === DESBLOQUEAR USUARIO ===
            case "/DESBLOQUEAR" -> {
                if (partes.length < 2) {
                    salida.writeUTF("Uso: /desbloquear <usuario>");
                    return;
                }
                String user = partes[1];
                if (!bloqueados.contains(user)) {
                    salida.writeUTF("Ese usuario no está bloqueado.");
                    return;
                }
                bloqueados.remove(user);
                salida.writeUTF("Has desbloqueado a " + user);
            }

            // === PROPONER PARTIDA ===
            case "/GATO" -> {
                if (nombre == null) {
                    salida.writeUTF("Debes iniciar sesión para jugar.");
                    return;
                }
                if (partes.length < 2) {
                    salida.writeUTF("Uso: /gato <usuario>");
                    return;
                }
                String oponente = partes[1];
                UnCliente rivalCliente = buscarUsuario(oponente);
                if (rivalCliente == null) {
                    salida.writeUTF("Usuario no encontrado o no conectado.");
                    return;
                }
                if (bloqueados.contains(oponente)) {
                    salida.writeUTF("Tienes bloqueado a ese usuario.");
                    return;
                }
                if (rivalCliente.bloqueados.contains(nombre)) {
                    salida.writeUTF("Ese usuario te tiene bloqueado.");
                    return;
                }
                if (rivalCliente.enPartida) {
                    salida.writeUTF("El usuario ya está en una partida.");
                    return;
                }

                rivalCliente.salida.writeUTF(nombre + " te ha propuesto jugar al gato. Acepta con /aceptar " + nombre);
                salida.writeUTF("Solicitud enviada a " + oponente);
            }

            // === ACEPTAR PARTIDA ===
            case "/ACEPTAR" -> {
                if (partes.length < 2) {
                    salida.writeUTF("Uso: /aceptar <usuario>");
                    return;
                }
                String quien = partes[1];
                UnCliente jugador = buscarUsuario(quien);
                if (jugador == null) {
                    salida.writeUTF("No se encontró al usuario.");
                    return;
                }

                if (jugador.enPartida || enPartida) {
                    salida.writeUTF("Ya estás o el otro jugador está en una partida.");
                    return;
                }

                this.enPartida = true;
                this.rival = quien;
                jugador.enPartida = true;
                jugador.rival = this.nombre;

                boolean empieza = new Random().nextBoolean();
                String msg = "🎲 Comienza la partida entre " + nombre + " y " + quien + ". Empieza: " + (empieza ? nombre : quien);
                jugador.salida.writeUTF(msg);
                this.salida.writeUTF(msg);

                // Simulación del resultado
                String resultado = new String[]{"gana1", "gana2", "empate"}[new Random().nextInt(3)];
                ServidorAsincrono.registrarResultado(nombre, quien, resultado);

                jugador.enPartida = false;
                this.enPartida = false;
                jugador.rival = null;
                this.rival = null;

                jugador.salida.writeUTF("Partida finalizada. Resultado: " + resultado);
                this.salida.writeUTF("Partida finalizada. Resultado: " + resultado);
            }

            default -> salida.writeUTF("Comando no reconocido.");
        }
    }

    private void broadcast(String mensaje) throws IOException {
        synchronized (ServidorAsincrono.CLIENTE_LOCK) {
            for (UnCliente c : ServidorAsincrono.Clientes.values()) {
                if (c != this && !c.bloqueados.contains(this.nombre)) {
                    c.salida.writeUTF(mensaje);
                }
            }
        }
    }

    private UnCliente buscarUsuario(String user) {
        synchronized (ServidorAsincrono.CLIENTE_LOCK) {
            for (UnCliente c : ServidorAsincrono.Clientes.values()) {
                if (user != null && user.equals(c.nombre)) return c;
            }
        }
        return null;
    }
}
