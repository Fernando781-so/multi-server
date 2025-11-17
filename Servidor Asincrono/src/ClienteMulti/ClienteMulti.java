package ClienteMulti;

import java.io.IOException;
import java.net.Socket;

public class ClienteMulti {
    public static void main(String[] args) {
        String host = "LocalHost"; 
        int port = 8080;
        try {
            Socket s = new Socket(host, port);
            System.out.println("Conectado al servidor.");

            paraMandar paraMandar = new paraMandar(s);
            Thread hiloParaMandar = new Thread(paraMandar);
            hiloParaMandar.start();

            paraRecibir paraRecibir = new paraRecibir(s);
            Thread hiloParaRecibir = new Thread(paraRecibir);
            hiloParaRecibir.start();

        } catch (IOException ex) {
            System.out.println("❌ No se pudo conectar al servidor (" + host + ":" + port + ").");
            System.out.println("Cerrando cliente.");
            // salir limpiamente
        }
    }
}


