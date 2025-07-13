
public class Processo extends Thread{
    private long id;
    private int tempoSolicitacao; // Tempo em segundos que o processo leva para solicitar um recurso
    private int tempoUtilizacao; // Tempo em segundos que o processo leva para usar o recurso
    private boolean acordado = true;

    public Processo(int id, int tempoSolicitacao, int tempoUtilizacao) {
        this.id = id;
        this.tempoSolicitacao = tempoSolicitacao;
        this.tempoUtilizacao = tempoUtilizacao;
    }

    public void setIsAwake(boolean acordado) {
        this.acordado = acordado;
    }

    public boolean isAwake() {
        return acordado;
    }

    public long getId() {
        return id;
    }
    public int getTempoSolicitacao() {
        return tempoSolicitacao;
    }

    public int getTempoUtilizacao() {
        return tempoUtilizacao;
    }


    @Override
    public void run() {
        while(true){
            long inicio = System.currentTimeMillis();
            while(System.currentTimeMillis() - inicio < tempoSolicitacao * 1000) {
                // Simula o tempo de solicitação do recurso
            }
            

        }
    }
}