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
    private long startTime;
    private boolean isBlocked = false;

    public Processo(int id, int deltaS, int deltaU, SistemaOperacional sistema,
            java.util.function.Consumer<String> logger) {
        this.id = id;
        this.deltaS = deltaS;
        this.deltaU = deltaU;
        this.sistema = sistema;
        this.logger = logger;
        this.startTime = System.currentTimeMillis();
    }

    public int getProcessoId() {
        return id;
    }

    public String getProcessoName() {
        return "" + id;
    }

    private synchronized boolean isBlocked() {
        return isBlocked;
    }

    private synchronized void setBlocked(boolean blocked) {
        this.isBlocked = blocked;
        if (!blocked) {
            notifyAll(); // Notifica todos os timers pausados
        }
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
        synchronized (this) {
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
                    synchronized (this) { // Sincroniza com o estado do processo
                        while (isBlocked() && !Thread.currentThread().isInterrupted()) { // Pausa se bloqueado
                            wait(); // Aguarda notificação para retomar
                        }
                    }
                }
                synchronized (recursosUsados) {
                    if (recursosUsados.remove(instance)) {
                        sistema.liberarRecurso(this, instance); // Libera só se ainda estiver na lista
                        timers.remove(instance);
                        timerStartTimes.remove(instance);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restaura o estado interrompido
            }
        });
        timers.put(instance, timer);
        timer.start();
    }

    @Override
    public void run() {
        long lastSolicitationTime = System.currentTimeMillis();
        while (!isInterrupted()) {
            try {
                synchronized (this) {
                    long currentTime = System.currentTimeMillis();
                    long elapsedSinceLastSolicitation = currentTime - lastSolicitationTime;

                    if (elapsedSinceLastSolicitation >= deltaS * 1000L) {
                        if (recursoSolicitado == null && sistema.getRecursoAguardado(this) == null) {
                            logger.accept("Processo " + id + " solicitando recurso às "
                                    + (currentTime - startTime) / 1000 + "s...");
                            RecursoInstance instance = new RecursoInstance(null,
                                    SistemaOperacional.getNextGlobalInstanceId());
                            recursoSolicitado = sistema.solicitarRecurso(this, instance);
                            logger.accept("Processo " + id + " obteve recurso" + instance.getClass().getName() + " às "  
                                    + (currentTime - startTime) / 1000 + "s...");
                            if (recursoSolicitado != null) {
                                synchronized (recursosUsados) {
                                    instance.setRecurso(recursoSolicitado);
                                    recursosUsados.add(instance);
                                    startTimerForRecurso(instance);
                                }
                                recursoSolicitado = null;
                                setBlocked(false); // Processo ativo
                            } else {
                                if (sistema.getRecursoAguardado(this) != null) {
                                    setBlocked(true); // Marca como bloqueado
                                    wait();
                                    setBlocked(false); // Desmarca ao acordar
                                    recursoSolicitado = sistema.solicitarRecurso(this,
                                            new RecursoInstance(null, SistemaOperacional.getNextGlobalInstanceId()));
                                    if (recursoSolicitado != null) {
                                        synchronized (recursosUsados) {
                                            instance.setRecurso(recursoSolicitado);
                                            recursosUsados.add(instance);
                                            startTimerForRecurso(instance);
                                        }
                                        recursoSolicitado = null;
                                    }
                                } else {
                                    logger.accept("Processo " + id
                                            + " não obteve recurso, tentando novamente no próximo ciclo às "
                                            + (currentTime - startTime) / 1000 + "s");
                                }
                            }
                        } else {
                            setBlocked(true); // Bloqueia se estiver aguardando recursoSolicitado
                            wait();
                            setBlocked(false); // Desbloqueia ao acordar
                        }
                        lastSolicitationTime = currentTime;
                    } else {
                        long waitTime = Math.max(0, (deltaS * 1000L) - elapsedSinceLastSolicitation);
                        wait(waitTime);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        // Cleanup
        timers.values().forEach(Thread::interrupt); // Interrompe todos os timers ao finalizar
        synchronized (recursosUsados) {
            for (RecursoInstance ri : new ArrayList<>(recursosUsados)) {
                sistema.liberarRecurso(this, ri);
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