package br.com.functionrelatorio.utils.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import br.com.functionrelatorio.model.report.DailyCount;
import br.com.functionrelatorio.model.report.ReportData;
import br.com.functionrelatorio.model.report.UrgentEvaluation;

public class PdfReportGenerator {

    private static final PDRectangle PAGE_SIZE = PDRectangle.A4;
    private static final float MARGIN = 50f;

    private static final PDFont FONT = PDType1Font.HELVETICA;
    private static final PDFont FONT_BOLD = PDType1Font.HELVETICA_BOLD;

    private static final float FONT_SIZE_TITLE = 14f;
    private static final float FONT_SIZE = 11f;
    private static final float LEADING = 14f;

    private static final DateTimeFormatter BR_DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");
    private static final DateTimeFormatter BR_DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm");

    private static final DecimalFormat MEDIA_FMT = new DecimalFormat("0.00");

    public byte[] generate(ReportData data) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PageCursor cursor = new PageCursor(doc);

            cursor.writeTitle(data.titulo());
            cursor.writeKeyValue("Período", BR_DATE.format(data.inicio()) + " a " + BR_DATE.format(data.fim()));
            cursor.writeKeyValue("Criticidade", data.criticidade().name());
            cursor.writeSpacer();

            cursor.writeSection("Resumo");
            cursor.writeKeyValue("Total de avaliações", String.valueOf(data.totalAvaliacoes()));
            cursor.writeKeyValue("Avaliações urgentes (nota < 5)", String.valueOf(data.totalUrgentes()));
            cursor.writeKeyValue("Média das notas", MEDIA_FMT.format(data.mediaNotas()));
            cursor.writeSpacer();

            cursor.writeSection("Quantidade de avaliações por dia");
            if (data.avaliacoesPorDia().isEmpty()) {
                cursor.writeParagraph("Nenhuma avaliação encontrada no período.");
            } else {
                for (DailyCount dc : data.avaliacoesPorDia()) {
                    cursor.writeParagraph(BR_DATE.format(dc.dia()) + " - " + dc.quantidade());
                }
            }
            cursor.writeSpacer();

            cursor.writeSection("Casos urgentes (nota < 5)");
            if (data.urgentes().isEmpty()) {
                cursor.writeParagraph("Não há avaliações urgentes no período.");
            } else {
                for (UrgentEvaluation u : data.urgentes()) {
                    List<String> lines = new ArrayList<>();
                    lines.add("Aula: " + safe(u.codIdAula()) + " | Nota: " + u.nota() + " | Data: " + BR_DATE_TIME.format(u.timestamp()));
                    if (u.texto() == null || u.texto().trim().isEmpty()) {
                        lines.add("Texto: (sem texto)");
                    } else {
                        lines.add("Texto: " + u.texto().trim());
                    }
                    cursor.writeBulletBlock(lines);
                }
            }

            cursor.close();

            doc.save(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Erro ao gerar PDF do relatório.", e);
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static final class PageCursor {
        private final PDDocument doc;
        private PDPage page;
        private PDPageContentStream cs;
        private float y;

        private PageCursor(PDDocument doc) {
            this.doc = doc;
            newPage();
        }

        private void newPage() {
            closeStreamQuietly();
            this.page = new PDPage(PAGE_SIZE);
            doc.addPage(page);
            try {
                this.cs = new PDPageContentStream(doc, page);
            } catch (IOException e) {
                throw new RuntimeException("Erro ao iniciar escrita do PDF.", e);
            }
            this.y = PAGE_SIZE.getHeight() - MARGIN;
        }

        private void ensureSpace(float needed) {
            if (y - needed < MARGIN) {
                newPage();
            }
        }

        private void writeTitle(String text) {
            ensureSpace(LEADING * 2);
            y = writeWrapped(text, FONT_BOLD, FONT_SIZE_TITLE, y);
            y -= 6;
        }

        private void writeSection(String text) {
            ensureSpace(LEADING * 2);
            y = writeWrapped(text, FONT_BOLD, FONT_SIZE, y);
        }

        private void writeKeyValue(String key, String value) {
            ensureSpace(LEADING);
            y = writeWrapped(key + ": " + value, FONT, FONT_SIZE, y);
        }

        private void writeParagraph(String text) {
            ensureSpace(LEADING);
            y = writeWrapped(text, FONT, FONT_SIZE, y);
        }

        private void writeBulletBlock(List<String> paragraphs) {
            ensureSpace(LEADING * (paragraphs.size() + 1));
            y = writeWrapped("• " + paragraphs.get(0), FONT, FONT_SIZE, y);
            for (int i = 1; i < paragraphs.size(); i++) {
                y = writeWrapped("  " + paragraphs.get(i), FONT, FONT_SIZE, y);
            }
            y -= 4;
        }

        private void writeSpacer() {
            y -= 10;
        }

        private float writeWrapped(String text, PDFont font, float fontSize, float startY) {
            List<String> lines = wrap(text, font, fontSize, PAGE_SIZE.getWidth() - 2 * MARGIN);
            float yy = startY;
            for (String line : lines) {
                ensureSpace(LEADING);
                try {
                    cs.beginText();
                    cs.setFont(font, fontSize);
                    cs.newLineAtOffset(MARGIN, yy);
                    cs.showText(line);
                    cs.endText();
                } catch (IOException e) {
                    throw new RuntimeException("Erro ao escrever conteúdo no PDF.", e);
                }
                yy -= LEADING;
                this.y = yy;
            }
            return yy;
        }

        private static List<String> wrap(String text, PDFont font, float fontSize, float maxWidth) {
            String normalized = text == null ? "" : text.replace("\r", "").replace("\n", " ").trim();
            if (normalized.isEmpty()) {
                return List.of("");
            }

            String[] words = normalized.split("\\s+");
            List<String> lines = new ArrayList<>();
            StringBuilder line = new StringBuilder();

            for (String word : words) {
                if (line.length() == 0) {
                    line.append(word);
                    continue;
                }

                String candidate = line + " " + word;
                if (width(font, fontSize, candidate) <= maxWidth) {
                    line.append(" ").append(word);
                } else {
                    lines.add(line.toString());
                    line.setLength(0);

                    if (width(font, fontSize, word) <= maxWidth) {
                        line.append(word);
                    } else {
                        // fallback: quebra palavra muito grande
                        lines.addAll(breakLongWord(word, font, fontSize, maxWidth));
                    }
                }
            }

            if (line.length() > 0) {
                lines.add(line.toString());
            }
            return lines;
        }

        private static List<String> breakLongWord(String word, PDFont font, float fontSize, float maxWidth) {
            List<String> parts = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (int i = 0; i < word.length(); i++) {
                current.append(word.charAt(i));
                if (width(font, fontSize, current.toString()) > maxWidth) {
                    if (current.length() == 1) {
                        parts.add(current.toString());
                        current.setLength(0);
                    } else {
                        char last = current.charAt(current.length() - 1);
                        current.setLength(current.length() - 1);
                        parts.add(current.toString());
                        current.setLength(0);
                        current.append(last);
                    }
                }
            }
            if (current.length() > 0) {
                parts.add(current.toString());
            }
            return parts;
        }

        private static float width(PDFont font, float fontSize, String text) {
            try {
                return font.getStringWidth(text) / 1000f * fontSize;
            } catch (IOException e) {
                throw new RuntimeException("Erro ao calcular largura do texto no PDF.", e);
            }
        }

        private void closeStreamQuietly() {
            if (cs != null) {
                try {
                    cs.close();
                } catch (IOException ignored) {
                }
            }
        }

        private void close() {
            closeStreamQuietly();
        }
    }
}
