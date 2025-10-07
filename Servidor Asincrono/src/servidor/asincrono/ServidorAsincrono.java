
package servidor.asincrono;

import java.io.IOException;
import java.util.HashMap;


public class ServidorAsincrono {

    static HashMap <String, UnCliente> Cliente = new HashMap<>();
    // Lock para sincronizar acceso a la colección de clientes
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
