package monedas.api.presentacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import monedas.api.aplicacion.seguridad.SeguridadServicio;
import monedas.api.aplicacion.servicios.ContadorIntentosAtomico;
import monedas.api.aplicacion.servicios.ContadorIntentosSincronizado;
import monedas.api.aplicacion.servicios.ContadorIntentosSinControl;
import monedas.api.aplicacion.servicios.IContadorIntentosLogin;
import monedas.api.aplicacion.servicios.UsuarioServicio;
import monedas.api.core.repositorios.IUsuarioRepositorio;
import monedas.api.dominio.entidades.Usuario;

/**
 * Rafaga de N ejecuciones concurrentes de UsuarioServicio.login(...) por cada variante
 * del contador de intentos (A sin control, B synchronized, C AtomicLong).
 */
class PruebaContadorIntentosLogin {

    /** Tamanos de rafaga; se pueden cambiar con -Drafagas=500,1000,2000 */
    private static final int[] RAFAGAS = java.util.Arrays
            .stream(System.getProperty("rafagas", "500,1000,2000").split(","))
            .map(String::trim).mapToInt(Integer::parseInt).toArray();
    private static final int HILOS = 200;

    /** Repositorio de prueba: no toca la base de datos. */
    private static class RepositorioFalso implements IUsuarioRepositorio {
        public Usuario login(String u, String c) { return null; }
        public List<Usuario> listar() { return List.of(); }
        public Optional<Usuario> obtenerPorId(int id) { return Optional.empty(); }
        public Optional<Usuario> obtener(String u) { return Optional.empty(); }
        public List<Usuario> buscarPorNombre(String n) { return List.of(); }
        public Usuario guardar(Usuario u) { return u; }
        public boolean eliminar(int id) { return false; }
    }

    private record Resultado(String variante, int n, long obtenido, long tiempoMs, long errores) { }

    private Resultado ejecutarRafaga(IContadorIntentosLogin contador, int n) throws Exception {
        UsuarioServicio servicio = new UsuarioServicio();
        ReflectionTestUtils.setField(servicio, "repositorio", new RepositorioFalso());
        ReflectionTestUtils.setField(servicio, "servicioSeguridad", new SeguridadServicio());
        ReflectionTestUtils.setField(servicio, "contadorIntentosLogin", contador);
        contador.reiniciar();

        ExecutorService pool = Executors.newFixedThreadPool(HILOS);
        CountDownLatch listos = new CountDownLatch(Math.min(n, HILOS)); // todos los hilos del pool ya estan esperando en la salida
        CountDownLatch salida = new CountDownLatch(1);   // senal de partida simultanea
        CountDownLatch fin = new CountDownLatch(n);      // todas terminaron
        AtomicInteger excepciones = new AtomicInteger();

        for (int i = 0; i < n; i++) {
            pool.submit(() -> {
                try {
                    listos.countDown();
                    salida.await();
                    servicio.login("usuario", "clave");
                } catch (Exception e) {
                    excepciones.incrementAndGet();
                } finally {
                    fin.countDown();
                }
            });
        }

        listos.await();
        long inicio = System.nanoTime();
        salida.countDown();
        fin.await();
        long tiempoMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio);
        pool.shutdown();

        long obtenido = contador.obtenerTotalIntentos();
        long errores = (n - obtenido) + excepciones.get();
        return new Resultado(contador.nombreEstrategia(), n, obtenido, tiempoMs, errores);
    }

    @Test
    void comparaVariantesBajoAltaConcurrencia() throws Exception {
        System.out.printf("%n%-26s %8s %10s %12s %10s%n", "Variante", "Esperado", "Obtenido", "Tiempo (ms)", "Errores");
        System.out.println("-".repeat(70));

        for (int n : RAFAGAS) {
            // Calentamiento de la JVM para no penalizar la primera variante
            ejecutarRafaga(new ContadorIntentosAtomico(), n);

            Resultado a = ejecutarRafaga(new ContadorIntentosSinControl(), n);
            Resultado b = ejecutarRafaga(new ContadorIntentosSincronizado(), n);
            Resultado c = ejecutarRafaga(new ContadorIntentosAtomico(), n);

            for (Resultado r : List.of(a, b, c)) {
                System.out.printf("%-26s %8d %10d %12d %10d%n", r.variante(), r.n(), r.obtenido(), r.tiempoMs(), r.errores());
            }
            System.out.println("-".repeat(70));

            // A es no determinista: nunca puede sobrepasar N, pero puede perder incrementos.
            assertTrue(a.obtenido() <= n, "La variante A no puede superar el esperado");
            // B y C deben ser exactas.
            assertEquals(n, b.obtenido(), "Variante B (synchronized) perdio incrementos");
            assertEquals(0, b.errores());
            assertEquals(n, c.obtenido(), "Variante C (AtomicLong) perdio incrementos");
            assertEquals(0, c.errores());
        }
    }
}
