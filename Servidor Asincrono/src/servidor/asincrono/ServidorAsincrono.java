package servidor.asincrono;

import java.io.*;
import java.net.*;
import java.util.*;

public class ServidorAsincrono {
    public static final Map<String, UnCliente> Cliente = new HashMap<>();
    public static final Map<String, JuegoGato> Partidas = new HashMap<>();
    public static final Object CLIENTE_LOCK = new Object();

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(8080)) {
            System.out.println("Servidor iniciado en puerto 8080");

            while (true) {
                Socket socket = serverSocket.accept();
                DataInputStream entrada = new DataInputStream(socket.getInputStream());
                DataOutputStream salida = new DataOutputStream(socket.getOutputStream());

                salida.writeUTF("Ingrese su nombre:");
                String nombre = entrada.readUTF().trim();

                synchronized (CLIENTE_LOCK) {
                    if (Cliente.containsKey(nombre)) {
                        salida.writeUTF("❌ Nombre ya en uso. Intenta con otro.");
                        socket.close();
                        continue;
                    }

                    UnCliente nuevo = new UnCliente(socket, nombre, entrada, salida);
                    Cliente.put(nombre, nuevo);
                    new Thread(nuevo).start();
                    System.out.println("Cliente conectado: " + nombre);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

