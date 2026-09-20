package com.proyecto.servicios.repositorys.sf;

import com.proyecto.servicios.entity.sf.AccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccountRepository extends JpaRepository<AccountEntity, UUID> {
}
