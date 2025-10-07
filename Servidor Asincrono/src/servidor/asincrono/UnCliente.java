
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
        while(true){
            try{
                mensaje = entrada.readUTF();
                String mensajeConNombre = nombre + ": " + mensaje;
                for(UnCliente cliente : ServidorAsincrono.Cliente.values()){
                    if (!cliente.nombre.equals(this.nombre)) {
                        cliente.salida.writeUTF(mensajeConNombre);
                    }
                }
            }catch (IOException ex){
            }
        }
    }
    
    
    
    
    
    
}
