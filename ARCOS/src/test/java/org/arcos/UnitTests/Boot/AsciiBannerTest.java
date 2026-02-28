package org.arcos.UnitTests.Boot;

import org.arcos.Boot.Banner.AsciiBanner;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

class AsciiBannerTest {

    @Test
    void print_DoesNotThrow() {
        // Given
        PrintStream original = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));

        try {
            // When / Then : aucune exception ne doit être levée
            assertDoesNotThrow(AsciiBanner::print);
        } finally {
            System.setOut(original);
        }
    }

    @Test
    void print_OutputsContent() {
        // Given
        PrintStream original = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));

        try {
            // When
            AsciiBanner.print();
            String output = baos.toString();

            // Then : le mot ARCOS doit apparaître dans la sortie (en art ASCII ou en texte)
            assertTrue(output.contains("ARTIFICIAL COGNITIVE SYSTEM"),
                    "La bannière doit contenir le sous-titre ARTIFICIAL COGNITIVE SYSTEM");
        } finally {
            System.setOut(original);
        }
    }
}
