package com.iglesiarioseco.api.domain.ports.out;

import com.iglesiarioseco.api.domain.model.Usuario;
import java.util.Optional;

public interface UsuarioRepositoryPort {
    // Contrato para guardar un usuario
    Usuario guardar(Usuario usuario);

    // Contrato para buscar un usuario por su correo
    Optional<Usuario> buscarPorCorreo(String correo);
}