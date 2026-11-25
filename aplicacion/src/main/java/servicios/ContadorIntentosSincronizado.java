package monedas.api.aplicacion.servicios;

/**
 * Variante B: exclusion mutua con synchronized.
 * Escritura y lectura usan el mismo cerrojo (this), garantizando atomicidad
 * y visibilidad entre hilos.
 * No es un bean de Spring: solo se usa en la prueba de concurrencia.
 */
public class ContadorIntentosSincronizado implements IContadorIntentosLogin {

    private long contador = 0;

    @Override
    public synchronized void registrarIntento() {
        contador++;
    }

    @Override
    public synchronized long obtenerTotalIntentos() {
        return contador;
    }

    @Override
    public synchronized void reiniciar() {
        contador = 0;
    }

    @Override
    public String nombreEstrategia() {
        return "B - synchronized";
    }
}
