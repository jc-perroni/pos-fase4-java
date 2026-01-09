package br.com.functionrelatorio.model.report;

import java.time.LocalDateTime;

public record UrgentEvaluation(
        String codIdAula,
        int nota,
        String texto,
        LocalDateTime timestamp
) {
}
