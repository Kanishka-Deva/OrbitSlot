# Libraries

Plain jars, downloaded from Maven Central and checked against the published SHA-1.
No build tool: `run.ps1` / `run.sh` put `lib/*` on the classpath.

| Jar | Version | Why | License |
|---|---|---|---|
| predict4java | 1.3.1 | SGP4/SDP4 pass prediction | GPL v2 |
| commons-lang | 2.6 | runtime dependency of predict4java | Apache 2.0 |
| commons-logging | 1.2 | runtime dependency of predict4java | Apache 2.0 |
| guava-mini | 0.1.2 | runtime dependency of predict4java | Apache 2.0 |
| jackson-databind / -core / -annotations | 2.17.2 | reading CelesTrak OMM JSON | Apache 2.0 |
| junit-platform-console-standalone | 1.11.3 | JUnit 5 tests (Stage 5) | EPL 2.0 |

predict4java's package is `com.github.amsacode.predict4java`.
