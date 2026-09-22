package sg.edu.nus.facilityflow.ui;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import javafx.concurrent.Task;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import sg.edu.nus.facilityflow.service.AuthorizationException;
import sg.edu.nus.facilityflow.service.ValidationException;

/** Shared JavaFX task boundary; service/storage code remains JavaFX-independent. */
public final class UiTasks {
    private final Executor executor;
    private final IntegerProperty pending = new SimpleIntegerProperty();
    private final BooleanBinding busy = pending.greaterThan(0);

    public UiTasks(Executor executor) {
        this.executor = executor;
    }

    public <T> void run(Callable<T> work, Consumer<T> success, Consumer<Throwable> failure) {
        pending.set(pending.get() + 1);
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(event -> {
            pending.set(pending.get() - 1);
            success.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            pending.set(pending.get() - 1);
            failure.accept(task.getException());
        });
        executor.execute(task);
    }

    public BooleanBinding busy() {
        return busy;
    }

    public static String safeMessage(Throwable error) {
        if (error instanceof AuthorizationException || error instanceof ValidationException) {
            return error.getMessage();
        }
        return "The operation could not be completed. Your input has been kept. Please try again.";
    }
}
