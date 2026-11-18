# OrbitSlot

Schedules satellite passes over one ground station and compares scheduling
algorithms. Java 21, no build tool, console output.

## Run

```powershell
.\run.ps1                          # real passes (predict4java), FCFS + Greedy, metrics table
.\run.ps1 --algo greedy            # one algorithm
.\run.ps1 --source sim             # seeded simulated passes instead of real ones
.\run.ps1 --passes --all           # also print the input pass list, no row limits
.\run.ps1 --help
.\run.ps1 -Main orbitslot.tools.Predict4JavaCheck   # OMM -> TLE -> predict4java round-trip check
```

macOS/Linux/Git Bash: `./run.sh` with the same options.
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
downlinked, antenna use, high-priority passes missed). Every schedule is
re-checked by `ScheduleValidator`; the program stops if one is invalid.

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
| `scenario` | `PassSource` interface: `Predict4JavaPassSource`, `SimulatedPassSource` |
| `constraint` | `Constraint` interface: min elevation, slew + setup |
| `scheduler` | `Scheduler` interface (Strategy), FCFS, Greedy, registry, validator |
| `metrics`, `output` | Metrics and console printing |
