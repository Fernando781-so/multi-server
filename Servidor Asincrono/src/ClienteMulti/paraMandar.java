package ClienteMulti;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

public class paraMandar implements Runnable {
    private final BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in));
    private final DataOutputStream salida;

    public paraMandar(Socket s) throws IOException {
        this.salida = new DataOutputStream(s.getOutputStream());
    }

    @Override
    public void run() {
        try {
            while (true) {
                String linea = teclado.readLine();
                if (linea == null) break;
                linea = linea.trim();
                if (linea.isEmpty()) continue;
                if (linea.equalsIgnoreCase("salir")) {
                    try {
                        salida.writeUTF("**SE_DESCONECTA**");
                    } catch (IOException ex) {}
                    break;
                }

                salida.writeUTF(linea);
            }
        } catch (IOException e) {
            System.out.println("Error al leer del teclado o enviar: " + e.getMessage());
        } finally {
            try { salida.close(); } catch (IOException e) {}
        }
    }
}
