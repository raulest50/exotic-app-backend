package exotic.app.planta.resource.bi;

import exotic.app.planta.service.bi.InformesDiariosService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/bi/informes-diarios")
@RequiredArgsConstructor
public class InformesDiariosResource {

    private final InformesDiariosService informesDiariosService;

    @GetMapping("/ping")
    public ResponseEntity<Map<String, String>> ping() {
        return ResponseEntity.ok(informesDiariosService.ping());
    }
}
