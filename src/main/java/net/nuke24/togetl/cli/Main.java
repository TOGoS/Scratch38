package net.nuke24.togetl.cli;

import net.nuke24.togetl.Version;

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
        String[] rest = new String[args.length - 1];
        System.arraycopy(args, 1, rest, 0, rest.length);

        if ("version".equals(command)) {
            System.out.println("togetl " + Version.VERSION);
            return;
        }
        if ("help".equals(command) || "--help".equals(command) || "-h".equals(command)) {
            usage();
            return;
        }
        if ("import".equals(command)) {
            System.exit(ImportCommand.run(rest));
            return;
        }
        if ("vocab".equals(command)) {
            System.exit(VocabCommand.run(rest));
            return;
        }
        System.err.println("Unknown command: " + command);
        usage();
        System.exit(1);
    }

    private static void usage() {
        System.err.println("Usage: togetl <command> [options]");
        System.err.println();
        System.err.println("Commands:");
        System.err.println("  import    Convert a source document to TOGETLTSV");
        System.err.println("  vocab     Check vocabulary definitions against mappings");
        System.err.println("  version   Print the version");
        System.err.println("  help      Print this message");
        System.err.println();
        System.err.println("Run 'togetl import --help' or 'togetl vocab --help' for options.");
    }
}
