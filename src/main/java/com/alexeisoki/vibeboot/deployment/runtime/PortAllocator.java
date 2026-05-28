package com.alexeisoki.vibeboot.deployment.runtime;

import java.io.IOException;
import java.net.ServerSocket;

import org.springframework.stereotype.Service;

@Service
public class PortAllocator {

    private static final int DEFAULT_START_PORT = 49152;
    private static final int DEFAULT_END_PORT = 49299;

    private final int startPort;
    private final int endPort;

    public PortAllocator() {
        this(DEFAULT_START_PORT, DEFAULT_END_PORT);
    }

    PortAllocator(int startPort, int endPort) {
        if (startPort > endPort) {
            throw new IllegalArgumentException("startPort must be less than or equal to endPort");
        }

        this.startPort = startPort;
        this.endPort = endPort;
    }

    public int allocatePort() {
        for (int port = startPort; port <= endPort; port++) {
            if (isAvailable(port)) {
                return port;
            }
        }

        throw new IllegalStateException("No available deployment ports");
    }

    private boolean isAvailable(int port) {
        try (ServerSocket socket = new ServerSocket(port)) {
            return true;
        } catch (IOException exception) {
            return false;
        }
    }
}
