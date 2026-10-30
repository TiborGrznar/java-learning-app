package com.tgrznar.javalearningapp.runner.sandbox;

/**
 * Raw outcome of one container run. Frames are not parsed here (that is a later step).
 *
 * @param stdout          bytes the launcher wrote to stdout (the result frames), possibly cut at the limit
 * @param stderr          decoded and truncated stderr of the launcher, for diagnostics only
 * @param exitCode        exit code of the docker client
 * @param outputTruncated true when the output limit was hit and the container was killed
 */
public record SandboxResult(byte[] stdout, String stderr, int exitCode, boolean outputTruncated) {
}