package servidor.asincrono;

import java.io.*;
import java.net.Socket;

public class UnCliente implements Runnable {
    final DataOutputStream salida;
    final DataInputStream entrada;
    private final String nombre;
    private boolean registrado = false;
    private int mensajesEnviados = 0;
    public UnCliente(Socket s, String nombre) throws IOException {
        this.salida = new DataOutputStream(s.getOutputStream());
        this.entrada = new DataInputStream(s.getInputStream());
        this.nombre = nombre;
    }

    @Override
    public void run() {
        String mensaje;
        try {
            salida.writeUTF("Bienvenido " + nombre + 
                ". Puedes enviar 3 mensajes antes de registrarte o iniciar sesión.");

            while (true) {
                mensaje = entrada.readUTF();

                if (registrado) {
                    reenviarMensaje(nombre + ": " + mensaje);
                    continue;
                }
                if (mensaje.startsWith("registrar:")) {
                    String[] partes = mensaje.split(" ");
                    if (partes.length == 3) {
                        String user = partes[1];
                        String pass = partes[2];
                        ServidorAsincrono.Usuarios.put(user, pass);
                        registrado = true;
                        salida.writeUTF("✅ Registro exitoso. Ahora puedes enviar mensajes ilimitados.");
                    } else {
                        salida.writeUTF("Formato incorrecto. Usa: registrar: usuario contraseña");
                    }
                    continue;
                }
                if (mensaje.startsWith("login:")) {
                    String[] partes = mensaje.split(" ");
                    if (partes.length == 3) {
                        String user = partes[1];
                        String pass = partes[2];
                        if (ServidorAsincrono.Usuarios.containsKey(user) && 
                            ServidorAsincrono.Usuarios.get(user).equals(pass)) {
                            registrado = true;
                            salida.writeUTF("✅ Inicio de sesión correcto. Bienvenido " + user + ".");
                        } else {
                            salida.writeUTF("❌ Usuario o contraseña incorrectos.");
                        }
                    } else {
                        salida.writeUTF("Formato incorrecto. Usa: login: usuario contraseña");
                    }
                    continue;
                }
                if (mensajesEnviados >= 3) {
                    salida.writeUTF("⚠️ Has alcanzado el límite de 3 mensajes.\n" +
                            "Usa 'registrar: usuario contraseña' o 'login: usuario contraseña' para continuar.");
                    continue;
                }
                mensajesEnviados++;
                reenviarMensaje(nombre + ": " + mensaje);
            }

        } catch (IOException ex) {
            System.out.println("Cliente desconectado: " + nombre);
        } finally {
            cerrarConexion();
        }
    }

    private void reenviarMensaje(String mensaje) {
        synchronized (ServidorAsincrono.CLIENTE_LOCK) {
            for (UnCliente cliente : ServidorAsincrono.Cliente.values()) {
                if (cliente != this) {
                    try {
                        cliente.salida.writeUTF(mensaje);
                    } catch (IOException e) {
                        System.out.println("Error al enviar mensaje a " + cliente.nombre);
                    }
                }
            }
        }
    }

    private void cerrarConexion() {
        try { entrada.close(); } catch (IOException e) {}
        try { salida.close(); } catch (IOException e) {}

        synchronized (ServidorAsincrono.CLIENTE_LOCK) {
            ServidorAsincrono.Cliente.remove(nombre);
        }

        synchronized (ServidorAsincrono.CLIENTE_LOCK) {
            for (UnCliente cliente : ServidorAsincrono.Cliente.values()) {
                try {
                    cliente.salida.writeUTF("🔴 " + nombre + " se ha desconectado.");
                } catch (IOException e) {}
            }
        }
    }
}

