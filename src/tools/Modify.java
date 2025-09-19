package tools;

// Build (example):
//   javac -cp "meico.jar:jackson-annotations.jar:jackson-core.jar:jackson-databind.jar:." ModifyMpmJson.java
// Run:
//   java  -cp "meico.jar:jackson-annotations.jar:jackson-core.jar:jackson-databind.jar:." ModifyMpmJson \
//       --in input.mpm --out output.mpm --params params.json
//
// params.json shape (missing keys are allowed):
// {
//   "increase": { "tempo": 0.5, "dynamics": 0.1 },
//   "exaggerate": {
//     "rubato": 0.4,
//     "tempo": 0.2,
//     "dynamics": 0.0,
//     "temporalSpread": 0.2,
//     "dynamicsGradient": 0.1,
//     "relativeVelocity": 0.3,
//     "relativeDuration": 0.25
//   }
// }

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

// -------- meico imports (exact, from your docs) --------
import meico.mpm.Mpm;
import meico.mpm.elements.Performance;
import meico.mpm.elements.Part;
import meico.mpm.elements.Global;
import meico.mpm.elements.Dated;
import meico.mpm.elements.Header;
import meico.mpm.elements.maps.GenericMap;
import meico.mpm.elements.maps.TempoMap;
import meico.mpm.elements.maps.RubatoMap;
import meico.mpm.elements.maps.OrnamentationMap;
import meico.mpm.elements.maps.data.TempoData;
import meico.mpm.elements.maps.data.RubatoData;
import meico.mpm.elements.styles.GenericStyle;
import meico.mpm.elements.styles.defs.OrnamentDef;
import meico.mpm.elements.styles.defs.OrnamentDef.TemporalSpread;

// nu.xom for attribute edits where needed
import nu.xom.Element;
import nu.xom.Attribute;

public class Modify {

  // ---------- JSON model ----------
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ModifyParams {
    public Increase increase;
    public Exaggerate exaggerate;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Increase {
    public Double tempo;
    public Double dynamics;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Exaggerate {
    public Double rubato;
    public Double tempo;
    public Double dynamics;
    public Double temporalSpread;
    public Double dynamicsGradient;
    public Double relativeVelocity;
    public Double relativeDuration;
  }

  private static class Args {
    File in, out, paramsFile;
  }

  public static void main(String[] argv) throws Exception {
    if (argv.length == 0 || Arrays.asList(argv).contains("--help")) {
      printHelp();
      return;
    }
    Args a = parseArgs(argv);
    validateFiles(a);

    System.out.println("Reading MPM from: " + a.in.getAbsolutePath());

    ModifyParams params = readParams(a.paramsFile);
    validateParams(params);

    Mpm mpm = new Mpm(a.in);

    if (params.increase != null) {
      if (params.increase.tempo != null) {
        applyTempoScale(mpm, params.increase.tempo);
      }
      if (params.increase.dynamics != null) {
        // TODO
      }
    }
    if (params.exaggerate != null) {
      if (params.exaggerate.tempo != null) {
        applyTempoScale(mpm, params.exaggerate.tempo);
      }
      if (params.exaggerate.rubato != null) {
        applyRubatoIntensityScale(mpm, params.exaggerate.rubato);
      }
      if (params.exaggerate.temporalSpread != null) {
        applyOrnamentTemporalSpreadScale(mpm, params.exaggerate.temporalSpread);
      }
      // TODO
      // dynamics, dynamicsGradient, relativeVelocity, relativeDuration
    }

    // Write result
    if (!mpm.writeMpm(a.out.getAbsolutePath())) {
      throw new IOException("Failed to write MPM to " + a.out);
    }
    System.out.println("Wrote modified MPM to: " + a.out.getAbsolutePath());
  }

  private static void printHelp() {
    System.out.println(
      "ModifyMpmJson - apply ModifyParams (JSON) to an MPM (meico)\n\n" +
      "USAGE:\n" +
      "  java ModifyMpmJson --in input.mpm --out output.mpm --params params.json\n\n" +
      "FLAGS:\n" +
      "  --in <path>       Input .mpm\n" +
      "  --out <path>      Output .mpm\n" +
      "  --params <path>   JSON file with ModifyParams\n" +
      "  --help\n"
    );
  }

  private static Args parseArgs(String[] argv) {
    Args a = new Args();
    for (int i = 0; i < argv.length; i++) {
      String k = argv[i];
      String v = (i + 1 < argv.length) ? argv[i + 1] : null;
      switch (k) {
        case "--in": a.in = new File(reqValue(k, v)); i++; break;
        case "--out": a.out = new File(reqValue(k, v)); i++; break;
        case "--params": a.paramsFile = new File(reqValue(k, v)); i++; break;
        default:
          if (k.startsWith("--")) throw new IllegalArgumentException("Unknown option: " + k);
      }
    }
    return a;
  }

  private static String reqValue(String key, String v) {
    if (v == null) throw new IllegalArgumentException("Missing value for " + key);
    return v;
  }

  private static void validateFiles(Args a) {
    if (a.in == null || a.out == null || a.paramsFile == null) {
      throw new IllegalArgumentException("Required: --in, --out, --params. Use --help.");
    }
    if (!a.in.exists()) throw new IllegalArgumentException("Input MPM not found: " + a.in);
    if (!a.paramsFile.exists()) throw new IllegalArgumentException("Params JSON not found: " + a.paramsFile);
  }

  // ---------- JSON I/O & validation ----------
  private static ModifyParams readParams(File jsonFile) throws IOException {
    ObjectMapper om = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    return om.readValue(jsonFile, ModifyParams.class);
  }

  private static void validateParams(ModifyParams p) {
    boolean any =
        (p.increase != null && (nz(p.increase.tempo) || nz(p.increase.dynamics))) ||
        (p.exaggerate != null && (
            nz(p.exaggerate.rubato) ||
            nz(p.exaggerate.tempo) ||
            nz(p.exaggerate.dynamics) ||
            nz(p.exaggerate.temporalSpread) ||
            nz(p.exaggerate.dynamicsGradient) ||
            nz(p.exaggerate.relativeVelocity) ||
            nz(p.exaggerate.relativeDuration)
        ));
    if (!any) throw new IllegalArgumentException("Params JSON must contain at least one factor.");

    // Non-negative finite checks (your schema requirement)
    checkNonNeg(p.increase != null ? p.increase.tempo : null, "increase.tempo");
    checkNonNeg(p.increase != null ? p.increase.dynamics : null, "increase.dynamics");
    if (p.exaggerate != null) {
      checkNonNeg(p.exaggerate.rubato, "exaggerate.rubato");
      checkNonNeg(p.exaggerate.tempo, "exaggerate.tempo");
      checkNonNeg(p.exaggerate.dynamics, "exaggerate.dynamics");
      checkNonNeg(p.exaggerate.temporalSpread, "exaggerate.temporalSpread");
      checkNonNeg(p.exaggerate.dynamicsGradient, "exaggerate.dynamicsGradient");
      checkNonNeg(p.exaggerate.relativeVelocity, "exaggerate.relativeVelocity");
      checkNonNeg(p.exaggerate.relativeDuration, "exaggerate.relativeDuration");
    }
  }

  private static boolean nz(Double d) { return d != null; }
  private static void checkNonNeg(Double d, String name) {
    if (d == null) return;
    if (!Double.isFinite(d) || d < 0) {
      throw new IllegalArgumentException(name + " must be a non-negative finite number.");
    }
  }

  // ---------- Transform helpers ----------
  // Apply (f + 1) scaling around mean for tempo transitions with bpm & transitionTo present.
  private static void applyTempoScale(Mpm mpm, double f) {
    forEachDated(mpm, dated -> {
      GenericMap map = dated.getMap(Mpm.TEMPO_MAP); // TempoMap expected
      if (!(map instanceof TempoMap)) return;
      TempoMap tempoMap = (TempoMap) map;
      int n = map.size();
      for (int i = 0; i < n; i++) {
        TempoData td = tempoMap.getTempoDataOf(i);
        System.out.println("TempoData " + i + ": " + td.startDate);
        if (td == null) continue;

        // We want entries that have both bpm and transitionTo (i.e., a transition segment)
        if (td.bpm == null || td.transitionTo == null) continue;

        double mean = (td.bpm + td.transitionTo) / 2.0;
        double scale = f + 1.0;
        double newBpm = mean + (td.bpm - mean) * scale;
        double newTo  = mean + (td.transitionTo - mean) * scale;

        // Update XML attributes (TempoData exposes xml Element)
        if (td.xml != null) {
          setNumericAttr(td.xml, "bpm", newBpm);
          setNumericAttr(td.xml, "transition.to", newTo);
          // Keep string mirrors if present
          td.bpm = newBpm;
          td.bpmString = Double.toString(newBpm);
          td.transitionTo = newTo;
          td.transitionToString = Double.toString(newTo);
        }
      }
    });
  }

  // Apply (x - 1) * (f + 1) + 1 for rubato intensity where present.
  private static void applyRubatoIntensityScale(Mpm mpm, double f) {
    forEachDated(mpm, dated -> {
      GenericMap map = dated.getMap(Mpm.RUBATO_MAP);
      if (!(map instanceof RubatoMap)) return;
      RubatoMap rubatoMap = (RubatoMap) map;
      int n = map.size();
      for (int i = 0; i < n; i++) {
        RubatoData rd = rubatoMap.getRubatoDataOf(i);
        if (rd == null || rd.intensity == null) continue;
        double newIntensity = (rd.intensity - 1.0) * (f + 1.0) + 1.0;

        if (rd.xml != null) {
          setNumericAttr(rd.xml, "intensity", newIntensity);
          rd.intensity = newIntensity;
        }
      }
    });
  }

  // Multiply OrnamentDef.TemporalSpread.frameLength by (f + 1) in both Global and Part headers.
  private static void applyOrnamentTemporalSpreadScale(Mpm mpm, double f) {
    double scale = f + 1.0;

    // Global header
    forEachHeader(mpm, header -> {
      Map<String, GenericStyle> styles = header.getAllStyleDefs(Mpm.ORNAMENTATION_STYLE);

      for (GenericStyle style : styles.values()) {
        HashMap<String, OrnamentDef> defs = style.getAllDefs();
        for (OrnamentDef def : defs.values()) {
          TemporalSpread ts = def.getTemporalSpread();
          if (ts != null) {
            double oldLen = ts.getFrameLength();
            ts.setFrameLength(oldLen * scale);
          }
        }
      }
    });
  }

  // ---------- Traversal ----------
  private static void forEachDated(Mpm mpm, Consumer<Dated> visitor) {
    // global dated
    for (int i = 0; i < mpm.size(); i++) {
      Performance perf = mpm.getPerformance(i);
      if (perf == null) continue;

      Global g = perf.getGlobal();
      if (g != null && g.getDated() != null) visitor.accept(g.getDated());

      for (Part p : perf.getAllParts()) {
        if (p != null && p.getDated() != null) visitor.accept(p.getDated());
      }
    }
  }

  private static void forEachHeader(Mpm mpm, Consumer<Header> visitor) {
    for (int i = 0; i < mpm.size(); i++) {
      Performance perf = mpm.getPerformance(i);
      if (perf == null) continue;

      Global g = perf.getGlobal();
      if (g != null && g.getHeader() != null) visitor.accept(g.getHeader());

      for (Part p : perf.getAllParts()) {
        if (p != null && p.getHeader() != null) visitor.accept(p.getHeader());
      }
    }
  }

  // ---------- XML helpers ----------
  private static void setNumericAttr(Element xml, String name, double value) {
    Attribute a = xml.getAttribute(name);
    if (a == null) {
      xml.addAttribute(new Attribute(name, Double.toString(value)));
    } else {
      a.setValue(Double.toString(value));
    }
  }
}
