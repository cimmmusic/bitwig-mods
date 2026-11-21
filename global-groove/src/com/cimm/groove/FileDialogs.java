package com.cimm.groove;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * The operating system's own open and save dialogs, run as a separate process (osascript on macOS, PowerShell's
 * Windows Forms dialogs on Windows, zenity or kdialog on Linux). Call off the UI thread: these block until closed.
 */
final class FileDialogs {
    private FileDialogs() {}

    private static final String OS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    static final boolean MAC = OS.contains("mac"), WINDOWS = OS.contains("win");

    /** Groove files (.agr, .mid, .midi) chosen in an open dialog, several at once; empty when cancelled. */
    static List<Path> openGrooves(String prompt, Path start) throws Exception {
        List<String> lines;
        if (MAC) {
            lines = run(List.of("osascript", "-e",
                    "set f to choose file with prompt " + as(prompt) + " of type {\"agr\", \"mid\", \"midi\"} "
                    + "default location (POSIX file " + as(start.toString()) + ") with multiple selections allowed\n"
                    + "set out to \"\"\nrepeat with x in f\nset out to out & POSIX path of x & linefeed\nend repeat\nreturn out"));
        } else if (WINDOWS) {
            lines = powershell(
                    "$d = New-Object System.Windows.Forms.OpenFileDialog\n"
                    + "$d.Title = " + ps(prompt) + "\n"
                    + "$d.Filter = 'Grooves (*.agr;*.mid;*.midi)|*.agr;*.mid;*.midi|All files (*.*)|*.*'\n"
                    + "$d.Multiselect = $true\n"
                    + "$d.InitialDirectory = " + ps(start.toString()) + "\n"
                    + "if ($d.ShowDialog($owner) -eq [System.Windows.Forms.DialogResult]::OK) { $d.FileNames | ForEach-Object { [Console]::Out.WriteLine($_) } }");
        } else if (has("zenity")) {
            lines = run(List.of("zenity", "--file-selection", "--multiple", "--separator=\n", "--title=" + prompt,
                    "--filename=" + start + "/", "--file-filter=Grooves | *.agr *.mid *.midi"));
        } else {
            lines = run(List.of("kdialog", "--title", prompt, "--multiple", "--separate-output",
                    "--getopenfilename", start.toString(), "*.agr *.mid *.midi|Grooves"));
        }
        List<Path> files = new ArrayList<>();
        for (String l : lines) if (!l.isBlank()) files.add(Path.of(l.trim()));
        return files;
    }

    /** A file chosen in a save dialog, or null when cancelled. */
    static Path save(String prompt, Path folder, String defaultName) throws Exception {
        List<String> lines;
        if (MAC) {
            lines = run(List.of("osascript", "-e",
                    "set f to choose file name with prompt " + as(prompt) + " default name " + as(defaultName)
                    + " default location (POSIX file " + as(folder.toString()) + ")\nreturn POSIX path of f"));
        } else if (WINDOWS) {
            lines = powershell(
                    "$d = New-Object System.Windows.Forms.SaveFileDialog\n"
                    + "$d.Title = " + ps(prompt) + "\n"
                    + "$d.Filter = 'Ableton groove (*.agr)|*.agr'\n"
                    + "$d.DefaultExt = 'agr'\n"
                    + "$d.AddExtension = $true\n"
                    + "$d.OverwritePrompt = $true\n"
                    + "$d.InitialDirectory = " + ps(folder.toString()) + "\n"
                    + "$d.FileName = " + ps(defaultName) + "\n"
                    + "if ($d.ShowDialog($owner) -eq [System.Windows.Forms.DialogResult]::OK) { [Console]::Out.WriteLine($d.FileName) }");
        } else if (has("zenity")) {
            lines = run(List.of("zenity", "--file-selection", "--save", "--confirm-overwrite", "--title=" + prompt,
                    "--filename=" + folder.resolve(defaultName)));
        } else {
            lines = run(List.of("kdialog", "--title", prompt, "--getsavefilename",
                    folder.resolve(defaultName).toString(), "*.agr|Ableton groove"));
        }
        for (String l : lines) if (!l.isBlank()) return Path.of(l.trim());
        return null;
    }

    /**
     * Runs a Windows Forms dialog script in PowerShell: single-threaded apartment (the dialogs need it), UTF-8 output
     * (non-ASCII file names), and a hidden topmost owner window so the dialog opens in front of Bitwig, not behind it.
     * The script goes in as -EncodedCommand, so no quoting of paths can break it.
     */
    private static List<String> powershell(String body) throws Exception {
        String script = "[Console]::OutputEncoding = [System.Text.Encoding]::UTF8\n"
                + "Add-Type -AssemblyName System.Windows.Forms\n"
                + "[System.Windows.Forms.Application]::EnableVisualStyles()\n"
                + "$owner = New-Object System.Windows.Forms.Form -Property @{ TopMost = $true; ShowInTaskbar = $false; "
                + "Width = 1; Height = 1; StartPosition = 'CenterScreen'; Opacity = 0 }\n"
                + "$owner.Show(); $owner.Activate()\n"
                + "try {\n" + body + "\n} finally { $owner.Dispose() }\n";
        String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
        String root = System.getenv("SystemRoot");
        Path exe = root == null ? null : Path.of(root, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
        return run(List.of(exe != null && Files.isRegularFile(exe) ? exe.toString() : "powershell.exe",
                "-NoProfile", "-NonInteractive", "-STA", "-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded));
    }

    /** Runs a command and returns its output lines; anything it writes to stderr goes to the mod's log. */
    private static List<String> run(List<String> cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(false).start();
        p.getOutputStream().close();
        Thread err = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8))) {
                for (String line; (line = r.readLine()) != null; )
                    if (!line.isBlank()) State.MOD.log(cmd.get(0) + ": " + line);
            } catch (Exception ignored) {}
        }, "cimm-groove-dialog-err");
        err.setDaemon(true);
        err.start();
        List<String> lines = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            for (String line; (line = r.readLine()) != null; ) lines.add(line.replace("﻿", ""));
        }
        p.waitFor();
        return lines;
    }

    private static boolean has(String tool) {
        for (String dir : System.getenv().getOrDefault("PATH", "").split(java.io.File.pathSeparator))
            if (!dir.isEmpty() && Files.isExecutable(Path.of(dir, tool))) return true;
        return false;
    }

    /** An AppleScript string literal. */
    private static String as(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** A PowerShell single-quoted string literal. */
    private static String ps(String s) {
        return "'" + s.replace("'", "''") + "'";
    }
}
