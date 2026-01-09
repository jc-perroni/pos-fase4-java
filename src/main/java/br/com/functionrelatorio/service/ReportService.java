package br.com.functionrelatorio.service;

import br.com.functionrelatorio.model.report.Criticidade;
import br.com.functionrelatorio.model.report.DailyCount;
import br.com.functionrelatorio.model.report.ReportData;
import br.com.functionrelatorio.model.report.UrgentEvaluation;
import br.com.functionrelatorio.repository.ReportRepository;
import br.com.functionrelatorio.utils.pdf.PdfReportGenerator;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ReportService {

    private static final DateTimeFormatter BR_DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");

    private final ReportRepository repository;
    private final PdfReportGenerator pdfReportGenerator;

    public ReportService(ReportRepository repository, PdfReportGenerator pdfReportGenerator) {
        this.repository = repository;
        this.pdfReportGenerator = pdfReportGenerator;
    }

    public PdfResult gerarRelatorioAvaliacoes(LocalDate inicio, LocalDate fimInclusivo, String tipoArquivo) {
        if (inicio == null || fimInclusivo == null) {
            throw new IllegalArgumentException("Período inválido: datas não podem ser nulas.");
        }
        if (fimInclusivo.isBefore(inicio)) {
            throw new IllegalArgumentException("Período inválido: 'fim' não pode ser anterior a 'inicio'.");
        }

        int total = repository.countAvaliacoes(inicio, fimInclusivo);
        int urgentesCount = repository.countAvaliacoesUrgentes(inicio, fimInclusivo);
        double media = repository.avgNotas(inicio, fimInclusivo);
        List<DailyCount> porDia = repository.countAvaliacoesPorDia(inicio, fimInclusivo);
        List<UrgentEvaluation> urgentes = repository.listAvaliacoesUrgentes(inicio, fimInclusivo);

        Criticidade criticidade = urgentesCount > 0 ? Criticidade.URGENTE : Criticidade.NORMAL;
        String titulo = "Relatório de Avaliações: Período (" + BR_DATE.format(inicio) + " a " + BR_DATE.format(fimInclusivo) + ")";

        ReportData data = new ReportData(
                titulo,
                inicio,
                fimInclusivo,
                criticidade,
                total,
                urgentesCount,
                media,
                porDia,
                urgentes
        );

        byte[] pdf = pdfReportGenerator.generate(data);
        String fileName = buildFileName(tipoArquivo, inicio, fimInclusivo);
        return new PdfResult(fileName, pdf);
    }

    private static String buildFileName(String tipo, LocalDate inicio, LocalDate fim) {
        String safeTipo = (tipo == null || tipo.isBlank()) ? "relatorio" : tipo;
        return "relatorio_" + safeTipo + "_" + inicio.toString().replace("-", "") + "_" + fim.toString().replace("-", "") + ".pdf";
    }

    public record PdfResult(String fileName, byte[] bytes) {
        public PdfResult {
            if (fileName == null || fileName.isBlank()) {
                throw new IllegalArgumentException("fileName não pode ser vazio.");
            }
            if (bytes == null || bytes.length == 0) {
                throw new IllegalArgumentException("PDF gerado está vazio.");
            }
        }
    }
}
