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
    private ConcurrentMap<Recurso, Long> timerStartTimes = new ConcurrentHashMap<>();
    private Recurso recursoSolicitado = null;
    private long lastRequestTime = System.currentTimeMillis();

    public Processo(int id, int deltaS, int deltaU, SistemaOperacional sistema,
            java.util.function.Consumer<String> logger) {
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
        synchronized (recursosUsados) {
            StringBuilder status = new StringBuilder("Processo " + id);
            if (recursoSolicitado != null) {
                status.append(" [bloqueado, aguardando ").append(recursoSolicitado.getNome());
                if (!recursosUsados.isEmpty()) {
                    status.append(", usando ");
                    status.append(String.join(", ", recursosUsados.stream()
                            .map(r -> r.getNome() + " (" + recursosUsados.stream().filter(x -> x == r).count() + ")")
                            .distinct()
                            .toList()));
                }
                status.append("]");
            } else if (recursosUsados.isEmpty()) {
                status.append(" [bloqueado]");
            } else {
                status.append(" [rodando, usando ");
                status.append(String.join(", ", recursosUsados.stream()
                        .map(r -> r.getNome() + " (" + recursosUsados.stream().filter(x -> x == r).count() + ")")
                        .distinct()
                        .toList()));
                status.append("]");
            }
            return status.toString();
        }
    }

    public List<Recurso> getRecursosUsados() {
        synchronized (recursosUsados) {
            return new ArrayList<>(recursosUsados);
        }
    }

    public Recurso getRecursoSolicitado() {
        return recursoSolicitado;
    }

    @Override
    public void run() {
        while (!isInterrupted()) {
            synchronized (this) {

                
                // Request a new resource if under system limit and not blocked
                long now = System.currentTimeMillis();
                if (recursosUsados.size() < sistema.getTotalRecursosSistema() && recursoSolicitado == null) {
                    if (now - lastRequestTime >= deltaS * 1000L) {
                        recursoSolicitado = sistema.solicitarRecurso(this);
                        lastRequestTime = now; // atualiza o tempo da última solicitação
                        logger.accept("Processo " + id + " solicitando recurso...");
                    }
                }

                
                

                if (recursoSolicitado != null) {
                    // Resource acquired
                    synchronized (recursosUsados) {
                        recursosUsados.add(recursoSolicitado);
                    }
                    logger.accept("Processo " + id + " obteve recurso " + recursoSolicitado.getNome());
                    // Start timer for new resource
                    Recurso acquired = recursoSolicitado;
                    long startTime = System.currentTimeMillis();
                    timerStartTimes.put(acquired, startTime);
                    Thread timer = new Thread(() -> {
                        try {
                            while (!Thread.currentThread().isInterrupted()) {
                                long elapsed = System.currentTimeMillis() - startTime;
                                long remaining = deltaU * 1000L - elapsed;
                                if (remaining <= 0) {
                                    synchronized (recursosUsados) {
                                        if (recursosUsados.remove(acquired)) {
                                            sistema.liberarRecurso(this, acquired);
                                            logger.accept(
                                                    "Processo " + id + " liberou recurso " + acquired.getNome());
                                        }
                                        timers.remove(acquired);
                                        timerStartTimes.remove(acquired);
                                    }
                                    break;
                                }
                                Thread.sleep(Math.min(remaining, 100));
                            }
                        } catch (InterruptedException e) {
                            // Timer interrupted
                        }
                    });
                    timers.put(acquired, timer);
                    timer.start();
                    recursoSolicitado = null;
                } else if (sistema.getRecursoAguardado(this) != null) {
                    // Block on the requested resource
                    recursoSolicitado = sistema.getRecursoAguardado(this);
                    logger.accept("Processo " + id + " bloqueado aguardando " + recursoSolicitado.getNome());
                    while (recursoSolicitado != null && !isInterrupted()) {
                        try {
                            wait();
                            recursoSolicitado = sistema.retryingSolicitarRecurso(this, recursoSolicitado);
                            if (recursoSolicitado == null) {
                                // Resource acquired
                                Recurso newlyAcquired = sistema.getRecursoAguardado(this);
                                if (newlyAcquired != null) {
                                    synchronized (recursosUsados) {
                                        recursosUsados.add(newlyAcquired);
                                    }
                                    long startTime = System.currentTimeMillis();
                                    timerStartTimes.put(newlyAcquired, startTime);
                                    Thread newTimer = new Thread(() -> {
                                        try {
                                            while (!Thread.currentThread().isInterrupted()) {
                                                long newStart = timerStartTimes.getOrDefault(newlyAcquired,
                                                        System.currentTimeMillis());
                                                long newElapsed = System.currentTimeMillis() - newStart;
                                                long newRemaining = deltaU * 1000L - newElapsed;
                                                if (newRemaining <= 0) {
                                                    synchronized (recursosUsados) {
                                                        if (recursosUsados.remove(newlyAcquired)) {
                                                            sistema.liberarRecurso(this, newlyAcquired);
                                                            logger.accept("Processo " + id + " liberou recurso "
                                                                    + newlyAcquired.getNome());
                                                        }
                                                        timers.remove(newlyAcquired);
                                                        timerStartTimes.remove(newlyAcquired);
                                                    }
                                                    break;
                                                }
                                                Thread.sleep(Math.min(newRemaining, 100));

                                            }
                                        } catch (InterruptedException e) {
                                            // Timer interrupted
                                        }
                                    });
                                    timers.put(newlyAcquired, newTimer);
                                    newTimer.start();
                                }
                                recursoSolicitado = null;
                                notify(); // Resume timers
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
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
        timerStartTimes.clear();
        sistema.limparAguardando(this);
    }

    public void notifyProcess() {
        synchronized (this) {
            notify();
        }
    }
}