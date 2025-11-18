package ClienteMulti;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

public class paraMandar implements Runnable {
    final BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in));
    final DataOutputStream salida;
    final Socket socket;

    public paraMandar(Socket s) throws IOException {
        this.socket = s;
        this.salida = new DataOutputStream(s.getOutputStream());
    }

    @Override
    public void run() {
        try {
            while (!socket.isClosed()) {
                String mensaje = teclado.readLine(); 
                if (mensaje == null) {
                    System.out.println("EOF detectado. Cerrando conexión...");
                    try {
                        salida.writeUTF("/desconectar");
                    } catch (IOException ignored) {}
                    break;
                }
                try {
                    salida.writeUTF(mensaje);
                } catch (IOException e) {
                    System.out.println("Error enviando: " + e.getMessage());
                    break;
                }
            }
            try { socket.close(); } catch (IOException ignored) {}
        } catch (IOException e) {
            System.out.println("Error en paraMandar: " + e.getMessage());
        }
    }
}
