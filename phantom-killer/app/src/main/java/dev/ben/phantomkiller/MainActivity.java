package dev.ben.phantomkiller;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import rikka.shizuku.Shizuku;

public final class MainActivity extends Activity {
    private static final int REQUEST_SHIZUKU = 7412;

    private static final int STATE_ERROR = -1;
    private static final int STATE_INACTIVE = 0;
    private static final int STATE_PARTIAL = 1;
    private static final int STATE_OK = 2;
    private static final int STATE_UNCHECKED = 3;

    private static final int BG = Color.rgb(8, 11, 17);
    private static final int SURFACE = Color.rgb(17, 24, 36);
    private static final int SURFACE_ALT = Color.rgb(22, 31, 45);
    private static final int BORDER = Color.rgb(39, 51, 69);
    private static final int TEXT = Color.rgb(242, 246, 251);
    private static final int MUTED = Color.rgb(148, 164, 185);
    private static final int GREEN = Color.rgb(114, 240, 182);
    private static final int GREEN_DARK = Color.rgb(9, 30, 24);
    private static final int YELLOW = Color.rgb(246, 198, 91);
    private static final int RED = Color.rgb(255, 123, 136);

    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private TextView shizukuPill;
    private TextView shizukuDetail;
    private TextView overallPill;
    private TextView overallDetail;
    private TextView activityLog;
    private TextView[] statePills = new TextView[3];
    private TextView[] valueLabels = new TextView[3];
    private Button applyButton;
    private Button checkButton;
    private Runnable pendingPermissionAction;
    private Shizuku.UserServiceArgs userServiceArgs;
    private ICommandService commandService;
    private Runnable pendingUserServiceAction;
    private boolean userServiceBinding;
    private boolean busy;

    private final ServiceConnection userServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ICommandService connected = ICommandService.Stub.asInterface(service);
            runOnUiThread(() -> {
                commandService = connected;
                userServiceBinding = false;
                refreshShizukuStatus();
                Runnable action = pendingUserServiceAction;
                pendingUserServiceAction = null;
                if (action != null) {
                    action.run();
                }
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            runOnUiThread(() -> {
                commandService = null;
                userServiceBinding = false;
                refreshShizukuStatus();
            });
        }
    };

    private final Shizuku.OnRequestPermissionResultListener permissionListener =
            (requestCode, grantResult) -> runOnUiThread(() -> {
                if (requestCode != REQUEST_SHIZUKU) {
                    return;
                }
                Runnable action = pendingPermissionAction;
                pendingPermissionAction = null;
                refreshShizukuStatus();
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    if (action != null) {
                        withUserService(action);
                    }
                } else {
                    setOverall(
                            "SHIZUKU PERMISSION REQUIRED",
                            "Grant Phantom Killer permission in the Shizuku prompt, then try again.",
                            STATE_ERROR
                    );
                    Toast.makeText(this, "Shizuku permission was not granted", Toast.LENGTH_LONG).show();
                }
            });

    private final Shizuku.OnBinderReceivedListener binderReceivedListener =
            () -> runOnUiThread(this::refreshShizukuStatus);
    private final Shizuku.OnBinderDeadListener binderDeadListener =
            () -> runOnUiThread(this::refreshShizukuStatus);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        buildUi();
        userServiceArgs = new Shizuku.UserServiceArgs(
                new ComponentName(this, ShellUserService.class)
        )
                .tag("phantom-killer-shell")
                .processNameSuffix("shell")
                .debuggable(BuildConfig.DEBUG)
                .daemon(false)
                .version(1);

        Shizuku.addRequestPermissionResultListener(permissionListener);
        Shizuku.addBinderReceivedListener(binderReceivedListener);
        Shizuku.addBinderDeadListener(binderDeadListener);

        refreshShizukuStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshShizukuStatus();
    }

    @Override
    protected void onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener);
        Shizuku.removeBinderReceivedListener(binderReceivedListener);
        Shizuku.removeBinderDeadListener(binderDeadListener);
        if (userServiceArgs != null && (commandService != null || userServiceBinding)) {
            try {
                Shizuku.unbindUserService(userServiceArgs, userServiceConnection, true);
            } catch (Throwable ignored) {
                // Shizuku may already have stopped; no cleanup is needed in that case.
            }
        }
        worker.shutdownNow();
        super.onDestroy();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(22), dp(22), dp(22), dp(26));
        scroll.addView(page, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));
        setContentView(scroll);

        LinearLayout header = horizontal();
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView logo = new TextView(this);
        logo.setText("PK");
        logo.setTextColor(BG);
        logo.setTextSize(18);
        logo.setTypeface(Typeface.DEFAULT_BOLD);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(shape(GREEN, dp(17), 0, Color.TRANSPARENT));
        header.addView(logo, new LinearLayout.LayoutParams(dp(54), dp(54)));

        LinearLayout titles = vertical();
        titles.setPadding(dp(14), 0, 0, 0);
        TextView appTitle = text("Phantom Killer", 25, TEXT, true);
        TextView appSubtitle = text("ANDROID PROCESS FIX", 11, MUTED, true);
        appSubtitle.setLetterSpacing(0.12f);
        titles.addView(appTitle);
        addGap(titles, 4);
        titles.addView(appSubtitle);
        header.addView(titles, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        page.addView(header);
        addGap(page, 24);

        TextView intro = text(
                "Apply the three system settings through Shizuku, then verify their live values.",
                15, MUTED, false
        );
        intro.setLineSpacing(dp(4), 1f);
        page.addView(intro);
        addGap(page, 18);

        LinearLayout connectionCard = card();
        TextView connectionEyebrow = text("PRIVILEGED EXECUTION", 10, MUTED, true);
        connectionEyebrow.setLetterSpacing(0.12f);
        connectionCard.addView(connectionEyebrow);
        addGap(connectionCard, 12);
        shizukuPill = pill("CHECKING SHIZUKU", STATE_UNCHECKED);
        connectionCard.addView(shizukuPill, wrapWidth());
        addGap(connectionCard, 10);
        shizukuDetail = text("Checking whether the Shizuku service is available…", 13, MUTED, false);
        shizukuDetail.setLineSpacing(dp(3), 1f);
        connectionCard.addView(shizukuDetail);
        page.addView(connectionCard, marginBottom(14));
        
        LinearLayout liveCard = card();
        LinearLayout liveHead = horizontal();
        liveHead.setGravity(Gravity.CENTER_VERTICAL);
        TextView liveTitle = text("Live system status", 18, TEXT, true);
        liveHead.addView(liveTitle, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        overallPill = pill("NOT CHECKED", STATE_UNCHECKED);
        liveHead.addView(overallPill, wrapWidth());
        liveCard.addView(liveHead);
        addGap(liveCard, 10);
        overallDetail = text("Tap CHECK STATUS to read the current device values.", 13, MUTED, false);
        overallDetail.setLineSpacing(dp(3), 1f);
        liveCard.addView(overallDetail);
        addGap(liveCard, 16);

        liveCard.addView(makeCheckRow(
                0,
                "DeviceConfig sync",
                "Must stay disabled across reboots",
                "persistent"
        ));
        addGap(liveCard, 10);
        liveCard.addView(makeCheckRow(
                1,
                "Phantom process limit",
                "Maximum configured process limit",
                "2147483647"
        ));
        addGap(liveCard, 10);
        liveCard.addView(makeCheckRow(
                2,
                "Phantom process monitor",
                "Global monitoring setting",
                "false"
        ));
        page.addView(liveCard, marginBottom(14));

        TextView warning = text(
                "Important: persistent DeviceConfig sync changes can limit Android's ability to restore configuration after some system failures. Use this at your own risk.",
                12, YELLOW, false
        );
        warning.setLineSpacing(dp(3), 1f);
        warning.setPadding(dp(2), dp(2), dp(2), dp(2));
        page.addView(warning);
        addGap(page, 18);

        applyButton = actionButton("APPLY PHANTOM KILLER", GREEN, BG);
        applyButton.setOnClickListener(v -> requireShizukuPermission(this::applyFix));
        page.addView(applyButton, fullWidth());
        addGap(page, 10);

        checkButton = actionButton("CHECK STATUS", SURFACE_ALT, TEXT);
        checkButton.setBackground(shape(SURFACE_ALT, dp(14), dp(1), BORDER));
        checkButton.setOnClickListener(v -> requireShizukuPermission(this::checkLiveStatus));
        page.addView(checkButton, fullWidth());
        addGap(page, 16);

        activityLog = text("Ready. Start Shizuku, grant access, and apply the fix.", 12, MUTED, false);
        activityLog.setGravity(Gravity.CENTER);
        activityLog.setLineSpacing(dp(3), 1f);
        page.addView(activityLog);
    }

    private View makeCheckRow(int index, String title, String description, String expected) {
        LinearLayout row = vertical();
        row.setPadding(dp(14), dp(13), dp(14), dp(13));
        row.setBackground(shape(SURFACE_ALT, dp(14), dp(1), BORDER));

        LinearLayout top = horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView titleView = text(title, 14, TEXT, true);
        top.addView(titleView, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        statePills[index] = pill("NOT CHECKED", STATE_UNCHECKED);
        top.addView(statePills[index], wrapWidth());
        row.addView(top);
        addGap(row, 5);

        TextView descriptionView = text(description, 12, MUTED, false);
        row.addView(descriptionView);
        addGap(row, 8);

        LinearLayout expectedLine = horizontal();
        TextView expectedLabel = text("Expected", 11, MUTED, false);
        expectedLine.addView(expectedLabel);
        TextView expectedValue = text("  " + expected, 12, GREEN, true);
        expectedValue.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        expectedLine.addView(expectedValue);
        row.addView(expectedLine);
        addGap(row, 5);

        valueLabels[index] = text("Current: not checked", 11, MUTED, false);
        valueLabels[index].setTypeface(Typeface.MONOSPACE);
        row.addView(valueLabels[index]);
        return row;
    }

    private void requireShizukuPermission(Runnable action) {
        if (busy) {
            return;
        }
        try {
            if (!Shizuku.pingBinder()) {
                refreshShizukuStatus();
                setOverall(
                        "SHIZUKU NOT RUNNING",
                        "Open Shizuku and start its service first. Phantom Killer will not run commands without it.",
                        STATE_ERROR
                );
                Toast.makeText(this, "Start Shizuku first", Toast.LENGTH_LONG).show();
                return;
            }
            if (Shizuku.isPreV11()) {
                setOverall("SHIZUKU VERSION UNSUPPORTED",
                        "Update Shizuku to a current version, then reopen Phantom Killer.",
                        STATE_ERROR);
                return;
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                withUserService(action);
            } else {
                pendingPermissionAction = action;
                setOverall("WAITING FOR SHIZUKU",
                        "Approve the permission request to continue.", STATE_PARTIAL);
                Shizuku.requestPermission(REQUEST_SHIZUKU);
            }
        } catch (Throwable error) {
            setOverall("SHIZUKU ERROR",
                    safeMessage(error), STATE_ERROR);
        }
    }

    private void withUserService(Runnable action) {
        if (commandService != null) {
            action.run();
            return;
        }

        pendingUserServiceAction = action;
        if (userServiceBinding) {
            setOverall("CONNECTING USER SERVICE",
                    "Waiting for Shizuku to start the privileged command service…", STATE_PARTIAL);
            return;
        }

        userServiceBinding = true;
        setOverall("CONNECTING USER SERVICE",
                "Starting Shizuku's privileged command service…", STATE_PARTIAL);
        try {
            Shizuku.bindUserService(userServiceArgs, userServiceConnection);
        } catch (Throwable error) {
            userServiceBinding = false;
            pendingUserServiceAction = null;
            setOverall("USER SERVICE ERROR", safeMessage(error), STATE_ERROR);
        }
    }

    private void refreshShizukuStatus() {
        try {
            if (!Shizuku.pingBinder()) {
                setPill(shizukuPill, "SHIZUKU NOT RUNNING", STATE_ERROR);
                shizukuDetail.setText("Start Shizuku before applying or checking the system settings.");
                return;
            }
            if (Shizuku.isPreV11()) {
                setPill(shizukuPill, "UPDATE SHIZUKU", STATE_ERROR);
                shizukuDetail.setText("This API version is not supported. Install a current Shizuku version.");
                return;
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                setPill(shizukuPill, "CONNECTED · PERMISSION GRANTED", STATE_OK);
                shizukuDetail.setText("Ready to execute commands with Shizuku's shell identity.");
            } else {
                setPill(shizukuPill, "CONNECTED · PERMISSION NEEDED", STATE_PARTIAL);
                shizukuDetail.setText("Shizuku is running. Tap a button and approve Phantom Killer's access request.");
            }
        } catch (Throwable error) {
            setPill(shizukuPill, "SHIZUKU ERROR", STATE_ERROR);
            shizukuDetail.setText(safeMessage(error));
        }
    }

    private void applyFix() {
        setBusy(true);
        setOverall("APPLYING FIX", "Sending all three commands through Shizuku…", STATE_PARTIAL);
        activityLog.setText("Applying settings, then reading them back from Android…");

        worker.execute(() -> {
            String[][] commands = new String[][]{
                    {"/system/bin/device_config", "set_sync_disabled_for_tests", "persistent"},
                    {"/system/bin/device_config", "put", "activity_manager", "max_phantom_processes", "2147483647"},
                    {"/system/bin/settings", "put", "global", "settings_enable_monitor_phantom_procs", "false"}
            };
            String[] names = new String[]{
                    "DeviceConfig sync",
                    "Phantom process limit",
                    "Phantom process monitor"
            };

            int executed = 0;
            List<String> failures = new ArrayList<>();
            for (int i = 0; i < commands.length; i++) {
                try {
                    ShellResult result = runShell(commands[i]);
                    if (result.exitCode == 0 && !looksLikeError(result.output)) {
                        executed++;
                    } else {
                        failures.add(names[i] + ": " + displayOutput(result.output, "exit code " + result.exitCode));
                    }
                } catch (Throwable error) {
                    failures.add(names[i] + ": " + safeMessage(error));
                }
            }

            List<CheckResult> checks = performChecks();
            final int executedFinal = executed;
            final List<String> failuresFinal = failures;
            runOnUiThread(() -> {
                renderChecks(checks, true, executedFinal, failuresFinal);
                setBusy(false);
            });
        });
    }

    private void checkLiveStatus() {
        setBusy(true);
        setOverall("CHECKING LIVE VALUES", "Reading the current values from Android through Shizuku…", STATE_PARTIAL);
        activityLog.setText("Live check running…");

        worker.execute(() -> {
            List<CheckResult> checks = performChecks();
            runOnUiThread(() -> {
                renderChecks(checks, false, 0, new ArrayList<>());
                setBusy(false);
            });
        });
    }

    private List<CheckResult> performChecks() {
        List<CheckResult> results = new ArrayList<>();
        results.add(checkSyncMode());
        results.add(checkSetting(
                "/system/bin/device_config",
                new String[]{"get", "activity_manager", "max_phantom_processes"},
                "2147483647"
        ));
        results.add(checkSetting(
                "/system/bin/settings",
                new String[]{"get", "global", "settings_enable_monitor_phantom_procs"},
                "false"
        ));
        return results;
    }

    private CheckResult checkSyncMode() {
        String current = "";
        String issue = "";
        try {
            ShellResult modern = runShell("/system/bin/device_config", "get_sync_disabled_for_tests");
            current = cleanOutput(modern.output).toLowerCase(Locale.ROOT);
            if (modern.exitCode == 0) {
                if ("persistent".equals(current)) {
                    return new CheckResult(STATE_OK, "persistent", "Current value matches.");
                }
                if ("until_reboot".equals(current)) {
                    return new CheckResult(STATE_PARTIAL, "until_reboot", "Disabled, but it may reset after reboot.");
                }
                if ("none".equals(current)) {
                    return new CheckResult(STATE_INACTIVE, "none", "DeviceConfig sync is not disabled.");
                }
            }
            issue = displayOutput(modern.output, "get_sync_disabled_for_tests unavailable");
        } catch (Throwable error) {
            issue = safeMessage(error);
        }

        // Android 12 exposes the legacy boolean command rather than the newer mode command.
        try {
            ShellResult legacy = runShell("/system/bin/device_config", "is_sync_disabled_for_tests");
            String legacyValue = cleanOutput(legacy.output).toLowerCase(Locale.ROOT);
            if (legacy.exitCode == 0 && "false".equals(legacyValue)) {
                return new CheckResult(STATE_INACTIVE, "false", "Legacy API reports sync enabled.");
            }
            if (legacy.exitCode == 0 && "true".equals(legacyValue)) {
                ShellResult stored = runShell("/system/bin/settings", "get", "global", "device_config_sync_disabled");
                String storedValue = cleanOutput(stored.output);
                if (stored.exitCode == 0 && "1".equals(storedValue)) {
                    return new CheckResult(STATE_OK, "true · stored mode 1", "Legacy API confirms persistent mode.");
                }
                if (stored.exitCode == 0 && "0".equals(storedValue)) {
                    return new CheckResult(STATE_PARTIAL, "true · stored mode 0", "Sync is disabled, but persistent mode could not be confirmed.");
                }
                return new CheckResult(STATE_PARTIAL, "true", "Sync is disabled; persistent mode could not be verified.");
            }
            String legacyError = displayOutput(legacy.output, "legacy check unavailable");
            return new CheckResult(STATE_ERROR, "unreadable", issue + "; " + legacyError);
        } catch (Throwable error) {
            return new CheckResult(STATE_ERROR, "unreadable", issue + "; " + safeMessage(error));
        }
    }

    private CheckResult checkSetting(String executable, String[] arguments, String expected) {
        String[] command = new String[arguments.length + 1];
        command[0] = executable;
        System.arraycopy(arguments, 0, command, 1, arguments.length);
        try {
            ShellResult result = runShell(command);
            String current = cleanOutput(result.output);
            if (result.exitCode != 0 || looksLikeError(current)) {
                return new CheckResult(STATE_ERROR, "unreadable", displayOutput(current, "exit code " + result.exitCode));
            }
            if (expected.equalsIgnoreCase(current)) {
                return new CheckResult(STATE_OK, current, "Current value matches.");
            }
            return new CheckResult(STATE_INACTIVE, current.isEmpty() ? "(empty)" : current,
                    "Current value differs from the expected value.");
        } catch (Throwable error) {
            return new CheckResult(STATE_ERROR, "unreadable", safeMessage(error));
        }
    }

    private ShellResult runShell(String... command) throws Exception {
        ICommandService service = commandService;
        if (service == null) {
            throw new IOException("Shizuku UserService is not connected");
        }
        Bundle result = service.execute(command);
        if (result == null) {
            throw new IOException("Shizuku UserService returned no result");
        }
        return new ShellResult(
                result.getInt("exitCode", -1),
                result.getString("output", "")
        );
    }

    private void renderChecks(List<CheckResult> checks, boolean wasApply, int commandsExecuted, List<String> failures) {
        int matched = 0;
        int errors = 0;
        for (int i = 0; i < checks.size(); i++) {
            CheckResult result = checks.get(i);
            setPill(statePills[i], stateText(result.state), result.state);
            valueLabels[i].setText("Current: " + result.current + " · " + result.detail);
            valueLabels[i].setTextColor(result.state == STATE_OK ? GREEN : result.state == STATE_PARTIAL ? YELLOW : result.state == STATE_INACTIVE ? RED : MUTED);
            if (result.state == STATE_OK) {
                matched++;
            } else if (result.state == STATE_ERROR) {
                errors++;
            }
        }

        String checkedAt = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        String detail = matched + "/3 settings verified · checked at " + checkedAt;
        if (wasApply) {
            detail = commandsExecuted + "/3 commands executed. " + detail;
            if (!failures.isEmpty()) {
                detail += " Apply errors: " + String.join(" | ", failures);
            }
        }

        if (matched == 3) {
            setOverall("PHANTOM KILLER ACTIVE", detail, STATE_OK);
        } else if (errors > 0) {
            setOverall("STATUS INCOMPLETE", detail + " At least one setting could not be read.", STATE_ERROR);
        } else if (matched > 0 || checks.stream().anyMatch(c -> c.state == STATE_PARTIAL)) {
            setOverall("PARTIALLY ACTIVE", detail, STATE_PARTIAL);
        } else {
            setOverall("NOT FULLY ACTIVE", detail, STATE_INACTIVE);
        }

        if (wasApply && !failures.isEmpty()) {
            activityLog.setText("Some apply commands reported errors. Read the live values above and check Shizuku/device compatibility.");
        } else if (matched == 3) {
            activityLog.setText("Verified: all three live values match. Tap CHECK STATUS again whenever you want a fresh reading.");
        } else {
            activityLog.setText("The status shown above comes from live system reads, not just command execution results.");
        }
        refreshShizukuStatus();
    }

    private void setBusy(boolean value) {
        busy = value;
        applyButton.setEnabled(!value);
        checkButton.setEnabled(!value);
        applyButton.setAlpha(value ? 0.62f : 1f);
        checkButton.setAlpha(value ? 0.62f : 1f);
    }

    private void setOverall(String title, String detail, int state) {
        setPill(overallPill, title, state);
        overallDetail.setText(detail);
    }

    private void setPill(TextView view, String label, int state) {
        view.setText(label);
        int foreground;
        int background;
        if (state == STATE_OK) {
            foreground = GREEN;
            background = GREEN_DARK;
        } else if (state == STATE_PARTIAL) {
            foreground = YELLOW;
            background = Color.rgb(43, 34, 17);
        } else if (state == STATE_INACTIVE || state == STATE_ERROR) {
            foreground = RED;
            background = Color.rgb(44, 22, 28);
        } else {
            foreground = MUTED;
            background = Color.rgb(32, 42, 56);
        }
        view.setTextColor(foreground);
        view.setBackground(shape(background, dp(30), 0, Color.TRANSPARENT));
    }

    private String stateText(int state) {
        if (state == STATE_OK) return "VERIFIED";
        if (state == STATE_PARTIAL) return "PARTIAL";
        if (state == STATE_INACTIVE) return "NOT ACTIVE";
        if (state == STATE_ERROR) return "READ ERROR";
        return "NOT CHECKED";
    }

    private boolean looksLikeError(String output) {
        String value = output == null ? "" : output.toLowerCase(Locale.ROOT);
        return value.contains("permission denial")
                || value.contains("permission denied")
                || value.contains("securityexception")
                || value.contains("unknown command")
                || value.contains("not found")
                || value.contains("failed to")
                || value.contains("exception");
    }

    private String cleanOutput(String output) {
        return output == null ? "" : output.trim().replace("\r", "");
    }

    private String displayOutput(String output, String fallback) {
        String value = cleanOutput(output);
        if (value.isEmpty()) {
            return fallback;
        }
        return value.length() > 120 ? value.substring(0, 120) : value;
    }

    private String safeMessage(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return error.getClass().getSimpleName();
        }
        return message;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setIncludeFontPadding(true);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return view;
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout horizontal() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        return layout;
    }

    private LinearLayout card() {
        LinearLayout layout = vertical();
        layout.setPadding(dp(16), dp(16), dp(16), dp(16));
        layout.setBackground(shape(SURFACE, dp(18), dp(1), BORDER));
        return layout;
    }

    private Button actionButton(String label, int background, int foreground) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(13);
        button.setTextColor(foreground);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), dp(10), dp(12), dp(10));
        button.setBackground(shape(background, dp(14), 0, Color.TRANSPARENT));
        return button;
    }

    private TextView pill(String label, int state) {
        TextView view = text(label, 9, MUTED, true);
        view.setLetterSpacing(0.025f);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(9), dp(6), dp(9), dp(6));
        setPill(view, label, state);
        return view;
    }

    private GradientDrawable shape(int color, float radius, int strokeWidth, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) {
            drawable.setStroke(strokeWidth, strokeColor);
        }
        return drawable;
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(54)
        );
    }

    private LinearLayout.LayoutParams wrapWidth() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams marginBottom(int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = dp(bottom);
        return params;
    }

    private void addGap(LinearLayout layout, int height) {
        View gap = new View(this);
        layout.addView(gap, new LinearLayout.LayoutParams(1, dp(height)));
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class ShellResult {
        final int exitCode;
        final String output;

        ShellResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output == null ? "" : output;
        }
    }

    private static final class CheckResult {
        final int state;
        final String current;
        final String detail;

        CheckResult(int state, String current, String detail) {
            this.state = state;
            this.current = current == null || current.trim().isEmpty() ? "(empty)" : current.trim();
            this.detail = detail == null ? "" : detail;
        }
    }
}
