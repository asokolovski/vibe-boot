package com.alexeisoki.vibeboot.deployment.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.ServerSocket;

import org.junit.jupiter.api.Test;

class PortAllocatorTest {

    @Test
    void allocatePort_returnsAvailablePortInRange() {
        PortAllocator portAllocator = new PortAllocator(49152, 49153);

        int port = portAllocator.allocatePort();

        assertThat(port).isBetween(49152, 49153);
    }

    @Test
    void allocatePort_skipsPortThatIsAlreadyInUse() throws IOException {
        int occupiedPort = findConsecutiveAvailablePorts();

        try (ServerSocket ignored = new ServerSocket(occupiedPort)) {
            PortAllocator portAllocator = new PortAllocator(occupiedPort, occupiedPort + 1);

            int port = portAllocator.allocatePort();

            assertThat(port).isEqualTo(occupiedPort + 1);
        }
    }

    @Test
    void allocatePort_throwsWhenNoPortIsAvailable() throws IOException {
        int occupiedPort = findAvailablePort();

        try (ServerSocket ignored = new ServerSocket(occupiedPort)) {
            PortAllocator portAllocator = new PortAllocator(occupiedPort, occupiedPort);

            assertThatThrownBy(portAllocator::allocatePort)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("No available deployment ports");
        }
    }

    @Test
    void constructor_throwsWhenRangeIsInvalid() {
        assertThatThrownBy(() -> new PortAllocator(49299, 49152))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("startPort must be less than or equal to endPort");
    }

    private static int findAvailablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static int findConsecutiveAvailablePorts() throws IOException {
        for (int attempt = 0; attempt < 100; attempt++) {
            int firstPort = findAvailablePort();
            int secondPort = firstPort + 1;
            if (secondPort <= 65535 && isAvailable(secondPort)) {
                return firstPort;
            }
        }

        throw new IOException("Could not find consecutive available ports");
    }

    private static boolean isAvailable(int port) {
        try (ServerSocket ignored = new ServerSocket(port)) {
            return true;
        } catch (IOException exception) {
            return false;
        }
    }
}
