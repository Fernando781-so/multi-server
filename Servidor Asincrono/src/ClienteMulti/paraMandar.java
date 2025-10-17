package ClienteMulti;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Scanner;

public class paraMandar implements Runnable {

    private final DataOutputStream salida;
    private final Socket socket;

    public paraMandar(DataOutputStream salida, Socket socket) {
        this.salida = salida;
        this.socket = socket;
    }

    @Override
    public void run() {
        try (Scanner sc = new Scanner(System.in)) {
            try {
                System.out.println("""
                        📜 Bienvenido al chat asíncrono.
                        Escribe 'ayuda' para ver los comandos disponibles.
                        ────────────────────────────────
                        """);

                while (true) {
                    String msg = sc.nextLine().trim();
                    if (msg.isEmpty()) continue;

                    salida.writeUTF(msg);

                    if (msg.equalsIgnoreCase("salir")) {
                        System.out.println("👋 Saliendo del chat...");
                        break;
                    }
                }
            } catch (IOException e) {
                System.out.println("⚠️ Error al enviar mensaje: " + e.getMessage());
            } finally {
                try {
                    socket.close();
                } catch (IOException ignored) {}
                System.out.println("🔴 Conexión cerrada.");
            }
        }
    }
}

