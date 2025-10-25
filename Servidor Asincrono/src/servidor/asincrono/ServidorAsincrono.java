package servidor.asincrono;

import java.io.*;
import java.net.*;
import java.util.*;

public class ServidorAsincrono {

    public static final Object CLIENTE_LOCK = new Object();
    static HashMap<String, UnCliente> Clientes = new HashMap<>();
    static HashMap<String, String> UsuariosRegistrados = new HashMap<>(); // nombre → password

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(8080)) {
            System.out.println("Servidor iniciado en el puerto 8080");

            while (true) {
                Socket socket = serverSocket.accept();
                DataInputStream entrada = new DataInputStream(socket.getInputStream());
                DataOutputStream salida = new DataOutputStream(socket.getOutputStream());

                salida.writeUTF("Bienvenido al servidor.\nEscribe: REGISTRAR <usuario> <contraseña> o LOGIN <usuario> <contraseña>");
                String linea = entrada.readUTF();
                String[] partes = linea.split(" ");
                String nombre = null;

                if (partes.length == 3) {
                    String comando = partes[0].toUpperCase();
                    String user = partes[1];
                    String pass = partes[2];

                    if (comando.equals("REGISTRAR")) {
                        synchronized (CLIENTE_LOCK) {
                            if (UsuariosRegistrados.containsKey(user)) {
                                salida.writeUTF("Usuario ya existe.");
                            } else {
                                UsuariosRegistrados.put(user, pass);
                                salida.writeUTF("Usuario registrado con éxito. Usa LOGIN para entrar.");
                            }
                        }
                    } else if (comando.equals("LOGIN")) {
                        synchronized (CLIENTE_LOCK) {
                            if (UsuariosRegistrados.containsKey(user) && UsuariosRegistrados.get(user).equals(pass)) {
                                nombre = user;
                                salida.writeUTF("Inicio de sesión correcto. Bienvenido, " + user);
                            } else {
                                salida.writeUTF("Credenciales incorrectas. Solo podrás observar mensajes.");
                            }
                        }
                    }
                }

                UnCliente nuevoCliente = new UnCliente(socket, nombre);
                synchronized (CLIENTE_LOCK) {
                    Clientes.put(socket.getRemoteSocketAddress().toString(), nuevoCliente);
                }

                new Thread(nuevoCliente).start();
                System.out.println("Nuevo cliente conectado: " + socket.getRemoteSocketAddress());
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

