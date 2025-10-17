package ClienteMulti;

import java.io.DataInputStream;
import java.io.IOException;

public class paraRecibir implements Runnable {

    private final DataInputStream entrada;

    public paraRecibir(DataInputStream entrada) {
        this.entrada = entrada;
    }

    @Override
    public void run() {
        try {
            while (true) {
                String msg = entrada.readUTF();
                System.out.println(msg);
            }
        } catch (IOException e) {
            System.out.println("⚠️ Conexión cerrada por el servidor.");
        }
    }
}

