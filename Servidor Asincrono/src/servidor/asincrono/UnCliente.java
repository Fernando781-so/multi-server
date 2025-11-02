package servidor.asincrono;

import java.io.*;
import java.net.*;
import java.util.*;

public class UnCliente implements Runnable {

    final Socket socket;
    final DataInputStream entrada;
    final DataOutputStream salida;
    final String nombre;
    private final Set<String> bloqueados = new HashSet<>();
    private String rival = null;
    private boolean enPartida = false;

    private String grupoActual = "Todos";

    public UnCliente(Socket s, String nombre) throws IOException {
        this.socket = s;
        this.nombre = nombre;
        entrada = new DataInputStream(s.getInputStream());
        salida = new DataOutputStream(s.getOutputStream());
    }

    @Override
    public void run() {
        try {
            String mensaje;
            while (true) {
                mensaje = entrada.readUTF();
                if (mensaje == null) break;

                if (mensaje.startsWith("/")) {
                    procesarComando(mensaje);
                } else {
                    enviarAGrupoActual(nombre + ": " + mensaje);
                }
            }
        } catch (IOException e) {
            System.out.println("Cliente desconectado: " + nombre);
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
            synchronized (ServidorAsincrono.CLIENTE_LOCK) {
                ServidorAsincrono.Clientes.remove(nombre);
                ServidorAsincrono.Grupos.get(grupoActual).salir(nombre);
            }
        }
    }

    private void procesarComando(String mensaje) throws IOException {
        String[] partes = mensaje.split(" ");
        String comando = partes[0].toUpperCase();

        switch (comando) {

            case "/RANKING" -> {
                if (enPartida) {
                    salida.writeUTF("🚫 No puedes consultar el ranking mientras estás jugando.");
                    return;
                }
                salida.writeUTF(ServidorAsincrono.obtenerRanking());
            }

            case "/VERSUS" -> {
                if (enPartida) {
                    salida.writeUTF("🚫 No puedes consultar el winrate mientras estás jugando.");
                    return;
                }
                if (partes.length < 3) {
                    salida.writeUTF("Uso: /versus <jugador1> <jugador2>");
                    return;
                }
                salida.writeUTF(ServidorAsincrono.obtenerVs(partes[1], partes[2]));
            }

            // ==== GRUPOS ====
            case "/GRUPOS" -> {
                salida.writeUTF("📚 Grupos disponibles:");
                for (String g : ServidorAsincrono.Grupos.keySet()) {
                    salida.writeUTF("- " + g);
                }
            }

            case "/CREARGRUPO" -> {
                if (partes.length < 2) {
                    salida.writeUTF("Uso: /creargrupo <nombre>");
                    return;
                }
                String nuevo = partes[1];
                if (nuevo.equalsIgnoreCase("Todos")) {
                    salida.writeUTF("🚫 No puedes crear un grupo llamado 'Todos'.");
                    return;
                }
                ServidorAsincrono.Grupos.putIfAbsent(nuevo, new GrupoChat(nuevo));
                salida.writeUTF("✅ Grupo '" + nuevo + "' creado o ya existente.");
            }

            case "/UNIR" -> {
                if (partes.length < 2) {
                    salida.writeUTF("Uso: /unir <grupo>");
                    return;
                }
                String nombreGrupo = partes[1];
                GrupoChat grupo = ServidorAsincrono.Grupos.get(nombreGrupo);
                if (grupo == null) {
                    salida.writeUTF("❌ No existe ese grupo.");
                    return;
                }
                grupo.unir(nombre);
                grupoActual = nombreGrupo;
                salida.writeUTF("📥 Te uniste al grupo: " + nombreGrupo);
                for (String msg : grupo.obtenerMensajesNoVistos()) {
                    salida.writeUTF("[Historial] " + msg);
                }
            }

            case "/SALIRGRUPO" -> {
                if (grupoActual.equals("Todos")) {
                    salida.writeUTF("🚫 No puedes salir del grupo 'Todos'.");
                    return;
                }
                ServidorAsincrono.Grupos.get(grupoActual).salir(nombre);
                grupoActual = "Todos";
                salida.writeUTF("↩️ Has regresado al grupo 'Todos'.");
            }

            case "/BORRARGRUPO" -> {
                if (partes.length < 2) {
                    salida.writeUTF("Uso: /borrargrupo <nombre>");
                    return;
                }
                String g = partes[1];
                if (g.equalsIgnoreCase("Todos")) {
                    salida.writeUTF("🚫 No se puede borrar el grupo 'Todos'.");
                    return;
                }
                GrupoChat grupo = ServidorAsincrono.Grupos.get(g);
                if (grupo == null) {
                    salida.writeUTF("❌ No existe el grupo.");
                    return;
                }
                if (!grupo.estaVacio()) {
                    salida.writeUTF("⚠️ No se puede borrar, aún tiene miembros.");
                    return;
                }
                ServidorAsincrono.Grupos.remove(g);
                salida.writeUTF("🗑️ Grupo '" + g + "' eliminado.");
            }

            // === NUEVO: listar miembros del grupo actual ===
            case "/MIEMBROS" -> {
                GrupoChat grupo = ServidorAsincrono.Grupos.get(grupoActual);
                salida.writeUTF("👥 Miembros del grupo '" + grupoActual + "':");
                for (String m : grupo.getMiembros()) {
                    salida.writeUTF("• " + m);
                }
            }

            default -> salida.writeUTF("Comando no reconocido.");
        }
    }

    private void enviarAGrupoActual(String msg) throws IOException {
        GrupoChat grupo = ServidorAsincrono.Grupos.get(grupoActual);
        grupo.agregarMensaje(msg);
        for (String usuario : grupo.getMiembros()) {
            if (!usuario.equals(nombre)) {
                UnCliente c = ServidorAsincrono.Clientes.get(usuario);
                if (c != null) c.salida.writeUTF(msg);
            }
        }
    }
}
