# TRIAD Implementation

This repository implements the [TRIAD](https://dl.acm.org/doi/10.1145/3597503.3639164) mechanism proposed by Gao et al.

# Usage

Edit the config.json file according to the projects you aim to analyze. Then run the main entry point in App.java using Maven:

```bash
mvn clean compile exec:java -Dexec.mainClass="io.github.ardoco.App" -Dtriad.enrich.m=0.35 -Dtriad.enrich.topk=10 -Dtriad.enrich.minAgree=1 -Dtriad.enrich.maxrep=3
```

The parameters are optional and are used to configure the enrichment step.
- `m` — Fusion weight between baseline IR and TRIAD-enriched scores
- `topk` — Nearest neighbors considered per artifact when forming consensus
- `minAgree` — Minimum neighbor agreements required to accept a biterm
- `maxrep` — Max times the same enrichment term may be added to an artifact