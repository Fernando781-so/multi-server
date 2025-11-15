package ClienteMulti;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

public class paraMandar implements Runnable {
    final BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in));
    final DataOutputStream salida;

    public paraMandar(Socket s) throws IOException {
        this.salida = new DataOutputStream(s.getOutputStream());
    }

    @Override
public void run() {
    try {
     while(ClienteMulti.activo){
        if(!ClienteMulti.activo)
          break;
            String mensaje = teclado.readLine();  // leer desde consola
            
            if (mensaje == null) {                 // Ctrl+Z o EOF detectado
                System.out.println("Entrada EOF detectada, cerrando conexión...");
                salida.writeUTF("/desconectarse"); // opcional: notificar al servidor
                break;                              // salir del hilo
               }
            try {
            salida.writeUTF(mensaje);               // enviar al servidor
         } catch (IOException e) {
                System.out.println("⚠️ No se pudo enviar (servidor desconectado).");
                ClienteMulti.activo = false;
                break;
            }
        }
    } catch (IOException ignored) {
    } finally {
        try { salida.close(); } catch (IOException ignored) {}
   }
  }
}


