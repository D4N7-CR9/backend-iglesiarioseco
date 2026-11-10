package com.iglesiarioseco.api.infrastructure.adapters.out.persistence.repository;

import com.iglesiarioseco.api.infrastructure.adapters.out.persistence.entity.UsuarioEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface SpringDataUsuarioRepository extends JpaRepository<UsuarioEntity, Long> {

    // Spring Boot detectará el nombre y armará el "SELECT * FROM usuario WHERE correo = ?" automáticamente
    Optional<UsuarioEntity> findByCorreo(String correo);
}
