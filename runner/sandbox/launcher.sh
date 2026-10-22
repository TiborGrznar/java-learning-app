#!/usr/bin/env bash
# Sandbox launcher. Runs INSIDE the jla-sandbox container as an unprivileged user.
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

WORK="${WORK:-/work}"
COMPILE_TIMEOUT_S="${COMPILE_TIMEOUT_S:-10}"
RUN_TIMEOUT_S="${RUN_TIMEOUT_S:-5}"
MAX_OUTPUT_BYTES="${MAX_OUTPUT_BYTES:-65536}"
MAX_CASES="${MAX_CASES:-20}"

SRC="$WORK/src"
OUT="$WORK/out"
RES="$WORK/res"
FINAL="$WORK/result.bin"
FSIZE_BLOCKS=$(( MAX_OUTPUT_BYTES / 1024 + 1 ))   # bash ulimit -f counts 1024-byte blocks

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

mkdir -p "$SRC" "$OUT" "$RES" || exit 2

fail() {
  echo "launcher: $*" >&2
  exit 2
}

# 1. Unpack the input. GNU tar refuses absolute paths and ".." members by default.
tar -xf - -C "$SRC" --no-same-owner --no-same-permissions || fail "cannot unpack input archive"
[ -f "$SRC/Solution.java" ] || fail "Solution.java missing in input"

if [ -f "$SRC/Main.java" ]; then
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

# 2. Compile. -proc:none: annotation processors are code execution at compile time.
(
  ulimit -f "$FSIZE_BLOCKS"
  exec timeout -s KILL "${COMPILE_TIMEOUT_S}s" \
    javac -proc:none -encoding UTF-8 -Xlint:none -Xmaxerrs 20 -Xmaxwarns 0 \
          -J-Xmx256m -J-XX:-UsePerfData -J-XX:TieredStopAtLevel=1 \
          -d "$OUT" "${SOURCES[@]}"
) > "$RES/compile.txt" 2>&1
compile_exit=$?
compile_timed_out=0
if [ "$compile_exit" -eq 124 ] || [ "$compile_exit" -eq 137 ]; then compile_timed_out=1; fi

compile_bytes=$(size_of "$RES/compile.txt")
{
  printf '@@COMPILE %s %s %s\n' "$compile_exit" "$compile_timed_out" "$compile_bytes"
  capped "$RES/compile.txt"
  printf '\n'
} >> "$FINAL"
echo "compile: exit=$compile_exit timed_out=$compile_timed_out" >&2

# 3. Run the cases, one JVM per case, each with its own time limit.
if [ "$compile_exit" -eq 0 ]; then
  mapfile -t CASE_FILES < <(find "$SRC/cases" -maxdepth 1 -type f -name '*.in' 2>/dev/null | sort -V | head -n "$MAX_CASES")
  if [ "${#CASE_FILES[@]}" -eq 0 ]; then
    : > "$SRC/empty.in"
    CASE_FILES=("$SRC/empty.in")
  fi

  for case_file in "${CASE_FILES[@]}"; do
    name=$(basename "$case_file" .in)
    start=$(millis)
      {
        (
          ulimit -f "$FSIZE_BLOCKS"
          exec timeout -s KILL "${RUN_TIMEOUT_S}s" \
            java "${JAVA_RUN_OPTS[@]}" -cp "$OUT" "$MAIN_CLASS" \
            < "$case_file" > "$RES/$name.out" 2> "$RES/$name.err"
        )
        run_exit=$?
      } 2>/dev/null
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

# The result is emitted only now, after the last program has finished, so ordinary output
# cannot interleave with it. Known spike limitation: student code runs as the same user as this
# launcher, so it could still write to /proc/<launcher pid>/fd/1 and forge frames. That cannot reveal
# expected outputs (they are not in the container); the fix (a separate uid for student code) is
# decided in step 3 of feature/code-sandbox.
cat "$FINAL"
