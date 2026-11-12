package net.nuke24.togetl.cli;

/**
 * Command-line entry point.
 */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            usage();
            System.exit(1);
        }
        String command = args[0];
        if ("version".equals(command)) {
            System.out.println("togetl 0.0.0");
            return;
        }
        if ("help".equals(command) || "--help".equals(command) || "-h".equals(command)) {
            usage();
            return;
        }
        System.err.println("Unknown command: " + command);
        usage();
        System.exit(1);
    }

    private static void usage() {
        System.err.println("Usage: togetl <command>");
        System.err.println();
        System.err.println("Commands:");
        System.err.println("  version   Print the version");
        System.err.println("  help      Print this message");
        System.err.println();
        System.err.println("Import and database commands are added in later milestones.");
    }
}
