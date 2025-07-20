import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class SistemaOperacional extends Thread {
    private List<Recurso> recursos = new ArrayList<>();
    private List<Processo> processos = new CopyOnWriteArrayList<>();
    private Map<Processo, List<Recurso>> alocados = new HashMap<>();
    private Map<Processo, Recurso> aguardando = new HashMap<>();
    private Map<Recurso, List<Processo>> processosAguardando = new HashMap<>();
    private java.util.function.Consumer<String> logger = System.out::println;
    private Runnable onUpdate = () -> {};
    private int intervaloVerificacao;
    private int[][] allocationMatrix;
    private int[][] requestMatrix;
    private int[] availableVector;
    private long lastMatrixUpdate = 0;
    private static final long MATRIX_UPDATE_INTERVAL_MS = 500;

    public SistemaOperacional(int intervaloVerificacao) {
        this.intervaloVerificacao = intervaloVerificacao;
    }

    public void setLogger(java.util.function.Consumer<String> logFunc) {
        this.logger = logFunc;
    }

    public void setOnUpdate(Runnable r) {
        this.onUpdate = r;
    }

    public boolean adicionarRecurso(Recurso r) {
        synchronized (recursos) {
            if (recursos.size() >= 10) {
                logger.accept("Erro: Limite de 10 recursos atingido.");
                return false;
            }
            for (Recurso recurso : recursos) {
                if (recurso.getId() == r.getId()) {
                    logger.accept("Erro: ID " + r.getId() + " já existe.");
                    return false;
                }
            }
            recursos.add(r);
            processosAguardando.put(r, new ArrayList<>());
            updateMatrices();
            logger.accept("Recurso " + r.getNome() + " adicionado.");
            onUpdate.run();
            return true;
        }
    }

    public void adicionarProcesso(Processo p) {
        synchronized (processos) {
            if (processos.size() < 10) {
                processos.add(p);
                alocados.put(p, new ArrayList<>());
                updateMatrices();
                logger.accept("Processo " + p.getProcessoName() + " adicionado.");
                onUpdate.run();
            } else {
                logger.accept("Erro: Limite de 10 processos atingido.");
            }
        }
    }

    public void removerProcesso(Processo p) {
        synchronized (processos) {
            processos.remove(p);
        }
        synchronized (alocados) {
            List<Recurso> recursosAlocados = alocados.remove(p);
            if (recursosAlocados != null) {
                for (Recurso r : recursosAlocados) {
                    r.liberar();
                    logger.accept("Recurso " + r.getNome() + " liberado por processo " + p.getProcessoName());
                    notifyWaitingProcesses(r);
                }
            }
        }
        synchronized (aguardando) {
            Recurso r = aguardando.remove(p);
            if (r != null) {
                synchronized (processosAguardando) {
                    processosAguardando.get(r).remove(p);
                }
                logger.accept("Processo " + p.getProcessoName() + " removido da espera por " + r.getNome());
            }
        }
        updateMatrices();
        onUpdate.run();
    }

    public void limparAguardando(Processo p) {
        synchronized (aguardando) {
            Recurso r = aguardando.remove(p);
            if (r != null) {
                synchronized (processosAguardando) {
                    processosAguardando.get(r).remove(p);
                }
                logger.accept("Processo " + p.getProcessoName() + " removido da espera por " + r.getNome());
                updateMatrices();
                onUpdate.run();
            }
        }
    }

    public Recurso getRecursoAguardado(Processo p) {
        synchronized (aguardando) {
            return aguardando.get(p);
        }
    }

    public List<Recurso> getRecursos() {
        synchronized (recursos) {
            return new ArrayList<>(recursos);
        }
    }

    public List<Processo> getProcessos() {
        return processos;
    }

    public Recurso solicitarRecurso(Processo p) {
        logger.accept("Processo " + p.getProcessoName() + " tentando alocar recurso...");
        if (recursos.isEmpty()) {
            logger.accept("Nenhum recurso disponível para processo " + p.getProcessoName());
            return null;
        }
        synchronized (alocados) {
            synchronized (aguardando) {
                // Check if process is waiting for a specific resource
                Recurso waitingFor = aguardando.get(p);
                if (waitingFor != null) {
                    if (waitingFor.alocar()) {
                        alocados.computeIfAbsent(p, k -> new ArrayList<>()).add(waitingFor);
                        aguardando.remove(p);
                        synchronized (processosAguardando) {
                            processosAguardando.get(waitingFor).remove(p);
                        }
                        logger.accept("Processo " + p.getProcessoName() + " obteve " + waitingFor.getNome());
                        updateMatrices();
                        onUpdate.run();
                        return waitingFor;
                    }
                    logger.accept("Recurso " + waitingFor.getNome() + " ainda indisponível para processo " + p.getProcessoName());
                    return null;
                }
                // Try to allocate a new resource
                List<Recurso> shuffledRecursos = new ArrayList<>(recursos);
                Collections.shuffle(shuffledRecursos);
                for (Recurso r : shuffledRecursos) {
                    if (r.alocar()) {
                        alocados.computeIfAbsent(p, k -> new ArrayList<>()).add(r);
                        logger.accept("Processo " + p.getProcessoName() + " obteve " + r.getNome());
                        updateMatrices();
                        onUpdate.run();
                        return r;
                    }
                }
                // No resource available, block on first resource
                Recurso r = recursos.get(0);
                aguardando.put(p, r);
                synchronized (processosAguardando) {
                    processosAguardando.computeIfAbsent(r, k -> new ArrayList<>()).add(p);
                }
                logger.accept("Processo " + p.getProcessoName() + " bloqueado aguardando " + r.getNome());
                updateMatrices();
                onUpdate.run();
                return null;
            }
        }
    }

    public void liberarRecurso(Processo p, Recurso r) {
        synchronized (alocados) {
            List<Recurso> recursosAlocados = alocados.get(p);
            if (recursosAlocados != null && recursosAlocados.remove(r)) {
                r.liberar();
                logger.accept("Processo " + p.getProcessoName() + " liberou " + r.getNome());
                updateMatrices();
                notifyWaitingProcesses(r);
            }
        }
    }

    private void notifyWaitingProcesses(Recurso r) {
        synchronized (aguardando) {
            synchronized (processosAguardando) {
                List<Processo> waiting = new ArrayList<>(processosAguardando.getOrDefault(r, new ArrayList<>()));
                logger.accept("Notificando " + waiting.size() + " processos aguardando " + r.getNome());
                for (Processo p : waiting) {
                    p.notifyProcess();
                }
            }
        }
    }

    public List<String> statusRecursos() {
        synchronized (recursos) {
            List<String> lista = new ArrayList<>();
            for (Recurso r : recursos) {
                lista.add(r.toString());
            }
            return lista;
        }
    }

    public List<String> statusProcessos() {
        List<String> lista = new ArrayList<>();
        for (Processo p : processos) {
            lista.add(p.status());
        }
        return lista;
    }

    public String getAllocationMatrixString() {
        updateMatrices();
        StringBuilder sb = new StringBuilder();
        if (processos.isEmpty() || recursos.isEmpty()) {
            sb.append("Nenhum processo ou recurso disponível.");
            return sb.toString();
        }
        sb.append("     ");
        for (Recurso r : recursos) {
            sb.append(String.format("%-6s", r.getNome()));
        }
        sb.append("\n");
        for (int i = 0; i < processos.size(); i++) {
            sb.append(String.format("P%-4s", processos.get(i).getProcessoName()));
            for (int j = 0; j < recursos.size(); j++) {
                sb.append(String.format("%-6d", allocationMatrix[i][j]));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    public String getRequestMatrixString() {
        updateMatrices();
        StringBuilder sb = new StringBuilder();
        if (processos.isEmpty() || recursos.isEmpty()) {
            sb.append("Nenhum processo ou recurso disponível.");
            return sb.toString();
        }
        sb.append("     ");
        for (Recurso r : recursos) {
            sb.append(String.format("%-6s", r.getNome()));
        }
        sb.append("\n");
        for (int i = 0; i < processos.size(); i++) {
            sb.append(String.format("P%-4s", processos.get(i).getProcessoName()));
            for (int j = 0; j < recursos.size(); j++) {
                sb.append(String.format("%-6d", requestMatrix[i][j]));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private void updateMatrices() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastMatrixUpdate < MATRIX_UPDATE_INTERVAL_MS) {
            return;
        }
        lastMatrixUpdate = currentTime;

        synchronized (recursos) {
            synchronized (alocados) {
                synchronized (aguardando) {
                    int n = processos.size();
                    int m = recursos.size();
                    allocationMatrix = new int[n][m];
                    requestMatrix = new int[n][m];
                    availableVector = new int[m];

                    for (int i = 0; i < n; i++) {
                        for (int j = 0; j < m; j++) {
                            allocationMatrix[i][j] = 0;
                            requestMatrix[i][j] = 0;
                        }
                    }
                    for (int j = 0; j < m; j++) {
                        availableVector[j] = recursos.get(j).getDisponivel();
                    }

                    for (int i = 0; i < n; i++) {
                        Processo p = processos.get(i);
                        List<Recurso> recursosAlocados = alocados.getOrDefault(p, new ArrayList<>());
                        for (Recurso r : recursosAlocados) {
                            int j = recursos.indexOf(r);
                            if (j >= 0) allocationMatrix[i][j] += 1;
                        }
                        Recurso r = aguardando.get(p);
                        if (r != null) {
                            int j = recursos.indexOf(r);
                            if (j >= 0) requestMatrix[i][j] = 1;
                        }
                    }
                }
            }
        }
    }

    @Override
    public void run() {
        while (!isInterrupted()) {
            try {
                Thread.sleep(intervaloVerificacao * 1000L);
                detectarDeadlock();
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private void detectarDeadlock() {
        if (recursos.isEmpty() || processos.isEmpty()) return;

        updateMatrices();
        int n = processos.size();
        int m = recursos.size();
        int[] work = availableVector.clone();
        boolean[] finish = new boolean[n];
        Arrays.fill(finish, false);

        List<Integer> safeSequence = new ArrayList<>();
        boolean progress;
        do {
            progress = false;
            for (int i = 0; i < n; i++) {
                if (!finish[i]) {
                    boolean canRun = true;
                    for (int j = 0; j < m; j++) {
                        if (requestMatrix[i][j] > work[j]) {
                            canRun = false;
                            break;
                        }
                    }
                    if (canRun) {
                        for (int j = 0; j < m; j++) {
                            work[j] += allocationMatrix[i][j];
                        }
                        finish[i] = true;
                        safeSequence.add(i);
                        progress = true;
                    }
                }
            }
        } while (progress && safeSequence.size() < n);

        if (safeSequence.size() < n && n > 1) {
            List<String> deadlocked = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if (!finish[i]) {
                    deadlocked.add(processos.get(i).getProcessoName());
                }
            }
            if (deadlocked.size() >= 2) {
                logger.accept("⚠ DEADLOCK DETECTADO entre processos: " + deadlocked);
            }
        } else {
            logger.accept("Sistema está em estado seguro.");
        }
        onUpdate.run();
    }
}