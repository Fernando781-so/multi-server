package ClienteMulti;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;

public class paraRecibir implements Runnable {
    private final DataInputStream entrada;
    private final Socket socket;

    public paraRecibir(Socket s) throws IOException {
        this.socket = s;
        this.entrada = new DataInputStream(s.getInputStream());
    }

    @Override
    public void run() {
        try {
            while (true) {
                String msg = entrada.readUTF(); // bloquea
                if (msg == null) break;
                System.out.println(msg);
            }
        } catch (IOException e) {
            System.out.println("Conexión cerrada por el servidor o error de I/O: " + e.getMessage());
        } finally {
            try { entrada.close(); } catch (IOException e) {}
            try { socket.close(); } catch (IOException e) {}
        }
    }
}
