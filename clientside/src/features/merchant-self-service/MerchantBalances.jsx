import React, { useState } from 'react';
import { getMerchantChannelBalances } from '../../shared/api/v2Client';

export default function MerchantBalances() {
  const [merchantNumber, setMerchantNumber] = useState('');
  const [balances, setBalances] = useState([]);
  const [message, setMessage] = useState('');

  async function loadBalances() {
    try {
      setMessage('');
      const result = await getMerchantChannelBalances(merchantNumber);
      setBalances(result || []);
    } catch (error) {
      setMessage(error.message);
    }
  }

  return (
    <div className="merchant-balances">
      <h2>Merchant Channel Balances</h2>
      <p>View balances grouped by channel and currency.</p>
      <div style={{ display: 'flex', gap: 8, marginBottom: 16 }}>
        <input
          value={merchantNumber}
          onChange={event => setMerchantNumber(event.target.value)}
          placeholder="Merchant number"
        />
        <button onClick={loadBalances}>Load Balances</button>
      </div>
      {message && <div>{message}</div>}
      <table width="100%">
        <thead>
          <tr>
            <th align="left">Channel</th>
            <th align="left">Gateway</th>
            <th align="left">Currency</th>
            <th align="right">Available</th>
            <th align="right">Ledger</th>
            <th align="right">Pending</th>
          </tr>
        </thead>
        <tbody>
          {balances.map(balance => (
            <tr key={`${balance.channelCode}-${balance.currency}`}>
              <td>{balance.channelCode}</td>
              <td>{balance.gatewayId}</td>
              <td>{balance.currency}</td>
              <td align="right">{balance.availableBalance}</td>
              <td align="right">{balance.ledgerBalance}</td>
              <td align="right">{balance.pendingBalance}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
