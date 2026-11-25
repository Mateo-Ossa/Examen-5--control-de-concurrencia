package monedas.api.aplicacion.servicios;

/**
 * Variante A: SIN control de concurrencia.
 * contador++ no es atomico (leer -> sumar -> escribir), por lo que varios hilos
 * pueden leer el mismo valor y pisarse, perdiendo incrementos.
 * No es un bean de Spring: solo se usa en la prueba de concurrencia.
 */
public class ContadorIntentosSinControl implements IContadorIntentosLogin {

    private long contador = 0;

    @Override
    public void registrarIntento() {
        contador++;
    }

    @Override
    public long obtenerTotalIntentos() {
        return contador;
    }

    @Override
    public void reiniciar() {
        contador = 0;
    }

    @Override
    public String nombreEstrategia() {
        return "A - Sin control (long)";
    }
}
