#!/usr/bin/env bash
# Sandbox launcher. Runs INSIDE the jla-sandbox container.
#
# Privileges: the launcher is root in the container, but the runner starts it with every capability
# dropped except SETUID, SETGID and KILL (and no_new_privs). Student code is never run as root:
#   - javac runs as COMPILER_UID (the only uid that can write /build, where the classes go),
#   - the student's program runs as STUDENT_UID, with no capabilities and no way to gain any.
# Neither uid can read or write the launcher's files (/work/in, /work/res, /work/result.bin), so
# the student can neither forge the result nor read other cases' inputs.
#
# Contract
#   stdin  : a tar archive with the files to run:
#              Solution.java        required, the student's code
#              Main.java            optional driver; when present it is the entry point,
#                                   otherwise Solution is
#              cases/<n>.in         optional, stdin for case n (numeric names, run in order);
#                                   without any case the program runs once with empty stdin
#   stdout : result frames (length-prefixed, so program output cannot forge a frame):
#              @@JLA 1
#              @@COMPILE <exit> <timed_out> <bytes>\n<compiler output>\n
#              @@CASE <n> <exit> <timed_out> <millis> <stdout_bytes> <stderr_bytes>\n<stdout><stderr>\n
#              @@DONE
#   stderr : one human-readable summary line per phase (for the spike and for debugging only)
#
# Limits come from the environment (set by the runner, never by the student).
# Expected outputs are NEVER passed in here; comparing results is the backend's job.

set -u
umask 077

WORK="${WORK:-/work}"          # tmpfs, root, mode 0711: others may pass through but not list or read
BUILD="${BUILD:-/build}"       # tmpfs owned by COMPILER_UID, mode 0755: compiled classes
STUDENT_UID="${STUDENT_UID:-10002}"
COMPILER_UID="${COMPILER_UID:-10003}"
COMPILE_TIMEOUT_S="${COMPILE_TIMEOUT_S:-10}"
RUN_TIMEOUT_S="${RUN_TIMEOUT_S:-5}"
MAX_OUTPUT_BYTES="${MAX_OUTPUT_BYTES:-65536}"
MAX_CASES="${MAX_CASES:-20}"
STUDENT_MAX_PROCS="${STUDENT_MAX_PROCS:-64}"

IN="$WORK/in"                  # unpacked input, root only (holds the cases)
SRC="$WORK/src"                # only the sources, readable by the compiler
RES="$WORK/res"                # raw outputs, root only
FINAL="$WORK/result.bin"       # the frames, root only
FSIZE_BLOCKS=$(( MAX_OUTPUT_BYTES / 1024 + 1 ))   # bash ulimit -f counts 1024-byte blocks

# Prefixes that run a command as an unprivileged uid. After the uid change the process is no longer
# root, so it holds no capabilities; no_new_privs forbids regaining any through exec.
STUDENT=(setpriv --reuid="$STUDENT_UID" --regid="$STUDENT_UID" --clear-groups --no-new-privs --)
COMPILER=(setpriv --reuid="$COMPILER_UID" --regid="$COMPILER_UID" --clear-groups --no-new-privs --)
# Student code and the compiler get a clean environment, whatever the container was started with.
CLEAN_ENV=(env -i "PATH=$PATH" "LANG=C.UTF-8")

JAVA_RUN_OPTS=(
  -Xmx64m -Xss1m
  -XX:+UseSerialGC
  -XX:TieredStopAtLevel=1
  -XX:-UsePerfData
  -XX:+ExitOnOutOfMemoryError
  -XX:+DisplayVMOutputToStderr
  -Xlog:disable -Xlog:all=warning:stderr
  -Djava.io.tmpdir=/tmp
  -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8
)

fail() {
  echo "launcher: $*" >&2
  exit 2
}

# Kills every process of an uid in this container (the student's program may leave children behind).
kill_all_of() {
  setpriv --reuid="$1" --regid="$1" --clear-groups --no-new-privs -- bash -c 'kill -KILL -1' 2>/dev/null
  return 0
}

mkdir -p "$IN" "$RES" || fail "cannot create work directories"
install -d -m 0755 "$SRC" || fail "cannot create $SRC"
: > "$FINAL"

# 0. Isolation self-check. If the container was started without the expected setup, refuse to run
# anything instead of running student code with weaker isolation.
"${STUDENT[@]}" true || fail "cannot drop privileges to uid $STUDENT_UID"
for prefix in STUDENT COMPILER; do
  uid_var="${prefix}_UID"
  for path in "$WORK" "$IN" "$RES" "$FINAL"; do
    setpriv --reuid="${!uid_var}" --regid="${!uid_var}" --clear-groups --no-new-privs -- test -r "$path" \
      && fail "$prefix uid can read $path"
    setpriv --reuid="${!uid_var}" --regid="${!uid_var}" --clear-groups --no-new-privs -- test -w "$path" \
      && fail "$prefix uid can write $path"
  done
done
[ "$("${STUDENT[@]}" grep -Ec '^Cap(Prm|Eff):[[:space:]]+0+$' /proc/self/status)" = 2 ] \
  || fail "student process still holds capabilities"
"${STUDENT[@]}" test -w "$BUILD" && fail "student can write $BUILD"
"${COMPILER[@]}" test -w "$BUILD" || fail "compiler cannot write $BUILD"

# 1. Unpack the input. GNU tar refuses absolute paths and ".." members by default.
tar -xf - -C "$IN" --no-same-owner --no-same-permissions || fail "cannot unpack input archive"
[ -z "$(find "$IN" ! -type f ! -type d -print -quit)" ] || fail "input archive contains more than files"
[ -f "$IN/Solution.java" ] || fail "Solution.java missing in input"

install -m 0644 -t "$SRC" "$IN/Solution.java" || fail "cannot stage Solution.java"
if [ -f "$IN/Main.java" ]; then
  install -m 0644 -t "$SRC" "$IN/Main.java" || fail "cannot stage Main.java"
  MAIN_CLASS=Main
  SOURCES=("$SRC/Solution.java" "$SRC/Main.java")
else
  MAIN_CLASS=Solution
  SOURCES=("$SRC/Solution.java")
fi

# Appends one length-prefixed frame body (file content capped to MAX_OUTPUT_BYTES) to the result.
capped() { head -c "$MAX_OUTPUT_BYTES" "$1"; }
size_of() { local n; n=$(capped "$1" | wc -c); echo "$n"; }

millis() { echo $(( $(date +%s%N) / 1000000 )); }

printf '@@JLA 1\n' > "$FINAL"

# 2. Compile as the compiler uid. -proc:none: annotation processors are code execution at compile time.
{
  (
    umask 022          # the student's program must be able to read the classes
    cd /tmp || exit 2
    ulimit -f "$FSIZE_BLOCKS"
    exec timeout -s KILL "${COMPILE_TIMEOUT_S}s" "${COMPILER[@]}" "${CLEAN_ENV[@]}" \
      javac -proc:none -encoding UTF-8 -Xlint:none -Xmaxerrs 20 -Xmaxwarns 0 \
            -J-Xmx256m -J-XX:-UsePerfData -J-XX:TieredStopAtLevel=1 \
            -d "$BUILD" "${SOURCES[@]}"
  ) > "$RES/compile.txt" 2>&1
  compile_exit=$?
} 2>/dev/null
kill_all_of "$COMPILER_UID"
compile_timed_out=0
if [ "$compile_exit" -eq 124 ] || [ "$compile_exit" -eq 137 ]; then compile_timed_out=1; fi

compile_bytes=$(size_of "$RES/compile.txt")
{
  printf '@@COMPILE %s %s %s\n' "$compile_exit" "$compile_timed_out" "$compile_bytes"
  capped "$RES/compile.txt"
  printf '\n'
} >> "$FINAL"
echo "compile: exit=$compile_exit timed_out=$compile_timed_out" >&2

# 3. Run the cases as the student uid, one JVM per case, each with its own time limit.
if [ "$compile_exit" -eq 0 ]; then
  mapfile -t CASE_FILES < <(find "$IN/cases" -maxdepth 1 -type f -name '*.in' 2>/dev/null | sort -V | head -n "$MAX_CASES")
  if [ "${#CASE_FILES[@]}" -eq 0 ]; then
    : > "$IN/empty.in"
    CASE_FILES=("$IN/empty.in")
  fi

  for case_file in "${CASE_FILES[@]}"; do
    name=$(basename "$case_file" .in)
    start=$(millis)
    {
      (
        cd /tmp || exit 2
        ulimit -f "$FSIZE_BLOCKS"
        ulimit -u "$STUDENT_MAX_PROCS"
        # The redirections are opened here, as root: the student's program gets the open files
        # but cannot open them by path.
        exec timeout -s KILL "${RUN_TIMEOUT_S}s" "${STUDENT[@]}" "${CLEAN_ENV[@]}" \
          java "${JAVA_RUN_OPTS[@]}" -cp "$BUILD" "$MAIN_CLASS" \
          < "$case_file" > "$RES/$name.out" 2> "$RES/$name.err"
      )
      run_exit=$?
    } 2>/dev/null
    kill_all_of "$STUDENT_UID"
    kill_all_of "$STUDENT_UID"     # a second pass catches a child forked during the first
    elapsed=$(( $(millis) - start ))
    timed_out=0
    if [ "$run_exit" -eq 124 ] || [ "$run_exit" -eq 137 ]; then timed_out=1; fi

    out_bytes=$(size_of "$RES/$name.out")
    err_bytes=$(size_of "$RES/$name.err")
    {
      printf '@@CASE %s %s %s %s %s %s\n' "$name" "$run_exit" "$timed_out" "$elapsed" "$out_bytes" "$err_bytes"
      capped "$RES/$name.out"
      capped "$RES/$name.err"
      printf '\n'
    } >> "$FINAL"
    echo "case $name: exit=$run_exit timed_out=$timed_out ${elapsed}ms stdout=${out_bytes}B stderr=${err_bytes}B" >&2
  done
fi

printf '@@DONE\n' >> "$FINAL"

# The result file is root-only and is printed after the last student process was killed.
cat "$FINAL"