package ClienteMulti;

import java.io.IOException;
import java.net.Socket;

public class ClienteMulti {
    public static void main(String[] args) {
        try {
            Socket s = new Socket("localhost", 8080);
            System.out.println("Conectado al servidor...");

            Thread hiloEnviar = new Thread(new paraMandar(s));
            hiloEnviar.start();

            Thread hiloRecibir = new Thread(new paraRecibir(s));
            hiloRecibir.start();

        } catch (IOException e) {
            System.out.println("No se pudo conectar al servidor.");
        }
    }
}

