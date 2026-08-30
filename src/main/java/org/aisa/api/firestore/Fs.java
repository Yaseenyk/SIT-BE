package org.aisa.api.firestore;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Turns Firestore's asynchronous API into the synchronous calls this application wants.
 *
 * <p>Every request handler here is a blocking servlet method that needs its data before it
 * can build a response, so there is nothing useful to do with a future except wait on it.
 * Making that explicit in one helper keeps the eight repositories free of identical
 * try/catch blocks — and, more usefully, keeps the interrupt handling correct in one place
 * rather than eight, where it would inevitably be wrong in at least one.
 */
public final class Fs {

    private Fs() {
    }

    public static <T> T block(ApiFuture<T> future, String what) {
        try {
            return future.get();
        } catch (InterruptedException ex) {
            // Restore the flag. Swallowing it leaves the thread looking un-interrupted to
            // everything further up, which is how shutdown hangs.
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while " + what, ex);
        } catch (ExecutionException ex) {
            // Unwrap: the ExecutionException itself carries no useful message, and the
            // cause is what names the actual Firestore failure.
            throw new IllegalStateException("Firestore call failed while " + what, ex.getCause());
        }
    }

    public static List<QueryDocumentSnapshot> documents(
            ApiFuture<com.google.cloud.firestore.QuerySnapshot> future, String what) {
        return block(future, what).getDocuments();
    }
}
