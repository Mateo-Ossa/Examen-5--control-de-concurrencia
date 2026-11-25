package monedas.api.aplicacion.servicios;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Variante C: operacion atomica lock-free con AtomicLong (CAS), igual enfoque
 * que CubetaTokens. Es la implementacion que Spring inyecta en UsuarioServicio.
 */
@Component
@Primary
public class ContadorIntentosAtomico implements IContadorIntentosLogin {

    private final AtomicLong contador = new AtomicLong();

    @Override
    public void registrarIntento() {
        contador.incrementAndGet();
    }

    @Override
    public long obtenerTotalIntentos() {
        return contador.get();
    }

    @Override
    public void reiniciar() {
        contador.set(0);
    }

    @Override
    public String nombreEstrategia() {
        return "C - AtomicLong";
    }
}
