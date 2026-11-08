package com.shopsphere.client.util;

import com.shopsphere.client.network.ApiException;
import java.awt.Component;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

/** Runs server calls off the Swing thread so the window never freezes, then reports back on the Swing thread. */
public final class Async {

    @FunctionalInterface
    public interface Call<T> {
        T run() throws ApiException;
    }

    private Async() { }

    public static <T> void run(Component owner, Call<T> call, Consumer<T> onSuccess) {
        run(owner, call, onSuccess, e -> showError(owner, e.getMessage()));
    }

    public static <T> void run(Component owner, Call<T> call, Consumer<T> onSuccess, Consumer<ApiException> onError) {
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return call.run();
            }

            @Override
            protected void done() {
                try {
                    onSuccess.accept(get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof ApiException api) {
                        onError.accept(api);
                    } else {
                        onError.accept(new ApiException(null, "Something went wrong. Please try again"));
                    }
                }
            }
        }.execute();
    }

    public static void showError(Component owner, String message) {
        JOptionPane.showMessageDialog(owner, message, "ShopSphere", JOptionPane.WARNING_MESSAGE);
    }

    public static void showInfo(Component owner, String message) {
        JOptionPane.showMessageDialog(owner, message, "ShopSphere", JOptionPane.INFORMATION_MESSAGE);
    }
}
