package com.proyecto.servicios.mapper;

import com.proyecto.servicios.entity.sf.ClienteEntity;
import com.proyecto.servicios.entity.sf.CuentaEntity;
import com.proyecto.servicios.entity.sf.DomicilioEntity;
import com.proyecto.servicios.model.cliente.ClienteResponse;
import com.proyecto.servicios.model.cliente.CuentaResponse;
import com.proyecto.servicios.model.cliente.DomicilioResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ClienteResponseMapper {

    public ClienteResponse toResponse(
            ClienteEntity cliente,
            DomicilioEntity domicilio,
            List<CuentaEntity> cuentas
    ) {
        return new ClienteResponse(
                cliente.getId(),
                cliente.getNombre(),
                cliente.getSegundoNombre(),
                cliente.getApellidoPaterno(),
                cliente.getApellidoMaterno(),
                cliente.getFechaNacimiento(),
                cliente.getCurp(),
                cliente.getRfc(),
                cliente.getSexo(),
                cliente.getNacionalidad(),
                cliente.getEstadoCivil(),
                cliente.getReferenciaReconocimientoFacial(),
                cliente.getCorreo(),
                cliente.getTelefonoMovil(),
                cliente.getTelefonoAlternativo(),
                cliente.getOcupacion(),
                cliente.getEmpresa(),
                cliente.getIngresoMensual(),
                cliente.isActivo(),
                toDomicilioResponse(domicilio),
                cuentas.stream().map(this::toCuentaResponse).toList(),
                cliente.getFechaCreacion(),
                cliente.getFechaActualizacion()
        );
    }

    private DomicilioResponse toDomicilioResponse(DomicilioEntity domicilio) {
        if (domicilio == null) {
            return null;
        }
        return new DomicilioResponse(
                domicilio.getId(),
                domicilio.getCalle(),
                domicilio.getNumeroExterior(),
                domicilio.getNumeroInterior(),
                domicilio.getColonia(),
                domicilio.getMunicipio(),
                domicilio.getEstado(),
                domicilio.getCodigoPostal(),
                domicilio.getPais()
        );
    }

    public CuentaResponse toCuentaResponse(CuentaEntity cuenta) {
        return new CuentaResponse(
                cuenta.getId(),
                cuenta.getNumeroCuenta(),
                cuenta.getSaldo(),
                cuenta.isActiva()
        );
    }
}
