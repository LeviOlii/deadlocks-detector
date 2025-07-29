import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class SistemaOperacional extends Thread {
    private List<Recurso> recursos = new ArrayList<>();
    private List<Processo> processos = new CopyOnWriteArrayList<>();
    private Map<Processo, List<RecursoInstance>> alocados = new HashMap<>(); // Alterado para RecursoInstance
    private Map<Processo, Recurso> aguardando = new ConcurrentHashMap<>();
    private Map<Recurso, CopyOnWriteArrayList<Processo>> processosAguardando = new ConcurrentHashMap<>();
    private java.util.function.Consumer<String> logger = System.out::println;
    private static final AtomicInteger globalInstanceCounter = new AtomicInteger(0);
    private Runnable onUpdate = () -> {
    };
    private int intervaloVerificacao;
    private int[][] allocationMatrix;
    private int[][] requestMatrix;
    private int[] availableVector;

    public SistemaOperacional(int intervaloVerificacao) {
        this.intervaloVerificacao = intervaloVerificacao;
    }

    public static int getNextGlobalInstanceId() {
        return globalInstanceCounter.getAndIncrement();
    }

    public void setLogger(java.util.function.Consumer<String> logFunc) {
        this.logger = logFunc;
    }

    public void setOnUpdate(Runnable r) {
        this.onUpdate = r;
    }

    public boolean adicionarRecurso(Recurso r) {
        if (recursos.size() >= 10)
            return false;
        for (Recurso recurso : recursos) {
            if (recurso.getId() == r.getId())
                return false;
        }
        recursos.add(r);
        processosAguardando.put(r, new CopyOnWriteArrayList<>());
        updateMatrices();
        return true;
    }

    public void adicionarProcesso(Processo p) {
        if (processos.size() < 10) {
            processos.add(p);
            alocados.put(p, new ArrayList<>()); // Inicializa com lista de RecursoInstance
            updateMatrices();
        }
    }

    public void removerProcesso(Processo p) {
        processos.remove(p);
        List<RecursoInstance> recursosAlocados = alocados.get(p);
        if (recursosAlocados != null) {
            for (RecursoInstance ri : new ArrayList<>(recursosAlocados)) {
                liberarRecurso(p, ri); // Passa RecursoInstance
            }
            alocados.remove(p);
        }
        Recurso r = aguardando.remove(p);
        if (r != null) {
            CopyOnWriteArrayList<Processo> list = processosAguardando.get(r);
            if (list != null) {
                list.remove(p);
            }
        }
        updateMatrices();
        onUpdate.run();
    }

    public void limparAguardando(Processo p) {
        Recurso r = aguardando.remove(p);
        if (r != null) {
            CopyOnWriteArrayList<Processo> list = processosAguardando.get(r);
            if (list != null) {
                list.remove(p);
                logger.accept(
                        "Processo " + p.getProcessoName() + " removido de processosAguardando para " + r.getNome());
            }
            updateMatrices();
            onUpdate.run();
        }
    }

    public Recurso getRecursoAguardado(Processo p) {
        return aguardando.get(p);
    }

    public List<Recurso> getRecursos() {
        return new ArrayList<>(recursos);
    }

    public List<Processo> getProcessos() {
        return processos;
    }

    public List<RecursoInstance> getAlocados(Processo p) {
        return new ArrayList<>(alocados.getOrDefault(p, new ArrayList<>()));
    }

    public int getTotalRecursosSistema() {
        return recursos.stream().mapToInt(Recurso::getTotal).sum();
    }

    public Recurso solicitarRecurso(Processo p, RecursoInstance instance) {
        List<RecursoInstance> recursosAlocados = alocados.getOrDefault(p, new ArrayList<>());
        Random random = new Random();

        while (true) {
            List<Recurso> recursosExistentes = new ArrayList<>(recursos);
            if (recursosExistentes.isEmpty()) {
                return null;
            }
            Recurso r = recursosExistentes.get(random.nextInt(recursosExistentes.size()));

            if (r.getDisponivel() > 0) {
                if (r.alocar()) {
                    instance.setRecurso(r);
                    alocados.computeIfAbsent(p, k -> new ArrayList<>()).add(instance);
                    Recurso prev = aguardando.remove(p);
                    if (prev != null) {
                        processosAguardando.get(prev).remove(p);
                    }
                    logger.accept("Processo " + p.getProcessoName() + " obteve recurso " + r.getNome());
                    updateMatrices();
                    onUpdate.run();
                    return r;
                }
            } else {
                long countAlocados = recursosAlocados.stream().filter(ri -> ri.getRecurso() == r).count();
                if (countAlocados < r.getTotal()) {
                    synchronized (processosAguardando) {
                        if (aguardando.putIfAbsent(p, r) == null) { // Só adiciona se não estiver aguardando
                            CopyOnWriteArrayList<Processo> waitingList = processosAguardando.computeIfAbsent(r,
                                    k -> new CopyOnWriteArrayList<>());
                            if (!waitingList.contains(p)) {
                                waitingList.add(p);
                                //logger.accept("Processo " + p.getProcessoName()
                                   //     + " adicionado a processosAguardando para " + r.getNome());
                            } else {
                                //logger.accept("Processo " + p.getProcessoName()
                                  //      + " já está em processosAguardando para " + r.getNome());
                            }
                            updateMatrices();
                            onUpdate.run();
                        }
                    }
                    return null;
                } else {
                    return null;
                }
            }
        }
    }

    public void liberarRecurso(Processo p, RecursoInstance instance) { // Alterado para RecursoInstance
        List<RecursoInstance> recursosAlocados = alocados.get(p);
        // logger.accept(recursosAlocados.toString());
        if (recursosAlocados != null) {
            Recurso r = instance.getRecurso();
            if (recursosAlocados.remove(instance)) {
                r.liberar();
                logger.accept("Processo " + p.getProcessoName() + " liberou recurso " + r.getNome());
                notifyWaitingProcesses(r);
                updateMatrices();
                onUpdate.run();
            } else {
                logger.accept("Falha ao remover " + r.getNome() + " de recursosAlocados para " + p.getProcessoName());
            }
        }
    }

    private synchronized void notifyWaitingProcesses(Recurso r) {
        CopyOnWriteArrayList<Processo> waiting = processosAguardando.getOrDefault(r, new CopyOnWriteArrayList<>());
        //logger.accept("Verificando notificação para " + r.getNome() + ", lista: "
        //        + waiting.stream().map(p -> p.getProcessoName()).collect(Collectors.joining(", ")));
        if (!waiting.isEmpty()) {
            for (Processo p : new ArrayList<>(waiting)) { // Cria uma cópia para evitar ConcurrentModificationException
                aguardando.remove(p); // Remove da lista de aguardando
                p.notifyProcess();
                waiting.remove(p); // Remove da cópia
            }
            processosAguardando.remove(r); // Limpa a lista após notificação
        }
    }

    public List<String> statusRecursos() {
        List<String> resultado = new ArrayList<>();
        if (recursos.isEmpty()) {
            resultado.add("Nenhum recurso disponível.");
            return resultado;
        }
        // Cabeçalho com nomes dos recursos
        StringBuilder cabecalho = new StringBuilder(String.format("%-6s", ""));
        for (Recurso r : recursos) {
            cabecalho.append(String.format("%-4s", r.getNome()));
        }
        resultado.add(cabecalho.toString());
        // Linha de quantidades disponíveis
        StringBuilder valores = new StringBuilder(String.format("%-6s", ""));
        for (Recurso r : recursos) {
            valores.append(String.format("%-4d", r.getDisponivel()));
        }
        resultado.add(valores.toString());
        return resultado;
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
            sb.append("Nenhuma alocação disponível.");
            return sb.toString();
        }
        sb.append(String.format("%-6s", ""));
        for (Recurso r : recursos) {
            sb.append(String.format("%-4s", r.getNome()));
        }
        sb.append("\n");
        for (int i = 0; i < processos.size(); i++) {
            sb.append(String.format("%-6s", "P" + processos.get(i).getProcessoName()));
            for (int j = 0; j < recursos.size(); j++) {
                sb.append(String.format("%-4d", allocationMatrix[i][j]));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    public String getRequestMatrixString() {
        updateMatrices();
        StringBuilder sb = new StringBuilder();
        if (processos.isEmpty() || recursos.isEmpty()) {
            sb.append("Nenhuma requisição disponível.");
            return sb.toString();
        }
        sb.append(String.format("%-6s", ""));
        for (Recurso r : recursos) {
            sb.append(String.format("%-4s", r.getNome()));
        }
        sb.append("\n");
        for (int i = 0; i < processos.size(); i++) {
            sb.append(String.format("%-6s", "P" + processos.get(i).getProcessoName()));
            for (int j = 0; j < recursos.size(); j++) {
                sb.append(String.format("%-4d", requestMatrix[i][j]));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private void updateMatrices() {
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
            List<RecursoInstance> recursosAlocados = alocados.getOrDefault(p, new ArrayList<>());
            for (RecursoInstance ri : recursosAlocados) {
                int j = recursos.indexOf(ri.getRecurso());
                if (j >= 0)
                    allocationMatrix[i][j]++;
            }
            Recurso r = aguardando.get(p);
            if (r != null) {
                int j = recursos.indexOf(r);
                if (j >= 0)
                    requestMatrix[i][j] = 1;
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
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void detectarDeadlock() {
        if (recursos.isEmpty() || processos.size() <= 1)
            return;

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

        if (safeSequence.size() < n) {
            List<String> deadlocked = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if (!finish[i]) {
                    deadlocked.add(processos.get(i).getProcessoName());
                }
            }
            if (!deadlocked.isEmpty()) {
                logger.accept("⚠ DEADLOCK DETECTADO entre processos: " + deadlocked);
            }
        } else {
            logger.accept("Sistema está em estado seguro.");
        }
        onUpdate.run();
    }
}