package ClienteMulti;

import java.io.IOException;
import java.net.Socket;

public class ClienteMulti {
    public static void main(String[] args) {
        String host = "localhost"; 
        int port = 8080;
        try {
            Socket s = new Socket(host, port);
            System.out.println("Conectado al servidor " + host + ":" + port);

            paraMandar pm = new paraMandar(s);
            Thread t1 = new Thread(pm);
            t1.start();

            paraRecibir pr = new paraRecibir(s);
            Thread t2 = new Thread(pr);
            t2.start();

        } catch (IOException e) {
            System.out.println("No se pudo conectar al servidor en " + host + ":" + port + " -> ");
            System.out.println("Cerrando cliente.");
            // salir sin crashear
        }
    }
}

