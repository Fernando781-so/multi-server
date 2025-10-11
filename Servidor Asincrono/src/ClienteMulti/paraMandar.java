package ClienteMulti;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

public class paraMandar implements Runnable {
    private final BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in));
    private final DataOutputStream salida;

    public paraMandar(Socket s) throws IOException {
        this.salida = new DataOutputStream(s.getOutputStream());
    }

    @Override
    public void run() {
        try {
            while (true) {
                String mensaje = teclado.readLine();
                if (mensaje.equalsIgnoreCase("salir")) {
                    salida.writeUTF("**se ha desconectado**");
                    break;
                }
                salida.writeUTF(mensaje);
            }
        } catch (IOException e) {
            System.out.println("Error enviando mensaje.");
        }
    }
}