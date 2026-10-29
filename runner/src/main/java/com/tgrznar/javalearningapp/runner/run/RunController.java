package com.tgrznar.javalearningapp.runner.run;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Runs a submission in the sandbox. Protected by the token filter like everything except /health.
 * The request is synchronous: the time limits are short and nothing has to be stored.
 */
@RestController
public class RunController {

    private final RunService runService;

    public RunController(RunService runService) {
        this.runService = runService;
    }

    @PostMapping("/run")
    public RunResponse run(@Valid @RequestBody RunRequest request) {
        return runService.run(request.toSandboxRequest());
    }
}
