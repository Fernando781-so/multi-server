package ClienteMulti;

import java.io.IOException;
import java.net.Socket;

public class ClienteMulti {
    public static void main(String[] args) throws IOException {
        try{
        Socket s = new Socket("192.168.137.1", 8080);
        System.out.println("Conectado al servidor.");

        paraMandar paraMandar = new paraMandar(s);
        Thread hiloParaMandar = new Thread(paraMandar);
        hiloParaMandar.start();

        paraRecibir paraRecibir = new paraRecibir(s);
        Thread hiloParaRecibir = new Thread(paraRecibir);
        hiloParaRecibir.start();
        } catch (IOException e) {
            System.out.println("No se pudo conectar al servidor: ");
            System.out.println("Cerrando cliente");
        }
    }
}


