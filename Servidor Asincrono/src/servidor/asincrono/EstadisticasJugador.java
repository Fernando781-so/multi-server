package servidor.asincrono;

public class EstadisticasJugador {
    private final String nombre;
    private int victorias = 0;
    private int derrotas = 0;
    private int empates = 0;
    private int puntos = 0;

    public EstadisticasJugador(String nombre) {
        this.nombre = nombre;
    }

    public synchronized void registrarVictoria() {
        victorias++;
        puntos += 2;
    }

    public synchronized void registrarDerrota() {
        derrotas++;
    }

    public synchronized void registrarEmpate() {
        empates++;
        puntos += 1;
    }

    public String getNombre() {
        return nombre;
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

    @Override
    public String toString() {
        return String.format("%-10s | 🏆 %d | ❌ %d | 🤝 %d | ⭐ %d pts",
                nombre, victorias, derrotas, empates, puntos);
    }
}
