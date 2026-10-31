package com.farmacia.farmaciaonline.cep;

import java.util.Optional;

public interface ConsultaCep {

    Optional<EnderecoCep> consultar(String cep);
}
