package servidor.asincrono;

import java.io.*;
import java.net.*;
import java.util.*;

public class ServidorAsincrono {

    public static final Object CLIENTE_LOCK = new Object();
    static HashMap<String, UnCliente> Clientes = new HashMap<>();
    static HashMap<String, String> UsuariosRegistrados = new HashMap<>(); // nombre → contraseña
    static HashMap<String, Integer> Puntos = new HashMap<>(); // nombre → puntos
    static List<Partida> HistorialPartidas = new ArrayList<>();

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(8080)) {
            System.out.println("Servidor iniciado en el puerto 8080");

            while (true) {
                Socket socket = serverSocket.accept();
                DataInputStream entrada = new DataInputStream(socket.getInputStream());
                DataOutputStream salida = new DataOutputStream(socket.getOutputStream());

                salida.writeUTF("Bienvenido al servidor.\nUsa: REGISTRAR <usuario> <contraseña> o LOGIN <usuario> <contraseña>");
                String linea = entrada.readUTF();
                String[] partes = linea.split(" ");
                String nombre = null;

                if (partes.length == 3) {
                    String comando = partes[0].toUpperCase();
                    String user = partes[1];
                    String pass = partes[2];

                    synchronized (CLIENTE_LOCK) {
                        if (comando.equals("REGISTRAR")) {
                            if (UsuariosRegistrados.containsKey(user)) {
                                salida.writeUTF("Usuario ya existe.");
                            } else {
                                UsuariosRegistrados.put(user, pass);
                                Puntos.put(user, 0);
                                salida.writeUTF("Usuario registrado con éxito. Usa LOGIN para entrar.");
                            }
                        } else if (comando.equals("LOGIN")) {
                            if (UsuariosRegistrados.containsKey(user) && UsuariosRegistrados.get(user).equals(pass)) {
                                nombre = user;
                                salida.writeUTF("Inicio de sesión correcto. Bienvenido, " + user);
                            } else {
                                salida.writeUTF("Credenciales incorrectas. Solo podrás observar mensajes.");
                            }
                        }
                    }
                }

                UnCliente nuevoCliente = new UnCliente(socket, nombre);
                synchronized (CLIENTE_LOCK) {
                    Clientes.put(socket.getRemoteSocketAddress().toString(), nuevoCliente);
                }

                new Thread(nuevoCliente).start();
                System.out.println("Cliente conectado: " + socket.getRemoteSocketAddress());
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ==================== MÉTODOS AUXILIARES ====================

    public static void registrarResultado(String jugador1, String jugador2, String resultado) {
        synchronized (CLIENTE_LOCK) {
            int p1 = Puntos.getOrDefault(jugador1, 0);
            int p2 = Puntos.getOrDefault(jugador2, 0);

            switch (resultado.toLowerCase()) {
                case "empate" -> {
                    p1 += 1;
                    p2 += 1;
                }
                case "gana1" -> p1 += 2;
                case "gana2" -> p2 += 2;
            }

            Puntos.put(jugador1, p1);
            Puntos.put(jugador2, p2);
            HistorialPartidas.add(new Partida(jugador1, jugador2, resultado));
        }
    }

    public static String obtenerRanking() {
        StringBuilder sb = new StringBuilder("🏆 Ranking de jugadores 🏆\n");
        synchronized (CLIENTE_LOCK) {
            List<Map.Entry<String, Integer>> lista = new ArrayList<>(Puntos.entrySet());
            lista.sort((a, b) -> b.getValue() - a.getValue());
            int pos = 1;
            for (var e : lista) {
                sb.append(pos++).append(". ").append(e.getKey()).append(" - ").append(e.getValue()).append(" pts\n");
            }
        }
        return sb.toString();
    }

    public static String obtenerVs(String j1, String j2) {
        int total = 0, gana1 = 0, gana2 = 0, empates = 0;

        synchronized (CLIENTE_LOCK) {
            for (Partida p : HistorialPartidas) {
                if ((p.j1.equals(j1) && p.j2.equals(j2)) || (p.j1.equals(j2) && p.j2.equals(j1))) {
                    total++;
                    if (p.resultado.equals("gana1") && p.j1.equals(j1)) gana1++;
                    else if (p.resultado.equals("gana2") && p.j2.equals(j1)) gana1++;
                    else if (p.resultado.equals("gana1") && p.j1.equals(j2)) gana2++;
                    else if (p.resultado.equals("gana2") && p.j2.equals(j2)) gana2++;
                    else empates++;
                }
            }
        }

        if (total == 0) return "No hay partidas entre " + j1 + " y " + j2 + ".";

        double porc1 = (gana1 * 100.0) / total;
        double porc2 = (gana2 * 100.0) / total;
        return String.format("Resultados entre %s y %s:\n%s: %.1f%% victorias\n%s: %.1f%% victorias\nEmpates: %d",
                j1, j2, j1, porc1, j2, porc2, empates);
    }
}
