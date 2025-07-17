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
        if (disponivel > 0) {
            disponivel--;
            return true;
        }
        return false;
    }

    public void liberar() {
        disponivel++;
    }

    public int getId() { return id; }
    public String getNome() { return nome; }
    public int getDisponivel() { return disponivel; }
    public int getTotal() { return total; }

    @Override
    public String toString() {
        return nome + " (" + disponivel + "/" + total + ")";
    }
}