package ClienteMulti;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;

public class paraRecibir implements Runnable {
    final DataInputStream entrada;
    final Socket socket;

    public paraRecibir(Socket s) throws IOException {
        this.socket = s;
        this.entrada = new DataInputStream(s.getInputStream());
    }

    @Override
    public void run() {
        try {
            while (!socket.isClosed()) {
                String mensaje = entrada.readUTF();
                if (mensaje == null) break;
                System.out.println(mensaje);
            }
        } catch (IOException e) {
            System.out.println("Conexión cerrada con el servidor.");
            try { socket.close(); } catch (IOException ignored) {}
        }
    }
}
