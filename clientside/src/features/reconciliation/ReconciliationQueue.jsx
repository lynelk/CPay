import React, { useEffect, useState } from 'react';
import { getUnmatchedReconciliation } from '../../shared/api/v2Client';

export default function ReconciliationQueue() {
  const [records, setRecords] = useState([]);
  const [message, setMessage] = useState('');

  useEffect(() => {
    getUnmatchedReconciliation(100).then(setRecords).catch(error => setMessage(error.message));
  }, []);

  return (
    <div className="reconciliation-queue">
      <h2>Reconciliation Queue</h2>
      <p>Review unmatched provider records and prepare manual matching actions.</p>
      {message && <div>{message}</div>}
      <table width="100%">
        <thead>
          <tr>
            <th align="left">Provider</th>
            <th align="left">Channel</th>
            <th align="left">Provider Ref</th>
            <th align="left">Merchant Ref</th>
            <th align="right">Amount</th>
            <th align="left">Currency</th>
            <th align="left">Status</th>
          </tr>
        </thead>
        <tbody>
          {records.map(record => (
            <tr key={record.id}>
              <td>{record.providerCode}</td>
              <td>{record.channelCode}</td>
              <td>{record.providerReference}</td>
              <td>{record.merchantReference}</td>
              <td align="right">{record.amount}</td>
              <td>{record.currency}</td>
              <td>{record.matchStatus}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
