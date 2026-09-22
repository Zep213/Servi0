package br.com.zep.servio.seguranca;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** csrf.spa(): token no cookie XSRF-TOKEN, precisa vir também no header X-XSRF-TOKEN. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class CsrfIT {

    @Autowired
    MockMvc mvc;

    @Test
    void postSemHeaderXXsrfTokenRecebe403() throws Exception {
        MvcResult resultado = mvc.perform(get("/api/auth/csrf")).andExpect(status().isNoContent()).andReturn();
        Cookie xsrf = resultado.getResponse().getCookie("XSRF-TOKEN");
        assertThat(xsrf).isNotNull();

        // cookie presente, mas sem o header: o CsrfFilter recusa antes de chegar no login
        mvc.perform(post("/api/auth/login").cookie(xsrf)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", "qualquer@servio.dev")
                        .param("senha", "qualquer1234"))
                .andExpect(status().isForbidden());
    }

    @Test
    void postComCookieEHeaderPassaDoCsrf() throws Exception {
        MvcResult resultado = mvc.perform(get("/api/auth/csrf")).andExpect(status().isNoContent()).andReturn();
        Cookie xsrf = resultado.getResponse().getCookie("XSRF-TOKEN");

        // token válido: a requisição passa do CSRF e só falha depois, na autenticação (401, não 403)
        mvc.perform(post("/api/auth/login").cookie(xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", "naoexiste@servio.dev")
                        .param("senha", "qualquer1234"))
                .andExpect(status().isUnauthorized());
    }
}
