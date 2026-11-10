package com.iglesiarioseco.api.application.services;

import com.iglesiarioseco.api.domain.model.Usuario;
import com.iglesiarioseco.api.domain.ports.in.SolicitarAccesoUseCase;
import com.iglesiarioseco.api.domain.ports.out.UsuarioRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// @Service le indica a Spring Boot que esta es la lógica de negocio central
@Service
@RequiredArgsConstructor
public class SolicitarAccesoService implements SolicitarAccesoUseCase {

    // Inyectamos el puerto de salida (nuestra interfaz, NO la base de datos directamente)
    private final UsuarioRepositoryPort usuarioRepositoryPort;

    @Override
    public Usuario solicitarAcceso(Usuario usuarioSolicitud) {
        // Lógica 1: Verificar si el correo ya existe para no duplicar
        if (usuarioRepositoryPort.buscarPorCorreo(usuarioSolicitud.getCorreo()).isPresent()) {
            throw new IllegalArgumentException("El correo ya está registrado en el sistema.");
        }

        // Lógica 2: Como acordamos, al registrarse, su estado inicial es "Pendiente" (ID = 1)
        // No tiene rol ni grupo todavía, eso lo hará el Pastor.
        usuarioSolicitud.setIdEstado(1L);

        // Lógica 3: Lo enviamos al puerto de salida para que se guarde en PostgreSQL
        return usuarioRepositoryPort.guardar(usuarioSolicitud);
    }
}