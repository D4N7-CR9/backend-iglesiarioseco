package com.iglesiarioseco.api.infrastructure.adapters.out.persistence.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

@Entity
@Table(name = "usuario")
@Data
public class UsuarioEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idUsuario;
    private String correo;
    private String nombreCompleto;
    private String passwordHash;
    private String metodoAuth;

    private LocalDate fechaNacimiento;
    private String estadoCivil;
    private Boolean tieneFamilia;
    private String genero;

    private Long idEstado;
    private Long idRol;
    private Long idGrupo;

    // Campos de auditoría esenciales para la inserción inicial
    @Column(name = "user_create", nullable = false)
    private String userCreate;

    @Column(name = "deleted", insertable = false)
    private Boolean deleted;
}