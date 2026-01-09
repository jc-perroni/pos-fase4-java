package br.com.functionrelatorio.model.report;

import java.time.LocalDate;
import java.util.List;

public record ReportData(
        String titulo,
        LocalDate inicio,
        LocalDate fim,
        Criticidade criticidade,
        int totalAvaliacoes,
        int totalUrgentes,
        double mediaNotas,
        List<DailyCount> avaliacoesPorDia,
        List<UrgentEvaluation> urgentes
) {
}
