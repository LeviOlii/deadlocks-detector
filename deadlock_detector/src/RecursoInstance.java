import java.util.Objects;

public class RecursoInstance {
    private Recurso recurso;
    private int instanceId;

    public RecursoInstance(Recurso recurso, int instanceId) {
        this.recurso = recurso;
        this.instanceId = instanceId;
    }

    public void setRecurso(Recurso recurso) {
        this.recurso = recurso;
    }

    public Recurso getRecurso() {
        return recurso;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        RecursoInstance that = (RecursoInstance) o;
        return instanceId == that.instanceId && recurso.equals(that.recurso);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recurso, instanceId);
    }

    @Override
    public String toString() {
        return "RecursoInstance{recurso=" + recurso.getNome() + ", instanceId=" + instanceId + "}";
    }
}