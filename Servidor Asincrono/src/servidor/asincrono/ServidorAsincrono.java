
package servidor.asincrono;

import java.io.IOException;
import java.util.HashMap;

class UnCliente implements Runnable {
    private java.net.Socket socket;
    private String nombre;

    public UnCliente(java.net.Socket socket, String nombre) {
        this.socket = socket;
        this.nombre = nombre;
    }

    @Override
    public void run() {
        
        try {
            java.io.DataInputStream entrada = new java.io.DataInputStream(socket.getInputStream());
            java.io.DataOutputStream salida = new java.io.DataOutputStream(socket.getOutputStream());
            salida.writeUTF("Bienvenido, " + nombre);
            // Example: echo messages
            String mensaje;
            while ((mensaje = entrada.readUTF()) != null) {
                salida.writeUTF("Echo: " + mensaje);
            }
        } catch (IOException e) {
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
            }
        }
    }
}

public class ServidorAsincrono {

    static HashMap <String, UnCliente> Cliente = new HashMap<>();
    public static final Object CLIENTE_LOCK = new Object();

    @SuppressWarnings("CallToPrintStackTrace")
    public static void main(String[] args) {
        try (java.net.ServerSocket serverSocket = new java.net.ServerSocket(8080)) {
            System.out.println("Servidor iniciado en el puerto 8080");
            while (true) {
                java.net.Socket socket = serverSocket.accept();
                java.io.DataInputStream entrada = new java.io.DataInputStream(socket.getInputStream());
                java.io.DataOutputStream salida = new java.io.DataOutputStream(socket.getOutputStream());
                salida.writeUTF("Ingrese su nombre:");
                String nombre = entrada.readUTF();
                UnCliente nuevoCliente = new UnCliente(socket, nombre);
                Cliente.put(nombre, nuevoCliente);
                new Thread(nuevoCliente).start();
                System.out.println("Cliente conectado: " + nombre);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
}
