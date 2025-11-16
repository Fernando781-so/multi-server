package servidor.asincrono;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Guarda estadísticas generales por jugador y enfrentamientos directos.
 */
public class EstadisticasJugador implements Serializable {

    private int victorias;
    private int derrotas;
    private int empates;
    private int puntos;

    // clave = nombre oponente
    private final Map<String, Enfrentamiento> enfrentamientos = new HashMap<>();

    public EstadisticasJugador() {
        this.victorias = 0;
        this.derrotas = 0;
        this.empates = 0;
        this.puntos = 0;
    }

    // getters / setters (necesarios para cargar desde BD)
    public int getVictorias() { return victorias; }
    public int getDerrotas() { return derrotas; }
    public int getEmpates() { return empates; }
    public int getPuntos() { return puntos; }

    public void setVictorias(int v) { victorias = v; }
    public void setDerrotas(int d) { derrotas = d; }
    public void setEmpates(int e) { empates = e; }
    public void setPuntos(int p) { puntos = p; }

    public void registrarVictoria() { victorias++; puntos += 2; }
    public void registrarDerrota() { derrotas++; }
    public void registrarEmpate() { empates++; puntos += 1; }

    // registrar resultado contra un oponente (para 'versus')
    public void registrarContra(String oponente, String resultado) {
        Enfrentamiento e = enfrentamientos.computeIfAbsent(oponente, k -> new Enfrentamiento());
        switch (resultado) {
            case "victoria" -> e.victorias++;
            case "derrota" -> e.derrotas++;
            case "empate" -> e.empates++;
        }
    }

    // cargar una entrada de vs desde BD
    public void cargarContra(String oponente, int victoriasO, int victoriasD, int empatesO) {
        Enfrentamiento e = enfrentamientos.computeIfAbsent(oponente, k -> new Enfrentamiento());
        e.victorias = victoriasO;
        e.derrotas = victoriasD;
        e.empates = empatesO;
    }

    // obtener resumen versus (para comando /versus)
    public String getResumenContra(String jugador, String oponente) {
        Enfrentamiento e = enfrentamientos.get(oponente);
        if (e == null || (e.victorias + e.derrotas) == 0) {
            return "⚠️ No hay partidas registradas entre " + jugador + " y " + oponente;
        }
        int total = e.victorias + e.derrotas;
        double winRate = (100.0 * e.victorias) / total;
        return String.format("📊 %s vs %s → %d victorias / %d derrotas / %d empates (%.1f%% win rate)",
                jugador, oponente, e.victorias, e.derrotas, e.empates, winRate);
    }

    // Exponer enfrentamiento para guardado en BD
    public Enfrentamiento getEnfrentamiento(String oponente) { return enfrentamientos.get(oponente); }
    public Map<String, Enfrentamiento> getEnfrentamientos() { return enfrentamientos; }

    // Clase interna pública para que BaseDeDatos la use
    public static class Enfrentamiento implements Serializable {
        public int victorias = 0;
        public int derrotas = 0;
        public int empates = 0;
    }

    @Override
    public String toString() {
        return String.format("🏅 %d pts | %dV / %dE / %dD", puntos, victorias, empates, derrotas);
    }
}
