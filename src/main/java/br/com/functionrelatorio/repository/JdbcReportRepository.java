package br.com.functionrelatorio.repository;

import javax.sql.DataSource;

import br.com.functionrelatorio.model.report.DailyCount;
import br.com.functionrelatorio.model.report.UrgentEvaluation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class JdbcReportRepository implements ReportRepository {

    private final DataSource dataSource;

    public JdbcReportRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public int countAvaliacoes(LocalDate inicio, LocalDate fimInclusivo) {
        String sql = "SELECT COUNT(1) FROM AVALIACAO_AULA WHERE TIMESTAMP_AVALIACAO >= ? AND TIMESTAMP_AVALIACAO < ?";
        DateRange r = DateRange.inclusive(inicio, fimInclusivo);

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setTimestamp(1, Timestamp.valueOf(r.startInclusive));
            ps.setTimestamp(2, Timestamp.valueOf(r.endExclusive));

            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro ao consultar total de avaliações no período.", e);
        }
    }

    @Override
    public int countAvaliacoesUrgentes(LocalDate inicio, LocalDate fimInclusivo) {
        String sql = "SELECT COUNT(1) FROM AVALIACAO_AULA "
                + "WHERE TIMESTAMP_AVALIACAO >= ? AND TIMESTAMP_AVALIACAO < ? AND NOTA_AVALIACAO < 5";
        DateRange r = DateRange.inclusive(inicio, fimInclusivo);

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setTimestamp(1, Timestamp.valueOf(r.startInclusive));
            ps.setTimestamp(2, Timestamp.valueOf(r.endExclusive));

            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro ao consultar avaliações urgentes no período.", e);
        }
    }

    @Override
    public double avgNotas(LocalDate inicio, LocalDate fimInclusivo) {
        String sql = "SELECT AVG(CAST(NOTA_AVALIACAO AS float)) "
                + "FROM AVALIACAO_AULA WHERE TIMESTAMP_AVALIACAO >= ? AND TIMESTAMP_AVALIACAO < ?";
        DateRange r = DateRange.inclusive(inicio, fimInclusivo);

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setTimestamp(1, Timestamp.valueOf(r.startInclusive));
            ps.setTimestamp(2, Timestamp.valueOf(r.endExclusive));

            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                double v = rs.getDouble(1);
                if (rs.wasNull()) {
                    return 0.0;
                }
                return v;
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro ao consultar média das notas no período.", e);
        }
    }

    @Override
    public List<DailyCount> countAvaliacoesPorDia(LocalDate inicio, LocalDate fimInclusivo) {
        String sql = "SELECT CAST(TIMESTAMP_AVALIACAO AS date) AS DIA, COUNT(1) AS QTD "
                + "FROM AVALIACAO_AULA "
                + "WHERE TIMESTAMP_AVALIACAO >= ? AND TIMESTAMP_AVALIACAO < ? "
                + "GROUP BY CAST(TIMESTAMP_AVALIACAO AS date) "
                + "ORDER BY DIA";
        DateRange r = DateRange.inclusive(inicio, fimInclusivo);

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setTimestamp(1, Timestamp.valueOf(r.startInclusive));
            ps.setTimestamp(2, Timestamp.valueOf(r.endExclusive));

            List<DailyCount> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LocalDate dia = rs.getDate("DIA").toLocalDate();
                    int qtd = rs.getInt("QTD");
                    out.add(new DailyCount(dia, qtd));
                }
            }
            return out;
        } catch (Exception e) {
            throw new RuntimeException("Erro ao consultar quantidade de avaliações por dia.", e);
        }
    }

    @Override
    public List<UrgentEvaluation> listAvaliacoesUrgentes(LocalDate inicio, LocalDate fimInclusivo) {
        String sql = "SELECT COD_ID_AULA, NOTA_AVALIACAO, TEXTO_AVALIACAO, TIMESTAMP_AVALIACAO "
                + "FROM AVALIACAO_AULA "
                + "WHERE TIMESTAMP_AVALIACAO >= ? AND TIMESTAMP_AVALIACAO < ? AND NOTA_AVALIACAO < 5 "
                + "ORDER BY TIMESTAMP_AVALIACAO";
        DateRange r = DateRange.inclusive(inicio, fimInclusivo);

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setTimestamp(1, Timestamp.valueOf(r.startInclusive));
            ps.setTimestamp(2, Timestamp.valueOf(r.endExclusive));

            List<UrgentEvaluation> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String cod = rs.getString("COD_ID_AULA");
                    int nota = rs.getInt("NOTA_AVALIACAO");
                    String texto = rs.getString("TEXTO_AVALIACAO");
                    LocalDateTime ts = rs.getTimestamp("TIMESTAMP_AVALIACAO").toLocalDateTime();
                    out.add(new UrgentEvaluation(cod, nota, texto, ts));
                }
            }
            return out;
        } catch (Exception e) {
            throw new RuntimeException("Erro ao listar avaliações urgentes no período.", e);
        }
    }

    private record DateRange(LocalDateTime startInclusive, LocalDateTime endExclusive) {
        private static DateRange inclusive(LocalDate inicio, LocalDate fimInclusivo) {
            if (inicio == null || fimInclusivo == null) {
                throw new IllegalArgumentException("Datas não podem ser nulas.");
            }
            LocalDateTime start = inicio.atStartOfDay();
            LocalDateTime end = fimInclusivo.plusDays(1).atStartOfDay();
            return new DateRange(start, end);
        }
    }
}