import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SistemaOperacional extends Thread{
    private List<Recurso> recursos = Collections.synchronizedList(new ArrayList<>());
    private List<Processo> processos = Collections.synchronizedList(new ArrayList<>());
}
