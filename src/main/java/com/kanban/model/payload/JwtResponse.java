package com.kanban.model.payload;

import lombok.Builder;
import lombok.Data;


@Data
@Builder
public class JwtResponse {
    private String accessToken;
    private String refreshToken;
}
