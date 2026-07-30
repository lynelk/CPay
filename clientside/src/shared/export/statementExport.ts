/**
 * Downloads a merchant statement export from the server-side CSV/XLSX endpoint (audit M5).
 *
 * Before this, the merchant/admin "Account Statement" screens built a CSV client-side (via the
 * `ExcelExport.js` shim, despite its name, out of whatever rows happened to already be loaded in
 * the table) instead of asking the server for the full date range. This calls the same
 * cursor-paginated statement export used by the signed v2 API
 * (`net.citotech.cito.api.v2.MerchantStatementExportService`) so the download reflects the whole
 * requested range - bounded by the service's own page-size cap, not by whatever the table
 * happened to have loaded - and gets a real server-rendered CSV or XLSX file instead of a
 * hand-built Blob.
 */
import { apiFetch } from '../api/httpClient';

export type StatementExportFormat = 'csv' | 'xlsx';

export interface DownloadStatementExportOptions {
  startDate: string;
  endDate: string;
  format: StatementExportFormat;
  /** Defaults to the merchant-portal self-service endpoint (session-authenticated). */
  path?: string;
  /** Bounded page size passed to the server; the endpoint caps this itself regardless. */
  limit?: number;
}

const DEFAULT_PATH = '/api/v2/merchant-self-service/statements';
const DEFAULT_LIMIT = 5000;

export async function downloadStatementExport(options: DownloadStatementExportOptions): Promise<void> {
  const { startDate, endDate, format, path = DEFAULT_PATH, limit = DEFAULT_LIMIT } = options;
  if (!startDate || !endDate) {
    throw new Error('Select a start and end date before downloading a statement.');
  }

  const params = new URLSearchParams({ startDate, endDate, format, limit: String(limit) });
  const response = await apiFetch(`${path}?${params.toString()}`, { method: 'GET' });
  if (!response.ok) {
    throw new Error(`Statement download failed (status ${response.status})`);
  }

  const blob = await response.blob();
  const filename = filenameFromDisposition(response.headers.get('Content-Disposition')) || `statement.${format}`;
  triggerBlobDownload(blob, filename);
}

function filenameFromDisposition(disposition: string | null): string | null {
  if (!disposition) return null;
  const match = /filename="?([^";]+)"?/i.exec(disposition);
  return match ? match[1] : null;
}

function triggerBlobDownload(blob: Blob, filename: string): void {
  if (typeof document === 'undefined' || typeof URL === 'undefined') {
    return;
  }
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}
