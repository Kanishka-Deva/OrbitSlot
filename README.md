# OrbitSlot

Schedules satellite passes over one ground station and compares scheduling
algorithms. Java 21, no build tool, console output.

## Run

```powershell
.\run.ps1                          # real passes (predict4java), FCFS + Greedy + SA, metrics table
.\run.ps1 --algo greedy            # one algorithm (fcfs, greedy, sa)
.\run.ps1 --source sim             # seeded simulated passes instead of real ones
.\run.ps1 --passes --all           # also print the input pass list, no row limits
.\run.ps1 --disrupt                # plan with Greedy, replay seeded disruptions with alerts + rolling repair
.\run.ps1 --disrupt --repair all   # compare no repair, rolling-horizon (2 h) and full re-plan
.\run.ps1 --benchmark              # 20 seeded scenarios: all algorithms and repairs, summary + results/benchmark.csv
.\run.ps1 --benchmark --scenarios 5
.\run.ps1 --charts                 # any mode: also write PNG charts + Gantt timelines to results/charts/
.\run.ps1 --help
.\run.ps1 -Main orbitslot.tools.Predict4JavaCheck   # OMM -> TLE -> predict4java round-trip check
.\test.ps1                         # compile src + test, run all JUnit 5 tests
```

macOS/Linux/Git Bash: `./run.sh` and `./test.sh` with the same options.
UML class diagrams: [docs/uml.md](docs/uml.md).
If PowerShell blocks the script: `powershell -ExecutionPolicy Bypass -File run.ps1`.

## Input (all in `data/`)

| File | Contents |
|---|---|
| `celestrak/*.json` | Orbit data snapshot (OMM JSON). File name decides the satellite type. |
| `stations.csv` | Ground stations; `station.id` (or `--station`) picks one. |
| `assumptions.properties` | Every made-up number: antennas, min elevation, slew speed, setup time, window, rates, priorities, seed. |
| `priorities.csv` | Optional fixed priority per NORAD id. |

## Output

For each algorithm: the passes on each antenna, an ASCII timeline per antenna,
the dropped passes with the reason, then a metrics table (passes scheduled, data
downlinked, antenna use, score, high-priority passes missed). Every schedule is
re-checked by `ScheduleValidator`; the program stops if one is invalid.

`--disrupt` prints a timestamped alert per disruption and per lost pass
(`!!` = high priority), what each repair changed, and before/after metrics with
forced losses and plan changes (stability). `--benchmark` prints mean
[min - max] per algorithm and per repair strategy and writes every row to
`results/benchmark.csv` (git-ignored; regenerate any time).

`--charts` (plain Java2D, no extra library) writes PNGs to `results/charts/`:

| Mode | Files |
|---|---|
| plan | `gantt-<algo>.png` (antennas x time, passes coloured by satellite type, missed high-priority passes), `gantt-compare.png` (all algorithms stacked), `plan-<metric>.png` |
| `--disrupt` | `gantt-disrupt-<repair>.png` (plan vs executed: outages hatched, cancellations crossed, repair changes outlined), `disrupt-<metric>.png` |
| `--benchmark` | `bench-plan-<metric>.png` and `bench-repair-<metric>.png` (mean bar, min-max whisker), `bench-plan-runtime.png`, `bench-plan-score-gain.png` (gain over FCFS per scenario) |

Metrics charted: score, data MB, data %, high-priority missed, passes scheduled, antenna use; for repairs also plan changes.

## Algorithms

| Name | Idea |
|---|---|
| `fcfs` | Earliest pass first, first free antenna (baseline) |
| `greedy` | Highest priority first, ties by data volume |
| `sa` | Simulated annealing from the Greedy schedule; maximises score = sum of priority x MB; never returns worse than Greedy |
| repair `none` | Accept the losses |
| repair `rolling` | After a disruption at t, re-plan only [t, t+2 h); keep the past and the rest of the plan |
| repair `full` | Re-plan everything after t (comparison for stability) |

Disruptions (seeded): cancellations of planned passes, announced 15-90 min ahead,
and antenna outages of 30-120 min, noticed when they start.

## Model in one paragraph

A pass is tracked completely or not at all. An antenna tracks one pass at a
time and needs slew time (azimuth difference / slew speed) plus a fixed setup
time between passes. Data volume = downlink rate x pass duration. Buffers are
not modelled yet. Only LEO satellites that fit the TLE format are used.

## Code map

| Package | Role |
|---|---|
| `model` | Satellite hierarchy, GroundStation, Antenna, Pass, Assignment, Schedule |
| `io` | Loading assumptions, stations, priorities, OMM JSON; OMM to TLE conversion |
| `catalog` | SatelliteFactory, PriorityAssigner, SatelliteCatalog (LEO filter) |
| `scenario` | `PassSource` interface: `Predict4JavaPassSource`, `SimulatedPassSource`; `ScenarioGenerator` |
| `constraint` | `Constraint` interface: min elevation, slew + setup, antenna outage |
| `scheduler` | `Scheduler` interface (Strategy), FCFS, Greedy (Template Method), SA, registry, validator |
| `repair` | `RepairStrategy` (Strategy): none, rolling horizon; `DisruptionSimulator` |
| `alert` | `ScheduleListener` (Observer), `ConsoleAlertListener` |
| `benchmark` | `BenchmarkRunner` |
| `metrics`, `output` | Metrics, console tables, CSV |
| `chart` | `GanttChart`, `BarChart`, `GroupedBarChart` (Java2D to PNG), `ChartWriter`; read the model only |
