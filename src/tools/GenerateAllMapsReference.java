import meico.msm.Msm;
import meico.mpm.Mpm;
import meico.mpm.elements.Performance;
import meico.mpm.elements.Global;
import meico.mpm.elements.Part;
import meico.mpm.elements.Header;
import meico.mpm.elements.maps.*;
import meico.mpm.elements.maps.data.*;
import meico.mpm.elements.styles.*;
import meico.mpm.elements.styles.defs.*;

import nu.xom.*;

import meico.midi.Midi;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Creates MSM + MPM with ALL map types, runs Performance.perform(),
 * and writes reference augmented MSM + raw MSM + MPM for cross-validation.
 */
public class GenerateAllMapsReference {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: GenerateAllMapsReference <output-dir>");
            System.exit(1);
        }
        File outputDir = new File(args[0]);
        outputDir.mkdirs();

        generateRubatoTest(outputDir);
        generateAsynchronyTest(outputDir);
        generateMetricalAccentuationTest(outputDir);
        generateMovementTest(outputDir);
        generateImprecisionTimingTest(outputDir);
        generateImprecisionDynamicsTest(outputDir);
        generateOrnamentationTest(outputDir);
        generateAllMapsTest(outputDir);

        System.out.println("Done generating all-maps reference data.");
    }

    private static Msm createSimpleMsm(String title) {
        Msm msm = Msm.createMsm(title, null, 720);
        Element part = Msm.makePart("Piano", 1, 0, 0);
        Element dated = part.getFirstChildElement("dated");
        Element tsMap = dated.getFirstChildElement("timeSignatureMap");
        tsMap.appendChild(Msm.makeTimeSignature(0, 4, 4, null));
        Element score = dated.getFirstChildElement("score");
        int[] pitches = {60, 62, 64, 65, 67, 69, 71, 72};
        for (int i = 0; i < pitches.length; i++) {
            Element note = new Element("note");
            note.addAttribute(new Attribute("xml:id", "http://www.w3.org/XML/1998/namespace", "n" + (i + 1)));
            note.addAttribute(new Attribute("date", Double.toString(i * 720.0)));
            note.addAttribute(new Attribute("midi.pitch", Double.toString(pitches[i])));
            note.addAttribute(new Attribute("pitchname", "x"));
            note.addAttribute(new Attribute("accidentals", "0.0"));
            note.addAttribute(new Attribute("octave", "3.0"));
            note.addAttribute(new Attribute("duration", "720.0"));
            score.appendChild(note);
        }
        Element globalDated = msm.getGlobal().getFirstChildElement("dated");
        Element sectionMap = globalDated.getFirstChildElement("sectionMap");
        Element section = new Element("section");
        section.addAttribute(new Attribute("date", "0.0"));
        section.addAttribute(new Attribute("date.end", "5760.0"));
        section.addAttribute(new Attribute("xml:id", "http://www.w3.org/XML/1998/namespace", "sec1"));
        sectionMap.appendChild(section);
        msm.addPart(part);
        msm.setFile(new File(title.replaceAll("\\s+", "_") + ".msm"));
        return msm;
    }

    private static Mpm createBasicMpm() {
        Mpm mpm = Mpm.createMpm();
        Performance perf = Performance.createPerformance("test performance", 720);
        mpm.addPerformance(perf);
        return mpm;
    }

    private static void generateRubatoTest(File outputDir) throws Exception {
        System.out.println("Generating rubato test...");
        Msm msm = createSimpleMsm("Rubato Test");
        Mpm mpm = createBasicMpm();
        Performance perf = mpm.getAllPerformances().get(0);
        TempoMap tempoMap = TempoMap.createTempoMap();
        tempoMap.addTempo(0, "120", 0.25);
        perf.getGlobal().getDated().addMap(tempoMap);
        RubatoMap rubatoMap = RubatoMap.createRubatoMap();
        rubatoMap.addRubato(0, 2880, 0.5, 0.0, 1.0, true);
        perf.getGlobal().getDated().addMap(rubatoMap);
        Part mpmPart = Part.createPart("Piano", 1, 0, 0);
        perf.addPart(mpmPart);
        writeTestData(outputDir, "rubato", msm, mpm, perf);
    }

    private static void generateAsynchronyTest(File outputDir) throws Exception {
        System.out.println("Generating asynchrony test...");
        Msm msm = createSimpleMsm("Asynchrony Test");
        Mpm mpm = createBasicMpm();
        Performance perf = mpm.getAllPerformances().get(0);
        TempoMap tempoMap = TempoMap.createTempoMap();
        tempoMap.addTempo(0, "120", 0.25);
        perf.getGlobal().getDated().addMap(tempoMap);
        AsynchronyMap asyncMap = AsynchronyMap.createAsynchronyMap();
        asyncMap.addAsynchrony(0, 50.0);
        asyncMap.addAsynchrony(2880, -30.0);
        Part mpmPart = Part.createPart("Piano", 1, 0, 0);
        mpmPart.getDated().addMap(asyncMap);
        perf.addPart(mpmPart);
        writeTestData(outputDir, "asynchrony", msm, mpm, perf);
    }

    private static void generateMetricalAccentuationTest(File outputDir) throws Exception {
        System.out.println("Generating metrical accentuation test...");
        Msm msm = createSimpleMsm("Metrical Accentuation Test");
        Mpm mpm = createBasicMpm();
        Performance perf = mpm.getAllPerformances().get(0);
        TempoMap tempoMap = TempoMap.createTempoMap();
        tempoMap.addTempo(0, "120", 0.25);
        perf.getGlobal().getDated().addMap(tempoMap);
        Header header = perf.getGlobal().getHeader();
        MetricalAccentuationStyle maStyle = MetricalAccentuationStyle.createMetricalAccentuationStyle("my accent style");
        AccentuationPatternDef apDef = AccentuationPatternDef.createAccentuationPatternDef("4/4 pattern", 2880.0);
        apDef.addAccentuation(0.0, 20.0, 0.0, 1.0);
        apDef.addAccentuation(720.0, -10.0, 0.0, 1.0);
        apDef.addAccentuation(1440.0, 10.0, 0.0, 1.0);
        apDef.addAccentuation(2160.0, -10.0, 0.0, 1.0);
        maStyle.addDef(apDef);
        header.addStyleDef(Mpm.METRICAL_ACCENTUATION_STYLE, maStyle);
        MetricalAccentuationMap maMap = MetricalAccentuationMap.createMetricalAccentuationMap();
        maMap.addStyleSwitch(0, "my accent style");
        maMap.addAccentuationPattern(0, "4/4 pattern", 1.0, true, true);
        perf.getGlobal().getDated().addMap(maMap);
        DynamicsMap dynMap = DynamicsMap.createDynamicsMap();
        dynMap.addDynamics(0.0, "100");
        Part mpmPart = Part.createPart("Piano", 1, 0, 0);
        mpmPart.getDated().addMap(dynMap);
        perf.addPart(mpmPart);
        writeTestData(outputDir, "metrical_accentuation", msm, mpm, perf);
    }

    private static void generateMovementTest(File outputDir) throws Exception {
        System.out.println("Generating movement test...");
        Msm msm = createSimpleMsm("Movement Test");
        Mpm mpm = createBasicMpm();
        Performance perf = mpm.getAllPerformances().get(0);
        TempoMap tempoMap = TempoMap.createTempoMap();
        tempoMap.addTempo(0, "120", 0.25);
        perf.getGlobal().getDated().addMap(tempoMap);
        MovementMap movMap = MovementMap.createMovementMap();
        MovementData md1 = new MovementData();
        md1.startDate = 0; md1.position = 1.0; md1.transitionTo = 0.0; md1.endDate = 2880.0; md1.controller = "sustain";
        movMap.addMovement(md1);
        MovementData md2 = new MovementData();
        md2.startDate = 2880.0; md2.position = 0.0; md2.transitionTo = 1.0; md2.endDate = 5760.0; md2.controller = "sustain";
        movMap.addMovement(md2);
        Part mpmPart = Part.createPart("Piano", 1, 0, 0);
        mpmPart.getDated().addMap(movMap);
        perf.addPart(mpmPart);
        writeTestData(outputDir, "movement", msm, mpm, perf);
    }

    private static void generateImprecisionTimingTest(File outputDir) throws Exception {
        System.out.println("Generating imprecision timing test...");
        Msm msm = createSimpleMsm("Imprecision Timing Test");
        Mpm mpm = createBasicMpm();
        Performance perf = mpm.getAllPerformances().get(0);
        TempoMap tempoMap = TempoMap.createTempoMap();
        tempoMap.addTempo(0, "120", 0.25);
        perf.getGlobal().getDated().addMap(tempoMap);
        ImprecisionMap impMap = ImprecisionMap.createImprecisionMap("timing");
        impMap.addDistributionUniform(0, -20.0, 20.0, 42L);
        Part mpmPart = Part.createPart("Piano", 1, 0, 0);
        mpmPart.getDated().addMap(impMap);
        perf.addPart(mpmPart);
        writeTestData(outputDir, "imprecision_timing", msm, mpm, perf);
    }

    private static void generateImprecisionDynamicsTest(File outputDir) throws Exception {
        System.out.println("Generating imprecision dynamics test...");
        Msm msm = createSimpleMsm("Imprecision Dynamics Test");
        Mpm mpm = createBasicMpm();
        Performance perf = mpm.getAllPerformances().get(0);
        TempoMap tempoMap = TempoMap.createTempoMap();
        tempoMap.addTempo(0, "120", 0.25);
        perf.getGlobal().getDated().addMap(tempoMap);
        DynamicsMap dynMap = DynamicsMap.createDynamicsMap();
        dynMap.addDynamics(0, "100");
        ImprecisionMap impMap = ImprecisionMap.createImprecisionMap("dynamics");
        impMap.addDistributionGaussian(0, 5.0, -15.0, 15.0, 42L);
        Part mpmPart = Part.createPart("Piano", 1, 0, 0);
        mpmPart.getDated().addMap(dynMap);
        mpmPart.getDated().addMap(impMap);
        perf.addPart(mpmPart);
        writeTestData(outputDir, "imprecision_dynamics", msm, mpm, perf);
    }

    private static Msm createChordMsm(String title) {
        Msm msm = Msm.createMsm(title, null, 720);
        Element part = Msm.makePart("Piano", 1, 0, 0);
        Element dated = part.getFirstChildElement("dated");
        Element tsMap = dated.getFirstChildElement("timeSignatureMap");
        tsMap.appendChild(Msm.makeTimeSignature(0, 4, 4, null));
        Element score = dated.getFirstChildElement("score");
        int[][] chords = {{60, 64, 67}, {62, 65, 69}, {64, 67, 71}, {65, 69, 72}};
        int noteNum = 1;
        for (int c = 0; c < chords.length; c++) {
            for (int p = 0; p < chords[c].length; p++) {
                Element note = new Element("note");
                note.addAttribute(new Attribute("xml:id", "http://www.w3.org/XML/1998/namespace", "n" + noteNum++));
                note.addAttribute(new Attribute("date", Double.toString(c * 1440.0)));
                note.addAttribute(new Attribute("midi.pitch", Double.toString(chords[c][p])));
                note.addAttribute(new Attribute("pitchname", "x"));
                note.addAttribute(new Attribute("accidentals", "0.0"));
                note.addAttribute(new Attribute("octave", "3.0"));
                note.addAttribute(new Attribute("duration", "1440.0"));
                score.appendChild(note);
            }
        }
        Element globalDated = msm.getGlobal().getFirstChildElement("dated");
        Element sectionMap = globalDated.getFirstChildElement("sectionMap");
        Element section = new Element("section");
        section.addAttribute(new Attribute("date", "0.0"));
        section.addAttribute(new Attribute("date.end", "5760.0"));
        section.addAttribute(new Attribute("xml:id", "http://www.w3.org/XML/1998/namespace", "sec1"));
        sectionMap.appendChild(section);
        msm.addPart(part);
        msm.setFile(new File(title.replaceAll("\\s+", "_") + ".msm"));
        return msm;
    }

    private static void generateOrnamentationTest(File outputDir) throws Exception {
        System.out.println("Generating ornamentation test...");
        Msm msm = createChordMsm("Ornamentation Test");
        Mpm mpm = createBasicMpm();
        Performance perf = mpm.getAllPerformances().get(0);
        TempoMap tempoMap = TempoMap.createTempoMap();
        tempoMap.addTempo(0, "120", 0.25);
        perf.getGlobal().getDated().addMap(tempoMap);

        Header globalHeader = perf.getGlobal().getHeader();
        OrnamentationStyle ornStyle = OrnamentationStyle.createOrnamentationStyle("orn style");
        OrnamentDef arpeggioDef = OrnamentDef.createDefaultOrnamentDef("arpeggio");   // ticks-domain temporal spread + dynamics gradient
        ornStyle.addDef(arpeggioDef);
        OrnamentDef spreadMsDef = OrnamentDef.createOrnamentDef("spreadMs");          // milliseconds-domain spread, noteoff shift
        spreadMsDef.setDynamicsGradient(-0.5, 0.5);
        spreadMsDef.setTemporalSpread(-30.0, 60.0, OrnamentDef.TemporalSpread.FrameDomain.Milliseconds, 2.0, OrnamentDef.TemporalSpread.NoteOffShift.True);
        ornStyle.addDef(spreadMsDef);
        OrnamentDef spreadMsNoShiftDef = OrnamentDef.createOrnamentDef("spreadMsNoShift"); // ms-domain spread WITHOUT noteoff shift: onset moves, note end stays
        spreadMsNoShiftDef.setTemporalSpread(-40.0, 80.0, OrnamentDef.TemporalSpread.FrameDomain.Milliseconds, 1.0, OrnamentDef.TemporalSpread.NoteOffShift.False);
        ornStyle.addDef(spreadMsNoShiftDef);
        globalHeader.addStyleDef(Mpm.ORNAMENTATION_STYLE, ornStyle);

        OrnamentationMap ornMap = OrnamentationMap.createOrnamentationMap();
        ornMap.addStyleSwitch(0, "orn style");
        ornMap.addOrnament(0.0, "arpeggio", 1.0, null, "orn1");
        java.util.ArrayList<String> descending = new java.util.ArrayList<>();
        descending.add("descending pitch");
        ornMap.addOrnament(1440.0, "arpeggio", 2.0, descending, "orn2");
        ornMap.addOrnament(2880.0, "spreadMs", 1.0, null, "orn3");
        ornMap.addOrnament(4320.0, "spreadMsNoShift", 1.0, null, "orn4");
        perf.getGlobal().getDated().addMap(ornMap);

        Part mpmPart = Part.createPart("Piano", 1, 0, 0);
        DynamicsMap dynMap = DynamicsMap.createDynamicsMap();
        dynMap.addDynamics(0, "100");
        mpmPart.getDated().addMap(dynMap);
        perf.addPart(mpmPart);
        writeTestData(outputDir, "ornamentation", msm, mpm, perf);
    }

    private static void generateAllMapsTest(File outputDir) throws Exception {
        System.out.println("Generating all-maps combined test...");
        Msm msm = createSimpleMsm("All Maps Combined Test");
        Mpm mpm = createBasicMpm();
        Performance perf = mpm.getAllPerformances().get(0);
        // Tempo with transition
        TempoMap tempoMap = TempoMap.createTempoMap();
        tempoMap.addTempo(0, "120", 0.25);
        tempoMap.addTempo(2880, "120", "90", 0.25, 0.5);
        perf.getGlobal().getDated().addMap(tempoMap);
        // Rubato
        RubatoMap rubatoMap = RubatoMap.createRubatoMap();
        rubatoMap.addRubato(0, 2880, 0.3, 0.0, 1.0, true);
        perf.getGlobal().getDated().addMap(rubatoMap);
        // Styles in global header
        Header globalHeader = perf.getGlobal().getHeader();
        MetricalAccentuationStyle maStyle = MetricalAccentuationStyle.createMetricalAccentuationStyle("accent style");
        AccentuationPatternDef apDef = AccentuationPatternDef.createAccentuationPatternDef("4/4", 2880.0);
        apDef.addAccentuation(0.0, 15.0, 0.0, 1.0);
        apDef.addAccentuation(720.0, -5.0, 0.0, 1.0);
        apDef.addAccentuation(1440.0, 8.0, 0.0, 1.0);
        apDef.addAccentuation(2160.0, -5.0, 0.0, 1.0);
        maStyle.addDef(apDef);
        globalHeader.addStyleDef(Mpm.METRICAL_ACCENTUATION_STYLE, maStyle);
        ArticulationStyle artStyle = ArticulationStyle.createArticulationStyle("artic style");
        ArticulationDef staccatoDef = ArticulationDef.createArticulationDef("staccato");
        staccatoDef.setRelativeDuration(0.5);
        staccatoDef.setAbsoluteVelocityChange(10.0);
        artStyle.addDef(staccatoDef);
        ArticulationDef legatoDef = ArticulationDef.createArticulationDef("legato");
        legatoDef.setRelativeDuration(0.95);
        artStyle.addDef(legatoDef);
        globalHeader.addStyleDef(Mpm.ARTICULATION_STYLE, artStyle);
        // Part-level maps
        Part mpmPart = Part.createPart("Piano", 1, 0, 0);
        DynamicsMap dynMap = DynamicsMap.createDynamicsMap();
        dynMap.addDynamics(0, "80");
        dynMap.addDynamics(2880.0, "80", "110", 0.0, 0.0, false);
        mpmPart.getDated().addMap(dynMap);
        MetricalAccentuationMap maMap = MetricalAccentuationMap.createMetricalAccentuationMap();
        maMap.addStyleSwitch(0, "accent style");
        maMap.addAccentuationPattern(0, "4/4", 1.0, true, true);
        mpmPart.getDated().addMap(maMap);
        ArticulationMap artMap = ArticulationMap.createArticulationMap();
        artMap.addStyleSwitch(0, "artic style", "legato");
        artMap.addArticulation(0, "staccato", null, null);
        artMap.addArticulation(720.0, "staccato", null, null);
        mpmPart.getDated().addMap(artMap);
        AsynchronyMap asyncMap = AsynchronyMap.createAsynchronyMap();
        asyncMap.addAsynchrony(0, 25.0);
        asyncMap.addAsynchrony(2880, -15.0);
        mpmPart.getDated().addMap(asyncMap);
        MovementMap movMap = MovementMap.createMovementMap();
        MovementData mdAll = new MovementData();
        mdAll.startDate = 0; mdAll.position = 1.0; mdAll.transitionTo = 0.0; mdAll.endDate = 2880.0; mdAll.controller = "sustain";
        movMap.addMovement(mdAll);
        mpmPart.getDated().addMap(movMap);
        ImprecisionMap impTiming = ImprecisionMap.createImprecisionMap("timing");
        impTiming.addDistributionUniform(0, -10.0, 10.0, 42L);
        mpmPart.getDated().addMap(impTiming);
        ImprecisionMap impDyn = ImprecisionMap.createImprecisionMap("dynamics");
        impDyn.addDistributionUniform(0, -5.0, 5.0, 42L);
        mpmPart.getDated().addMap(impDyn);
        perf.addPart(mpmPart);
        writeTestData(outputDir, "all_maps", msm, mpm, perf);
    }

    private static void writeTestData(File outputDir, String name, Msm msm, Mpm mpm, Performance perf) throws IOException {
        writeFile(new File(outputDir, name + ".msm"), msm.toXML());
        writeFile(new File(outputDir, name + ".mpm"), mpm.toXML());
        Msm augmented = perf.perform(msm);
        writeFile(new File(outputDir, name + "_augmented.msm"), augmented.toXML());
        // Generate expressive MIDI from augmented MSM
        Midi expressiveMidi = msm.exportExpressiveMidi(perf, true);
        if (expressiveMidi != null) {
            File midiFile = new File(outputDir, name + "_expressive.mid");
            expressiveMidi.writeMidi(midiFile);
            System.out.println("  Wrote: " + midiFile.getName() + " (" + midiFile.length() + " bytes)");
        } else {
            System.out.println("  WARNING: expressive MIDI is null for " + name);
        }
        // Generate raw MIDI
        Midi rawMidi = msm.exportMidi(120.0, true);
        if (rawMidi != null) {
            File rawFile = new File(outputDir, name + "_raw.mid");
            rawMidi.writeMidi(rawFile);
            System.out.println("  Wrote: " + rawFile.getName() + " (" + rawFile.length() + " bytes)");
        } else {
            System.out.println("  WARNING: raw MIDI is null for " + name);
        }
    }

    private static void writeFile(File file, String content) throws IOException {
        try (FileWriter fw = new FileWriter(file)) { fw.write(content); }
        System.out.println("  Wrote: " + file.getName());
    }
}
