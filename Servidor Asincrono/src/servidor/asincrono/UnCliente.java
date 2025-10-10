package servidor.asincrono;

import java.io.*;
import java.net.Socket;

public class UnCliente implements Runnable {
    final DataOutputStream salida;
    final DataInputStream entrada;
    private final String nombre;

    public UnCliente(Socket s, String nombre) throws IOException {
        this.salida = new DataOutputStream(s.getOutputStream());
        this.entrada = new DataInputStream(s.getInputStream());
        this.nombre = nombre;
    }
    @Override
    public void run() {
        String mensaje;
        try {
            while (true) {
                mensaje = entrada.readUTF();
                String mensajeConNombre = nombre + ": " + mensaje;
                synchronized (ServidorAsincrono.CLIENTE_LOCK) {
                    for (UnCliente cliente : ServidorAsincrono.Cliente.values()) {
                        if (cliente != this) {
                            try {
                                cliente.salida.writeUTF(mensajeConNombre);
                            } catch (IOException e) {
                                System.out.println("Error al enviar a " + cliente.nombre);
                            }
                        }
                    }
                }
            }
        } catch (IOException ex) {
            System.out.println("Cliente desconectado: " + nombre);
        } finally {
            try { entrada.close(); } catch (IOException e) {}
            try { salida.close(); } catch (IOException e) {}

            synchronized (ServidorAsincrono.CLIENTE_LOCK) {
                ServidorAsincrono.Cliente.remove(nombre);
            }

            synchronized (ServidorAsincrono.CLIENTE_LOCK) {
                for (UnCliente cliente : ServidorAsincrono.Cliente.values()) {
                    try {
                        cliente.salida.writeUTF("🔴 " + nombre + " se ha desconectado.");
                    } catch (IOException e) {}
                }
            }
        }
    }
}

