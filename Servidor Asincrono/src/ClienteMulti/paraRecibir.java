
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
        String mensaje = "";
        while(true){
            try {
                mensaje = entrada.readUTF();
                int idx = mensaje.indexOf(": ");
                if (idx != -1) {
                    String remitente = mensaje.substring(0, idx);
                    String contenido = mensaje.substring(idx + 2);
                    System.out.println("Mensaje de " + remitente + ": " + contenido);
                } else {
                    System.out.println(mensaje);
                }
            } catch (IOException ex) {
      }
    } 
  } 
}
