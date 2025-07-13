import java.util.ArrayList;


public class Processo extends Thread{
    private int id;
    private int tempoSolicitacao; // Tempo em segundos que o processo leva para solicitar um recurso
    private int tempoUtilizacao; // Tempo em segundos que o processo leva para usar o recurso
    private boolean acordado = true;

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