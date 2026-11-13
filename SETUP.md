# Setup

What TOGETL needs, and how to get it on Debian or Alpine.

## What you need

| Component | Needed for | Debian 13 | Alpine |
| --- | --- | --- | --- |
| JDK 8 | build, tests, CLI | not in apt; use `scripts/get-jdk.sh` or your own JDK 8 | `openjdk8-jdk` |
| SQLite JDBC jar | `togetl load`, database tests | `scripts/get-sqlite-jdbc.sh` | same |
| curl, ca-certificates | fetch scripts | apt | apk |
| PHP CLI + PDO SQLite | read-only browser in `web/` | `php-cli php-sqlite3` | `php php-pdo_sqlite` |
| uv | `scripts/format-java-indent.py` | not in apt; Astral's installer (below) | same installer |
| Upstream fixtures | manual checks only (optional) | `scripts/fetch-fixtures.sh` | same |

`scripts/install-deps.sh --distro debian|alpine` installs the apt/apk rows.
It does not install JDK 8 on Debian, the SQLite JDBC jar, or uv.
Use `--dry-run` to print the commands without running them.

To install uv (not from apt; this is Astral's standalone installer, run as `curl | bash`):

```sh
curl -fsSL https://docs.astral.sh/uv/install.sh | bash
exec $SHELL                        # refresh PATH
uv --version
```

## Java selection

`build.sh`, `test/run-tests.sh`, and `togetl` pick a JDK in this order:

1. `$JAVAC` and `$JAVA`, if set.
2. `$JAVA_HOME/bin/javac` and `$JAVA_HOME/bin/java`, if `$JAVA_HOME` is set.
3. `.toolchain/jdk8`, if present (fallback only).
4. `javac` and `java` on `$PATH`.

`scripts/get-jdk.sh` downloads a glibc build of Temurin 8 for Linux x86_64.
It will not run on Alpine (musl) or on arm64, so on Alpine set `JAVA_HOME` instead.

The build targets Java 8 (`--release 8`, or `-source/-target 1.8` with a JDK 8 `javac`).

## Debian quick start

```sh
scripts/install-deps.sh --distro debian
scripts/get-jdk.sh                 # skip if JAVA_HOME already points at JDK 8
scripts/get-sqlite-jdbc.sh
./build.sh
./test/run-tests.sh
```

## Alpine quick start

```sh
scripts/install-deps.sh --distro alpine
scripts/get-sqlite-jdbc.sh
./build.sh
./test/run-tests.sh
```

On Alpine, `JAVA_HOME` should be `/usr/lib/jvm/java-1.8-openjdk`.
The devcontainer sets this for you.

## Checking the result

```sh
java -version                      # 1.8.x
php -m | grep -i -E '^pdo'         # should list PDO and pdo_sqlite
uv --version                       # only needed for the formatter
```

## Running the browser

After `togetl load` has produced a database, see [web/README.md](web/README.md).
