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
    private boolean procesarComandoRegistroLogin(String texto) throws IOException {
        String trimmed = texto.trim();
        if (trimmed.startsWith("registrar ")) {
            String[] partes = trimmed.split("\\s+");
            if (partes.length == 3) {
                String user = partes[1];
                String pass = partes[2];
                synchronized (ServidorAsincrono.CLIENTE_LOCK) {
                    if (ServidorAsincrono.Usuarios.containsKey(user)) {
                        salida.writeUTF("❌ Usuario ya registrado: " + user);
                    } else {
                        ServidorAsincrono.Usuarios.put(user, pass);
                        registrado = true;
                        salida.writeUTF("✅ Registro exitoso. Ahora puedes enviar mensajes ilimitados.");
                    }
                }
            } else {
                salida.writeUTF("Formato incorrecto. Usa: registrar usuario contraseña");
            }
            return true;
        } else if (trimmed.startsWith("login ")) {
            String[] partes = trimmed.split("\\s+");
            if (partes.length == 3) {
                String user = partes[1];
                String pass = partes[2];
                synchronized (ServidorAsincrono.CLIENTE_LOCK) {
                    if (ServidorAsincrono.Usuarios.containsKey(user) &&
                        ServidorAsincrono.Usuarios.get(user).equals(pass)) {
                        registrado = true;
                        salida.writeUTF("✅ Inicio de sesión correcto. Bienvenido " + user + ".");
                    } else {
                        salida.writeUTF("❌ Usuario o contraseña incorrectos.");
                    }
                }
            } else {
                salida.writeUTF("Formato incorrecto. Usa: login usuario contraseña");
            }
            return true;
        }
        return false;
    }

    @Override
    public void run() {
        try {
            salida.writeUTF("Bienvenido " + nombre + ". Puedes enviar " + LIMITE_MENSAJES_SIN_REGISTRO
                    + " mensajes antes de registrarte o iniciar sesión.\n"
                    + "Comandos:\n  registrar usuario contraseña\n  login usuario contraseña");

            while (true) {
                String mensaje = entrada.readUTF(); 
                if (mensaje == null) break;
                mensaje = mensaje.trim();
                if (mensaje.isEmpty()) continue;
                if (procesarComandoRegistroLogin(mensaje)) {
                    continue;
                }
                if (registrado) {
                    String conNombre = nombre + ": " + mensaje;
                    reenviarAotros(conNombre);
                    continue;
                }
                if (mensajesEnviados >= LIMITE_MENSAJES_SIN_REGISTRO) {
                    salida.writeUTF("⚠️ Has alcanzado el límite de " + LIMITE_MENSAJES_SIN_REGISTRO
                            + " mensajes. Usa 'registrar usuario contraseña' o 'login usuario contraseña' para continuar.\n"
                            + "Nota: aún puedes ver mensajes de los demás.");
                    continue;
                }
                mensajesEnviados++;
                String conNombre = nombre + ": " + mensaje;
                reenviarAotros(conNombre);
            }
        } catch (IOException e) {
            System.out.println("Cliente desconectado o error I/O: " + nombre + " -> " + e.getMessage());
        } finally {
            cerrarYNotificar();
        }
    }

    private void cerrarYNotificar() {
        try { entrada.close(); } catch (IOException e) {}
        try { salida.close(); } catch (IOException e) {}
        try { socket.close(); } catch (IOException e) {}
        synchronized (ServidorAsincrono.CLIENTE_LOCK) {
            ServidorAsincrono.Cliente.remove(this.nombre);
        }
        synchronized (ServidorAsincrono.CLIENTE_LOCK) {
            for (UnCliente cliente : ServidorAsincrono.Cliente.values()) {
                cliente.enviarSeguro("🔴 " + this.nombre + " se ha desconectado.");
            }
        }

        System.out.println("Limpieza terminada para: " + nombre);
    }
}
