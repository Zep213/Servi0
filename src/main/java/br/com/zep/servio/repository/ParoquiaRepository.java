package br.com.zep.servio.repository;

import br.com.zep.servio.model.Paroquia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ParoquiaRepository extends JpaRepository<Paroquia, Long> {

    List<Paroquia> findByActiveTrue();
}
