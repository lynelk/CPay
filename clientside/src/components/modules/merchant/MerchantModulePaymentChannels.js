import React from 'react';
import common from '../../Common';

class MerchantModulePaymentChannels extends React.Component {
  constructor(props) {
    super(props);
    this.state = { channels: [], selected: null, values: {}, message: '' };
  }

  componentDidMount() { this.loadChannels(); }

  async loadChannels() {
    try {
      const response = await fetch(common.base_url + '/api/v2/merchant-self-service/channels', {
        method: 'GET', mode: 'cors', credentials: 'include', headers: { 'Content-Type': 'application/json' }
      });
      const data = await response.json();
      if (!response.ok) { this.setState({ message: data.message || 'Unable to load channels.' }); return; }
      this.setState({ channels: data });
    } catch (error) { this.setState({ message: error.message }); }
  }

  fieldsFor(channelCode) {
    if (channelCode === 'safaricom_mpesa') return ['shortCode', 'consumerKey', 'consumerSecret', 'passKey'];
    if (channelCode === 'airtel_open_api') return ['clientId', 'clientSecret', 'subscriberMsisdn'];
    return ['apiUser', 'apiKey', 'collectionAccount'];
  }

  select(channel) { this.setState({ selected: channel, values: {}, message: '' }); }
  update(field, value) { this.setState({ values: { ...this.state.values, [field]: value } }); }

  async save() {
    await this.call('/api/v2/merchant-self-service/channels/save', {
      channelCode: this.state.selected.channelCode, environment: 'SANDBOX', credentials: this.state.values
    }, 'Channel details saved.');
  }

  async test() {
    await this.call('/api/v2/merchant-self-service/channels/test', {
      channelCode: this.state.selected.channelCode, environment: 'SANDBOX'
    }, 'Sandbox readiness check completed.');
  }

  async submitForApproval() {
    await this.call('/api/v2/merchant-self-service/channels/submit', {
      channelCode: this.state.selected.channelCode, environment: 'SANDBOX'
    }, 'Channel submitted for approval.');
  }

  async call(path, body, successMessage) {
    try {
      const response = await fetch(common.base_url + path, {
        method: 'POST', mode: 'cors', credentials: 'include', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body)
      });
      const data = await response.json();
      if (!response.ok) { this.setState({ message: data.message || 'Action failed.' }); return; }
      this.setState({ message: successMessage, selected: data });
      this.loadChannels();
    } catch (error) { this.setState({ message: error.message }); }
  }

  renderSelected() {
    const selected = this.state.selected;
    if (!selected) return <p>Select a provider to begin setup.</p>;
    return (
      <div style={{ padding: 16, border: '1px solid #ddd', borderRadius: 6 }}>
        <h3>{selected.displayName}</h3>
        <p><strong>Status:</strong> {selected.status || 'NOT_CONFIGURED'}</p>
        <p><strong>Environment:</strong> SANDBOX</p>
        <p>Enter the provider setup values for this channel. Saved values are stored securely and only masked values are shown later.</p>
        {this.fieldsFor(selected.channelCode).map(field => (
          <label key={field} style={{ display: 'block', marginBottom: 10 }}>
            <span style={{ display: 'block', fontWeight: 600 }}>{field}</span>
            <input value={this.state.values[field] || ''} onChange={e => this.update(field, e.target.value)} style={{ width: '100%', padding: 8, border: '1px solid #ccc', borderRadius: 4 }} />
          </label>
        ))}
        <button onClick={this.save.bind(this)} style={{ padding: '8px 12px', marginRight: 8 }}>Save</button>
        <button onClick={this.test.bind(this)} style={{ padding: '8px 12px', marginRight: 8 }}>Test</button>
        <button onClick={this.submitForApproval.bind(this)} style={{ padding: '8px 12px' }}>Submit</button>
        {selected.credentials ? <pre style={{ marginTop: 12 }}>{JSON.stringify(selected.credentials, null, 2)}</pre> : null}
      </div>
    );
  }

  render() {
    return (
      <div style={{ padding: 10 }}>
        <h2>Payment Channels</h2>
        <p>Connect and manage channels such as MTN, Airtel, Airtel OpenAPI, and Safaricom.</p>
        {this.state.message ? <p style={{ padding: 10, background: '#f5f5f5' }}>{this.state.message}</p> : null}
        <div style={{ display: 'flex', gap: 16 }}>
          <div style={{ width: 300 }}>
            {this.state.channels.map(channel => (
              <div key={channel.channelCode} onClick={() => this.select(channel)} style={{ padding: 12, marginBottom: 8, border: '1px solid #ddd', cursor: 'pointer', borderRadius: 6 }}>
                <strong>{channel.displayName}</strong><br />
                <span>{channel.countryCode} {channel.currencyCode}</span><br />
                <span>Status: {channel.status || 'NOT_CONFIGURED'}</span>
              </div>
            ))}
          </div>
          <div style={{ flex: 1 }}>{this.renderSelected()}</div>
        </div>
      </div>
    );
  }
}

export default MerchantModulePaymentChannels;
