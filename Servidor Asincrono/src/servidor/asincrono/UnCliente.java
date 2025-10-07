
package servidor.asincrono;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class UnCliente implements Runnable {
    final DataOutputStream salida;
    final DataInputStream entrada;
    private final String nombre;

     public UnCliente(Socket s, String nombre)throws IOException {
         salida = new DataOutputStream(s.getOutputStream());
         entrada = new DataInputStream(s.getInputStream());
         this.nombre = nombre;
     }
    
    @Override
    
    public void run(){
        String mensaje;
        try {
            while(true){
                mensaje = entrada.readUTF();
                String mensajeConNombre = nombre + ": " + mensaje;
                // sincronizar al iterar sobre la colección compartida
                synchronized (ServidorAsincrono.CLIENTE_LOCK) {
                    for(UnCliente cliente : ServidorAsincrono.Cliente.values()){
                        // asegurar que no se reenvíe al propio remitente (comparación por identidad)
                        if (cliente != this) {
                            cliente.salida.writeUTF(mensajeConNombre);
                        }
                    }
                }
            }
        } catch (IOException ex) {
            // El cliente se desconectó o ocurrió un error de IO: limpiamos recursos y lo removemos del mapa
            try {
                entrada.close();
            } catch (IOException e) {
                // ignorar
            }
            try {
                salida.close();
            } catch (IOException e) {
                // ignorar
            }
            ServidorAsincrono.Cliente.remove(this.nombre);
            System.out.println("Cliente desconectado: " + this.nombre);
        }
    }
    
    
    
    
    
    
}
