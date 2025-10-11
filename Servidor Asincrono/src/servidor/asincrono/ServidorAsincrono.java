package servidor.asincrono;

import java.io.*;
import java.net.*;
import java.util.*;

public class ServidorAsincrono {

    public static final Object CLIENTE_LOCK = new Object();
    public static HashMap<String, UnCliente> Cliente = new HashMap<>();
    public static HashMap<String, String> Usuarios = new HashMap<>();

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(8080)) {
            System.out.println("Servidor iniciado en el puerto 8080");

            while (true) {
                Socket socket = serverSocket.accept();
                DataInputStream entrada = new DataInputStream(socket.getInputStream());
                DataOutputStream salida = new DataOutputStream(socket.getOutputStream());

                salida.writeUTF("Ingrese su nombre:");
                String nombre = entrada.readUTF();

                UnCliente nuevoCliente = new UnCliente(socket, nombre);
                synchronized (CLIENTE_LOCK) {
                    Cliente.put(nombre, nuevoCliente);
                }

                new Thread(nuevoCliente).start();

                System.out.println("Cliente conectado: " + nombre);

                synchronized (CLIENTE_LOCK) {
                    for (UnCliente c : Cliente.values()) {
                        if (!c.equals(nuevoCliente)) {
                            c.salida.writeUTF("🔵 " + nombre + " se ha conectado.");
                        }
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
