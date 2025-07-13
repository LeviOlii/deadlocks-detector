import java.util.concurrent.Semaphore;

public class Recurso {
    private int id;
    private String nome;
    private Semaphore quantidadeTotal;

    public Recurso(int id, String nome, int quantidadeTotal) {
        this.id = id;
        this.nome = nome;
        this.quantidadeTotal = new Semaphore(quantidadeTotal);
    }

    public void adquirirRecurso() throws InterruptedException {
        quantidadeTotal.acquire();
    }

    public void liberarRecurso() {
        quantidadeTotal.release();
    }

    public int getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public Semaphore getSemaforoQuantidadeTotal() {
        return quantidadeTotal;
    }
}
