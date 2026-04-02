import meico.mei.Mei;
import meico.msm.Msm;
import meico.mpm.Mpm;
import meico.supplementary.KeyValue;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * Generates reference MSM and MPM output from MEI files for cross-validation
 * with the TypeScript port.
 */
public class GenerateReference {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: GenerateReference <mei-input-dir> <reference-output-dir>");
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

                // Convert with default parameters: ppq=720, dontUseChannel10=true, ignoreExpansions=false, cleanup=true
                KeyValue<List<Msm>, List<Mpm>> result = mei.exportMsmMpm(720, true, false, true);
                List<Msm> msms = result.getKey();
                List<Mpm> mpms = result.getValue();

                System.out.println("  Generated " + msms.size() + " MSM(s) and " + mpms.size() + " MPM(s)");

                for (int i = 0; i < msms.size(); i++) {
                    String suffix = msms.size() > 1 ? "_" + i : "";

                    String msmXml = msms.get(i).toXML();
                    writeFile(new File(outputDir, baseName + suffix + ".msm"), msmXml);

                    String mpmXml = mpms.get(i).toXML();
                    writeFile(new File(outputDir, baseName + suffix + ".mpm"), mpmXml);
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
