import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class SistemaOperacional extends Thread {
    private List<Recurso> recursos = new ArrayList<>();
    private List<Processo> processos = new CopyOnWriteArrayList<>();
    private Map<Processo, Recurso> alocados = new HashMap<>();
    private java.util.function.Consumer<String> logger = System.out::println;
    private Runnable onUpdate = () -> {};
    private int intervaloVerificacao; // em segundos

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
        if (recursos.size() >= 10) return false;
        for (Recurso recurso : recursos) {
            if (recurso.getId() == r.getId()) return false;
        }
        recursos.add(r);
        return true;
    }

    public void adicionarProcesso(Processo p) {
        if (processos.size() < 10)
            processos.add(p);
    }

    public void removerProcesso(Processo p) {
        processos.remove(p);
        alocados.remove(p);
    }

    public List<Processo> getProcessos() {
        return processos;
    }

    public Recurso solicitarRecurso(Processo p) {
        Collections.shuffle(recursos);
        for (Recurso r : recursos) {
            if (r.alocar()) {
                alocados.put(p, r);
                logger.accept("Processo " + p.getName() + " obteve " + r.getNome());
                onUpdate.run();
                return r;
            }
        }
        logger.accept("Processo " + p.getName() + " bloqueado (sem recursos disponíveis)");
        onUpdate.run();
        return null;
    }

    public void liberarRecurso(Processo p, Recurso r) {
        r.liberar();
        alocados.remove(p);
        logger.accept("Processo " + p.getName() + " liberou " + r.getNome());
        onUpdate.run();
    }

    public List<String> statusRecursos() {
        List<String> lista = new ArrayList<>();
        for (Recurso r : recursos) lista.add(r.toString());
        return lista;
    }

    public List<String> statusProcessos() {
        List<String> lista = new ArrayList<>();
        for (Processo p : processos) lista.add(p.status());
        return lista;
    }

    @Override
    public void run() {
        while (true) {
            try {
                Thread.sleep(intervaloVerificacao * 1000L);
                detectarDeadlock();
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private void detectarDeadlock() {
        if (alocados.size() >= 2) {
            logger.accept("⚠ POSSÍVEL DEADLOCK detectado entre processos: " + alocados.keySet().stream().map(p -> "" + p.getName()).toList());
        }
    }
}