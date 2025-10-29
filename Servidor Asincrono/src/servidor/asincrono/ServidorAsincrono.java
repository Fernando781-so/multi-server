package servidor.asincrono;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServidorAsincrono {
    public static final Object CLIENTE_LOCK = new Object();
    public static final Map<String, UnCliente> Clientes = new ConcurrentHashMap<>();
    public static final Map<String, JuegoGato> Partidas = new ConcurrentHashMap<>();
    public static final Map<String, EstadisticasJugador> Ranking = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(8080)) {
            System.out.println("🟢 Servidor iniciado en puerto 8080");

            while (true) {
                Socket socket = serverSocket.accept();
                java.io.DataInputStream entrada = new java.io.DataInputStream(socket.getInputStream());
                java.io.DataOutputStream salida = new java.io.DataOutputStream(socket.getOutputStream());

                salida.writeUTF("Ingrese su nombre:");
                String nombre = entrada.readUTF();

                UnCliente nuevoCliente = new UnCliente(socket, nombre);
                Clientes.put(nombre, nuevoCliente);

                Ranking.putIfAbsent(nombre, new EstadisticasJugador(nombre));

                new Thread(nuevoCliente).start();
                System.out.println("👤 Cliente conectado: " + nombre);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
