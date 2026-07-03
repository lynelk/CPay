import React, { useEffect, useState } from 'react';
import { v2Client } from '../shared/api/v2Client';
import { opsClient } from '../shared/api/opsClient';

export default function OperationsConsole() {
  const [channels, setChannels] = useState([]);
  const [summary, setSummary] = useState({});
  const [message, setMessage] = useState('');

  useEffect(() => {
    v2Client.channels().then(setChannels).catch(error => setMessage(error.message));
    opsClient.summary().then(setSummary).catch(() => setSummary({}));
  }, []);

  const show = promise => promise.then(result => setMessage(typeof result === 'string' ? result : JSON.stringify(result))).catch(error => setMessage(error.message));

  return React.createElement('section', null,
    React.createElement('h2', null, 'Operations Console'),
    message ? React.createElement('p', null, message) : null,
    React.createElement('h3', null, 'Dashboard'),
    React.createElement('pre', null, JSON.stringify(summary, null, 2)),
    React.createElement('h3', null, 'Actions'),
    React.createElement('button', { onClick: () => show(v2Client.runCallbacks()) }, 'Run Callbacks'),
    React.createElement('button', { onClick: () => show(v2Client.autoMatch()) }, 'Auto Match'),
    React.createElement('button', { onClick: () => show(opsClient.finance()) }, 'Finance Summary'),
    React.createElement('button', { onClick: () => show(opsClient.closeDay(new Date().toISOString().slice(0, 10))) }, 'Close Today'),
    React.createElement('h3', null, 'Provider Sandbox'),
    React.createElement('ul', null, channels.map(channel => React.createElement('li', { key: channel.channelCode },
      React.createElement('span', null, `${channel.displayName || channel.channelCode} ${channel.currencyCode || ''} `),
      React.createElement('button', { onClick: () => show(opsClient.sandbox(channel.channelCode)) }, 'Run Sandbox')
    )))
  );
}
