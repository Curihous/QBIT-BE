package com.curihous.qbit.infra.alpaca.dto.request;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AlpacaOrderQueryParams {
    private final String status;
    private final Integer limit;
    private final String direction;
    private final Boolean nested;
}


