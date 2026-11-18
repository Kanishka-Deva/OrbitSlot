package orbitslot.alert;

import orbitslot.model.Disruption.AntennaOutage;
import orbitslot.model.Disruption;
import orbitslot.model.Pass;
import orbitslot.model.Disruption.PassCancellation;

import java.io.PrintStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Prints alerts to the console; lost high-priority passes are marked with "!!". */
public final class ConsoleAlertListener implements ScheduleListener {

    private final DateTimeFormatter time;
    private final int highPriorityMin;
    private final PrintStream out;

    public ConsoleAlertListener(ZoneId zone, int highPriorityMin, PrintStream out) {
        this.time = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(zone);
        this.highPriorityMin = highPriorityMin;
        this.out = out;
    }

    @Override
    public void disruptionAnnounced(Disruption disruption) {
        String text = switch (disruption) {
            case PassCancellation c -> "pass cancelled: " + c.pass().satellite().name()
                    + " at " + time.format(c.pass().aos());
            case AntennaOutage o -> o.antenna() + " outage until " + time.format(o.to());
        };
        out.println("[" + time.format(disruption.announcedAt()) + "] ALERT  " + text);
    }

    @Override
    public void passLost(Pass pass, String reason) {
        String mark = pass.satellite().priority() >= highPriorityMin ? "!! " : "   ";
        out.println("              " + mark + "lost " + pass.satellite().name()
                + " (prio " + pass.satellite().priority() + ", " + time.format(pass.aos()) + "): " + reason);
    }

    @Override
    public void repaired(String strategy, Instant at, int passesAdded, int passesRemoved) {
        out.println("              repair '" + strategy + "': +" + passesAdded + " / -" + passesRemoved + " passes");
    }
}
