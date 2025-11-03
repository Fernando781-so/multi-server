package servidor.asincrono;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Clase que guarda estadísticas generales de cada jugador
 * y también estadísticas entre jugadores específicos.
 */
public class EstadisticasJugador implements Serializable {

    private int victorias;
    private int derrotas;
    private int empates;
    private int puntos;

    // Registro de enfrentamientos directos: clave = nombre del otro jugador
    private final Map<String, Enfrentamiento> enfrentamientos = new HashMap<>();

    public EstadisticasJugador() {
        this.victorias = 0;
        this.derrotas = 0;
        this.empates = 0;
        this.puntos = 0;
    }

    // ====== ESTADÍSTICAS GENERALES ======
    public void registrarVictoria() {
        victorias++;
        puntos += 2;
    }

    public void registrarDerrota() {
        derrotas++;
    }

    public void registrarEmpate() {
        empates++;
        puntos += 1;
    }

    public int getVictorias() {
        return victorias;
    }

    public int getDerrotas() {
        return derrotas;
    }

    public int getEmpates() {
        return empates;
    }

    public int getPuntos() {
        return puntos;
    }

    // ====== ESTADÍSTICAS ENTRE JUGADORES ======
    public void registrarContra(String oponente, String resultado) {
        Enfrentamiento e = enfrentamientos.computeIfAbsent(oponente, k -> new Enfrentamiento());
        switch (resultado) {
            case "victoria" -> e.victorias++;
            case "derrota" -> e.derrotas++;
            case "empate" -> e.empates++;
        }
    }

    public String getResumenContra(String jugador, String oponente) {
        Enfrentamiento e = enfrentamientos.get(oponente);
        if (e == null || (e.victorias + e.derrotas) == 0) {
            return "⚠️ No hay partidas registradas entre " + jugador + " y " + oponente;
        }

        int total = e.victorias + e.derrotas;
        double winRate = (100.0 * e.victorias) / total;

        return String.format(
            "📊 %s vs %s → %d victorias / %d derrotas / %d empates (%.1f%% win rate)",
            jugador, oponente, e.victorias, e.derrotas, e.empates, winRate
        );
    }

    // ====== CLASE INTERNA ======
    private static class Enfrentamiento implements Serializable {
        int victorias = 0;
        int derrotas = 0;
        int empates = 0;
    }

    @Override
    public String toString() {
        return String.format(
            "🏅 %d pts | %dV / %dE / %dD",
            puntos, victorias, empates, derrotas
        );
    }
}
