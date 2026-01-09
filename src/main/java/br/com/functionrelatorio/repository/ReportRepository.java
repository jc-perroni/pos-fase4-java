package br.com.functionrelatorio.repository;

import java.time.LocalDate;
import java.util.List;

import br.com.functionrelatorio.model.report.DailyCount;
import br.com.functionrelatorio.model.report.UrgentEvaluation;

public interface ReportRepository {

    int countAvaliacoes(LocalDate inicio, LocalDate fimInclusivo);

    int countAvaliacoesUrgentes(LocalDate inicio, LocalDate fimInclusivo);

    double avgNotas(LocalDate inicio, LocalDate fimInclusivo);

    List<DailyCount> countAvaliacoesPorDia(LocalDate inicio, LocalDate fimInclusivo);

    List<UrgentEvaluation> listAvaliacoesUrgentes(LocalDate inicio, LocalDate fimInclusivo);
}