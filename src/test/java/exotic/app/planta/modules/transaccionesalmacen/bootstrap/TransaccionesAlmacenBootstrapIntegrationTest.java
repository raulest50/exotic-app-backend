package exotic.app.planta.modules.transaccionesalmacen.bootstrap;

import exotic.app.planta.model.master.configs.MasterDirective;
import exotic.app.planta.model.master.configs.MasterDirectiveKeys;
import exotic.app.planta.model.master.configs.dto.DTO_MasterD_Update;
import exotic.app.planta.modules.transaccionesalmacen.support.AbstractTransaccionesAlmacenIntegrationTest;
import exotic.app.planta.repo.master.configs.MasterDirectiveRepo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransaccionesAlmacenBootstrapIntegrationTest extends AbstractTransaccionesAlmacenIntegrationTest {

    @Autowired
    private MasterDirectiveRepo masterDirectiveRepo;

    @Test
    void getSuperMasterConfig_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/super-master-directives/config"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void getSuperMasterConfig_returnsFlagsForAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/super-master-directives/config")
                        .with(bearerToken())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.habilitarAjustesInventario").exists())
                .andExpect(jsonPath("$.habilitarCargaMasiva").exists())
                .andExpect(jsonPath("$.habilitarEliminacionForzada").exists());
    }

    @Test
    void getMasterDirectives_returnsDefaultOcmReceptionLimitForAuthenticatedUser() throws Exception {
        setLimiteRecepcionesParcialesOcm("3");

        mockMvc.perform(get("/api/super-master-directives/directives")
                        .with(bearerToken())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.masterDirectives[*].nombre").value(hasItem(MasterDirectiveKeys.LIMITE_RECEPCIONES_PARCIALES_OCM)))
                .andExpect(jsonPath("$.masterDirectives[*].tipoDato").value(hasItem("NUMERO")))
                .andExpect(jsonPath("$.masterDirectives[*].valor").value(hasItem("3")));
    }

    @Test
    void updateMasterDirective_rejectsInvalidNumericReceptionLimit() throws Exception {
        MasterDirective directive = setLimiteRecepcionesParcialesOcm("2");
        MasterDirective invalidDirective = cloneDirective(directive);
        invalidDirective.setValor("0");

        DTO_MasterD_Update update = new DTO_MasterD_Update(directive, invalidDirective);

        mockMvc.perform(put("/api/super-master-directives/directives/update")
                        .with(bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("mayor o igual a 1")));
    }

    @Test
    void updateMasterDirective_acceptsValidNumericReceptionLimit() throws Exception {
        MasterDirective directive = setLimiteRecepcionesParcialesOcm("2");
        MasterDirective updatedDirective = cloneDirective(directive);
        updatedDirective.setValor("3");

        DTO_MasterD_Update update = new DTO_MasterD_Update(directive, updatedDirective);

        mockMvc.perform(put("/api/super-master-directives/directives/update")
                        .with(bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value(MasterDirectiveKeys.LIMITE_RECEPCIONES_PARCIALES_OCM))
                .andExpect(jsonPath("$.valor").value("3"));
    }

    private MasterDirective setLimiteRecepcionesParcialesOcm(String valor) {
        MasterDirective directive = masterDirectiveRepo.findByNombre(MasterDirectiveKeys.LIMITE_RECEPCIONES_PARCIALES_OCM)
                .orElseGet(() -> {
                    MasterDirective created = new MasterDirective();
                    created.setNombre(MasterDirectiveKeys.LIMITE_RECEPCIONES_PARCIALES_OCM);
                    created.setTipoDato(MasterDirective.TipoDato.NUMERO);
                    created.setGrupo(MasterDirective.GRUPO.COMPRAS_ALMACEN);
                    created.setResumen("Limite de recepciones parciales permitidas por OCM");
                    created.setAyuda("Fixture de prueba");
                    return created;
                });
        directive.setValor(valor);
        return masterDirectiveRepo.save(directive);
    }

    private MasterDirective cloneDirective(MasterDirective source) {
        MasterDirective clone = new MasterDirective();
        clone.setId(source.getId());
        clone.setNombre(source.getNombre());
        clone.setResumen(source.getResumen());
        clone.setValor(source.getValor());
        clone.setTipoDato(source.getTipoDato());
        clone.setGrupo(source.getGrupo());
        clone.setAyuda(source.getAyuda());
        return clone;
    }
}
