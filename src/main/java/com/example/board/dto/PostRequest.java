package com.example.board.dto;

import jakarta.validation.constraints.*;

public record PostRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank String content) {
}
