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

    final Set<String> bloqueados = Collections.synchronizedSet(new HashSet<>());
    final Set<String> rivalesActivos = Collections.synchronizedSet(new HashSet<>());

    String grupoActual = "Todos";

    public UnCliente(Socket s) {
        this.socket = s;
        try {
            entrada = new DataInputStream(s.getInputStream());
            salida = new DataOutputStream(s.getOutputStream());
            this.nombre = "Invitado_" + new Random().nextInt(10000);
            enviar("👋 Bienvenido " + nombre + ". Puedes usar 3 mensajes antes de /login <nombre>.\nEscribe /ayuda para comandos.");
            ServidorAsincrono.Grupos.putIfAbsent("Todos", new GrupoChat("Todos"));
            ((GrupoChat) ServidorAsincrono.Grupos.get("Todos")).unir(nombre);
            ServidorAsincrono.Clientes.put(nombre, this);
        } catch (IOException e) {
            System.out.println("Error al crear cliente: " + e.getMessage());
        }
    }

    public synchronized void enviar(String m) {
        try {
            salida.writeUTF(m);
        } catch (IOException e) {
            // si no se puede enviar, cerramos
            cerrar();
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
                if (linea.startsWith("/")) procesarComando(linea);
                else {
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
                    // Persistir mensaje en BD (si no es vacío)
                    BaseDatos.guardarMensaje(nombre, linea);
                }
            }
        } catch (IOException e) {
            System.out.println("Desconexión de " + nombre);
            manejarDesconexion();
        } finally {
            cerrar();
        }
    }

    private void procesarComando(String linea) {
        String[] partes = linea.split(" ", 3);
        String cmd = partes[0].toLowerCase();

        try {
            switch (cmd) {
                case "/login" -> {
                    if (this.nombre != null && !this.nombre.startsWith("Invitado")) {
                        enviar("⚠️ Ya has iniciado sesión como " + this.nombre);
                        return;
                    }
                    if (partes.length < 2) { enviar("Uso: /login <usuario>"); return; }
                    String nuevoNombre = partes[1].trim();
                    if (ServidorAsincrono.Clientes.containsKey(nuevoNombre)) { enviar("❌ Ese nombre ya está en uso."); return; }

                    synchronized (ServidorAsincrono.class) {
                        ServidorAsincrono.Clientes.remove(this.nombre);
                        ServidorAsincrono.Clientes.put(nuevoNombre, this);
                    }

                    BaseDatos.asegurarJugador(nuevoNombre);
                    ServidorAsincrono.Ranking.putIfAbsent(nuevoNombre, new EstadisticasJugador());

                    this.esAnonimo = false;
                    this.mensajesAnonimos = 0;

                    enviar("✅ Sesión iniciada como: " + nuevoNombre);
                    System.out.println("🔑 Usuario '" + this.nombre + "' ahora es '" + nuevoNombre + "'");
                    try {
                        java.lang.reflect.Field f = this.getClass().getDeclaredField("nombre");
                        f.setAccessible(true);
                        f.set(this, nuevoNombre);
                    } catch (Exception ignored) {}

                    ServidorAsincrono.Grupos.putIfAbsent("Todos", new GrupoChat("Todos"));
                    GrupoChat grupoTodos = ServidorAsincrono.Grupos.get("Todos");
                    if (!grupoTodos.getMiembros().contains(nuevoNombre)) grupoTodos.unir(nuevoNombre);

                    this.grupoActual = "Todos";
                    enviar("📥 Te has unido al grupo 'Todos'. Mostrando últimos mensajes:");
                    for (String msg : grupoTodos.obtenerMensajesNoVistos()) enviar("[Historial] " + msg);
                }

                case "/conectados" -> {
                    StringBuilder sb = new StringBuilder("🟢 Usuarios conectados y sus grupos:\n");
                    for (Map.Entry<String, UnCliente> entry : ServidorAsincrono.Clientes.entrySet()) {
                        UnCliente c = entry.getValue();
                        sb.append("- ").append(c.nombre)
                            .append(" => Grupo: ").append(c.grupoActual)
                            .append(c.esAnonimo ? " (Invitado)" : "")
                            .append("\n");
                    }
                    enviar(sb.toString());
                }

                case "/desconectar" -> {
                    boolean enPartida = ServidorAsincrono.Partidas.values().stream()
                            .anyMatch(j -> j.contieneJugador(nombre));
                    if (enPartida) { enviar("🚫 No puedes desconectarte durante una partida. Usa /rendirse."); return; }
                    enviar("👋 Desconectando...");
                    cerrar();
                }

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
                BaseDatos.guardarGrupo(g);

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
                            BaseDatos.borrarGrupo(g);

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
                    // comprobar partidas activas entre ambos
                    for (JuegoGato partida : ServidorAsincrono.Partidas.values()) {
                        if (partida.contieneJugador(nombre) && partida.contieneJugador(u)) {
                            enviar("🚫 No puedes bloquear a tu rival mientras están jugando una partida.");
                            return;
                        }
                    }
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
                    if (partes.length < 2) { enviar("Uso: /jugar <usuario>"); return; }
                    String o = partes[1].trim();
                    if (o.equals(nombre)) { enviar("⚠️ No puedes jugar contigo mismo."); return; }
                    UnCliente oponente = ServidorAsincrono.Clientes.get(o);
                    if (oponente == null) { enviar("❌ Usuario no encontrado."); return; }
                    if (rivalesActivos.contains(o)) { enviar("⚠️ Ya tienes una partida activa con " + o); return; }
                    ServidorAsincrono.SolicitudesPendientes.put(o, nombre);
                    oponente.enviar("🎮 " + nombre + " te ha retado. Usa /aceptar " + nombre + " para comenzar.");
                    enviar("✅ Solicitud enviada a " + o);
                }

                case "/aceptar" -> {
                    if (partes.length < 2) { enviar("Uso: /aceptar <usuario>"); return; }
                    String who = partes[1].trim();
                    String pendiente = ServidorAsincrono.SolicitudesPendientes.get(nombre);
                    if (pendiente == null || !pendiente.equals(who)) { enviar("⚠️ No tienes invitación de " + who); return; }
                    UnCliente retador = ServidorAsincrono.Clientes.get(who);
                    if (retador == null) { enviar("❌ El retador ya no está disponible."); return; }

                    rivalesActivos.add(who);
                    retador.rivalesActivos.add(nombre);
                    ServidorAsincrono.iniciarPartida(retador, this);
                    ServidorAsincrono.SolicitudesPendientes.remove(nombre);
                }

                case "/mover" -> {
                    if (partes.length < 3) { enviar("Uso: /mover <usuario> <1-9>"); return; }
                    String rival = partes[1];
                    int pos = Integer.parseInt(partes[2]);
                    ServidorAsincrono.mover(nombre, rival, pos);
                }

                case "/rendirse" -> {
                    if (partes.length < 2) { enviar("Uso: /rendirse <usuario>"); return; }
                    String rival = partes[1].trim();
                    ServidorAsincrono.rendirse(nombre, rival);
                }

                case "/ranking" -> enviar(ServidorAsincrono.obtenerRanking());

                case "/versus" -> {
                    if (partes.length < 3) { enviar("Uso: /versus <jugador1> <jugador2>"); return; }
                    enviar(ServidorAsincrono.obtenerVs(partes[1], partes[2]));
                }

                case "/ayuda" -> enviar(ServidorAsincrono.ayuda());

                default -> enviar("❓ Comando desconocido. Usa /ayuda.");
            }
        } catch (Exception e) {
            enviar("⚠️ Error: " + e.getMessage());
        }
    }

    // enviar a todos en el grupo actual, pero NO enviarte a ti mismo
    private void enviarAGrupo(String mensaje) {
        GrupoChat g = ServidorAsincrono.Grupos.get(grupoActual);
        if (g == null) return;
        g.agregarMensaje(mensaje);
        for (String miembro : g.getMiembros()) {
            if (miembro.equals(nombre)) continue;  // no enviarte a ti mismo
            UnCliente c = ServidorAsincrono.Clientes.get(miembro);
            if (c == null) continue;
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
        ServidorAsincrono.GruposUsuarios.put(nombre, nuevo);
    }

    private void manejarDesconexion() {
        ServidorAsincrono.finalizarPartidaPorDesconexion(nombre);
    }

    private void cerrar() {
        try {
            GrupoChat g = ServidorAsincrono.Grupos.get(grupoActual);
            if (g != null) g.salir(nombre);
            ServidorAsincrono.Clientes.remove(nombre);
            socket.close();
        } catch (IOException ignored) {}
    }
}



