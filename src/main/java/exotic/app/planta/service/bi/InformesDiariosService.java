package exotic.app.planta.service.bi;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class InformesDiariosService {

    public Map<String, String> ping() {
        return Map.of("status", "ok");
    }
}
