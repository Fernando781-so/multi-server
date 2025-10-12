package servidor.asincrono;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class UnCliente implements Runnable {

    private final Socket socket;
    private final DataOutputStream salida;
    private final DataInputStream entrada;
    private final String nombre;

    private boolean registrado = false;
    private int mensajesEnviados = 0;
    private static final int LIMITE_MENSAJES_SIN_REGISTRO = 3;

    public UnCliente(Socket socket, String nombre, DataInputStream entrada, DataOutputStream salida) {
        this.socket = socket;
        this.nombre = nombre;
        this.entrada = entrada;
        this.salida = salida;
    }

    public String getNombre() {
        return nombre;
    }

    public void enviarDirecto(String texto) throws IOException {
        salida.writeUTF(texto);
    }

    private void enviarSeguro(String texto) {
        try {
            salida.writeUTF(texto);
        } catch (IOException e) {
        }
    }

    private void reenviarAotros(String mensaje) {
        synchronized (ServidorAsincrono.CLIENTE_LOCK) {
            for (UnCliente cliente : ServidorAsincrono.Cliente.values()) {
                if (!cliente.getNombre().equals(this.nombre)) {
                    cliente.enviarSeguro(mensaje);
                }
            }
        }
    }
    private void mostrarAyuda() throws IOException {
        salida.writeUTF("📜 Comandos disponibles:\n"
                + "──────────────────────────────\n"
                + " Puedes enviar mensajes escribiendo texto normal.\n"
                + " ayuda → muestra este menú de ayuda\n"
                + " registrar <usuario> <contraseña> → crea una nueva cuenta\n"
                + " login <usuario> <contraseña> → inicia sesión en una cuenta ya registrada\n"
                + " salir → desconecta del servidor\n"
                + "\nSin registrarte puedes mandar solo " + LIMITE_MENSAJES_SIN_REGISTRO + " mensajes.\n"
                + "Después de eso solo podrás leer mensajes hasta registrarte o iniciar sesión.");
    }

    private boolean procesarComando(String texto) throws IOException {
        String trimmed = texto.trim().toLowerCase();

        if (trimmed.equals("ayuda")) {
            mostrarAyuda();
            return true;
        }

        if (trimmed.startsWith("registrar ")) {
            String[] partes = texto.split("\\s+");
            if (partes.length == 3) {
                String user = partes[1];
                String pass = partes[2];
                synchronized (ServidorAsincrono.CLIENTE_LOCK) {
                    if (ServidorAsincrono.Usuarios.containsKey(user)) {
                        salida.writeUTF("❌ El usuario '" + user + "' ya está registrado.");
                    } else {
                        ServidorAsincrono.Usuarios.put(user, pass);
                        registrado = true;
                        salida.writeUTF("✅ Registro exitoso. ¡Bienvenido, " + user + "!");
                    }
                }
            } else {
                salida.writeUTF("Formato incorrecto. Usa: registrar <usuario> <contraseña>");
            }
            return true;
        }

        if (trimmed.startsWith("login ")) {
            String[] partes = texto.split("\\s+");
            if (partes.length == 3) {
                String user = partes[1];
                String pass = partes[2];
                synchronized (ServidorAsincrono.CLIENTE_LOCK) {
                    if (ServidorAsincrono.Usuarios.containsKey(user)
                            && ServidorAsincrono.Usuarios.get(user).equals(pass)) {
                        registrado = true;
                        salida.writeUTF("✅ Inicio de sesión correcto. Bienvenido de nuevo, " + user + ".");
                    } else {
                        salida.writeUTF("❌ Usuario o contraseña incorrectos.");
                    }
                }
            } else {
                salida.writeUTF("Formato incorrecto. Usa: login <usuario> <contraseña>");
            }
            return true;
        }

        return false;
    }

    @Override
    public void run() {
        try {
            salida.writeUTF("👋 Bienvenido " + nombre + " al servidor de chat.\n"
                    + "Puedes enviar hasta " + LIMITE_MENSAJES_SIN_REGISTRO + " mensajes sin registrarte.\n"
                    + "Escribe 'ayuda' para ver los comandos disponibles.\n");

            while (true) {
                String mensaje = entrada.readUTF();
                if (mensaje == null) break;
                mensaje = mensaje.trim();
                if (mensaje.isEmpty()) continue;
                if (procesarComando(mensaje)) continue;
                if (registrado) {
                    String conNombre = nombre + ": " + mensaje;
                    reenviarAotros(conNombre);
                    continue;
                }
                if (mensajesEnviados >= LIMITE_MENSAJES_SIN_REGISTRO) {
                    salida.writeUTF("""
                                     Límite de mensajes alcanzado. Usa 'registrar' o 'login' para continuar enviando. 
                                    Puedes seguir viendo los mensajes de los demás.""");
                    continue;
                }

                // Aún puede enviar dentro del límite
                mensajesEnviados++;
                String conNombre = nombre + ": " + mensaje;
                reenviarAotros(conNombre);
            }
        } catch (IOException e) {
            System.out.println("Cliente desconectado: " + nombre);
        } finally {
            cerrarYNotificar();
        }
    }

    private void cerrarYNotificar() {
        try { entrada.close(); } catch (IOException ignored) {}
        try { salida.close(); } catch (IOException ignored) {}
        try { socket.close(); } catch (IOException ignored) {}

        synchronized (ServidorAsincrono.CLIENTE_LOCK) {
            ServidorAsincrono.Cliente.remove(nombre);
            for (UnCliente cliente : ServidorAsincrono.Cliente.values()) {
                cliente.enviarSeguro("🔴 " + nombre + " se ha desconectado.");
            }
        }

        System.out.println("Conexión finalizada para: " + nombre);
    }
}
