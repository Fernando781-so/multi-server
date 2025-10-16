package servidor.asincrono;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;

public class ServidorAsincrono {

    public static final Object CLIENTE_LOCK = new Object();
    public static final HashMap<String, UnCliente> Cliente = new HashMap<>();
    public static final HashMap<String, String> Usuarios = new HashMap<>();

    public static void main(String[] args) {
        final int PORT = 8080;
        System.out.println("Servidor iniciado en puerto " + PORT);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Nueva conexión desde " + socket.getRemoteSocketAddress());

                DataInputStream entrada = new DataInputStream(socket.getInputStream());
                DataOutputStream salida = new DataOutputStream(socket.getOutputStream());

                String nombre;
                while (true) {
                    salida.writeUTF("Ingrese su nombre:");
                    nombre = entrada.readUTF().trim();

                    if (nombre.isEmpty()) {
                        salida.writeUTF("❌ Nombre vacío. Intenta de nuevo.");
                        continue;
                    }

                    synchronized (CLIENTE_LOCK) {
                        if (Cliente.containsKey(nombre)) {
                            salida.writeUTF("❌ Ese nombre ya está en uso.");
                        } else {
                            break;
                        }
                    }
                }

                UnCliente nuevo = new UnCliente(socket, nombre, entrada, salida);
                synchronized (CLIENTE_LOCK) {
                    Cliente.put(nombre, nuevo);
                }

                new Thread(nuevo).start();
                System.out.println("Cliente conectado: " + nombre);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
