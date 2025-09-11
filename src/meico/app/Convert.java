package meico.app;

import meico.mei.Mei;
import meico.msm.Msm;
import meico.mpm.Mpm;
import meico.mpm.elements.Performance;
import meico.midi.Midi;
import meico.audio.Audio;
import nu.xom.*;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Command-line tool for converting MEI + MPM to MP3 with optional MEI ID filtering.
 * 
 * @author Meico Convert Tool
 */
public class Convert {
    
    public static void main(String[] args) {
        System.out.println("Meico Convert Tool v1.0");
        
        if (args.length < 3) {
            printUsage();
            System.exit(1);
        }
        
        try {
            String meiFile = args[0];
            String mpmFile = args[1];
            String outputFile = args[2];
            Set<String> filterIds = null;
            
            // Parse optional MEI IDs
            if (args.length > 3) {
                filterIds = new HashSet<>();
                for (int i = 3; i < args.length; i++) {
                    filterIds.add(args[i]);
                }
                System.out.println("Filtering by MEI IDs: " + filterIds);
            }
            
            // Load MEI file
            System.out.println("Loading MEI file: " + meiFile);
            Mei mei = new Mei(new File(meiFile));
            
            // Apply MEI ID filtering if specified
            if (filterIds != null && !filterIds.isEmpty()) {
                System.out.println("Applying MEI ID filtering...");
                mei = filterMeiByIds(mei, filterIds);
            }
            
            // Convert MEI to MSM
            System.out.println("Converting MEI to MSM...");
            List<Msm> msms = mei.exportMsm();
            if (msms.isEmpty()) {
                System.err.println("Error: No MSM generated from MEI");
                System.exit(1);
            }
            Msm msm = msms.get(0); // Use first movement
            
            // Load MPM file
            System.out.println("Loading MPM file: " + mpmFile);
            Mpm mpm = new Mpm(new File(mpmFile));
            
            // Get first performance from MPM
            if (mpm.size() == 0) {
                System.err.println("Error: No performances found in MPM file");
                System.exit(1);
            }
            Performance performance = mpm.getPerformance(0);
            System.out.println("Using performance: " + performance.getName());
            
            // Apply performance to MSM to create expressive MSM
            System.out.println("Applying performance to MSM...");
            Msm expressiveMsm = performance.perform(msm);
            
            // Shift onsets to first note if filtering was applied
            if (filterIds != null && !filterIds.isEmpty()) {
                System.out.println("Shifting onsets to first note...");
                shiftOnsetsToFirstNote(expressiveMsm);
            }
            
            // Convert expressive MSM to MIDI
            System.out.println("Converting MSM to MIDI...");
            Midi midi = expressiveMsm.exportMidi();
            
            // Convert MIDI to audio
            System.out.println("Converting MIDI to audio...");
            Audio audio = midi.exportAudio();
            
            // Export as MP3
            System.out.println("Exporting as MP3: " + outputFile);
            if (!outputFile.toLowerCase().endsWith(".mp3")) {
                outputFile += ".mp3";
            }
            boolean success = audio.writeMp3(outputFile);
            
            if (success) {
                System.out.println("Conversion completed successfully!");
            } else {
                System.err.println("Error writing MP3 file");
                System.exit(1);
            }
            
        } catch (Exception e) {
            System.err.println("Error during conversion: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void printUsage() {
        System.out.println("Usage: java -jar convert.jar <mei-file> <mpm-file> <output-file> [mei-id1] [mei-id2] ...");
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  mei-file    : Path to MEI input file");
        System.out.println("  mpm-file    : Path to MPM input file");
        System.out.println("  output-file : Path to output MP3 file (will add .mp3 extension if missing)");
        System.out.println("  mei-id      : Optional MEI IDs to filter (all other notes will be ignored)");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar convert.jar input.mei performance.mpm output.mp3");
        System.out.println("  java -jar convert.jar input.mei performance.mpm output.mp3 note1 note2 chord1");
    }
    
    /**
     * Filter MEI by keeping only notes/chords with specified IDs
     */
    private static Mei filterMeiByIds(Mei originalMei, Set<String> filterIds) throws Exception {
        Document meiDoc = (Document) originalMei.getDocument().copy();
        
        // Find all note and chord elements
        Nodes notes = meiDoc.getRootElement().query("//mei:note", getNamespaceContext());
        Nodes chords = meiDoc.getRootElement().query("//mei:chord", getNamespaceContext());
        
        List<Element> toRemove = new ArrayList<>();
        
        // Check notes
        for (int i = 0; i < notes.size(); i++) {
            Element note = (Element) notes.get(i);
            String id = note.getAttributeValue("id", "http://www.w3.org/XML/1998/namespace");
            if (id == null || !filterIds.contains(id)) {
                toRemove.add(note);
            }
        }
        
        // Check chords
        for (int i = 0; i < chords.size(); i++) {
            Element chord = (Element) chords.get(i);
            String id = chord.getAttributeValue("id", "http://www.w3.org/XML/1998/namespace");
            if (id == null || !filterIds.contains(id)) {
                toRemove.add(chord);
            }
        }
        
        // Remove elements that don't match filter
        for (Element element : toRemove) {
            element.detach();
        }
        
        System.out.println("Filtered out " + toRemove.size() + " elements");
        
        return new Mei(meiDoc);
    }
    
    /**
     * Shift all onsets in the MSM so that the first note starts at time 0
     */
    private static void shiftOnsetsToFirstNote(Msm msm) {
        // Find the earliest onset time
        Nodes notes = msm.getDocument().getRootElement().query("//msm:note", getNamespaceContext());
        
        if (notes.size() == 0) {
            return; // No notes to shift
        }
        
        double earliestTime = Double.MAX_VALUE;
        for (int i = 0; i < notes.size(); i++) {
            Element note = (Element) notes.get(i);
            String dateStr = note.getAttributeValue("date");
            if (dateStr != null) {
                try {
                    double date = Double.parseDouble(dateStr);
                    if (date < earliestTime) {
                        earliestTime = date;
                    }
                } catch (NumberFormatException e) {
                    // Skip invalid date values
                }
            }
        }
        
        if (earliestTime == Double.MAX_VALUE || earliestTime == 0.0) {
            return; // No valid times found or already starting at 0
        }
        
        System.out.println("Shifting all onsets by -" + earliestTime + " to start at 0");
        
        // Shift all note onsets
        for (int i = 0; i < notes.size(); i++) {
            Element note = (Element) notes.get(i);
            String dateStr = note.getAttributeValue("date");
            if (dateStr != null) {
                try {
                    double date = Double.parseDouble(dateStr);
                    double newDate = date - earliestTime;
                    note.getAttribute("date").setValue(String.valueOf(newDate));
                } catch (NumberFormatException e) {
                    // Skip invalid date values
                }
            }
        }
        
        // Also shift any other timed events (dynamics, etc.)
        Nodes timedElements = msm.getDocument().getRootElement().query("//*[@date]", getNamespaceContext());
        for (int i = 0; i < timedElements.size(); i++) {
            Element element = (Element) timedElements.get(i);
            if (!element.getLocalName().equals("note")) { // We already handled notes
                String dateStr = element.getAttributeValue("date");
                if (dateStr != null) {
                    try {
                        double date = Double.parseDouble(dateStr);
                        double newDate = date - earliestTime;
                        element.getAttribute("date").setValue(String.valueOf(newDate));
                    } catch (NumberFormatException e) {
                        // Skip invalid date values
                    }
                }
            }
        }
    }
    
    /**
     * Create namespace context for XPath queries
     */
    private static XPathContext getNamespaceContext() {
        XPathContext context = new XPathContext();
        context.addNamespace("mei", "http://www.music-encoding.org/ns/mei");
        context.addNamespace("msm", "http://www.cemfi.de/msm");
        return context;
    }
}