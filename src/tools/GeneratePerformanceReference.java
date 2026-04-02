import meico.mei.Mei;
import meico.msm.Msm;
import meico.mpm.Mpm;
import meico.mpm.elements.Performance;
import meico.midi.Midi;
import meico.supplementary.KeyValue;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * Generates reference augmented MSM and MIDI output from MEI files
 * using the full performance rendering pipeline.
 */
public class GeneratePerformanceReference {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: GeneratePerformanceReference <mei-input-dir> <reference-output-dir>");
            System.exit(1);
        }

        File inputDir = new File(args[0]);
        File outputDir = new File(args[1]);

        if (!inputDir.isDirectory()) {
            System.err.println("Input directory does not exist: " + inputDir);
            System.exit(1);
        }

        outputDir.mkdirs();

        File[] meiFiles = inputDir.listFiles((dir, name) -> name.endsWith(".mei"));
        if (meiFiles == null || meiFiles.length == 0) {
            System.err.println("No .mei files found in " + inputDir);
            System.exit(1);
        }

        for (File meiFile : meiFiles) {
            String baseName = meiFile.getName().replaceFirst("\\.mei$", "");
            System.out.println("Processing: " + meiFile.getName());

            try {
                Mei mei = new Mei(meiFile);

                KeyValue<List<Msm>, List<Mpm>> result = mei.exportMsmMpm(720, true, false, true);
                List<Msm> msms = result.getKey();
                List<Mpm> mpms = result.getValue();

                if (msms.isEmpty() || mpms.isEmpty()) {
                    System.err.println("  No MSM/MPM generated, skipping.");
                    continue;
                }

                Msm msm = msms.get(0);
                Mpm mpm = mpms.get(0);

                // Generate raw MIDI
                Midi rawMidi = msm.exportMidi(120.0, true);
                if (rawMidi != null) {
                    rawMidi.writeMidi(new File(outputDir, baseName + "_raw.mid"));
                }

                // Generate expressive MIDI via performance rendering
                List<Performance> performances = mpm.getAllPerformances();
                if (!performances.isEmpty()) {
                    Performance perf = performances.get(0);

                    // Perform: augment MSM with expression data
                    Msm augmentedMsm = perf.perform(msm);

                    // Write the augmented MSM
                    String augMsmXml = augmentedMsm.toXML();
                    writeFile(new File(outputDir, baseName + "_augmented.msm"), augMsmXml);

                    // Generate expressive MIDI
                    Midi expressiveMidi = msm.exportExpressiveMidi(perf, true);
                    if (expressiveMidi != null) {
                        expressiveMidi.writeMidi(new File(outputDir, baseName + "_expressive.mid"));
                    }
                } else {
                    System.out.println("  No performances found in MPM.");
                }

            } catch (Exception e) {
                System.err.println("  ERROR: " + e.getMessage());
                e.printStackTrace();
            }
        }

        System.out.println("Done.");
    }

    private static void writeFile(File file, String content) throws IOException {
        try (FileWriter fw = new FileWriter(file)) {
            fw.write(content);
        }
        System.out.println("  Wrote: " + file.getName());
    }
}
