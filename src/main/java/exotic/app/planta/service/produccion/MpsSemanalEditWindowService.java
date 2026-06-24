package exotic.app.planta.service.produccion;

import exotic.app.planta.service.master.configs.MasterDirectiveService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class MpsSemanalEditWindowService {

    private final Clock applicationClock;
    private final MasterDirectiveService masterDirectiveService;

    public LocalDate getEditableFromDate() {
        return LocalDate.now(applicationClock).plusDays(masterDirectiveService.getMpsSemanalDiasBloqueoEdicion());
    }

    public boolean isEditable(LocalDate date) {
        return date != null && !date.isBefore(getEditableFromDate());
    }
}
