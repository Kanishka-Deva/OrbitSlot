# OrbitSlot: Project Schedule

**Start:** Wed 18 Nov 2026 · **Target finish:** Tue 12 Jan 2027 (8 weeks)
**Team:** 4 people · **Stack:** Java 21, no build tool (plain `javac`), predict4java, console output only, CelesTrak data

**Rule for the whole schedule:** get a working end-to-end program first (data in, schedule out, metrics printed), using only **Baseline (FCFS)** and **Greedy by priority**. Algorithms 3–5, JavaFX and other extras are in Stage 6 and start only once we decide to go ahead.

---

## Overview

| Stage | Weeks | Dates | Goal | Exit check |
|---|---|---|---|---|
| 0. Setup | W1 | 18 Nov – 24 Nov | Repo, folders, compile script, jars, data snapshot | `run.ps1` compiles and prints "hello" using a class from predict4java |
| 1. Domain model + fake passes | W1–W2 | 21 Nov – 1 Dec | Core classes, plus hard-coded or simulated passes so scheduling never waits on orbit data | A list of `Pass` objects prints to the console |
| 2. Scheduling core | W2–W3 | 25 Nov – 8 Dec | `Scheduler` interface, FCFS, Greedy, constraints, metrics | Both algorithms run on the same input and print a conflict-free schedule plus metrics |
| 3. Real orbit data | W3–W4 | 2 Dec – 15 Dec | CelesTrak TLE snapshot, then predict4java, then `Pass` objects | Scheduler runs on real predicted passes for 1 station |
| **Milestone A** | end W4 | **15 Dec** | **Core works end to end** | Demo: real data, both algorithms, metrics table |
| 4. Scenarios + benchmark | W5 | 16 Dec – 22 Dec | Seeded scenario generator, benchmark mode, Observer alerts | `benchmark` prints FCFS vs Greedy over N seeds |
| 5. Hardening | W6 | 23 Dec – 29 Dec (holidays, light week) | Bug fixes, tests for constraints and schedulers, clean-up | All tests pass and the code is readable enough to defend |
| 6. Extensions (once we decide) | W6–W7 | 23 Dec – 5 Jan | Picked from the extension list below | Each extension is merged only if the core still works |
| 7. Report + viva prep | W8 | 6 Jan – 12 Jan | Measured results, UML, slides, rehearsal | Every claim in the report comes from our own benchmark output |

Stages overlap by a few days on purpose so that different people can work on them in parallel (see "Who does what").

---

## Stage 0: Setup (W1: 18–24 Nov)

- [x] Git repo (local for now; GitHub later). Branch rules: work on a feature branch, merge into `main` with `--no-ff` only when it compiles, so a whole stage can be undone with one `git revert -m 1 <merge>`.
- [ ] Folder layout:
  ```
  oop/
    src/orbitslot/...        Java sources (packages below)
    test/orbitslot/...       JUnit tests (optional, Stage 5)
    lib/                     predict4java jar (+ its deps), junit-platform-console-standalone jar
    data/                    CelesTrak snapshot + stations + assumptions
    out/                     compiled classes (git-ignored)
    run.ps1 / run.sh         compile + run scripts
  ```
- [x] Download the predict4java jar (and check what it depends on) into `lib/`. See `lib/README.md` (predict4java is GPL v2).
- [x] Compile/run script with no build tool (`run.ps1`, `run.sh`):
  ```powershell
  $src = Get-ChildItem -Recurse src -Filter *.java | % FullName
  javac --release 21 -d out -cp "lib/*" $src
  java -cp "out;lib/*" orbitslot.Main @args
  ```
- [x] Save a **CelesTrak snapshot** to `data/celestrak/` (OMM JSON: weather, iridium, cubesat), so demos work offline.
  - Decision: we keep OMM JSON and convert to TLE with `orbitslot.io.OmmToTleConverter` (checked against CelesTrak's own TLE output and round-tripped through predict4java: `.\run.ps1 -Main orbitslot.tools.Predict4JavaCheck`). Objects with 6-digit catalog numbers can't be TLEs and are left out (say so in the report).
- [x] `data/assumptions.properties`: antenna count, slew speed (deg/s), min elevation, downlink rate per satellite type, buffer sizes, priority ranges. **All made-up numbers go here and nowhere else.**

## Stage 1: Domain model + simulated passes (W1–W2: 21 Nov – 1 Dec)

Packages and classes (keep them small and easy to explain):

- `model`: `Satellite` (abstract), with `CubeSat`, `WeatherSat`, `CommsSat` (each with its own downlink rate, buffer and default priority); `GroundStation`; `Antenna`; `Pass` (satellite, start, end, max elevation, azimuth at AOS/LOS); `Assignment` (pass + antenna); `Schedule`
- `io`: `AssumptionsLoader` (reads the properties file)
- `scenario`: `SimulatedPassSource` (random but **seeded** passes), behind a `PassSource` interface

Tasks:
- [x] Write `PassSource` as an interface now, so the real predict4java source in Stage 3 just plugs in. This is our first real use of polymorphism.
- [x] Data volume per pass = downlink rate × pass duration (an assumption; write it down in the assumptions file).
- [x] `Main` prints the passes as a sorted table (`--all` for every row).

Decisions made in Stage 1:
- One ground station: SatNOGS #1492 (Archenhold, Berlin). Antennas configurable, default 2. Window 24 h from a fixed start, min elevation 10°. Times printed in UTC.
- LEO only (period < 128 min); GEO, Molniya and MEO objects are excluded and listed at start-up.
- Priority: each type has a range (weather 7–10, comms 4–7, cubesat 1–5); each satellite gets its own seeded value inside it; `data/priorities.csv` can pin exact values.
- Satellite loading (`catalog` package, with `SatelliteFactory`) was done here instead of Stage 3, so simulated passes use the real satellite list.

## Stage 2: Scheduling core (W2–W3: 25 Nov – 8 Dec), the most important stage

- `scheduler`:
  - `Scheduler` interface: `Schedule schedule(List<Pass> passes, GroundStation station)` (**Strategy pattern**)
  - `FcfsScheduler`: take passes by start time and give each to the first free antenna
  - `GreedyPriorityScheduler`: sort by priority (ties broken by data volume), give each to a free antenna if every constraint allows it
  - `SchedulerRegistry`: maps a name to a scheduler, e.g. `--algo greedy`. Adding algorithms 3–5 later means adding one class and one line here.
- `constraint`:
  - `Constraint` interface: `boolean allows(Assignment candidate, Schedule current)` plus a reason string for why a pass was dropped
  - `MinElevationConstraint`, `SlewTimeConstraint` (time needed to rotate from the last pass's LOS azimuth to the next pass's AOS azimuth), `BufferConstraint` (onboard data must not overflow between contacts; start with a simple version)
  - Schedulers take a `List<Constraint>`, so any constraint can be switched on or off.
- `metrics`:
  - `MetricsCalculator`: total data downlinked, antenna utilisation %, number of high-priority passes missed, total passes dropped
- Console output:
  - [x] Schedule table per antenna (time, satellite, duration, data volume)
  - [x] List of dropped passes, each with its reason
  - [x] Metrics summary
  - [x] A simple ASCII timeline per antenna (works as our "chart" until JavaFX)
- [x] Self-check: a `ScheduleValidator` that confirms no antenna has overlapping passes (slew time included). Run it after every scheduler.

Done in Stage 2 (decisions):
- Passes are all-or-nothing. Between passes an antenna needs slew time (azimuth only, shorter way round) + a fixed setup time (`station.setupSeconds`, default 60 s).
- `BufferConstraint` is **skipped for now** (decided 18 Nov). `Constraint` returns `Optional<String>` (empty = allowed, otherwise the reason).
- FCFS and Greedy share `OrderedAssignmentScheduler` (Template Method); they differ only in the sort order. FCFS ties are broken by pass id.
- High priority = priority >= 8 (`metrics.highPriorityMin`).

## Stage 3: Real orbit data (W3–W4: 2–15 Dec)

- [x] Loading + mapping to subclasses: done in Stage 1 as `SatelliteCatalog` + `SatelliteFactory` (OMM JSON, converted to TLE).
- [x] `Predict4JavaPassSource implements PassSource`: predicts passes over the window for one station, trims them to the minimum elevation (binary search, 1 s), drops passes outside [start, end).
  - predict4java's own `getMaxEl()` underestimates high passes by up to ~12°, so the source finds the highest point itself (ternary search on elevation).
- [x] Station coordinates in `data/stations.csv` (SatNOGS #1492).
- [x] Sanity check against an independent predictor: all 995 passes of 18 Nov matched Skyfield 1.55 (sgp4 2.27) fed the same OMM data, with AOS/LOS within 1 s and max elevation within 0.02°.
- [x] **Fallback:** `--source sim` still uses `SimulatedPassSource`.

### Milestone A (15 Dec): core works end to end
`run.ps1 --source celestrak --station <id> --algo greedy` prints a valid schedule plus metrics, and the same run with `--algo fcfs` also works. **Reached on 18 Nov.**

## Stage 4: Scenarios + benchmark (W5: 16–22 Dec)

- [x] `ScenarioGenerator`: seed -> a reproducible scenario: the passes of a random subset of satellites (`bench.satellitesPerScenario`) from the real or simulated pass pool, plus seeded disruptions.
- [x] `BenchmarkRunner`: runs every registered scheduler and every repair strategy on the same N scenarios (`--benchmark`, `--scenarios N`), prints mean [min - max] per metric and writes `results/benchmark.csv`.
- [x] **Observer pattern:** `ScheduleListener` + `ConsoleAlertListener`, told about disruptions, lost passes (`!!` = high priority) and repairs while `DisruptionSimulator` replays a day. (Dropped passes at planning time are already listed with reasons by the schedule output, so they are not repeated as alerts.)
- [x] Disruption events: `PassCancellation` (planned pass, announced 15-90 min ahead) and `AntennaOutage` (30-120 min, noticed when it starts). Decided 18 Nov.

Measured on 18 Nov (20 scenarios, seeds 42-61, 100 satellites each, real passes, 2 antennas; all numbers rest on our assumptions file):
- SA beat Greedy on score in every scenario, by 0.67-3.14 % (mean 1.90 %), and downlinked more data, but missed slightly more high-priority passes (7.5 vs 6.4 on average), because its objective (priority x MB) values big passes.
- Rolling repair never did worse than no repair; it recovered part of the loss (mean score 180614 vs 177031; high-priority missed 9.0 vs 10.3).
- Rolling (2 h) reached the same score as a full re-plan in every scenario with slightly fewer plan changes (15.5 vs 15.9), because every generated disruption only affects the next 2 h.

## Stage 5: Hardening (W6: 23–29 Dec, light week)

- [ ] JUnit 5 tests via the console-standalone jar: each constraint, both schedulers on small hand-made cases (e.g. 3 overlapping passes where we know the right answer), and the validator.
- [ ] Remove dead code; add short Javadoc comments on the interfaces; everyone should be able to explain every class.
- [ ] Draw the UML class diagram from the real code.

## Stage 6: Extensions (once we decide to go ahead)

Each item is a separate piece of work; we pick what we want and in what order:

| Extension | Where it plugs in |
|---|---|
| 3. Weighted Interval Scheduling (DP) | new `Scheduler` class; optimal only for 1 antenna without extra constraints, so state that clearly |
| 4. Local Search / Simulated Annealing | **done (18 Nov):** `SimulatedAnnealingScheduler` (`--algo sa`), starts from Greedy, maximises priority x MB |
| 5. Rolling-horizon / local repair | **done (18 Nov):** `RollingHorizonRepair` re-plans [t, t+2 h) after each disruption (`--disrupt`), compared with no repair and full re-plan |
| Command pattern undo/redo | `ScheduleCommand` (add/remove/move a pass) + history stack |
| JavaFX Gantt timeline | reads the `Schedule` model; no scheduling logic in the UI |
| Charts | from the benchmark CSV (JFreeChart, or a spreadsheet) |
| Stretch | multiple stations, fairness between operators, OR-Tools comparison |

The program has to keep working after every extension. If it doesn't by the end of W7, we drop that extension.

## Stage 7: Report + viva prep (W8: 6–12 Jan)

- [ ] Run the final benchmark (fixed seeds, recorded in the report) and report *measured* improvement of Greedy (and any extensions) over FCFS. No made-up numbers.
- [ ] Report: problem, assumptions (from the assumptions file), OOP design with each pattern and *why* we used it, algorithms, results, limitations (simulated priorities/buffers, TLE-only satellites, single station).
- [ ] Slides + live demo script (with the offline snapshot); a rehearsal with every member presenting a part.

---

## Who does what (suggested split for 4)

| Person | Stages 0–3 | Stage 4+ |
|---|---|---|
| A | Repo/scripts, `model` package, `Main` CLI | Benchmark runner, report lead |
| B | `scheduler` package (FCFS, Greedy, registry), validator | First algorithm extension |
| C | `constraint` package + `metrics` | Tests, Observer alerts |
| D | CelesTrak snapshot, `TleLoader`, predict4java pass source | Scenario generator, UI/charts extension |

Stage 1 should be agreed by all four together (the class names and fields), because everything else depends on it.

## Weekly checkpoints

- **Every week:** a short meeting; `main` must compile and run.
- **1 Dec:** the model is frozen (only additions after this).
- **8 Dec:** FCFS + Greedy work on simulated passes.
- **15 Dec:** Milestone A.
- **22 Dec:** benchmark table exists.
- **5 Jan:** feature freeze.
- **12 Jan:** submission-ready.
