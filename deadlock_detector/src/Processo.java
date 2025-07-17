public class Processo extends Thread {
    private int id;
    private int deltaS;
    private int deltaU;
    private SistemaOperacional sistema;
    private Recurso recursoUsado = null;
    private java.util.function.Consumer<String> logger;

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

    public String status() {
        return "Processo " + id + (recursoUsado == null ? " [bloqueado]" : " usando " + recursoUsado.getNome());
    }

    @Override
    public void run() {
        while (!isInterrupted()) {
            try {
                Thread.sleep(deltaS * 1000L);
                logger.accept("Processo " + id + " solicitando recurso...");
                // Tenta obter o recurso, bloqueando até conseguir
                while (!isInterrupted()) {
                    recursoUsado = sistema.solicitarRecurso(this);
                    if (recursoUsado != null) {
                        logger.accept("Processo " + id + " usando recurso " + recursoUsado.getNome());
                        Thread.sleep(deltaU * 1000L);
                        sistema.liberarRecurso(this, recursoUsado);
                        logger.accept("Processo " + id + " liberou recurso " + recursoUsado.getNome());
                        recursoUsado = null;
                        break; // Sai do loop de solicitação e volta ao início do while externo
                    } else {
                        // Se não conseguiu, dorme um pouco antes de tentar de novo
                        Thread.sleep(500); // 0.5s, pode ajustar conforme desejar
                    }
                }
            } catch (InterruptedException e) {
                break;
            }
        }
    }
}