import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class Processo extends Thread {
    private int id;
    private int deltaS;
    private int deltaU;
    private SistemaOperacional sistema;
    private List<Recurso> recursosUsados = new ArrayList<>();
    private java.util.function.Consumer<String> logger;
    private ConcurrentMap<Recurso, Thread> timers = new ConcurrentHashMap<>();

    public Processo(int id, int deltaS, int deltaU, SistemaOperacional sistema, java.util.function.Consumer<String> logger) {
        this.id = id;
        this.deltaS = deltaS;
        this.deltaU = deltaU;
        this.sistema = sistema;
        this.logger = logger;
    }

    public int getProcessoId() {
        return id;
    }

    public String getProcessoName() {
        return "" + id;
    }

    public String status() {
        StringBuilder status = new StringBuilder("Processo " + id);
        synchronized (recursosUsados) {
            Recurso aguardando = sistema.getRecursoAguardado(this);
            if (aguardando != null) {
                status.append(" [bloqueado, aguardando ").append(aguardando.getNome());
                if (!recursosUsados.isEmpty()) {
                    status.append(", usando ").append(String.join(", ", recursosUsados.stream().map(Recurso::getNome).toList()));
                }
                status.append("]");
            } else if (recursosUsados.isEmpty()) {
                status.append(" [bloqueado]");
            } else {
                status.append(" [rodando, usando ");
                status.append(String.join(", ", recursosUsados.stream().map(Recurso::getNome).toList()));
                status.append("]");
            }
        }
        return status.toString();
    }

    public List<Recurso> getRecursosUsados() {
        synchronized (recursosUsados) {
            return new ArrayList<>(recursosUsados);
        }
    }

    public Recurso getRecursoSolicitado() {
        return sistema.getRecursoAguardado(this);
    }

    @Override
    public void run() {
        while (!isInterrupted()) {
            try {
                synchronized (recursosUsados) {
                    int totalInstances = sistema.getRecursos().stream().mapToInt(Recurso::getTotal).sum();
                    if (recursosUsados.size() >= totalInstances) {
                        logger.accept("Processo " + id + " não pode solicitar mais recursos (limite atingido).");
                        Thread.sleep(deltaS);
                        continue;
                    }
                }
                Thread.sleep(deltaS);
                synchronized (this) {
                    logger.accept("Processo " + id + " solicitando recurso...");
                    Recurso r = sistema.solicitarRecurso(this);
                    if (r != null) {
                        // Resource acquired
                        synchronized (recursosUsados) {
                            recursosUsados.add(r);
                        }
                        logger.accept("Processo " + id + " obteve recurso " + r.getNome());
                        // Start timer to release resource after deltaU
                        final Recurso acquiredResource = r;
                        Thread timer = new Thread(() -> {
                            try {
                                Thread.sleep(deltaU);
                                synchronized (recursosUsados) {
                                    if (recursosUsados.remove(acquiredResource)) {
                                        sistema.liberarRecurso(this, acquiredResource);
                                    }
                                }
                                timers.remove(acquiredResource);
                            } catch (InterruptedException e) {
                                // Timer interrupted
                            }
                        });
                        timers.put(acquiredResource, timer);
                        timer.start();
                    } else if (sistema.getRecursoAguardado(this) != null) {
                        // Block until resource is available
                        Recurso aguardando = sistema.getRecursoAguardado(this);
                        logger.accept("Processo " + id + " bloqueado aguardando " + aguardando.getNome());
                        while (sistema.getRecursoAguardado(this) != null && !isInterrupted()) {
                            try {
                                wait(2000); // 2-second timeout
                                // Retry resource acquisition
                                Recurso newResource = sistema.solicitarRecurso(this);
                                if (newResource != null) {
                                    synchronized (recursosUsados) {
                                        recursosUsados.add(newResource);
                                    }
                                    logger.accept("Processo " + id + " obteve recurso " + newResource.getNome());
                                    final Recurso newlyAcquired = newResource;
                                    Thread timer = new Thread(() -> {
                                        try {
                                            Thread.sleep(deltaU);
                                            synchronized (recursosUsados) {
                                                if (recursosUsados.remove(newlyAcquired)) {
                                                    sistema.liberarRecurso(this, newlyAcquired);
                                                }
                                            }
                                            timers.remove(newlyAcquired);
                                        } catch (InterruptedException e) {
                                            // Timer interrupted
                                        }
                                    });
                                    timers.put(newlyAcquired, timer);
                                    timer.start();
                                    break;
                                }
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        // Cleanup
        timers.values().forEach(Thread::interrupt);
        synchronized (recursosUsados) {
            for (Recurso r : new ArrayList<>(recursosUsados)) {
                sistema.liberarRecurso(this, r);
            }
            recursosUsados.clear();
        }
        sistema.limparAguardando(this);
        logger.accept("Processo " + id + " finalizado.");
    }

    public void notifyProcess() {
        synchronized (this) {
            logger.accept("Notificando processo " + id);
            notify();
        }
    }
}