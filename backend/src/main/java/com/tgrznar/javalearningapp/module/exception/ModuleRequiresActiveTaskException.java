package com.tgrznar.javalearningapp.module.exception;

/**
 * An active module must keep at least one active coding task, so the requested change
 * (activating the module, or deactivating or deleting its last active task) is refused.
 */
public class ModuleRequiresActiveTaskException extends RuntimeException {

    public ModuleRequiresActiveTaskException(Long moduleId) {
        super("Module " + moduleId + " requires at least one active coding task");
    }
}
