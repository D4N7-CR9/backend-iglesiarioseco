package com.iglesiarioseco.api.domain.ports.in;

import com.iglesiarioseco.api.domain.model.Usuario;

public interface SolicitarAccesoUseCase {
    Usuario solicitarAcceso(Usuario usuario);
}