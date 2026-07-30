package net.citotech.cito.export;

import java.util.function.Function;

/**
 * One column of a tabular export (audit M5): a header label plus how to pull that column's
 * value out of a row of type {@code T}. Shared across every CSV/XLSX export surface so a column
 * layout is declared once, next to the data it describes, instead of being re-implemented per
 * format (hand-built CSV strings in one module, a client-side spreadsheet shim in another).
 */
public record ExportColumn<T>(String header, Function<T, ?> valueExtractor) {

    public static <T> ExportColumn<T> of(String header, Function<T, ?> valueExtractor) {
        return new ExportColumn<>(header, valueExtractor);
    }

    public Object valueOf(T row) {
        return valueExtractor.apply(row);
    }
}
