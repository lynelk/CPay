import React from 'react';
import common from '../../Common';
import { Badge, Button, Card, TextField, Toolbar } from '../../../ui';

const FIELD_LABELS = {
  collectUrl: 'Collection URL',
  payoutUrl: 'Payout URL',
  authHeaderName: 'Auth header name',
  authHeaderValue: 'Auth header value',
  shortCode: 'Short code',
  consumerKey: 'Consumer key',
  consumerSecret: 'Consumer secret',
  passKey: 'Pass key',
  clientId: 'Client ID',
  clientSecret: 'Client secret',
  subscriberMsisdn: 'Subscriber MSISDN',
  apiUser: 'API user',
  apiKey: 'API key',
  collectionAccount: 'Collection account',
};

function statusTone(status) {
  if (status === 'APPROVED' || status === 'READY' || status === 'ACTIVE') return 'success';
  if (status === 'PENDING' || status === 'SUBMITTED') return 'warning';
  if (status === 'REJECTED' || status === 'FAILED') return 'danger';
  return 'neutral';
}

class MerchantModulePaymentChannelsV2 extends React.Component {
  constructor(props) {
    super(props);
    this.state = { channels: [], selected: null, values: {}, message: '', loading: false };
  }

  componentDidMount() { this.loadChannels(); }

  async loadChannels() {
    this.setState({ loading: true });
    try {
      const response = await fetch(common.base_url + '/api/v2/merchant-self-service/channels', {
        method: 'GET', mode: 'cors', credentials: 'include', headers: { 'Content-Type': 'application/json' },
      });
      const data = await response.json();
      if (!response.ok) { this.setState({ message: data.message || 'Unable to load channels.', loading: false }); return; }
      this.setState({ channels: Array.isArray(data) ? data : [], loading: false });
    } catch (error) {
      this.setState({ message: error.message, loading: false });
    }
  }

  fieldsFor(channelCode) {
    const base = ['collectUrl', 'payoutUrl', 'authHeaderName', 'authHeaderValue'];
    if (channelCode === 'safaricom_mpesa') return base.concat(['shortCode', 'consumerKey', 'consumerSecret', 'passKey']);
    if (channelCode === 'airtel_open_api') return base.concat(['clientId', 'clientSecret', 'subscriberMsisdn']);
    return base.concat(['apiUser', 'apiKey', 'collectionAccount']);
  }

  select(channel) {
    this.setState({ selected: channel, values: channel.credentials || {}, message: '' });
  }

  update(field, value) {
    this.setState((prev) => ({ values: { ...prev.values, [field]: value } }));
  }

  async save() {
    if (!this.state.selected) return;
    await this.call('/api/v2/merchant-self-service/channels/save', {
      channelCode: this.state.selected.channelCode,
      environment: 'SANDBOX',
      credentials: this.state.values,
    }, 'Channel details saved.');
  }

  async test() {
    if (!this.state.selected) return;
    await this.call('/api/v2/merchant-self-service/channels/test', {
      channelCode: this.state.selected.channelCode,
      environment: 'SANDBOX',
    }, 'Sandbox readiness check completed.');
  }

  async submitForApproval() {
    if (!this.state.selected) return;
    await this.call('/api/v2/merchant-self-service/channels/submit', {
      channelCode: this.state.selected.channelCode,
      environment: 'SANDBOX',
    }, 'Channel submitted for approval.');
  }

  async call(path, body, successMessage) {
    this.setState({ loading: true, message: '' });
    try {
      const response = await fetch(common.base_url + path, {
        method: 'POST', mode: 'cors', credentials: 'include',
        headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
      });
      const data = await response.json();
      if (!response.ok) { this.setState({ message: data.message || 'Action failed.', loading: false }); return; }
      this.setState({ message: successMessage, selected: data, loading: false });
      this.loadChannels();
    } catch (error) {
      this.setState({ message: error.message, loading: false });
    }
  }

  renderSelected() {
    const selected = this.state.selected;
    if (!selected) {
      return (
        <Card className="ios-channel-empty">
          <h3 className="ios-section-title">Select a provider</h3>
          <p>Choose a channel to configure sandbox credentials, test readiness, and submit for approval.</p>
        </Card>
      );
    }

    return (
      <Card className="ios-channel-panel">
        <Toolbar>
          <div>
            <h3 className="ios-section-title" style={{ margin: 0 }}>{selected.displayName}</h3>
            <p className="ios-channel-subtitle">{selected.countryCode} {selected.currencyCode} - SANDBOX</p>
          </div>
          <Toolbar.Spacer />
          <Badge tone={statusTone(selected.status)}>{selected.status || 'NOT_CONFIGURED'}</Badge>
        </Toolbar>
        <div className="ios-channel-form">
          {this.fieldsFor(selected.channelCode).map((field) => (
            <TextField
              key={field}
              id={`channel-${field}`}
              label={FIELD_LABELS[field] || field}
              value={this.state.values[field] || ''}
              onValueChange={(value) => this.update(field, value)}
              autoComplete="off"
            />
          ))}
        </div>
        <Toolbar>
          <Button variant="primary" className="ios-btn--sm" loading={this.state.loading} onClick={() => this.save()}>Save</Button>
          <Button variant="ghost" className="ios-btn--sm" loading={this.state.loading} onClick={() => this.test()}>Test</Button>
          <Button variant="secondary" className="ios-btn--sm" loading={this.state.loading} onClick={() => this.submitForApproval()}>Submit</Button>
        </Toolbar>
      </Card>
    );
  }

  render() {
    return (
      <Card className="ios-channel-page">
        <Toolbar>
          <div>
            <h2 className="ios-section-title" style={{ margin: 0 }}>Payment Channels</h2>
            <p className="ios-channel-subtitle">Connect MTN, Airtel, Safaricom, and similar settlement channels.</p>
          </div>
          <Toolbar.Spacer />
          <Button variant="ghost" className="ios-btn--sm" loading={this.state.loading} onClick={() => this.loadChannels()}>Refresh</Button>
        </Toolbar>
        {this.state.message ? <div className="ios-channel-message">{this.state.message}</div> : null}
        <div className="ios-channel-layout">
          <div className="ios-channel-list" aria-label="Payment channels">
            {this.state.channels.map((channel) => (
              <button
                key={channel.channelCode}
                type="button"
                className={`ios-channel-option ${this.state.selected?.channelCode === channel.channelCode ? 'ios-channel-option--active' : ''}`.trim()}
                onClick={() => this.select(channel)}
              >
                <strong>{channel.displayName}</strong>
                <span>{channel.countryCode} {channel.currencyCode}</span>
                <Badge tone={statusTone(channel.status)}>{channel.status || 'NOT_CONFIGURED'}</Badge>
              </button>
            ))}
          </div>
          <div className="ios-channel-detail">{this.renderSelected()}</div>
        </div>
      </Card>
    );
  }
}

export default MerchantModulePaymentChannelsV2;
