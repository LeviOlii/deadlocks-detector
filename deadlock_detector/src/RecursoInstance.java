import java.util.Objects;
public class RecursoInstance {
    private final Recurso recurso;
    private final int instanceId;

    public RecursoInstance(Recurso recurso, int instanceId) {
        this.recurso = recurso;
        this.instanceId = instanceId;
    }

    public Recurso getRecurso() {
        return recurso;
    }

    public int getInstanceId() {
        return instanceId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RecursoInstance that = (RecursoInstance) o;
        return instanceId == that.instanceId && recurso.equals(that.recurso);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recurso, instanceId);
    }
}