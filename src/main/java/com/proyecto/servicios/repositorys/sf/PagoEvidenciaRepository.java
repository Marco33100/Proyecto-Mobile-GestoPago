package com.proyecto.servicios.repositorys.sf;
import com.proyecto.servicios.entity.sf.PagoEvidenciaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface PagoEvidenciaRepository extends JpaRepository<PagoEvidenciaEntity, UUID> { }
