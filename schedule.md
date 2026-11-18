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

- [ ] Git repo on GitHub; everyone can push. Agree on simple branch rules (work on a feature branch, merge into `main` only when it compiles).
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
- [ ] Download the predict4java jar (and check what it depends on) into `lib/`.
- [ ] Compile/run script with no build tool:
  ```powershell
  $src = Get-ChildItem -Recurse src -Filter *.java | % FullName
  javac --release 21 -d out -cp "lib/*" $src
  java -cp "out;lib/*" orbitslot.Main @args
  ```
- [ ] Save a **CelesTrak snapshot** in TLE format (e.g. the `weather`, `cubesat` and `stations` groups) to `data/`, so demos work offline.
  - Note: predict4java reads TLEs. Objects with 6-digit catalog numbers have no TLE, so leave them out and say so in the report.
- [ ] `data/assumptions.properties`: antenna count, slew speed (deg/s), min elevation, downlink rate per satellite type, buffer sizes, priority ranges. **All made-up numbers go here and nowhere else.**

## Stage 1: Domain model + simulated passes (W1–W2: 21 Nov – 1 Dec)

Packages and classes (keep them small and easy to explain):

- `model`: `Satellite` (abstract), with `CubeSat`, `WeatherSat`, `CommsSat` (each with its own downlink rate, buffer and default priority); `GroundStation`; `Antenna`; `Pass` (satellite, start, end, max elevation, azimuth at AOS/LOS); `Assignment` (pass + antenna); `Schedule`
- `io`: `AssumptionsLoader` (reads the properties file)
- `scenario`: `SimulatedPassSource` (random but **seeded** passes), behind a `PassSource` interface

Tasks:
- [ ] Write `PassSource` as an interface now, so the real predict4java source in Stage 3 just plugs in. This is our first real use of polymorphism.
- [ ] Data volume per pass = downlink rate × pass duration (an assumption; write it down in the assumptions file).
- [ ] `Main` prints the passes as a sorted table.

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
  - [ ] Schedule table per antenna (time, satellite, duration, data volume)
  - [ ] List of dropped passes, each with its reason
  - [ ] Metrics summary
  - [ ] A simple ASCII timeline per antenna (works as our "chart" until JavaFX)
- [ ] Self-check: a `ScheduleValidator` that confirms no antenna has overlapping passes (slew time included). Run it after every scheduler.

## Stage 3: Real orbit data (W3–W4: 2–15 Dec)

- [ ] `TleLoader`: reads the CelesTrak TLE snapshot and maps each satellite to a subclass (using the group name or a small mapping file). This can be a simple **Factory**.
- [ ] `Predict4JavaPassSource implements PassSource`: predicts passes over a time window (e.g. 24 h) for one station, using the minimum elevation from the assumptions file.
- [ ] Station coordinates in `data/stations.csv` (we can take a few real ones from SatNOGS).
- [ ] Compare a few predicted passes with an online pass predictor as a sanity check.
- [ ] **Fallback:** if predict4java causes trouble, keep using `SimulatedPassSource` and keep the rest of the project moving. Orbit prediction must not block scheduling.

### Milestone A (15 Dec): core works end to end
`run.ps1 --source celestrak --station <id> --algo greedy` prints a valid schedule plus metrics, and the same run with `--algo fcfs` also works.

## Stage 4: Scenarios + benchmark (W5: 16–22 Dec)

- [ ] `ScenarioGenerator`: seed + parameters (number of satellites, antennas, time window, how many passes overlap) gives a reproducible pass list.
- [ ] `BenchmarkRunner`: runs every registered scheduler on the same N scenarios and prints a comparison table (mean and min/max for each metric). It can also write a CSV so charts can be made later.
- [ ] **Observer pattern:** a `ScheduleListener` interface with a `ConsoleAlertListener` that is told about conflicts, dropped high-priority passes and, later, cancellations.
- [ ] Cancellation *events* in the scenario generator (data only for now). The actual repair logic is algorithm 5, which waits until we decide to build it.

## Stage 5: Hardening (W6: 23–29 Dec, light week)

- [ ] JUnit 5 tests via the console-standalone jar: each constraint, both schedulers on small hand-made cases (e.g. 3 overlapping passes where we know the right answer), and the validator.
- [ ] Remove dead code; add short Javadoc comments on the interfaces; everyone should be able to explain every class.
- [ ] Draw the UML class diagram from the real code.

## Stage 6: Extensions (once we decide to go ahead)

Each item is a separate piece of work; we pick what we want and in what order:

| Extension | Where it plugs in |
|---|---|
| 3. Weighted Interval Scheduling (DP) | new `Scheduler` class; optimal only for 1 antenna without extra constraints, so state that clearly |
| 4. Local Search / Simulated Annealing | new `Scheduler` that starts from the Greedy result |
| 5. Rolling-horizon / local repair | listens for cancellation events (Observer) and repairs only the affected part of the schedule |
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
