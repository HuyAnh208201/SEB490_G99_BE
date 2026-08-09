package base.api.feature.shift.util;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mirrors FE {@code deriveShiftSlots}: split branch open→close into equal same-day slots
 * each ≤ {@link #MAX_SHIFT_HOURS}.
 */
public final class ShiftSlotDeriver {

    public static final int MAX_SHIFT_HOURS = 6;

    private static final Pattern HOURS_PATTERN =
            Pattern.compile("(\\d{1,2}:\\d{2})\\s*[-–]\\s*(\\d{1,2}:\\d{2})");

    private ShiftSlotDeriver() {
    }

    public record SlotTemplate(LocalTime start, LocalTime end, int index, boolean first, boolean last) {
    }

    public static List<SlotTemplate> derive(String operatingHours) {
        LocalTime[] bounds = parseOperatingHours(operatingHours);
        int openMins = toMinutes(bounds[0]);
        int closeMins = toMinutes(bounds[1]);
        if (closeMins <= openMins) {
            if (openMins == 0 && closeMins == 23 * 60 + 59) {
                // already fine
            } else if (bounds[0].equals(LocalTime.MIDNIGHT) && bounds[1].equals(LocalTime.of(23, 59))) {
                closeMins = 23 * 60 + 59;
            } else {
                closeMins = openMins + Math.min(MAX_SHIFT_HOURS * 60, 60);
            }
        }
        int durationMins = Math.max(1, closeMins - openMins);
        int slotCount = Math.max(1, (int) Math.ceil(durationMins / (double) (MAX_SHIFT_HOURS * 60)));
        double slotLength = durationMins / (double) slotCount;

        List<SlotTemplate> slots = new ArrayList<>();
        for (int i = 0; i < slotCount; i++) {
            int startMins = openMins + (int) Math.round(slotLength * i);
            int endMins = i == slotCount - 1
                    ? closeMins
                    : openMins + (int) Math.round(slotLength * (i + 1));
            slots.add(new SlotTemplate(
                    fromMinutes(startMins),
                    fromMinutes(endMins),
                    i,
                    i == 0,
                    i == slotCount - 1));
        }
        return slots;
    }

    public static int indexForTime(List<SlotTemplate> slots, LocalTime time) {
        if (slots == null || slots.isEmpty() || time == null) {
            return -1;
        }
        for (int i = 0; i < slots.size(); i++) {
            SlotTemplate slot = slots.get(i);
            if (!time.isBefore(slot.start()) && time.isBefore(slot.end())) {
                return i;
            }
        }
        return -1;
    }

    /** For QA outside branch hours: pick the slot whose midpoint is closest to {@code time}. */
    public static int nearestSlotIndex(List<SlotTemplate> slots, LocalTime time) {
        if (slots == null || slots.isEmpty()) {
            return 0;
        }
        if (time == null) {
            return 0;
        }
        int timeMins = toMinutes(time);
        int best = 0;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < slots.size(); i++) {
            SlotTemplate slot = slots.get(i);
            int mid = (toMinutes(slot.start()) + toMinutes(slot.end())) / 2;
            int distance = Math.abs(timeMins - mid);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    private static LocalTime[] parseOperatingHours(String value) {
        if (value == null || value.isBlank()) {
            return new LocalTime[]{LocalTime.of(8, 0), LocalTime.of(22, 0)};
        }
        String trimmed = value.trim();
        if (trimmed.matches("(?i).*24\\s*/\\s*7.*")) {
            return new LocalTime[]{LocalTime.MIDNIGHT, LocalTime.of(23, 59)};
        }
        Matcher match = HOURS_PATTERN.matcher(trimmed);
        if (!match.find()) {
            return new LocalTime[]{LocalTime.of(8, 0), LocalTime.of(22, 0)};
        }
        return new LocalTime[]{parseTime(match.group(1)), parseTime(match.group(2))};
    }

    private static LocalTime parseTime(String hhmm) {
        String[] parts = hhmm.split(":");
        int h = Integer.parseInt(parts[0]);
        int m = Integer.parseInt(parts[1]);
        return LocalTime.of(h, m);
    }

    private static int toMinutes(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }

    private static LocalTime fromMinutes(int total) {
        int clamped = Math.max(0, Math.min(23 * 60 + 59, total));
        return LocalTime.of(clamped / 60, clamped % 60);
    }
}
