public class Recurso {
    private int id;
    private String nome;
    private int total;
    private int disponivel;

    public Recurso(int id, String nome, int total) {
        this.id = id;
        this.nome = nome;
        this.total = total;
        this.disponivel = total;
    }

    public boolean alocar() {
        System.out.println("Recurso " + nome + ": tentando alocar, disponivel=" + disponivel);
        if (disponivel > 0) {
            disponivel--;
            System.out.println("Recurso " + nome + ": alocado, disponivel=" + disponivel);
            return true;
        }
        System.out.println("Recurso " + nome + ": não alocado, disponivel=" + disponivel);
        return false;
    }

    public void liberar() {
        if (disponivel < total) {
            disponivel++;
            System.out.println("Recurso " + nome + ": liberado, disponivel=" + disponivel);
        }
    }

    public int getId() { return id; }
    public String getNome() { return nome; }
    public int getDisponivel() { return disponivel; }
    public int getTotal() { return total; }

    @Override
    public String toString() {
        return nome + " (ID: " + id + ", " + disponivel + "/" + total + ")";
    }
}