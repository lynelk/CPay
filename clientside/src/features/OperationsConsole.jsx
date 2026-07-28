import React, { useEffect, useState } from 'react';
import { v2Client } from '../shared/api/v2Client';

export default function OperationsConsole() {
  const [channels, setChannels] = useState([]);
  const [message, setMessage] = useState('');

  useEffect(() => {
    v2Client.channels().then(setChannels).catch(error => setMessage(error.message));
  }, []);

  return React.createElement('section', null,
    React.createElement('h2', null, 'Operations Console'),
    message ? React.createElement('p', null, message) : null,
    React.createElement('button', { onClick: () => v2Client.runCallbacks().then(result => setMessage(String(result))) }, 'Run Callbacks'),
    React.createElement('button', { onClick: () => v2Client.autoMatch().then(result => setMessage(String(result))) }, 'Auto Match'),
    React.createElement('ul', null, channels.map(channel => React.createElement('li', { key: channel.channelCode }, `${channel.displayName || channel.channelCode} ${channel.currencyCode || ''}`)))
  );
}
