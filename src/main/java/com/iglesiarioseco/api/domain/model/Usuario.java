package com.iglesiarioseco.api.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Usuario {

    private Long idUsuario;
    private String correo;
    private String nombreCompleto;
    private String passwordHash;
    private String metodoAuth;

    private LocalDate fechaNacimiento;
    private String estadoCivil;
    private Boolean tieneFamilia;
    private String genero;

    // Para simplificar la lógica central, manejaremos las relaciones por sus IDs
    private Long idEstado;
    private Long idRol;
    private Long idGrupo;
}