package base.api.feature.shift.util;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShiftSlotDeriverTest {

    @Test
    void derivesThreeSlotsForLongDay() {
        List<ShiftSlotDeriver.SlotTemplate> slots = ShiftSlotDeriver.derive("06:00 - 23:00");
        assertEquals(3, slots.size());
        assertEquals(LocalTime.of(6, 0), slots.get(0).start());
        assertEquals(LocalTime.of(23, 0), slots.get(2).end());
        assertTrue(slots.get(0).first());
        assertTrue(slots.get(2).last());
        assertFalse(slots.get(1).first());
        assertFalse(slots.get(1).last());
    }

    @Test
    void eachSlotAtMostSixHours() {
        List<ShiftSlotDeriver.SlotTemplate> slots = ShiftSlotDeriver.derive("08:00 - 22:00");
        for (ShiftSlotDeriver.SlotTemplate slot : slots) {
            long minutes = java.time.Duration.between(slot.start(), slot.end()).toMinutes();
            assertTrue(minutes <= ShiftSlotDeriver.MAX_SHIFT_HOURS * 60L);
        }
    }
}
