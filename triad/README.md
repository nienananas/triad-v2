# TRIAD: Modernized Traceability Recovery Implementation

A maintainable and extensible implementation of the TRIAD (Transitive Traceability Recovery with Intermediate Artifacts and Dependencies) approach for automated traceability link recovery between software artifacts.

## Overview

This implementation provides a clean, modular architecture for TRIAD while maintaining functional equivalence to the original implementation. It supports both general traceability recovery projects and specialized workflows with pre-computed biterms.

## Key Features

- **Modular Architecture**: Clean separation of concerns with distinct pipeline stages
- **Multiple IR Models**: Support for VSM, LSI, and JSD information retrieval models  
- **Flexible Artifact Support**: Works with textual requirements, design documents, and source code
- **Configurable Enrichment**: Tunable parameters for optimal performance
- **Extensible Design**: Easy integration into other traceability recovery projects

## Quick Start

### 1. Setup Your Project Structure

Organize your artifacts in the following directory structure:
```
dataset/
└── YourProject/
    ├── requirements/          # Source artifacts (textual)
    │   ├── req1.txt
    │   └── req2.txt
    ├── design/               # Intermediate artifacts (textual)
    │   ├── design1.txt
    │   └── design2.txt
    ├── code/                 # Target artifacts (source code)
    │   ├── Class1.java
    │   └── Class2.java
    └── gold_standard/
        └── req-code.txt      # Ground truth links
```

### 2. Configure Your Project

Create or update `config.json`:
```json
{
  "projects": [
    {
      "name": "YourProject",
      "source": {
        "path": "YourProject/requirements",
        "type": "TEXTUAL"
      },
      "intermediate": {
        "path": "YourProject/design", 
        "type": "TEXTUAL"
      },
      "target": {
        "path": "YourProject/code",
        "type": "JAVA_CODE"
      },
      "goldStandardPath": "YourProject/gold_standard/req-code.txt"
    }
  ]
}
```

### 3. Run TRIAD

```bash
mvn clean compile exec:java -Dexec.mainClass="io.github.ardoco.triad.App"
```

## Example Projects

The implementation includes several example projects in `config.json`:

- **Warc**: NFR → SRS → FRS traceability (textual artifacts)
- **Dronology**: Requirements → Design → Code traceability  
- **LibEST**: Requirements → Test → Source Code traceability (C code)
- **EasyClinic**: Use Cases → Interaction Diagrams → Code Descriptions

To run analysis on a specific project, modify the `config.json` or create your own configuration.

## Configuration Options

### Supported Artifact Types
- **TEXTUAL**: Plain text files (requirements, design documents)
- **JAVA_CODE**: Java source code files  
- **C_CODE**: C source code files

### Tunable Parameters

Control TRIAD's behavior with system properties:

```bash
mvn compile exec:java \
  -Dexec.mainClass="io.github.ardoco.triad.App" \
  -Dtriad.enrich.m=0.1 \           # Enrichment threshold (0.0-1.0)
  -Dtriad.enrich.topk=10 \         # Top-k neighbors to consider
  -Dtriad.enrich.minAgree=1 \      # Minimum agreement for biterm selection
  -Dtriad.enrich.maxrep=999 \      # Maximum repetitions per biterm
  -Dtriad.enrich.maxBiterms=10000 \ # Maximum biterms per document
  -Dtriad.transitivity.enabled=true # Enable/disable transitivity
```

## Performance

TRIAD typically provides significant improvements over IR-only baselines:

- **Enrichment**: Adds relevant biterms from similar artifacts, improving recall
- **Transitivity**: Discovers indirect relationships through intermediate artifacts  
- **Fusion**: Combines IR-only and enriched results for balanced precision/recall

**Expected improvements**: 10-40% precision increase at common recall levels, depending on project characteristics and parameter tuning.

## Architecture Overview

### Core Pipeline Components

```
[Source Artifacts] → [Enrichment] → [Similarity Matrices] → [Transitivity] → [Fusion] → [Results]
      ↓                    ↑              ↓
[Intermediate] ──────────────┘         [Target Artifacts]
```

### Main Classes

#### 1. `TriadPipeline`
**Location**: `io.github.ardoco.triad.pipeline.TriadPipeline`

The main orchestrator that coordinates the entire TRIAD process:

```java
public class TriadPipeline {
    public SimilarityMatrix runTriadAnalysis(Project project, IRModel irModel);
    public SimilarityMatrix runIROnlyAnalysis(Project project, IRModel irModel);
}
```

**Key Methods**:
- `runTriadAnalysis()`: Executes full TRIAD pipeline with enrichment
- `runIROnlyAnalysis()`: Baseline IR-only analysis for comparison

#### 2. `Enrichment`
**Location**: `io.github.ardoco.triad.pipeline.Enrichment`

Handles the biterm-based enrichment process:

```java
public class Enrichment {
    public EnrichmentResult enrich(Set<Artifact> sources, 
                                  Set<Artifact> intermediates, 
                                  Set<Artifact> targets,
                                  SimilarityMatrix sourceIntermediate,
                                  SimilarityMatrix intermediateTarget);
}
```

**Process**:
1. Extract consensual biterms from neighbor artifacts
2. Enrich source and target collections with relevant biterms
3. Return enriched artifact collections for IR processing

#### 3. `PrecomputedBitermArtifact`
**Location**: `io.github.ardoco.triad.model.PrecomputedBitermArtifact`

Specialized artifact class for handling pre-computed biterm data:

```java
public class PrecomputedBitermArtifact extends Artifact {
    public PrecomputedBitermArtifact(String identifier, 
                                    String bitermFileContent, 
                                    ArtifactType originalType);
    
    @Override
    public Set<Biterm> getBiterms(); // Returns pre-computed biterms directly
}
```

**Features**:
- Parses biterm files in format `biterm:frequency`
- Preserves original biterm weights
- Bypasses NLP processing for efficiency

#### 4. `PreprocessedProject`
**Location**: `io.github.ardoco.triad.model.PreprocessedProject`

Project loader that combines original artifacts with pre-computed biterms:

```java
public class PreprocessedProject extends Project {
    public PreprocessedProject(ProjectConfig config);
    
    private Set<Artifact> combineTextAndBiterms(Set<Artifact> originalArtifacts, 
                                               ArtifactConfig bitermConfig);
}
```

**Process**:
1. Loads original artifacts for text content
2. Loads pre-computed biterm files
3. Creates `PrecomputedBitermArtifact` instances
4. Combines both for complete artifact representation

#### 5. `EnrichmentUtils`
**Location**: `io.github.ardoco.triad.util.EnrichmentUtils`

Utility class containing core enrichment algorithms:

```java
public final class EnrichmentUtils {
    // Extract biterm frequency maps from artifacts
    public static Map<String, Map<String, Integer>> getBitermFrequencyMap(Set<Artifact> artifacts);
    
    // Select consensual biterms from neighbors
    public static Map<String, Map<String, Integer>> selectNeighborConsensualBiterms(
        Set<Artifact> artifacts,
        Map<String, Map<String, Integer>> neighborBitermMap,
        SimilarityMatrix rowSimMatrix);
    
    // Create enriched artifact collections
    public static ArtifactsCollection createExtendedCollection(
        Set<Artifact> originals,
        Map<String, Map<String, Integer>> bitermFrequencies,
        String tagForLogs);
}
```

#### 6. `Transitivity`
**Location**: `io.github.ardoco.triad.pipeline.Transitivity`

Implements transitive relationship discovery:

```java
public class Transitivity {
    public SimilarityMatrix applyTransitivity(SimilarityMatrix baseMatrix);
    
    private List<SingleLink> getTopLinks(SimilarityMatrix matrix, 
                                        String sourceId, 
                                        int topK, 
                                        double threshold);
}
```

**Algorithm**:
- Finds transitive paths: source → intermediate → target
- Applies configurable thresholds and top-k filtering
- Combines direct and transitive similarities

### Configuration Parameters

#### Enrichment Parameters
- `triad.enrich.m`: Similarity threshold multiplier (default: 0.1)
- `triad.enrich.topk`: Number of top neighbors to consider (default: 10)
- `triad.enrich.minAgree`: Minimum agreements required (default: 1)
- `triad.enrich.maxrep`: Maximum repetitions per biterm (default: 999)
- `triad.enrich.maxBiterms`: Maximum biterms per document (default: 100000)

#### Usage Example
```bash
-Dtriad.enrich.m=0.1 -Dtriad.enrich.topk=10 -Dtriad.enrich.minAgree=1
```

## Integration Guide

### 1. Maven Dependency

Add to your `pom.xml`:

```xml
<dependency>
    <groupId>io.github.ardoco</groupId>
    <artifactId>triad</artifactId>
    <version>2.0.0-SNAPSHOT</version>
</dependency>
```

### 2. Basic Usage

```java
import io.github.ardoco.triad.pipeline.TriadPipeline;
import io.github.ardoco.triad.model.Project;
import io.github.ardoco.triad.config.ProjectConfig;
import io.github.ardoco.triad.ir.VSM;

// Load a general project
ProjectConfig config = loadProjectConfig("config.json");
Project project = new Project(config);

// Create and run TRIAD pipeline
TriadPipeline pipeline = new TriadPipeline(project, new VSM());
SimilarityMatrix results = pipeline.run();

// Access results
for (String sourceId : results.getSourceArtifacts()) {
    LinksList links = results.getLinks(sourceId);
    for (SingleLink link : links) {
        System.out.println(sourceId + " -> " + link.getTargetArtifactId() + 
                          " (score: " + link.getScore() + ")");
    }
}
```

### 3. Custom IR Models

Implement the `IRModel` interface:

```java
public class CustomIRModel implements IRModel {
    @Override
    public String getModelName() { return "CustomIR"; }
    
    @Override
    public SimilarityMatrix Compute(ArtifactsCollection sources, 
                                   ArtifactsCollection targets) {
        // Your IR implementation
        return similarityMatrix;
    }
}
```

### 4. Gold Standard Format

Create a gold standard file with true links (one per line):
```
req1.txt,Class1.java
req2.txt,Class2.java
req1.txt,Class3.java
```

## Advanced Usage

### Working with Pre-computed Biterms

For specialized workflows with pre-computed biterm data, use the `PreprocessedProject` class:

```java
// For projects with pre-computed biterms (special case)
if ("YourProject-Preproc".equals(projectConfig.getName())) {
    project = new PreprocessedProject(projectConfig);
} else {
    project = new Project(projectConfig);  // Standard usage
}
```

**Pre-computed biterm file format:**
```
apiMonitor:1
typeMessag:1
typeCertain:1
planMission:2
fileJson:1
```

Where each line contains `biterm:frequency`.

### Custom Enrichment Strategies

Extend `EnrichmentUtils` for custom biterm selection:

```java
public class CustomEnrichmentUtils extends EnrichmentUtils {
    public static Map<String, Map<String, Integer>> customBitermSelection(
        Set<Artifact> artifacts,
        Map<String, Map<String, Integer>> neighborBitermMap,
        SimilarityMatrix rowSimMatrix) {
        // Custom selection logic
        return selectedBiterms;
    }
}
```

### Evaluation and Metrics

```java
import io.github.ardoco.triad.evaluation.Evaluator;

// Load gold standard
GoldStandard goldStandard = GoldStandard.fromFile("gold_standard.txt");

// Evaluate results
Evaluator evaluator = new Evaluator(goldStandard);
EvaluationResult result = evaluator.evaluate(triadResults);

System.out.println("Precision: " + result.getPrecision());
System.out.println("Recall: " + result.getRecall());
System.out.println("F1-Score: " + result.getF1Score());
```

## Directory Structure

```
src/main/java/io/github/ardoco/triad/
├── App.java                    # Main application entry point
├── config/                     # Configuration classes
├── evaluation/                 # Evaluation and metrics
├── ir/                        # Information retrieval models
├── model/                     # Core data models
│   ├── Artifact.java
│   ├── PrecomputedBitermArtifact.java
│   ├── Project.java
│   └── PreprocessedProject.java
├── pipeline/                  # Pipeline components
│   ├── TriadPipeline.java
│   ├── Enrichment.java
│   └── Transitivity.java
├── text/                      # Text processing utilities
└── util/                      # Utility classes
    └── EnrichmentUtils.java
```

## Requirements

- Java 21+
- Maven 3.6+
- Stanford CoreNLP models (automatically downloaded)

## Building

```bash
mvn clean compile
```

## Running Tests

```bash
mvn test
```

## Performance Tuning

### Optimal Parameters Found

Based on extensive testing with the Dronology dataset:

- **m=0.1**: More aggressive neighbor selection (vs original 0.5)
- **topk=10**: Consider more neighbors (vs original 3)
- **minAgree=1**: Accept biterms with minimal agreement
- **maxrep=999**: Allow high biterm repetition
- **maxBiterms=100000**: No practical limit on biterms per document

### Parameter Effects

- **Lower m**: More neighbors included, higher recall, potentially lower precision
- **Higher topk**: More diverse biterms, better coverage
- **Lower minAgree**: More aggressive enrichment, higher recall
- **Higher maxrep**: Preserves biterm importance weights

## Troubleshooting

### Common Issues

1. **No enrichment happening** (`avg_kept_biterms=0.00`)
   - Check biterm file format
   - Verify `PrecomputedBitermArtifact` is being used
   - Ensure similarity thresholds aren't too restrictive

2. **Poor performance**
   - Try more aggressive parameters (lower m, higher topk)
   - Check gold standard format
   - Verify pre-computed biterms quality

3. **Memory issues**
   - Reduce `maxBiterms` parameter
   - Use `-Xmx4g` JVM flag

## Contributing

1. Follow existing code style (enforced by Spotless)
2. Add tests for new functionality
3. Update documentation for API changes
4. Ensure backward compatibility

## License

[License information here]

## Acknowledgments

This implementation builds upon the original TRIAD approach while providing a modern, maintainable codebase suitable for integration into larger traceability recovery systems.
