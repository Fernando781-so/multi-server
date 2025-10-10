package servidor.asincrono;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

class UnCliente implements Runnable {
    private Socket socket;
    private String nombre;
    private DataInputStream entrada;
    private DataOutputStream salida;
    private static Map<String, UnCliente> clientes;

    public UnCliente(Socket socket, String nombre, Map<String, UnCliente> clientes) throws IOException {
        this.socket = socket;
        this.nombre = nombre;
        this.clientes = clientes;
        this.entrada = new DataInputStream(socket.getInputStream());
        this.salida = new DataOutputStream(socket.getOutputStream());
    }

    public void enviarMensaje(String mensaje) {
        try {
            salida.writeUTF(mensaje);
        } catch (IOException e) {
            System.out.println("Error al enviar mensaje a " + nombre);
        }
    }

    @Override
    public void run() {
        try {
            String mensaje;
            while ((mensaje = entrada.readUTF()) != null) {
                System.out.println(nombre + ": " + mensaje);
                for (UnCliente c : clientes.values()) {
                    if (!c.nombre.equals(this.nombre)) {
                        c.enviarMensaje(nombre + ": " + mensaje);
                    }
                }
            }
        } catch (IOException e) {
            System.out.println(nombre + " se desconectó.");
        } finally {
            try {
                socket.close();
            } catch (IOException e) {}
            clientes.remove(nombre);
        }
    }
}

public class ServidorAsincrono {
    public static void main(String[] args) {
        Map<String, UnCliente> clientes = new HashMap<>();

        try (ServerSocket serverSocket = new ServerSocket(8080)) {
            System.out.println("Servidor iniciado en el puerto 8080");

            while (true) {
                Socket socket = serverSocket.accept();
                DataInputStream entrada = new DataInputStream(socket.getInputStream());
                DataOutputStream salida = new DataOutputStream(socket.getOutputStream());

                salida.writeUTF("Ingrese su nombre:");
                String nombre = entrada.readUTF();

                UnCliente nuevoCliente = new UnCliente(socket, nombre, clientes);
                clientes.put(nombre, nuevoCliente);

                for (UnCliente c : clientes.values()) {
                    if (!c.equals(nuevoCliente)) {
                        c.enviarMensaje("🔵 " + nombre + " se ha conectado.");
                    }
                }

                new Thread(nuevoCliente).start();
                System.out.println("Cliente conectado: " + nombre);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
