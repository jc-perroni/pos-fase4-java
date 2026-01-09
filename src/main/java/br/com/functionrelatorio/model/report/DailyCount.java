package br.com.functionrelatorio.model.report;

import java.time.LocalDate;

public record DailyCount(LocalDate dia, int quantidade) {
}
