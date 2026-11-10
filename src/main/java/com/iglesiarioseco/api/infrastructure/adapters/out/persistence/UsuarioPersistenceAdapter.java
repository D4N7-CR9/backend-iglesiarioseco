package com.iglesiarioseco.api.infrastructure.adapters.out.persistence;

import com.iglesiarioseco.api.domain.model.Usuario;
import com.iglesiarioseco.api.domain.ports.out.UsuarioRepositoryPort;
import com.iglesiarioseco.api.infrastructure.adapters.out.persistence.entity.UsuarioEntity;
import com.iglesiarioseco.api.infrastructure.adapters.out.persistence.repository.SpringDataUsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

// @Component le dice a Spring Boot que él debe administrar esta clase en memoria
@Component
@RequiredArgsConstructor
public class UsuarioPersistenceAdapter implements UsuarioRepositoryPort {

    private final SpringDataUsuarioRepository repository;

    @Override
    public Usuario guardar(Usuario usuario) {
        // 1. Convertimos el objeto puro del dominio a la entidad de la base de datos
        UsuarioEntity entity = toEntity(usuario);

        // 2. Lógica de Auditoría: Al guardar por primera vez, el user_create es obligatorio
        if (entity.getIdUsuario() == null) {
            entity.setUserCreate(usuario.getCorreo()); // El propio usuario es quien origina el registro
        }

        // 3. Guardamos en PostgreSQL usando la magia de Spring Data
        UsuarioEntity guardado = repository.save(entity);

        // 4. Devolvemos el resultado convertido de nuevo al dominio
        return toDomain(guardado);
    }

    @Override
    public Optional<Usuario> buscarPorCorreo(String correo) {
        return repository.findByCorreo(correo).map(this::toDomain);
    }

    // =========================================================
    // MÉTODOS MAPEO: Para traducir entre Dominio y Base de Datos
    // =========================================================

    private UsuarioEntity toEntity(Usuario dominio) {
        UsuarioEntity entity = new UsuarioEntity();
        entity.setIdUsuario(dominio.getIdUsuario());
        entity.setCorreo(dominio.getCorreo());
        entity.setNombreCompleto(dominio.getNombreCompleto());
        entity.setPasswordHash(dominio.getPasswordHash());
        entity.setMetodoAuth(dominio.getMetodoAuth());
        entity.setFechaNacimiento(dominio.getFechaNacimiento());
        entity.setEstadoCivil(dominio.getEstadoCivil());
        entity.setTieneFamilia(dominio.getTieneFamilia());
        entity.setGenero(dominio.getGenero());
        entity.setIdEstado(dominio.getIdEstado());
        entity.setIdRol(dominio.getIdRol());
        entity.setIdGrupo(dominio.getIdGrupo());
        return entity;
    }

    private Usuario toDomain(UsuarioEntity entity) {
        Usuario dominio = new Usuario();
        dominio.setIdUsuario(entity.getIdUsuario());
        dominio.setCorreo(entity.getCorreo());
        dominio.setNombreCompleto(entity.getNombreCompleto());
        dominio.setPasswordHash(entity.getPasswordHash());
        dominio.setMetodoAuth(entity.getMetodoAuth());
        dominio.setFechaNacimiento(entity.getFechaNacimiento());
        dominio.setEstadoCivil(entity.getEstadoCivil());
        dominio.setTieneFamilia(entity.getTieneFamilia());
        dominio.setGenero(entity.getGenero());
        dominio.setIdEstado(entity.getIdEstado());
        dominio.setIdRol(entity.getIdRol());
        dominio.setIdGrupo(entity.getIdGrupo());
        return dominio;
    }
}