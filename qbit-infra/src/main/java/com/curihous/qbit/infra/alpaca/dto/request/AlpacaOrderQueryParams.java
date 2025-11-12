package com.curihous.qbit.infra.alpaca.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AlpacaOrderQueryParams {
    private String status;
    private Integer limit;
    private String direction;
    private Boolean nested;
    private String after;
    private String until;
}
