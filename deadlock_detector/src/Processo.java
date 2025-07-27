
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class Processo extends Thread {
    private int id;
    private int deltaS;
    private int deltaU;
    private SistemaOperacional sistema;
    private List<RecursoInstance> recursosUsados = new ArrayList<>();
    private java.util.function.Consumer<String> logger;
    private ConcurrentMap<RecursoInstance, Thread> timers = new ConcurrentHashMap<>();
    private ConcurrentMap<RecursoInstance, Long> timerStartTimes = new ConcurrentHashMap<>();
    private Recurso recursoSolicitado = null;
    private int instanceCounter = 0;
    private long startTime;

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
            if (recursoSolicitado != null || sistema.getRecursoAguardado(this) != null) {
                Recurso aguardado = recursoSolicitado != null ? recursoSolicitado : sistema.getRecursoAguardado(this);
                status.append(" [bloqueado, aguardando ").append(aguardado.getNome());
                if (!recursosUsados.isEmpty()) {
                    status.append(", usando ");
                    status.append(String.join(", ", recursosUsados.stream()
                            .map(r -> r.getRecurso().getNome() + " ("
                                    + recursosUsados.stream().filter(x -> x.getRecurso() == r.getRecurso()).count()
                                    + ")")
                            .distinct()
                            .toList()));
                }
                status.append("]");
            } else if (recursosUsados.isEmpty()) {
                status.append(" [Bloqueado]");
            } else {
                status.append(" [rodando, usando ");
                status.append(String.join(", ", recursosUsados.stream()
                        .map(r -> r.getRecurso().getNome() + " ("
                                + recursosUsados.stream().filter(x -> x.getRecurso() == r.getRecurso()).count() + ")")
                        .distinct()
                        .toList()));
                status.append("]");
            }
            return status.toString();
        }
    }

    public List<RecursoInstance> getRecursosUsados() {
        synchronized (recursosUsados) {
            return new ArrayList<>(recursosUsados);
        }
    }

    public Recurso getRecursoSolicitado() {
        synchronized (this){
            return recursoSolicitado;
        }
    }

    private void startTimerForRecurso(RecursoInstance instance) {
        timerStartTimes.put(instance, System.currentTimeMillis());
        Thread timer = new Thread(() -> {
            try {
                long startTime = timerStartTimes.get(instance);
                long elapsed = 0;
                while (!Thread.currentThread().isInterrupted() && elapsed < deltaU * 1000L) {
                    Thread.sleep(100); // Verifica a cada 100ms
                    elapsed = System.currentTimeMillis() - startTime;
                    if (recursoSolicitado != null || sistema.getRecursoAguardado(this) != null) {
                        synchronized (Processo.this) {
                            Processo.this.wait(); // Pausa o timer se bloqueado
                        }
                    }
                }
                synchronized (recursosUsados) {
                    if (recursosUsados.remove(instance)) {
                        sistema.liberarRecurso(this, instance.getRecurso());
                        timers.remove(instance);
                        timerStartTimes.remove(instance);
                    }
                }
                synchronized (Processo.this) {
                    Processo.this.notify(); // Notifica para reiniciar o ciclo
                }
            } catch (InterruptedException e) {
                // Timer interrupted
            }
        });
        timers.put(instance, timer);
        timer.start();
    }

    @Override
public void run() {
    long lastSolicitationTime = startTime;
    while (!isInterrupted()) {
        try {
            synchronized (this) {
                long currentTime = System.currentTimeMillis();
                long elapsedSinceLastSolicitation = currentTime - lastSolicitationTime;

                // Solicitação a cada deltaS segundos
                if (elapsedSinceLastSolicitation >= deltaS * 1000L) {
                    if (recursoSolicitado == null && sistema.getRecursoAguardado(this) == null) {
                        logger.accept("Processo " + id + " solicitando recurso às " + (currentTime - startTime) / 1000 + "s...");
                        recursoSolicitado = sistema.solicitarRecurso(this);
                        if (recursoSolicitado != null) {
                            synchronized (recursosUsados) {
                                RecursoInstance instance = new RecursoInstance(recursoSolicitado, instanceCounter++);
                                recursosUsados.add(instance);
                                startTimerForRecurso(instance);
                            }
                            recursoSolicitado = null;
                        } else if (sistema.getRecursoAguardado(this) == null) {
                            wait(); // Aguarda notificação se não houver recurso disponível
                        }
                    } else {
                        wait(); // Aguarda se já estiver solicitando ou aguardando
                    }
                    lastSolicitationTime = currentTime;
                } else {
                    wait(Math.max(0, (deltaS * 1000L) - elapsedSinceLastSolicitation));
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
        for (RecursoInstance ri : new ArrayList<>(recursosUsados)) {
            sistema.liberarRecurso(this, ri.getRecurso());
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
