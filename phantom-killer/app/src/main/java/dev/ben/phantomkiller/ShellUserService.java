package dev.ben.phantomkiller;

import android.os.Bundle;
import android.os.RemoteException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public final class ShellUserService extends ICommandService.Stub {
    public ShellUserService() {
        // Shizuku instantiates this class in its privileged UserService process.
    }

    @Override
    public Bundle execute(String[] command) throws RemoteException {
        Bundle result = new Bundle();
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(command);
            boolean completed = process.waitFor(12, TimeUnit.SECONDS);
            if (!completed) {
                process.destroy();
                result.putInt("exitCode", 124);
                result.putString("output", "Command timed out after 12 seconds");
                return result;
            }

            String stdout = readAll(process.getInputStream());
            String stderr = readAll(process.getErrorStream());
            result.putInt("exitCode", process.exitValue());
            result.putString("output", (stdout + "\n" + stderr).trim());
        } catch (Throwable error) {
            if (process != null) {
                process.destroy();
            }
            result.putInt("exitCode", -1);
            String message = error.getMessage();
            result.putString("output", message == null ? error.getClass().getSimpleName() : message);
        }
        return result;
    }

    @Override
    public void destroy() {
        System.exit(0);
    }

    private String readAll(InputStream stream) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (output.length() > 0) {
                    output.append('\n');
                }
                output.append(line);
            }
        }
        return output.toString().trim();
    }
}
