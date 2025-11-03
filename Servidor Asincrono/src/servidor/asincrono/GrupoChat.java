package servidor.asincrono;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class GrupoChat {
    private final String nombre;
    private final Set<String> miembros = Collections.synchronizedSet(new LinkedHashSet<>());
    private final Queue<String> mensajes = new ConcurrentLinkedQueue<>();

    public GrupoChat(String nombre) { this.nombre = nombre; }

    public String getNombre() { return nombre; }

    public void unir(String usuario) { miembros.add(usuario); }

    public void salir(String usuario) { miembros.remove(usuario); }

    public boolean estaVacio() { return miembros.isEmpty(); }

    public boolean contiene(String usuario) { return miembros.contains(usuario); }

    public void agregarMensaje(String msg) {
        mensajes.add(msg);
        if (mensajes.size() > 200) mensajes.poll(); 
    }

    public List<String> obtenerMensajesNoVistos() {
        return new ArrayList<>(mensajes);
    }

    public Set<String> getMiembros() { return new LinkedHashSet<>(miembros); }
}

