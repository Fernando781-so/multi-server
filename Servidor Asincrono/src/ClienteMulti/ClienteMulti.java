package cliente.asincrono;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Scanner;

public class ClienteMulti {

    public static void main(String[] args) {
        final String SERVER = "localhost";
        final int PORT = 8080;

        try {
            Socket socket = new Socket(SERVER, PORT);
            System.out.println("✅ Conectado al servidor " + SERVER + ":" + PORT);

            DataInputStream entrada = new DataInputStream(socket.getInputStream());
            DataOutputStream salida = new DataOutputStream(socket.getOutputStream());
            Thread receptor = new Thread(new ParaRecibir(entrada));
            receptor.start();

            Thread emisor = new Thread(new ParaMandar(salida, socket));
            emisor.start();

            receptor.join();
            emisor.join();

        } catch (IOException | InterruptedException e) {
            System.out.println("❌ Error en conexión: " + e.getMessage());
        }
    }
}

