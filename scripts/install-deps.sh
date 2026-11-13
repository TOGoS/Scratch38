#!/bin/sh
# Install the OS packages TOGETL needs. See SETUP.md for what each one is for.
set -eu

usage() {
	cat <<'EOF'
Usage: scripts/install-deps.sh --distro NAME [--dry-run]

Options:
  --distro NAME  Required. One of: debian, alpine.
  --include-vs-code-tools
  --dry-run      Print the commands instead of running them.
  -h, --help     Show this message.

Does not install JDK 8, the SQLite JDBC jar, or uv; see SETUP.md.
EOF
}

die() {
	echo "install-deps.sh: $*" >&2
	exit 1
}

distro=""
dry_run=no
include_vs_code_tools=no

while [ $# -gt 0 ]; do
	case "$1" in
		--distro)
			[ $# -ge 2 ] || die "--distro requires a value"
			distro=$2
			shift 2
			;;
		--include-vs-code-tools)
			include_vs_code_tools=yes
			shift
			;;
		--dry-run)
			dry_run=yes
			shift
			;;
		-h|--help)
			usage
			exit 0
			;;
		*)
			die "unknown option: $1"
			;;
	esac
done

SUDO=""
if [ "$(id -u)" -ne 0 ]; then
	SUDO=sudo
fi

run() {
	if [ "$dry_run" = yes ]; then
		echo "+ $*"
	else
		"$@"
	fi
}

case "$distro" in
	debian)
		run $SUDO apt-get update
		run $SUDO apt-get install -y curl ca-certificates php-cli php-sqlite3
		;;
	alpine)
		run $SUDO apk add --no-cache curl ca-certificates openjdk8-jdk php php-pdo_sqlite
		if [ "$include_vs_code_tools" = yes ]; then
			# Needed for the Red Hat Java extension, apparently
			run $SUDO apk add --no-cache openjdk25-jdk
		fi
		;;
	"")
		usage >&2
		die "--distro is required (debian or alpine)"
		;;
	*)
		die "unsupported distro: $distro (expected debian or alpine)"
		;;
esac

echo "Done. Remaining steps are in SETUP.md."
