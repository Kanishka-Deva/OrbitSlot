# OrbitSlot: UML class diagrams

Drawn from the code in `src/orbitslot/` (Stage 5). Split into four views so each
fits on a slide. Only the important members are shown; getters of records
are left out. Renders on GitHub and in VS Code (Mermaid).

Legend: `<|--` inheritance / implements, `*--` composition, `o--` aggregation,
`-->` association, `..>` dependency (uses).

## 1. Domain model (`model`)

```mermaid
classDiagram
    direction LR
    class Satellite {
        <<abstract sealed>>
        -int noradId
        -String name
        -int priority
        -double downlinkKbps
        -double bufferMB
        +typeName()* String
    }
    class WeatherSat {
        <<nested in Satellite>>
    }
    class CommsSat {
        <<nested in Satellite>>
    }
    class CubeSat {
        <<nested in Satellite>>
    }
    Satellite <|-- WeatherSat
    Satellite <|-- CommsSat
    Satellite <|-- CubeSat

    class GroundStation {
        <<record>>
        id, name, lat, lon, alt
        minElevationDeg
        slewSpeedDegPerSec
        setupSeconds
    }
    class Antenna {
        <<record>>
        int number
        +label() String
    }
    GroundStation *-- "1..*" Antenna

    class Pass {
        <<record>>
        String id
        Instant aos, los
        double maxElevationDeg
        double aosAzimuthDeg, losAzimuthDeg
        +duration() Duration
        +dataVolumeMB() double
    }
    Pass --> "1" Satellite

    class Assignment {
        <<record nested in Schedule>>
    }
    Assignment --> Pass
    Assignment --> Antenna

    class DroppedPass {
        <<record nested in Schedule>>
        String reason
    }
    DroppedPass --> Pass

    class Schedule {
        +add(Assignment)
        +remove(Pass)
        +drop(Pass, String)
        +assignmentOf(Pass) Optional~Assignment~
        +assignmentsFor(Antenna) List~Assignment~
        +dropped() List~DroppedPass~
        +copy() Schedule
    }
    Schedule --> GroundStation
    Schedule *-- "*" Assignment
    Schedule *-- "*" DroppedPass

    class Disruption {
        <<sealed interface>>
        +announcedAt() Instant
    }
    class PassCancellation {
        <<record nested in Disruption>>
    }
    class AntennaOutage {
        <<record nested in Disruption>>
        Instant from, to
        +blocks(Assignment) boolean
    }
    Disruption <|.. PassCancellation
    Disruption <|.. AntennaOutage
    PassCancellation --> Pass
    AntennaOutage --> Antenna
```

## 2. Scheduling: Strategy + Template Method (`scheduler`, `constraint`)

```mermaid
classDiagram
    direction TB
    class Scheduler {
        <<interface>>
        +name() String
        +schedule(List~Pass~, GroundStation) Schedule
    }
    class OrderedAssignmentScheduler {
        <<abstract>>
        #order()* Comparator~Pass~
        +schedule(...) Schedule
        +extend(Schedule, List~Pass~)
    }
    class FcfsScheduler {
        #order() by AOS
    }
    class GreedyPriorityScheduler {
        #order() by priority, then MB
    }
    class SimulatedAnnealingScheduler {
        -long seed
        -int iterations
        -insert(...)
        -move(...)
    }
    Scheduler <|.. OrderedAssignmentScheduler
    OrderedAssignmentScheduler <|-- FcfsScheduler
    OrderedAssignmentScheduler <|-- GreedyPriorityScheduler
    Scheduler <|.. SimulatedAnnealingScheduler
    SimulatedAnnealingScheduler ..> GreedyPriorityScheduler : starts from

    class SchedulerRegistry {
        +get(String) Scheduler
        +all() Collection~Scheduler~
    }
    SchedulerRegistry o-- "*" Scheduler

    class Constraint {
        <<interface>>
        +violation(Assignment, Schedule) Optional~String~
    }
    class MinElevationConstraint
    class SlewTimeConstraint
    class AntennaOutageConstraint {
        -List~AntennaOutage~ outages
    }
    class Turnaround {
        <<utility>>
        +required(Pass, Pass, GroundStation)$ Duration
        +fits(Pass, Pass, GroundStation)$ boolean
    }
    Constraint <|.. MinElevationConstraint
    Constraint <|.. SlewTimeConstraint
    Constraint <|.. AntennaOutageConstraint
    SlewTimeConstraint ..> Turnaround

    OrderedAssignmentScheduler o-- "*" Constraint
    SimulatedAnnealingScheduler o-- "*" Constraint

    class Placement {
        <<package-private>>
        +placeOrExplain(...)$ Optional~String~
        +firstViolation(...)$ Optional~String~
    }
    OrderedAssignmentScheduler ..> Placement
    SimulatedAnnealingScheduler ..> Placement

    class Objective {
        <<utility>>
        +score(Pass)$ double
        +score(Schedule)$ double
    }
    SimulatedAnnealingScheduler ..> Objective

    class ScheduleValidator {
        +validate(Schedule, List~Pass~) List~String~
        +validate(Schedule, List~Pass~, List~Disruption~) List~String~
    }
    ScheduleValidator ..> Turnaround
```

## 3. Disruptions, repair (Strategy) and alerts (Observer) (`repair`, `alert`)

```mermaid
classDiagram
    direction TB
    class DisruptionSimulator {
        -List~Constraint~ baseConstraints
        +addListener(ScheduleListener)
        +generate(Schedule, Instant, Instant, long seed, Assumptions) List~Disruption~
        +run(Schedule, List~Pass~, List~Disruption~, RepairStrategy) DisruptionResult
    }
    class ScheduleListener {
        <<interface>>
        +disruptionAnnounced(Disruption)
        +passLost(Pass, String)
        +repaired(String, Instant, int, int)
    }
    class ConsoleAlertListener
    ScheduleListener <|.. ConsoleAlertListener
    DisruptionSimulator o-- "*" ScheduleListener : notifies

    class RepairStrategy {
        <<interface>>
        +name() String
        +repair(Schedule, RepairContext) Schedule
    }
    class NoRepair
    class RollingHorizonRepair {
        -Duration horizon
    }
    RepairStrategy <|.. NoRepair
    RepairStrategy <|.. RollingHorizonRepair
    RollingHorizonRepair ..> GreedyPriorityScheduler : extend()

    class RepairContext {
        <<record>>
        Instant now
        List~Pass~ candidates
        List~Constraint~ constraints
    }
    class DisruptionResult {
        <<record>>
        String strategy
        Schedule schedule
        int forcedLosses
        int planChanges
    }
    DisruptionSimulator ..> RepairStrategy : uses
    DisruptionSimulator ..> RepairContext : creates
    DisruptionSimulator ..> DisruptionResult : returns
    DisruptionSimulator ..> AntennaOutageConstraint : adds per outage

    class BenchmarkRunner {
        +run(...) Results
        -subset(List~Pass~, long seed) List~Pass~
    }
    BenchmarkRunner --> DisruptionSimulator
    BenchmarkRunner --> MetricsCalculator
    BenchmarkRunner ..> Scheduler
    BenchmarkRunner ..> ScheduleValidator
```

## 4. Input and output (`io`, `catalog`, `scenario`, `metrics`, `output`, `chart`)

```mermaid
classDiagram
    direction TB
    class PassSource {
        <<interface>>
        +passes(List~Satellite~, GroundStation, Instant, Instant) List~Pass~
        +description() String
    }
    class SimulatedPassSource
    class Predict4JavaPassSource
    PassSource <|.. SimulatedPassSource
    PassSource <|.. Predict4JavaPassSource
    Predict4JavaPassSource --> SatelliteCatalog
    Predict4JavaPassSource ..> OmmToTleConverter

    class SatelliteCatalog {
        +load(Path, Assumptions, SatelliteFactory)$ SatelliteCatalog
        +satellites() List~Satellite~
        +excluded() List~Excluded~
        +orbitOf(Satellite) OmmRecord
    }
    class SatelliteFactory {
        +create(String typeKey, int, String) Satellite
    }
    class PriorityAssigner {
        +priorityFor(String, int) int
    }
    SatelliteCatalog ..> SatelliteFactory
    SatelliteCatalog ..> OmmJsonReader
    SatelliteFactory --> PriorityAssigner
    SatelliteFactory ..> Satellite : creates subclasses

    class Assumptions {
        +getString/getInt/getDouble/getLong(String)
        +getInstant(String) Instant
    }
    class AssumptionsLoader
    class StationLoader
    class PriorityOverridesLoader
    class OmmJsonReader
    class OmmRecord {
        <<record>>
    }
    class OmmToTleConverter
    AssumptionsLoader ..> Assumptions : creates
    OmmJsonReader ..> OmmRecord : creates

    class MetricsCalculator {
        +calculate(String, List~Pass~, Schedule, Instant, Instant) Metrics
    }
    class Metrics {
        <<record>>
    }
    MetricsCalculator ..> Metrics : creates

    class ConsolePrinter {
        +printPasses(List~Pass~)
        +printSchedule(String, Schedule, Instant, Instant)
        +printMetrics(String, List~Metrics~)
    }
    class BenchmarkPrinter

    class ChartWriter {
        +benchmark(Results, String, BufferedImage) : writes summary.png only
        +write(String, BufferedImage)
    }
    class BarChart {
        +render(title, subtitle, unit, List~Bar~, format, slots) BufferedImage
    }
    class GroupedBarChart {
        +render(title, subtitle, unit, axis, categories, List~Series~) BufferedImage
    }
    class GanttChart {
        +render(title, subtitle, List~Panel~, Instant, Instant) BufferedImage
    }
    class Canvas {
        <<package-private utility>>
        colours, fonts, ticks()
    }
    ChartWriter ..> BarChart
    ChartWriter ..> GroupedBarChart
    ChartWriter ..> Metrics
    GanttChart ..> Schedule : reads only
    BarChart ..> Canvas
    GroupedBarChart ..> Canvas
    GanttChart ..> Canvas
```

## Design patterns at a glance

| Pattern | Where | Why |
|---|---|---|
| Strategy | `Scheduler`, `RepairStrategy`, `PassSource`, `Constraint` | Swap algorithms / data sources / rules without touching the callers |
| Template Method | `OrderedAssignmentScheduler.schedule()` with abstract `order()` | FCFS and Greedy share the placement loop, differ only in sort order |
| Observer | `DisruptionSimulator` + `ScheduleListener` | Alerts are decoupled from the simulation; tests use their own listener |
| Factory | `SatelliteFactory` | One place maps a type key to the right `Satellite` subclass |
| Registry | `SchedulerRegistry` | Command-line name to scheduler; adding an algorithm = one line |
| Sealed hierarchy + pattern matching | `Disruption` | `switch` over disruptions is checked for completeness by the compiler |
