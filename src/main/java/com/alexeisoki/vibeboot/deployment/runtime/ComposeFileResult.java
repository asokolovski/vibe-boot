package com.alexeisoki.vibeboot.deployment.runtime;

import java.nio.file.Path;

public record ComposeFileResult(
        Path composeFile,
        int containerPort
) {
}
