package br.com.zep.servio.pastoral;

import br.com.zep.servio.seguranca.TestcontainersConfig;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Documentação da API: fechada por padrão e aberta só com OPENAPI_ENABLED (propriedade do springdoc). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class OpenApiIT {

    @Autowired
    MockMvc mvc;

    @Test
    void desligadaPorPadraoNaoExpoeNada() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().is4xxClientError());
    }

    @Nested
    @TestPropertySource(properties = {"springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true"})
    class Ligada {

        @Autowired
        MockMvc mvcLigado;

        @Test
        void ligadaExpoeAsRotasDeLeitura() throws Exception {
            mvcLigado.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paths./api/me/escalas").exists())
                    .andExpect(jsonPath("$.paths./api/pastorais/{pastoralId}/membros").exists());
        }
    }
}
