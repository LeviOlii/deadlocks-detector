import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Semaphore;

public class SistemaOperacional extends Thread{
    private List<Recurso> recursos = Collections.synchronizedList(new ArrayList<>());
    private List<Processo> processos = Collections.synchronizedList(new ArrayList<>());
    private Random random = new Random();
    private Semaphore mutex = new Semaphore(1);

    public void adicionarRecurso(Recurso recurso) {
        recursos.add(recurso);
    }

    public void adicionarProcesso(Processo processo) {
        processos.add(processo);
    }

    // Método para solicitar um recurso para um processo, utilizado por Processos
    public void solicitarRecurso(Processo processo) {
        if(recursos.isEmpty()) {
            System.out.println("Nenhum recurso disponível para o processo " + processo.getId());
            return;
        }
        try {
            mutex.acquire();
            Recurso recurso = recursos.get(random.nextInt(recursos.size()));
            if(recurso.getSemaforoQuantidadeTotal().tryAcquire()){ // Tenta adquirir o recurso, se não conseguir, retorna false
                System.out.println("Processo " + processo.getId() + " adquiriu o recurso " + recurso.getNome());
                processo.setIsAwake(true); // Processo agora está usando o recurso
                mutex.release();
                
                // Simula o tempo de utilização do recurso
                long inicio = System.currentTimeMillis();
                while(System.currentTimeMillis() - inicio < processo.getTempoUtilizacao() * 1000) {
                    System.out.println("Processo " + processo.getId() + " está utilizando o recurso " + recurso.getNome());
                }
                
                recurso.liberarRecurso(); // Libera o recurso após uso
                System.out.println("Processo " + processo.getId() + " liberou o recurso " + recurso.getNome());
            } else {
                System.out.println("Processo " + processo.getId() + " não conseguiu adquirir o recurso " + recurso.getNome() + ", está BLOQUEADO.");
                processo.setIsAwake(false); // Processo não conseguiu usar o recurso, então está dormindo
                mutex.release();
            }
            

        }catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
