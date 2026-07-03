import React, { useEffect, useState } from 'react';
import { autoMatchReconciliation, getGatewayChannels, processDueWebhooks } from '../../shared/api/v2Client';

export default function GatewayAdminDashboard() {
  const [channels, setChannels] = useState([]);
  const [message, setMessage] = useState('');

  useEffect(() => {
    getGatewayChannels().then(setChannels).catch(error => setMessage(error.message));
  }, []);

  async function runWebhookRetry() {
    const result = await processDueWebhooks(50);
    setMessage(typeof result === 'string' ? result : JSON.stringify(result));
  }

  async function runAutoMatch() {
    const result = await autoMatchReconciliation();
    setMessage(typeof result === 'string' ? result : JSON.stringify(result));
  }

  return (
    <div className="gateway-admin-dashboard">
      <h2>Gateway Admin Console</h2>
      <p>Manage channel visibility, webhook retries, and reconciliation operations from one place.</p>
      <div style={{ display: 'flex', gap: 12, marginBottom: 16 }}>
        <button onClick={runWebhookRetry}>Process Due Webhooks</button>
        <button onClick={runAutoMatch}>Auto-match Reconciliation</button>
      </div>
      {message && <div>{message}</div>}
      <table width="100%">
        <thead>
          <tr>
            <th align="left">Channel</th>
            <th align="left">Country</th>
            <th align="left">Currency</th>
            <th align="left">Collections</th>
            <th align="left">Payouts</th>
          </tr>
        </thead>
        <tbody>
          {channels.map(channel => (
            <tr key={channel.channelCode}>
              <td>{channel.displayName || channel.channelCode}</td>
              <td>{channel.countryCode}</td>
              <td>{channel.currencyCode}</td>
              <td>{channel.collections ? 'Yes' : 'No'}</td>
              <td>{channel.payouts ? 'Yes' : 'No'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
