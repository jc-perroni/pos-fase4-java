package br.com.functionrelatorio;

import br.com.functionrelatorio.infrastructure.AppContext;
import br.com.functionrelatorio.service.ReportService;
import br.com.functionrelatorio.utils.pdf.PdfReportGenerator;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

public class Function {

    private static final ZoneId ZONE_ID = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter BR_DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");

    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd

    private static final Pattern JSON_DATE_FIELD =
            Pattern.compile("\"(inicio|fim)\"\\s*:\\s*\"(\\d{2}/\\d{2}/\\d{4})\"");


    @FunctionName("gerarRelatorioSemanal")
    public HttpResponseMessage run(
            @HttpTrigger(
                    name = "req",
                    methods = {HttpMethod.GET, HttpMethod.POST},
                    authLevel = AuthorizationLevel.FUNCTION
            )
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context
    ) {
        context.getLogger().info("Função gerarRelatorioSemanal acionada. Method=" + request.getHttpMethod());

        try {
            if (request.getHttpMethod() == HttpMethod.GET) {
                return handleGet(request, context);
            }
            if (request.getHttpMethod() == HttpMethod.POST) {
                return handlePost(request, context);
            }

            return request.createResponseBuilder(HttpStatus.METHOD_NOT_ALLOWED)
                    .body("Método não suportado. Use GET ou POST.")
                    .build();

        } catch (IllegalArgumentException ex) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body(ex.getMessage())
                    .build();
        } catch (Exception ex) {
            context.getLogger().severe("Erro inesperado: " + ex.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro interno ao processar a requisição.")
                    .build();
        }
    }

    private HttpResponseMessage handleGet(HttpRequestMessage<Optional<String>> request, ExecutionContext context) {
        LocalDate hoje = LocalDate.now(ZONE_ID);
        LocalDate inicio = hoje.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        LocalDate fimInclusivo = inicio.plusWeeks(1).minusDays(1);

        return processarRelatorio(request, context, inicio, fimInclusivo, "semanal");
    }

    private HttpResponseMessage handlePost(HttpRequestMessage<Optional<String>> request, ExecutionContext context) {
        Map<String, String> qp = request.getQueryParameters();

        String inicioStr = qp.get("inicio");
        String fimStr = qp.get("fim");

        if ((inicioStr == null || fimStr == null) && request.getBody().isPresent()) {
            Map<String, String> fromJson = extractInicioFimFromJson(request.getBody().get());
            if (inicioStr == null) inicioStr = fromJson.get("inicio");
            if (fimStr == null) fimStr = fromJson.get("fim");
        }

        if (inicioStr == null || fimStr == null) {
            throw new IllegalArgumentException("Parâmetros obrigatórios ausentes. Envie 'inicio' e 'fim' (dd/MM/yyyy) via querystring ou body JSON.");
        }

        LocalDate inicio = parseIsoDateOrThrow(inicioStr, "inicio");
        LocalDate fimInclusivo = parseIsoDateOrThrow(fimStr, "fim");

        if (fimInclusivo.isBefore(inicio)) {
            throw new IllegalArgumentException("Intervalo inválido: 'fim' não pode ser anterior a 'inicio'.");
        }

        return processarRelatorio(request, context, inicio, fimInclusivo, "custom");
    }

    private HttpResponseMessage processarRelatorio(
            HttpRequestMessage<Optional<String>> request,
            ExecutionContext context,
            LocalDate inicio,
            LocalDate fimInclusivo,
            String tipo
    ) {
        String fileName = "relatorio_" + tipo + "_" + inicio.format(FILE_DATE) + "_" + fimInclusivo.format(FILE_DATE) + ".pdf";
        context.getLogger().info("Relatório solicitado. inicio=" + inicio + " fimInclusivo=" + fimInclusivo + " fileName=" + fileName);

        ReportService service = new ReportService(AppContext.reportRepository(), new PdfReportGenerator());
        ReportService.PdfResult pdf = service.gerarRelatorioAvaliacoes(inicio, fimInclusivo, tipo);

        return request.createResponseBuilder(HttpStatus.OK)
            .header("Content-Type", "application/pdf")
            .header("Content-Disposition", "attachment; filename=\"" + pdf.fileName() + "\"")
            .header("Cache-Control", "no-store")
            .body(pdf.bytes())
            .build();
    }

    private LocalDate parseIsoDateOrThrow(String value, String fieldName) {
        try {
            return LocalDate.parse(value, BR_DATE);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(
                    "Data inválida em '" + fieldName + "'. Use formato dd/MM/yyyy. Valor recebido: " + value
            );
        }
    }

    private Map<String, String> extractInicioFimFromJson(String body) {
        java.util.HashMap<String, String> map = new java.util.HashMap<>();
        Matcher m = JSON_DATE_FIELD.matcher(body);
        while (m.find()) {
            map.put(m.group(1), m.group(2));
        }
        return map;
    }
}