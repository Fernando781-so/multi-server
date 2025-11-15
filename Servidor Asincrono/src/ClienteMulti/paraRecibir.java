package ClienteMulti;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;

public class paraRecibir implements Runnable {
    final DataInputStream entrada;

    public paraRecibir(Socket s) throws IOException {
        entrada = new DataInputStream(s.getInputStream());
    }

    @Override
    public void run() {
        try {
            while (true) {
                String mensaje = entrada.readUTF();
                System.out.println(mensaje);
            }
        } catch (IOException e) {
            System.out.println("Conexión cerrada con el servidor.");
        }finally{
            ClienteMulti.activo = false;
            try{entrada.close();} catch (IOException ignored){}
        }
    }
}

