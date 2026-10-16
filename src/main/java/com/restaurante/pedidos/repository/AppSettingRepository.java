package com.restaurante.pedidos.repository;

import com.restaurante.pedidos.domain.AppSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppSettingRepository extends JpaRepository<AppSetting, String> {
}
