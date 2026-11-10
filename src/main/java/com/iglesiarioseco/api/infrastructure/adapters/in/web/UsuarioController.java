package com.iglesiarioseco.api.infrastructure.adapters.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class UsuarioController {

    private final JdbcTemplate jdbcTemplate;

    // 1. SOLICITAR ACCESO (Con contraseña e idioma preferido)
    @PostMapping("/solicitar-acceso")
    public ResponseEntity<Map<String, String>> solicitarAcceso(@RequestBody SolicitudAccesoDTO solicitud) {
        Map<String, String> respuesta = new HashMap<>();

        String correo = solicitud.correo() != null ? solicitud.correo().trim() : "";
        String nombre = solicitud.nombreCompleto() != null ? solicitud.nombreCompleto().trim() : "";
        String password = solicitud.password() != null ? solicitud.password().trim() : "";
        String idioma = solicitud.idiomaPreferido() != null ? solicitud.idiomaPreferido().trim() : "ES";

        if (!nombre.matches("^[A-Za-zÁÉÍÓÚáéíóúÑñÜü ]{3,150}$")) {
            respuesta.put("mensaje", "Error: El nombre completo solo puede contener letras (mínimo 3 caracteres).");
            return ResponseEntity.badRequest().body(respuesta);
        }

        if (!correo.matches("^(?=[^@]*[A-Za-z])[A-Za-z0-9._%+-]{3,}@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            respuesta.put("mensaje", "Error: El correo electrónico no tiene un formato válido.");
            return ResponseEntity.badRequest().body(respuesta);
        }

        if (password.length() < 4) {
            respuesta.put("mensaje", "Error: La contraseña debe tener al menos 4 caracteres.");
            return ResponseEntity.badRequest().body(respuesta);
        }

        Integer existe = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.usuario WHERE LOWER(correo) = LOWER(?)", Integer.class, correo);
        if (existe != null && existe > 0) {
            respuesta.put("mensaje", "Error: Este correo electrónico ya está registrado en el sistema.");
            return ResponseEntity.badRequest().body(respuesta);
        }

        Integer maxLista = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(numero_lista), 0) + 1 FROM public.usuario", Integer.class);

        jdbcTemplate.update("""
            INSERT INTO public.usuario (
                numero_lista, correo, nombre_completo, password_hash, metodo_auth,
                idioma_preferido, id_estado, id_rol, id_distrito, id_grupo, user_create
            ) VALUES (?, ?, ?, ?, 'PASSWORD', ?, 1, 5, 1, 3, ?)
        """, maxLista, correo, nombre, password, idioma, correo);

        respuesta.put("mensaje", "¡Solicitud enviada con éxito! Cuando el Pastor acepte tu solicitud podrás ingresar con tu contraseña.");
        return ResponseEntity.ok(respuesta);
    }

    // 2. INICIAR SESIÓN CON CORREO Y CONTRASEÑA (O GOOGLE)
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequestDTO request) {
        Map<String, Object> respuesta = new HashMap<>();
        String correo = request.correo() != null ? request.correo().trim() : "";
        String password = request.password() != null ? request.password().trim() : "";

        String sql = """
            SELECT u.id_usuario, u.numero_lista, u.correo, u.nombre_completo, u.password_hash,
                   u.foto_perfil_url, u.idioma_preferido, u.permiso_predicador_activo,
                   u.id_estado, COALESCE(e.nombre, 'PENDIENTE') AS estado_nombre,
                   u.id_rol, COALESCE(r.nombre, 'HERMANO') AS rol_nombre,
                   u.id_distrito, COALESCE(d.nombre, 'Distrito 1') AS distrito_nombre,
                   u.id_grupo, COALESCE(g.nombre, 'Jóvenes') AS grupo_nombre,
                   u.id_familia, COALESCE(f.nombre_familia, 'Sin familia asignada') AS familia_nombre
            FROM public.usuario u
            LEFT JOIN public.estado_cuenta e ON u.id_estado = e.id_estado
            LEFT JOIN public.rol r ON u.id_rol = r.id_rol
            LEFT JOIN public.distrito d ON u.id_distrito = d.id_distrito
            LEFT JOIN public.grupo g ON u.id_grupo = g.id_grupo
            LEFT JOIN public.familia f ON u.id_familia = f.id_familia
            WHERE LOWER(u.correo) = LOWER(?) AND u.deleted = FALSE
        """;

        List<Map<String, Object>> usuarios = jdbcTemplate.queryForList(sql, correo);
        if (usuarios.isEmpty()) {
            respuesta.put("mensaje", "Correo no encontrado. Verifica tus datos o solicita acceso al sistema.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(respuesta);
        }

        Map<String, Object> usuario = usuarios.get(0);
        String estadoNombre = String.valueOf(usuario.get("estado_nombre"));
        String passGuardada = String.valueOf(usuario.get("password_hash"));

        if (!Boolean.TRUE.equals(request.modoGoogle()) && !passGuardada.equals(password)) {
            respuesta.put("mensaje", "Contraseña incorrecta. Inténtalo nuevamente.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(respuesta);
        }

        if ("PENDIENTE".equalsIgnoreCase(estadoNombre)) {
            respuesta.put("mensaje", "Tu solicitud sigue PENDIENTE. El Pastor debe aceptarla para que puedas entrar.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(respuesta);
        }

        if ("RECHAZADO".equalsIgnoreCase(estadoNombre) || "BLOQUEADO".equalsIgnoreCase(estadoNombre)) {
            respuesta.put("mensaje", "Tu solicitud o cuenta no tiene autorización de acceso (" + estadoNombre + ").");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(respuesta);
        }

        Long idUsuario = ((Number) usuario.get("id_usuario")).longValue();
        if (request.idiomaSeleccionado() != null && !request.idiomaSeleccionado().isBlank()) {
            jdbcTemplate.update("UPDATE public.usuario SET idioma_preferido = ? WHERE id_usuario = ?",
                    request.idiomaSeleccionado(), idUsuario);
            usuario.put("idioma_preferido", request.idiomaSeleccionado());
        }

        List<String> cargos = jdbcTemplate.queryForList("""
            SELECT c.nombre FROM public.usuario_cargo uc
            JOIN public.cargo c ON uc.id_cargo = c.id_cargo
            WHERE uc.id_usuario = ? AND uc.deleted = FALSE
        """, String.class, idUsuario);

        usuario.put("cargos", cargos);
        usuario.remove("password_hash");
        return ResponseEntity.ok(usuario);
    }

    // 3. ACEPTAR O RECHAZAR SOLICITUD DE ACCESO (Vista limpia del Pastor)
    @PutMapping("/{idUsuario}/responder-solicitud")
    public ResponseEntity<Map<String, String>> responderSolicitud(
            @PathVariable Long idUsuario,
            @RequestBody RespuestaSolicitudDTO dto) {
        Map<String, String> res = new HashMap<>();
        int nuevoEstado = "ACEPTAR".equalsIgnoreCase(dto.accion()) ? 2 : 3; // 2=ACTIVO, 3=RECHAZADO
        jdbcTemplate.update("""
            UPDATE public.usuario SET id_estado = ?, user_update = ? WHERE id_usuario = ?
        """, nuevoEstado, dto.usuarioAutorizador(), idUsuario);

        res.put("mensaje", nuevoEstado == 2 ? "Miembro aceptado en el sistema." : "Solicitud rechazada.");
        return ResponseEntity.ok(res);
    }

    // 4. ASIGNAR EXCLUSIVAMENTE CARGOS A UN MIEMBRO
    @PutMapping("/{idUsuario}/asignar-cargos")
    public ResponseEntity<Map<String, String>> asignarCargos(
            @PathVariable Long idUsuario,
            @RequestBody AsignarCargosDTO dto) {
        Map<String, String> res = new HashMap<>();
        jdbcTemplate.update("DELETE FROM public.usuario_cargo WHERE id_usuario = ?", idUsuario);
        if (dto.idsCargos() != null) {
            for (Integer idCargo : dto.idsCargos()) {
                jdbcTemplate.update("""
                    INSERT INTO public.usuario_cargo (id_usuario, id_cargo, user_create)
                    VALUES (?, ?, ?)
                """, idUsuario, idCargo, dto.usuarioAutorizador());
            }
        }
        res.put("mensaje", "Cargos actualizados correctamente.");
        return ResponseEntity.ok(res);
    }

    // 5. DASHBOARD COMPLETO (Miembros, Asistencias Semáforo, 4 Sobres, Consultas, Oración, Plan, Sedes)
    @GetMapping("/datos-completos")
    public ResponseEntity<Map<String, Object>> obtenerDatosCompletos() {
        Map<String, Object> data = new HashMap<>();

        // Solicitudes pendientes (Solo nombre y correo para el Pastor)
        data.put("solicitudesPendientes", jdbcTemplate.queryForList("""
            SELECT id_usuario, nombre_completo, correo, time_create
            FROM public.usuario
            WHERE id_estado = 1 AND deleted = FALSE
            ORDER BY id_usuario DESC
        """));

        // Miembros activos con su familia, grupo, distrito y métricas de puntualidad Markov
        List<Map<String, Object>> miembros = jdbcTemplate.queryForList("""
            SELECT u.id_usuario, COALESCE(u.numero_lista, u.id_usuario) AS numero_lista,
                   u.correo, u.nombre_completo, u.foto_perfil_url,
                   u.id_rol, COALESCE(r.nombre, 'HERMANO') AS rol_nombre,
                   u.id_distrito, COALESCE(d.nombre, 'Distrito 1') AS distrito_nombre,
                   u.id_grupo, COALESCE(g.nombre, 'Jóvenes') AS grupo_nombre,
                   u.id_familia, COALESCE(f.nombre_familia, 'Sin Familia') AS familia_nombre,
                   COALESCE(pm.estado_actual, 'ACTIVO') AS estado_markov,
                   COALESCE(pm.probabilidad_activo, 0.80) AS prob_activo,
                   COALESCE(pm.probabilidad_irregular, 0.15) AS prob_irregular,
                   COALESCE(pm.probabilidad_riesgo, 0.05) AS prob_riesgo,
                   COALESCE(pm.total_temprano, 0) AS total_temprano,
                   COALESCE(pm.total_tarde, 0) AS total_tarde,
                   COALESCE(pm.total_faltas, 0) AS total_faltas,
                   COALESCE(pm.perfil_puntualidad, 'LLEGA_TEMPRANO') AS perfil_puntualidad
            FROM public.usuario u
            LEFT JOIN public.rol r ON u.id_rol = r.id_rol
            LEFT JOIN public.distrito d ON u.id_distrito = d.id_distrito
            LEFT JOIN public.grupo g ON u.id_grupo = g.id_grupo
            LEFT JOIN public.familia f ON u.id_familia = f.id_familia
            LEFT JOIN public.prediccion_markov pm ON u.id_usuario = pm.id_usuario
            WHERE u.id_estado = 2 AND u.deleted = FALSE
            ORDER BY COALESCE(u.numero_lista, u.id_usuario) ASC
        """);

        for (Map<String, Object> m : miembros) {
            Long idU = ((Number) m.get("id_usuario")).longValue();

            // Cargos del miembro
            m.put("cargos", jdbcTemplate.queryForList("""
                SELECT c.id_cargo, c.nombre FROM public.usuario_cargo uc
                JOIN public.cargo c ON uc.id_cargo = c.id_cargo
                WHERE uc.id_usuario = ? AND uc.deleted = FALSE
            """, idU));

            // Asistencias por fecha (TEMPRANO, TARDE, FALTA)
            m.put("asistencias", jdbcTemplate.queryForList("""
                SELECT fecha_asistencia, dia_semana, estado_asistencia, hora_llegada
                FROM public.asistencia
                WHERE id_usuario = ? AND deleted = FALSE
                ORDER BY fecha_asistencia ASC
            """, idU));

            // Totales en los 4 sobres por hermano
            Map<String, Object> sobres = new HashMap<>();
            sobres.put("OFRENDA", jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(monto_bs), 0) FROM public.finanza_sobre WHERE id_usuario_miembro = ? AND categoria_sobre = 'OFRENDA' AND deleted = FALSE",
                    Double.class, idU));
            sobres.put("DIEZMO", jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(monto_bs), 0) FROM public.finanza_sobre WHERE id_usuario_miembro = ? AND categoria_sobre = 'DIEZMO' AND deleted = FALSE",
                    Double.class, idU));
            sobres.put("CONSTRUCCION_IGLESIA", jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(monto_bs), 0) FROM public.finanza_sobre WHERE id_usuario_miembro = ? AND categoria_sobre = 'CONSTRUCCION_IGLESIA' AND deleted = FALSE",
                    Double.class, idU));
            sobres.put("MISION_BOLIVIA", jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(monto_bs), 0) FROM public.finanza_sobre WHERE id_usuario_miembro = ? AND categoria_sobre = 'MISION_BOLIVIA' AND deleted = FALSE",
                    Double.class, idU));
            m.put("sobres", sobres);
        }
        data.put("miembros", miembros);

        // Catálogos para filtros
        data.put("familias", jdbcTemplate.queryForList("SELECT id_familia, nombre_familia FROM public.familia WHERE deleted = FALSE ORDER BY nombre_familia"));
        data.put("grupos", jdbcTemplate.queryForList("SELECT id_grupo, nombre, rango_edad FROM public.grupo WHERE deleted = FALSE ORDER BY id_grupo"));
        data.put("distritos", jdbcTemplate.queryForList("SELECT id_distrito, nombre FROM public.distrito WHERE deleted = FALSE ORDER BY id_distrito"));
        data.put("cargos", jdbcTemplate.queryForList("SELECT id_cargo, nombre FROM public.cargo WHERE deleted = FALSE ORDER BY id_cargo"));
        data.put("iglesiasBolivia", jdbcTemplate.queryForList("SELECT id_iglesia, nombre, ciudad, direccion FROM public.iglesia_sede WHERE deleted = FALSE ORDER BY id_iglesia"));

        // Solicitudes de Consulta Pastoral
        data.put("consultas", jdbcTemplate.queryForList("""
            SELECT cb.id_solicitud, cb.id_usuario_solicitante, u.nombre_completo, u.foto_perfil_url,
                   cb.tipo_solicitud, cb.motivo_confidencial, cb.estado_solicitud, cb.fecha_programada, cb.time_create
            FROM public.consejeria_bautismo cb
            JOIN public.usuario u ON cb.id_usuario_solicitante = u.id_usuario
            WHERE cb.deleted = FALSE
            ORDER BY cb.id_solicitud DESC
        """));

        // Peticiones de Oración
        data.put("oraciones", jdbcTemplate.queryForList("""
            SELECT co.id_oracion, co.id_usuario, u.nombre_completo, u.foto_perfil_url,
                   co.rol_solicitante, co.motivo_oracion, co.estado_oracion, co.time_create,
                   COALESCE(d.nombre, 'General') AS distrito_nombre,
                   COALESCE(g.nombre, 'General') AS grupo_nombre
            FROM public.cadena_oracion co
            JOIN public.usuario u ON co.id_usuario = u.id_usuario
            LEFT JOIN public.distrito d ON co.id_distrito = d.id_distrito
            LEFT JOIN public.grupo g ON co.id_grupo = g.id_grupo
            WHERE co.deleted = FALSE
            ORDER BY co.id_oracion DESC
        """));

        // Plan Mensual de la Iglesia
        data.put("planIglesia", jdbcTemplate.queryForList("""
            SELECT id_evento, titulo, descripcion, tipo_evento, fecha_inicio, fecha_fin, mes_programado, lugar
            FROM public.evento
            WHERE deleted = FALSE
            ORDER BY fecha_inicio ASC
        """));

        // Lecturas Bíblicas
        data.put("lecturas", jdbcTemplate.queryForList("""
            SELECT id_lectura, id_usuario, libro_biblia, capitulo_leido, fecha_lectura, reflexion_personal, estado_lectura
            FROM public.lectura_biblica
            WHERE deleted = FALSE
            ORDER BY id_lectura DESC
        """));

        return ResponseEntity.ok(data);
    }

    // 6. CAMBIAR CASILLA DE ASISTENCIA (Verde=TEMPRANO, Naranja=TARDE, Rojo=FALTA)
    @PostMapping("/asistencia/registrar")
    public ResponseEntity<Map<String, String>> registrarAsistencia(@RequestBody RegistroAsistenciaDTO dto) {
        jdbcTemplate.update("""
            INSERT INTO public.asistencia (id_usuario, fecha_asistencia, dia_semana, estado_asistencia, user_create)
            VALUES (?, CAST(? AS DATE), ?, ?, ?)
            ON CONFLICT (id_usuario, fecha_asistencia, dia_semana)
            DO UPDATE SET estado_asistencia = EXCLUDED.estado_asistencia, user_update = EXCLUDED.user_create
        """, dto.idUsuario(), dto.fecha(), dto.diaSemana(), dto.estadoAsistencia(), dto.usuarioEjecutor());

        Map<String, String> res = new HashMap<>();
        res.put("mensaje", "Asistencia actualizada.");
        return ResponseEntity.ok(res);
    }

    // 7. REGISTRAR APORTE EN UNO DE LOS 4 SOBRES
    @PostMapping("/finanzas/registrar")
    public ResponseEntity<Map<String, String>> registrarSobre(@RequestBody RegistroSobreDTO dto) {
        String color = switch (dto.categoriaSobre()) {
            case "OFRENDA" -> "ROSA_OSCURO";
            case "DIEZMO" -> "CELESTE";
            case "CONSTRUCCION_IGLESIA" -> "AMARILLO";
            default -> "BLANCO";
        };
        jdbcTemplate.update("""
            INSERT INTO public.finanza_sobre (id_usuario_miembro, categoria_sobre, color_sobre, monto_bs, user_create)
            VALUES (?, ?, ?, ?, ?)
        """, dto.idUsuarioMiembro(), dto.categoriaSobre(), color, dto.montoBs(), dto.usuarioEjecutor());

        Map<String, String> res = new HashMap<>();
        res.put("mensaje", "Aporte registrado en el sobre correspondiente.");
        return ResponseEntity.ok(res);
    }

    // 8. CREAR PETICIÓN DE ORACIÓN (Pastor, Encargado o Hermano)
    @PostMapping("/oracion/crear")
    public ResponseEntity<Map<String, String>> crearOracion(@RequestBody CrearOracionDTO dto) {
        jdbcTemplate.update("""
            INSERT INTO public.cadena_oracion (id_usuario, rol_solicitante, id_distrito, id_grupo, motivo_oracion, user_create)
            VALUES (?, ?, ?, ?, ?, ?)
        """, dto.idUsuario(), dto.rolSolicitante(), dto.idDistrito(), dto.idGrupo(), dto.motivo(), dto.correoEjecutor());

        Map<String, String> res = new HashMap<>();
        res.put("mensaje", "Petición de oración publicada.");
        return ResponseEntity.ok(res);
    }

    // 9. CREAR SOLICITUD DE CONSULTA PASTORAL (Hermano -> Pastor)
    @PostMapping("/consulta/crear")
    public ResponseEntity<Map<String, String>> crearConsulta(@RequestBody CrearConsultaDTO dto) {
        jdbcTemplate.update("""
            INSERT INTO public.consejeria_bautismo (id_usuario_solicitante, tipo_solicitud, motivo_confidencial, user_create)
            VALUES (?, ?, ?, ?)
        """, dto.idUsuario(), dto.tipoSolicitud(), dto.motivo(), dto.correoEjecutor());

        Map<String, String> res = new HashMap<>();
        res.put("mensaje", "Solicitud de consulta enviada al Pastor.");
        return ResponseEntity.ok(res);
    }

    // 10. AGREGAR ACTIVIDAD AL PLAN MENSUAL DE LA IGLESIA (Solo Pastor y Superadmin)
    @PostMapping("/plan/crear")
    public ResponseEntity<Map<String, String>> crearEventoPlan(@RequestBody CrearPlanDTO dto) {
        jdbcTemplate.update("""
            INSERT INTO public.evento (titulo, descripcion, tipo_evento, fecha_inicio, fecha_fin, mes_programado, lugar, user_create)
            VALUES (?, ?, 'PLAN_MENSUAL', CAST(? AS TIMESTAMP), CAST(? AS TIMESTAMP), ?, ?, ?)
        """, dto.titulo(), dto.descripcion(), dto.fechaInicio() + " 19:00:00", dto.fechaInicio() + " 21:00:00",
                dto.mesProgramado(), dto.lugar(), dto.correoEjecutor());

        Map<String, String> res = new HashMap<>();
        res.put("mensaje", "Fecha agregada al Plan Mensual de la Iglesia.");
        return ResponseEntity.ok(res);
    }

    // 11. REGISTRAR LECTURA BÍBLICA PERSONAL (Hermano)
    @PostMapping("/lectura/crear")
    public ResponseEntity<Map<String, String>> crearLectura(@RequestBody CrearLecturaDTO dto) {
        jdbcTemplate.update("""
            INSERT INTO public.lectura_biblica (id_usuario, libro_biblia, capitulo_leido, reflexion_personal, user_create)
            VALUES (?, ?, ?, ?, ?)
        """, dto.idUsuario(), dto.libroBiblia(), dto.capituloLeido(), dto.reflexion(), dto.correoEjecutor());

        Map<String, String> res = new HashMap<>();
        res.put("mensaje", "Lectura bíblica registrada en tu seguimiento personal.");
        return ResponseEntity.ok(res);
    }

    // 12. ACTUALIZAR PERFIL DEL USUARIO (Nombre, Foto, Contraseña)
    @PutMapping("/{idUsuario}/perfil")
    public ResponseEntity<Map<String, String>> actualizarPerfil(
            @PathVariable Long idUsuario,
            @RequestBody ActualizarPerfilDTO dto) {
        jdbcTemplate.update("""
            UPDATE public.usuario
            SET nombre_completo = ?, foto_perfil_url = ?, user_update = ?
            WHERE id_usuario = ?
        """, dto.nombreCompleto(), dto.fotoPerfilUrl(), dto.correoEjecutor(), idUsuario);

        if (dto.nuevaPassword() != null && !dto.nuevaPassword().isBlank()) {
            jdbcTemplate.update("UPDATE public.usuario SET password_hash = ? WHERE id_usuario = ?",
                    dto.nuevaPassword().trim(), idUsuario);
        }

        Map<String, String> res = new HashMap<>();
        res.put("mensaje", "Perfil actualizado correctamente.");
        return ResponseEntity.ok(res);
    }
}

record SolicitudAccesoDTO(String correo, String nombreCompleto, String password, String idiomaPreferido) {}
record LoginRequestDTO(String correo, String password, String idiomaSeleccionado, Boolean modoGoogle) {}
record RespuestaSolicitudDTO(String accion, String usuarioAutorizador) {}
record AsignarCargosDTO(List<Integer> idsCargos, String usuarioAutorizador) {}
record RegistroAsistenciaDTO(Long idUsuario, String fecha, String diaSemana, String estadoAsistencia, String usuarioEjecutor) {}
record RegistroSobreDTO(Long idUsuarioMiembro, String categoriaSobre, Double montoBs, String usuarioEjecutor) {}
record CrearOracionDTO(Long idUsuario, String rolSolicitante, Integer idDistrito, Integer idGrupo, String motivo, String correoEjecutor) {}
record CrearConsultaDTO(Long idUsuario, String tipoSolicitud, String motivo, String correoEjecutor) {}
record CrearPlanDTO(String titulo, String descripcion, String fechaInicio, Integer mesProgramado, String lugar, String correoEjecutor) {}
record CrearLecturaDTO(Long idUsuario, String libroBiblia, String capituloLeido, String reflexion, String correoEjecutor) {}
record ActualizarPerfilDTO(String nombreCompleto, String fotoPerfilUrl, String nuevaPassword, String correoEjecutor) {}