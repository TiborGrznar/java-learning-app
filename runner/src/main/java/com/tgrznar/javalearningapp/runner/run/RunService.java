package com.tgrznar.javalearningapp.runner.run;

import com.tgrznar.javalearningapp.runner.sandbox.DockerSandbox;
import com.tgrznar.javalearningapp.runner.sandbox.SandboxRequest;
import com.tgrznar.javalearningapp.runner.sandbox.SandboxResult;
import com.tgrznar.javalearningapp.runner.sandbox.config.SandboxProperties;
import com.tgrznar.javalearningapp.runner.sandbox.exception.SandboxUnavailableException;
import com.tgrznar.javalearningapp.runner.sandbox.result.FrameParser;
import com.tgrznar.javalearningapp.runner.sandbox.result.ParsedResult;
import org.springframework.stereotype.Service;

/**
 * Runs one submission and turns the raw frames of the container into a sanitized result.
 *
 * <p>The launcher caps every output stream, so the total size of the frames has a known upper
 * bound. A run that still exceeds it (the container was stopped for that reason) means the
 * launcher did not do its job, and is reported as a fault of the sandbox, never as a verdict
 * on the student's code.
 */
@Service
public class RunService {

    private final DockerSandbox sandbox;
    private final SandboxProperties props;

    public RunService(DockerSandbox sandbox, SandboxProperties props) {
        this.sandbox = sandbox;
        this.props = props;
    }

    /**
     * @throws IllegalArgumentException    for input over the limits
     * @throws com.tgrznar.javalearningapp.runner.sandbox.exception.SandboxBusyException
     *                                     when all slots and the queue are taken
     * @throws SandboxUnavailableException when the sandbox failed or returned malformed frames
     */
    public RunResponse run(SandboxRequest request) {
        SandboxResult raw = sandbox.run(request);
        if (raw.outputTruncated()) {
            throw new SandboxUnavailableException("The output exceeded the limit the launcher enforces");
        }
        ParsedResult parsed = FrameParser.parse(raw.stdout(), request.caseInputs().size(), props.maxOutputBytes());
        return RunResponse.from(parsed);
    }
}
