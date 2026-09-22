package exotic.app.planta.service.controles;

import org.hibernate.exception.ConstraintViolationException;
import org.postgresql.util.PSQLException;

public class CodigoPlanDuplicadoException extends RuntimeException {
    private static final String CONSTRAINT = "control_plan_codigo_key";

    public CodigoPlanDuplicadoException() {
        this(null);
    }

    public CodigoPlanDuplicadoException(Throwable cause) {
        super("Ya existe un plan con ese código. Utilice un código diferente.", cause);
    }

    static boolean correspondeA(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && "23505".equals(violation.getSQLState())
                    && CONSTRAINT.equals(violation.getConstraintName())) return true;
            if (cause instanceof PSQLException postgres
                    && "23505".equals(postgres.getSQLState())
                    && postgres.getServerErrorMessage() != null
                    && CONSTRAINT.equals(postgres.getServerErrorMessage().getConstraint())) return true;
            if (cause == cause.getCause()) break;
        }
        return false;
    }
}
