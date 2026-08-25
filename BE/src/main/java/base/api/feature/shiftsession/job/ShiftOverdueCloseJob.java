package base.api.feature.shiftsession.job;

import base.api.feature.shiftsession.service.IShiftSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ShiftOverdueCloseJob {

    @Autowired
    private IShiftSessionService shiftSessionService;

    @Scheduled(fixedDelayString = "${shift.overdue-check-ms:300000}")
    public void closeOverdueShiftSessions() {
        shiftSessionService.autoCloseOverdueSessions();
        shiftSessionService.purgeFutureClosedSessions();
    }
}
