import React from 'react';

function getCellValue(row, value) {
    if (typeof value === 'function') {
        return value(row);
    }

    if (typeof value === 'string') {
        return row?.[value];
    }

    return value;
}

class ExcelFile extends React.Component {
    download() {
        const sheets = React.Children.toArray(this.props.children).filter(
            (child) => child && child.type === ExcelSheet
        );

        const sheet = sheets[0];
        if (!sheet) {
            return;
        }

        const columns = React.Children.toArray(sheet.props.children).filter(
            (child) => child && child.type === ExcelColumn
        );

        const rows = (sheet.props.data || []).map((row) =>
            columns.map((column) => getCellValue(row, column.props.value))
        );

        const escapeValue = (value) => {
            const stringValue = value == null ? '' : String(value);
            return `"${stringValue.replace(/"/g, '""')}"`;
        };

        const header = columns.map((column) => escapeValue(column.props.label)).join(',');
        const body = rows.map((row) => row.map(escapeValue).join(',')).join('\n');
        const csv = [header, body].filter(Boolean).join('\n');

        if (typeof document === 'undefined' || typeof URL === 'undefined') {
            return;
        }

        const fileName = this.props.filename && this.props.filename.endsWith('.csv')
            ? this.props.filename
            : `${this.props.filename || 'export'}.csv`;

        const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
        const link = document.createElement('a');
        link.href = URL.createObjectURL(blob);
        link.download = fileName;
        link.click();
        URL.revokeObjectURL(link.href);
    }

    render() {
        return this.props.element || null;
    }
}

function ExcelSheet() {
    return null;
}

function ExcelColumn() {
    return null;
}

ExcelFile.ExcelSheet = ExcelSheet;
ExcelFile.ExcelColumn = ExcelColumn;

export default {
    ExcelFile,
};
