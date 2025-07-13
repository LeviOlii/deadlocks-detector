import java.util.concurrent.Semaphore;

public class Recurso {
    private int id;
    private String nome;
    private int quantidadeTotal;
    private Semaphore semaforo;


    public Recurso(int id, String nome, int quantidadeTotal) {
        this.id = id;
        this.nome = nome;
        this.quantidadeTotal = quantidadeTotal;
        this.semaforo = new Semaphore(quantidadeTotal);
    }

    public void adquirirRecurso() throws InterruptedException {
        semaforo.acquire();
    }

    public void liberarRecurso() {
        semaforo.release();
    }

    public int getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public int getQuantidadeTotal() {
        return quantidadeTotal;
    }

    public Semaphore getSemaforo() {
        return semaforo;
    }
}
