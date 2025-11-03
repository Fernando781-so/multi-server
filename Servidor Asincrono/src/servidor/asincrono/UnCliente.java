package servidor.asincrono;

import java.io.*;
import java.net.*;
import java.util.*;

public final class UnCliente extends Thread {
    Socket socket;
    DataInputStream entrada;
    DataOutputStream salida;

    String nombre;                
    boolean esAnonimo = true;      
    int mensajesAnonimos = 0;

    // bloqueo y grupos
    final Set<String> bloqueados = Collections.synchronizedSet(new HashSet<>());

    // partida
    boolean enPartida = false;
    String rival = null;

    // grupos
    String grupoActual = "Todos";
    int ultimoIndiceLeido = 0; 

    public UnCliente(Socket s) {
        this.socket = s;
        try {
            entrada = new DataInputStream(s.getInputStream());
            salida = new DataOutputStream(s.getOutputStream());
            // initial anonymous name assigned; client may send /login immediately
            this.nombre = "Invitado_" + new Random().nextInt(10000);
            enviar("👋 Bienvenido. Has entrado como '" + nombre + "' (invitado). Puedes enviar 3 mensajes. Usa /login <nombre> para registrarte.");
            // unir al grupo Todos
            ServidorAsincrono.Grupos.get("Todos").unir(nombre);
            // register client under temporary key (so other code can find by this.nombre)
            ServidorAsincrono.Clientes.put(nombre, this);
        } catch (IOException e) {
            System.out.println("Error al crear cliente: " + e.getMessage());
        }
    }

    // enviar mensaje al socket
    public synchronized void enviar(String m) {
        try {
            salida.writeUTF(m);
        } catch (IOException e) {
            // si falla al escribir, cerrar conexión (sera manejado en run)
        }
    }

    @Override
    public void run() {
        try {
            while (true) {
                String linea = entrada.readUTF();
                if (linea == null) break;
                linea = linea.trim();
                if (linea.isEmpty()) continue;

                if (linea.startsWith("/")) {
                    procesarComando(linea);
                } else {
                    // mensaje normal: comprobar invitado y límites
                    if (esAnonimo) {
                        mensajesAnonimos++;
                        if (mensajesAnonimos > 3) {
                            enviar("🚫 Límite de 3 mensajes alcanzado para invitados. Usa /login <nombre> para seguir.");
                            continue;
                        }
                        // invitados solo en Todos
                        if (!"Todos".equals(grupoActual)) {
                            enviar("🚫 Invitados solo pueden estar en 'Todos'. Cambiando a 'Todos'.");
                            cambiarGrupoInterno("Todos");
                        }
                        enviarAGrupo(nombre + " (Invitado): " + linea);
                    } else {
                        enviarAGrupo(nombre + ": " + linea);
                    }
                }
            }
        } catch (IOException e) {
            // desconexión del socket (puede pasar en medio de partida)
            System.out.println("Desconexión de " + nombre);
            manejarDesconexion();
        } finally {
            cerrar();
        }
    }

    private void procesarComando(String linea) {
        String[] partes = linea.split(" ", 2);
        String cmd = partes[0].toLowerCase();

        try {
            switch (cmd) {
              case "/LOGIN" -> {
                    if (this.nombre != null && !this.nombre.startsWith("Invitado")) {
                        salida.writeUTF("⚠️ Ya has iniciado sesión como " + this.nombre);
                        return;
                    }

                    if (partes.length < 2) {
                        salida.writeUTF("Uso: /login <usuario>");
                        return;
                    }

                    String nuevoNombre = partes[1].trim();

                    if (ServidorAsincrono.Clientes.containsKey(nuevoNombre)) {
                        salida.writeUTF("❌ Ese nombre ya está en uso.");
                        return;
                    }

                    synchronized (ServidorAsincrono.Clientes) {
                        ServidorAsincrono.Clientes.remove(this.nombre);
                        ServidorAsincrono.Clientes.put(nuevoNombre, this);
                    }

                    ServidorAsincrono.Ranking.putIfAbsent(nuevoNombre, new EstadisticasJugador());

                    salida.writeUTF("✅ Sesión iniciada como: " + nuevoNombre);
                    System.out.println("🔑 " + this.nombre + " ahora es " + nuevoNombre);

                    // Actualizar nombre interno
                    try {
                        java.lang.reflect.Field f = this.getClass().getDeclaredField("nombre");
                        f.setAccessible(true);
                        f.set(this, nuevoNombre);
                    } catch (Exception ignored) {}
                }

                case "/salir" -> {
                    if (enPartida) {
                        enviar("🚫 No puedes desconectarte durante una partida. Usa /rendirse si quieres abandonar.");
                        return;
                    }
                    enviar("👋 Desconexando...");
                    cerrar();
                }

                case "/ayuda" -> enviar(ServidorAsincrono.ayuda());

                case "/grupos" -> {
                    StringBuilder sb = new StringBuilder("📚 Grupos disponibles:\n");
                    for (String g : ServidorAsincrono.Grupos.keySet()) sb.append("- ").append(g).append("\n");
                    enviar(sb.toString());
                }

                case "/creargrupo" -> {
                    if (esAnonimo) { enviar("🚫 Invitados no pueden crear grupos."); return; }
                    if (partes.length < 2) { enviar("Uso: /creargrupo <nombre>"); return; }
                    String g = partes[1].trim();
                    if (g.equalsIgnoreCase("Todos")) { enviar("🚫 No se puede crear 'Todos'."); return; }
                    ServidorAsincrono.Grupos.putIfAbsent(g, new GrupoChat(g));
                    enviar("✅ Grupo '" + g + "' creado (o ya existía).");
                }

                case "/unir" -> {
                    if (partes.length < 2) { enviar("Uso: /unir <grupo>"); return; }
                    String g = partes[1].trim();
                    GrupoChat grupo = ServidorAsincrono.Grupos.get(g);
                    if (grupo == null) { enviar("❌ Grupo no existe."); return; }
                    if (esAnonimo && !"Todos".equals(g)) { enviar("🚫 Invitados solo pueden unirse a 'Todos'."); return; }
                    grupo.unir(nombre);
                    grupoActual = g;
                    enviar("📥 Te has unido a '" + g + "'. Mostrando últimos mensajes:");
                    for (String msg : grupo.obtenerMensajesNoVistos()) enviar("[Historial] " + msg);
                }

                case "/salirgrupo" -> {
                    if ("Todos".equals(grupoActual)) { enviar("🚫 No puedes salir de 'Todos'."); return; }
                    GrupoChat old = ServidorAsincrono.Grupos.get(grupoActual);
                    if (old != null) old.salir(nombre);
                    grupoActual = "Todos";
                    ServidorAsincrono.Grupos.get("Todos").unir(nombre);
                    enviar("↩️ Has vuelto al grupo 'Todos'.");
                }

                case "/borrargrupo" -> {
                    if (esAnonimo) { enviar("🚫 Invitados no pueden borrar grupos."); return; }
                    if (partes.length < 2) { enviar("Uso: /borrargrupo <nombre>"); return; }
                    String g = partes[1].trim();
                    if (g.equalsIgnoreCase("Todos")) { enviar("🚫 No se puede borrar 'Todos'."); return; }
                    GrupoChat grupo = ServidorAsincrono.Grupos.get(g);
                    if (grupo == null) { enviar("❌ No existe."); return; }
                    if (!grupo.estaVacio()) { enviar("⚠️ No se puede borrar: aún tiene miembros."); return; }
                    ServidorAsincrono.Grupos.remove(g);
                    enviar("🗑️ Grupo '" + g + "' borrado.");
                }

                case "/miembros" -> {
                    GrupoChat grupo = ServidorAsincrono.Grupos.get(grupoActual);
                    if (grupo == null) { enviar("❌ Grupo actual inválido."); return; }
                    enviar("👥 Miembros de '" + grupoActual + "': " + grupo.getMiembros());
                }

                case "/bloquear" -> {
                    if (partes.length < 2) { enviar("Uso: /bloquear <usuario>"); return; }
                    String u = partes[1].trim();
                    if (u.equals(nombre)) { enviar("❌ No puedes bloquearte a ti mismo."); return; }
                    UnCliente objetivo = ServidorAsincrono.Clientes.get(u);
                    if (objetivo == null) { enviar("❌ Usuario no encontrado."); return; }
                    // no bloquear rival en partida
                    if (enPartida && u.equals(rival)) { enviar("🚫 No puedes bloquear a tu rival durante la partida."); return; }
                    bloqueados.add(u);
                    enviar("🚫 Has bloqueado a " + u);
                }

                case "/desbloquear" -> {
                    if (partes.length < 2) { enviar("Uso: /desbloquear <usuario>"); return; }
                    String u = partes[1].trim();
                    if (!bloqueados.remove(u)) { enviar("⚠️ Ese usuario no estaba bloqueado."); return; }
                    enviar("✅ Usuario " + u + " desbloqueado.");
                }

                case "/jugar" -> {
                    if (esAnonimo) { enviar("🚫 Invitados no pueden jugar."); return; }
                    if (enPartida) { enviar("🚫 Ya estás en una partida."); return; }
                    if (partes.length < 2) { enviar("Uso: /jugar <usuario>"); return; }
                    String o = partes[1].trim();
                    if (o.equals(nombre)) { enviar("⚠️ No puedes jugar contra ti mismo."); return; }
                    UnCliente oponente = ServidorAsincrono.Clientes.get(o);
                    if (oponente == null) { enviar("❌ Usuario no conectado."); return; }
                    if (oponente.esAnonimo) { enviar("🚫 No puedes jugar con invitados."); return; }
                    if (oponente.enPartida) { enviar("⚠️ El usuario ya está en partida."); return; }
                    // si bloqueado mutuo?
                    if (bloqueados.contains(o) || oponente.bloqueados.contains(nombre)) { enviar("🚫 No puedes jugar: bloqueo presente."); return; }
                    // enviar solicitud
                    ServidorAsincrono.SolicitudesPendientes.put(o, nombre);
                    oponente.enviar("🎮 " + nombre + " te ha retado. Para aceptar: /aceptar " + nombre);
                    enviar("✅ Solicitud enviada a " + o);
                }

                case "/aceptar" -> {
                    if (partes.length < 2) { enviar("Uso: /aceptar <usuario>"); return; }
                    String who = partes[1].trim();
                    String pendiente = ServidorAsincrono.SolicitudesPendientes.get(nombre);
                    if (pendiente == null || !pendiente.equals(who)) { enviar("⚠️ No tienes invitación de " + who); return; }
                    UnCliente retador = ServidorAsincrono.Clientes.get(who);
                    if (retador == null) { enviar("❌ El retador ya no está disponible."); return; }
                    // iniciar partida
                    ServidorAsincrono.iniciarPartida(retador, this);
                    ServidorAsincrono.SolicitudesPendientes.remove(nombre);
                }

                case "/mover" -> {
                    if (partes.length < 2) { enviar("Uso: /mover <1-9>"); return; }
                    try {
                        int pos = Integer.parseInt(partes[1].trim());
                        ServidorAsincrono.mover(nombre, pos);
                    } catch (NumberFormatException ex) {
                        enviar("❌ Posición inválida. Usa 1-9.");
                    }
                }

                case "/rendirse" -> {
                    if (!enPartida) { enviar("⚠️ No estás en partida."); return; }
                    ServidorAsincrono.rendirse(nombre);
                }

                case "/ranking" -> {
                    if (enPartida) { enviar("🚫 No puedes ver ranking durante una partida."); return; }
                    enviar(ServidorAsincrono.obtenerRanking());
                }

                case "/versus" -> {
                    if (partes.length < 2) { enviar("Uso: /versus <jugador1> <jugador2>"); return; }
                    String[] p = partes[1].trim().split(" ");
                    if (p.length < 2) { enviar("Uso: /versus <jugador1> <jugador2>"); return; }
                    if (enPartida) { enviar("🚫 No puedes consultar versus durante una partida."); return; }
                    enviar(ServidorAsincrono.obtenerVs(p[0], p[1]));
                }

                default -> enviar("❓ Comando no reconocido. Usa /ayuda para ver opciones.");
            }
        } catch (Exception e) {
            enviar("⚠️ Error procesando comando: " + e.getMessage());
        }
    }

    private void enviarAGrupo(String mensaje) {
        GrupoChat g = ServidorAsincrono.Grupos.get(grupoActual);
        if (g == null) return;
        g.agregarMensaje(mensaje);
        for (String miembro : g.getMiembros()) {
            if (miembro.equals(nombre)) continue;
            UnCliente c = ServidorAsincrono.Clientes.get(miembro);
            if (c == null) continue;
            // si receptor bloqueó al emisor, no recibir
            if (c.bloqueados.contains(this.nombre)) continue;
            c.enviar("[" + grupoActual + "] " + mensaje);
        }
    }

    private void cambiarGrupoInterno(String nuevo) {
        GrupoChat old = ServidorAsincrono.Grupos.get(grupoActual);
        if (old != null) old.salir(nombre);
        grupoActual = nuevo;
        GrupoChat g = ServidorAsincrono.Grupos.get(nuevo);
        if (g != null) g.unir(nombre);
    }

    // Manejar desconexión inesperada: si estaba en partida, otorgar victoria al otro
    private void manejarDesconexion() {
        try {
            if (enPartida && rival != null) {
                UnCliente oponente = ServidorAsincrono.Clientes.get(rival);
                if (oponente != null) {
                    oponente.enviar("⚠️ Tu rival (" + nombre + ") se desconectó. Ganas automáticamente.");
                    // actualizar ranking y versus
                    ServidorAsincrono.registrarResultado(oponente.nombre, this.nombre, "gana1");
                }
                String clave = JuegoGato.clave(nombre, rival);
                ServidorAsincrono.Partidas.remove(clave);
            }
        } catch (Exception ignored) {}
    }

    // cerrar y limpiar
    private void cerrar() {
        try {
            // salir de grupo
            GrupoChat g = ServidorAsincrono.Grupos.get(grupoActual);
            if (g != null) g.salir(nombre);
            // remover cliente
            ServidorAsincrono.Clientes.remove(nombre);
            socket.close();
        } catch (IOException ignored) {}
    }
}
