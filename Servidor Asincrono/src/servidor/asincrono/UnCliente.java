package servidor.asincrono;

import java.io.*;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;

public class UnCliente implements Runnable {

    private final Socket socket;
    private final DataInputStream entrada;
    private final DataOutputStream salida;
    private final String nombre;

    private boolean registrado = false;
    private int mensajesEnviados = 0;
    private static final int LIMITE_SIN_REGISTRO = 3;
    private final Set<String> bloqueados = new HashSet<>();

    public UnCliente(Socket socket, String nombre, DataInputStream entrada, DataOutputStream salida) {
        this.socket = socket;
        this.nombre = nombre;
        this.entrada = entrada;
        this.salida = salida;
    }

    public String getNombre() {
        return nombre;
    }

    public void enviar(String mensaje, String remitente) {
        if (bloqueados.contains(remitente)) return;
        try {
            salida.writeUTF(mensaje);
        } catch (IOException ignored) {}
    }

    private void enviarDirecto(String texto) {
        try { salida.writeUTF(texto); } catch (IOException ignored) {}
    }

    private void reenviarATodos(String mensaje) {
        synchronized (ServidorAsincrono.CLIENTE_LOCK) {
            for (UnCliente c : ServidorAsincrono.Cliente.values()) {
                if (!c.getNombre().equals(this.nombre)) {
                    c.enviar(nombre + ": " + mensaje, this.nombre);
                }
            }
        }
    }

    private void mostrarAyuda() {
        enviarDirecto("📜 Comandos disponibles:\n"
                + "──────────────────────────────\n"
                + "💬 Escribe cualquier texto para enviar un mensaje.\n"
                + "🆘 ayuda → muestra esta ayuda\n"
                + "📝 registrar <usuario> <contraseña> → crea cuenta\n"
                + "🔐 login <usuario> <contraseña> → inicia sesión\n"
                + "🚫 bloquear <usuario> → bloquea a alguien (no verás sus mensajes)\n"
                + "✅ desbloquear <usuario> → lo desbloqueas\n"
                + "👥 bloqueados → muestra tu lista actual\n"
                + "🚪 salir → desconecta\n"
                + "\nLímite sin registro: " + LIMITE_SIN_REGISTRO + " mensajes.");
    }

    private boolean procesarComando(String msg) {
        String[] partes = msg.trim().split("\\s+");
        String comando = partes[0].toLowerCase();

        try {
            switch (comando) {
                case "ayuda" -> mostrarAyuda();

                case "registrar" -> {
                    if (partes.length != 3) {
                        enviarDirecto("Uso correcto: registrar <usuario> <contraseña>");
                        return true;
                    }
                    String user = partes[1], pass = partes[2];
                    synchronized (ServidorAsincrono.CLIENTE_LOCK) {
                        if (ServidorAsincrono.Usuarios.containsKey(user)) {
                            enviarDirecto("❌ El usuario '" + user + "' ya está registrado.");
                        } else {
                            ServidorAsincrono.Usuarios.put(user, pass);
                            registrado = true;
                            enviarDirecto("✅ Registro exitoso. ¡Bienvenido, " + user + "!");
                        }
                    }
                }

                case "login" -> {
                    if (partes.length != 3) {
                        enviarDirecto("Uso correcto: login <usuario> <contraseña>");
                        return true;
                    }
                    String user = partes[1], pass = partes[2];
                    synchronized (ServidorAsincrono.CLIENTE_LOCK) {
                        if (ServidorAsincrono.Usuarios.containsKey(user)
                                && ServidorAsincrono.Usuarios.get(user).equals(pass)) {
                            registrado = true;
                            enviarDirecto("✅ Sesión iniciada correctamente como " + user + ".");
                        } else {
                            enviarDirecto("❌ Usuario o contraseña incorrectos.");
                        }
                    }
                }

                case "bloquear" -> {
                    if (partes.length != 2) {
                        enviarDirecto("Uso correcto: bloquear <nombre>");
                        return true;
                    }
                    String objetivo = partes[1];
                    synchronized (ServidorAsincrono.CLIENTE_LOCK) {
                        if (!ServidorAsincrono.Cliente.containsKey(objetivo)) {
                            enviarDirecto("❌ El usuario '" + objetivo + "' no está conectado.");
                            return true;
                        }
                        if (objetivo.equals(nombre)) {
                            enviarDirecto("❌ No puedes bloquearte a ti mismo.");
                            return true;
                        }
                        if (bloqueados.contains(objetivo)) {
                            enviarDirecto("⚠️ Ya tienes bloqueado a " + objetivo + ".");
                            return true;
                        }
                        bloqueados.add(objetivo);
                        enviarDirecto("🚫 Has bloqueado a " + objetivo + ".");
                    }
                }

                case "desbloquear" -> {
                    if (partes.length != 2) {
                        enviarDirecto("Uso correcto: desbloquear <nombre>");
                        return true;
                    }
                    String objetivo = partes[1];
                    if (!bloqueados.contains(objetivo)) {
                        enviarDirecto("⚠️ " + objetivo + " no está bloqueado.");
                        return true;
                    }
                    bloqueados.remove(objetivo);
                    enviarDirecto("✅ Has desbloqueado a " + objetivo + ".");
                }

                case "bloqueados" -> {
                    if (bloqueados.isEmpty()) {
                        enviarDirecto("🟢 No tienes usuarios bloqueados.");
                    } else {
                        enviarDirecto("🚫 Usuarios bloqueados: " + bloqueados);
                    }
                }

                case "salir" -> {
                    enviarDirecto("👋 Desconectando...");
                    socket.close();
                    return true;
                }

                default -> {
                    return false; 
                }
            }
        } catch (IOException e) {
            enviarDirecto("❌ Error al ejecutar comando: " + e.getMessage());
        }

        return true;
    }

    @Override
    public void run() {
        enviarDirecto("👋 Bienvenido " + nombre + ". Escribe 'ayuda' para ver los comandos.\n");

        try {
            while (true) {
                String mensaje = entrada.readUTF();
                if (mensaje == null) break;
                mensaje = mensaje.trim();
                if (mensaje.isEmpty()) continue;

                if (procesarComando(mensaje)) continue;
                if (!registrado) {
                    if (mensajesEnviados >= LIMITE_SIN_REGISTRO) {
                        enviarDirecto("⚠️ Has alcanzado el límite de " + LIMITE_SIN_REGISTRO
                                + " mensajes. Usa 'registrar' o 'login' para continuar.");
                        continue;
                    }
                    mensajesEnviados++;
                }

                reenviarATodos(mensaje);
            }
        } catch (IOException e) {
            System.out.println("Cliente desconectado: " + nombre);
        } finally {
            cerrar();
        }
    }

    private void cerrar() {
        try { entrada.close(); } catch (IOException ignored) {}
        try { salida.close(); } catch (IOException ignored) {}
        try { socket.close(); } catch (IOException ignored) {}

        synchronized (ServidorAsincrono.CLIENTE_LOCK) {
            ServidorAsincrono.Cliente.remove(nombre);
            for (UnCliente c : ServidorAsincrono.Cliente.values()) {
                c.enviar("🔴 " + nombre + " se ha desconectado.", nombre);
            }
        }

        System.out.println("Cliente " + nombre + " desconectado y limpiado.");
    }
}
