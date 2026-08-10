import React, { useEffect, useMemo, useState } from 'react';
import { Alert, Button, Card, Spinner, Table, Tabs, TextArea, TextField, Toolbar } from '../../../ui';
import type { Column } from '../../../ui';
import { ApiError, request } from '../../../shared/api/httpClient';
import MerchantModuleSms from './MerchantModuleSms';

interface UssdRow {
  id?: number;
  sessionId?: string;
  msisdnHash?: string;
  lastInput?: string;
  responseText?: string;
  status?: string;
  updatedAt?: string;
}

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  if (error instanceof Error) return error.message;
  return 'Something went wrong.';
}

interface Props {
  loader?: (op: 'START' | 'STOP') => void;
  refreshSignal?: unknown;
  sessionExpired?: () => void;
  logOut?: () => void;
}

export default function MerchantModuleCommunication(props: Props): React.ReactElement {
  const { loader, refreshSignal, sessionExpired } = props;
  const [tab, setTab] = useState('sms');
  const [waRecipients, setWaRecipients] = useState('');
  const [waContent, setWaContent] = useState('');
  const [ussdRows, setUssdRows] = useState<UssdRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [feedback, setFeedback] = useState('');

  async function loadUssd() {
    setLoading(true); setError(null); loader?.('START');
    try {
      const rows = await request<UssdRow[]>('/api/v2/merchant-self-service/communication/ussd/sessions');
      setUssdRows(Array.isArray(rows) ? rows : []);
    } catch (e) {
      setError(e);
      if (e instanceof ApiError && e.status === 401) sessionExpired?.();
    } finally { setLoading(false); loader?.('STOP'); }
  }

  useEffect(() => { if (tab === 'ussd') void loadUssd(); }, [tab, refreshSignal]);

  async function sendWhatsApp() {
    setError(null); setFeedback(''); loader?.('START');
    try {
      const result = await request<{ status?: string; successful?: boolean }>('/api/v2/merchant-self-service/communication/whatsapp/send', {
        method: 'POST',
        body: JSON.stringify({ recipients: waRecipients, content: waContent }),
      });
      setFeedback(`WhatsApp message ${result.status ?? (result.successful ? 'SENT' : 'submitted')}.`);
      if (result.successful) { setWaRecipients(''); setWaContent(''); }
    } catch (e) {
      setError(e);
      if (e instanceof ApiError && e.status === 401) sessionExpired?.();
    } finally { loader?.('STOP'); }
  }

  const ussdColumns = useMemo<Column<UssdRow>[]>(() => [
    { key: 'session', header: 'Session', accessor: (r) => r.sessionId ?? '' },
    { key: 'customer', header: 'Customer hash', accessor: (r) => r.msisdnHash ?? '' },
    { key: 'input', header: 'Last input', accessor: (r) => r.lastInput ?? '' },
    { key: 'response', header: 'Response', accessor: (r) => r.responseText ?? '' },
    { key: 'status', header: 'Status', accessor: (r) => r.status ?? '' },
    { key: 'updated', header: 'Updated', accessor: (r) => r.updatedAt ?? '' },
  ], []);

  return (
    <div className="cpay-merchant-communication">
      {error ? <Alert variant="error">{errorMessage(error)}</Alert> : null}
      {feedback ? <Alert variant="success">{feedback}</Alert> : null}
      <Card>
        <Toolbar><strong>Communication</strong><Toolbar.Spacer />{tab === 'ussd' ? <Button variant="ghost" onClick={() => void loadUssd()}>Refresh</Button> : null}</Toolbar>
        <p>Send and review merchant communications from one place. Available channels depend on your configured CPay services.</p>
        <Tabs items={[{ key: 'sms', label: 'SMS' }, { key: 'whatsapp', label: 'WhatsApp' }, { key: 'ussd', label: 'USSD' }]} active={tab} onChange={setTab} />
      </Card>

      {tab === 'sms' ? <MerchantModuleSms {...props} /> : null}
      {tab === 'whatsapp' ? <Card>
        <TextField id="merchant-wa-recipients" label="Recipient(s)" value={waRecipients} onValueChange={setWaRecipients} placeholder="2567..., comma-separated" />
        <TextArea id="merchant-wa-content" label="Message" value={waContent} onValueChange={setWaContent} rows={5} />
        <Button onClick={() => void sendWhatsApp()}>Send WhatsApp message</Button>
      </Card> : null}
      {tab === 'ussd' ? <>
        {loading ? <Spinner label="Loading USSD sessions" /> : null}
        <Table columns={ussdColumns} rows={ussdRows} rowKey={(r, i) => r.id ?? i} pageSize={20} emptyText="No USSD sessions recorded for this account." />
      </> : null}
    </div>
  );
}
