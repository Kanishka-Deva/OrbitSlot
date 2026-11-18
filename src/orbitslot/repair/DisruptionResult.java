package orbitslot.repair;

import orbitslot.model.Schedule;

/**
 * Outcome of playing all disruptions against a plan.
 *
 * @param strategy     repair strategy name
 * @param schedule     the schedule that was actually executed
 * @param forcedLosses planned passes removed by disruptions (before repair)
 * @param planChanges  stability: passes added or moved by repair, plus passes
 *                     the repair removed on purpose
 */
public record DisruptionResult(String strategy, Schedule schedule, int forcedLosses, int planChanges) {
}
