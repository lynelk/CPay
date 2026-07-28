import React from 'react';
import common from '../../Common';
import { Badge, Button, Card, TextField, Toolbar } from '../../../ui';

import { apiFetch } from '../../../shared/api/httpClient';

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

const ENVIRONMENTS = [
  { id: 'SANDBOX', label: 'Sandbox', copy: 'Use guided credentials and deterministic test numbers.' },
  { id: 'PRODUCTION', label: 'Production', copy: 'Use live credentials after approval. Daily limit applies.' },
];

function statusTone(status) {
  if (status === 'APPROVED' || status === 'READY' || status === 'ACTIVE' || status === 'SANDBOX_TESTED') return 'success';
  if (status === 'PENDING' || status === 'SUBMITTED' || status === 'SUBMITTED_FOR_APPROVAL' || status === 'CONFIGURED') return 'warning';
  if (status === 'REJECTED' || status === 'FAILED') return 'danger';
  return 'neutral';
}

function environmentRecord(channel, environment) {
  const records = channel?.environments || {};
  return {
    ...channel,
    ...(records[environment] || {}),
    environments: records,
    sandboxCredentials: channel?.sandboxCredentials,
    sandboxGuide: channel?.sandboxGuide,
  };
}

class MerchantModulePaymentChannelsV2 extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      channels: [],
      selected: null,
      values: {},
      environment: 'SANDBOX',
      environmentStatus: null,
      sandboxGuide: null,
      message: '',
      loading: false,
    };
  }

  componentDidMount() {
    this.loadPage();
  }

  componentDidUpdate(prevProps) {
    if (prevProps.refreshSignal !== this.props.refreshSignal) {
      this.loadPage(true);
    }
  }

  async loadPage(silent = false) {
    if (!silent) this.setState({ loading: true });
    const environment = await this.loadEnvironment();
    await this.loadChannels(environment || this.state.environment);
  }

  async loadEnvironment() {
    try {
      const response = await apiFetch(common.base_url + '/api/v2/merchant-self-service/environment', {
        method: 'GET', mode: 'cors', credentials: 'include', headers: { 'Content-Type': 'application/json' },
      });
      const data = await response.json();
      if (!response.ok) {
        this.setState({ message: data.message || 'Unable to load environment status.', loading: false });
        return this.state.environment;
      }
      const environment = data.environment || 'SANDBOX';
      this.setState({
        environment,
        environmentStatus: data,
        sandboxGuide: data.sandbox || null,
      });
      return environment;
    } catch (error) {
      this.setState({ message: error.message, loading: false });
      return this.state.environment;
    }
  }

  async loadChannels(environment = this.state.environment) {
    this.setState({ loading: true });
    try {
      const response = await apiFetch(common.base_url + '/api/v2/merchant-self-service/channels', {
        method: 'GET', mode: 'cors', credentials: 'include', headers: { 'Content-Type': 'application/json' },
      });
      const data = await response.json();
      if (!response.ok) {
        this.setState({ message: data.message || 'Unable to load channels.', loading: false });
        return;
      }
      const channels = Array.isArray(data) ? data : [];
      const selectedCode = this.state.selected?.channelCode || channels[0]?.channelCode;
      const baseSelected = channels.find(channel => channel.channelCode === selectedCode) || null;
      const selected = baseSelected ? environmentRecord(baseSelected, environment) : null;
      this.setState({
        channels,
        selected,
        values: selected?.credentials || {},
        loading: false,
      });
    } catch (error) {
      this.setState({ message: error.message, loading: false });
    }
  }

  async setEnvironment(environment) {
    if (environment === this.state.environment) return;
    this.setState({ loading: true, message: '' });
    try {
      const response = await apiFetch(common.base_url + '/api/v2/merchant-self-service/environment', {
        method: 'POST', mode: 'cors', credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ environment }),
      });
      const data = await response.json();
      if (!response.ok) {
        this.setState({ message: data.message || 'Unable to switch environment.', loading: false });
        return;
      }
      this.setState({
        environment: data.environment || environment,
        environmentStatus: data,
        sandboxGuide: data.sandbox || this.state.sandboxGuide,
      }, () => this.loadChannels(data.environment || environment));
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
    const selected = environmentRecord(channel, this.state.environment);
    this.setState({ selected, values: selected.credentials || {}, message: '' });
  }

  update(field, value) {
    this.setState((prev) => ({ values: { ...prev.values, [field]: value } }));
  }

  applySandboxCredentials() {
    if (!this.state.selected) return;
    this.setState({ values: { ...(this.state.selected.sandboxCredentials || {}) }, message: 'Sandbox credentials loaded for local testing.' });
  }

  async save() {
    if (!this.state.selected) return;
    await this.call('/api/v2/merchant-self-service/channels/save', {
      channelCode: this.state.selected.channelCode,
      environment: this.state.environment,
      credentials: this.state.values,
    }, `${this.state.environment} channel details saved.`);
  }

  async test() {
    if (!this.state.selected) return;
    await this.call('/api/v2/merchant-self-service/channels/test', {
      channelCode: this.state.selected.channelCode,
      environment: this.state.environment,
    }, `${this.state.environment} readiness check completed.`);
  }

  async submitForApproval() {
    if (!this.state.selected) return;
    await this.call('/api/v2/merchant-self-service/channels/submit', {
      channelCode: this.state.selected.channelCode,
      environment: this.state.environment,
    }, `${this.state.environment} channel submitted for approval.`);
  }

  async call(path, body, successMessage) {
    this.setState({ loading: true, message: '' });
    try {
      const response = await apiFetch(common.base_url + path, {
        method: 'POST', mode: 'cors', credentials: 'include',
        headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
      });
      const data = await response.json();
      if (!response.ok) { this.setState({ message: data.message || 'Action failed.', loading: false }); return; }
      this.setState({ message: successMessage, loading: false });
      await this.loadChannels(this.state.environment);
    } catch (error) {
      this.setState({ message: error.message, loading: false });
    }
  }

  renderEnvironmentSwitch() {
    const limit = this.state.environmentStatus?.productionLimit || {};
    return (
      <div className="ios-channel-env-panel">
        <div className="ios-channel-env-switch" role="tablist" aria-label="Gateway environment">
          {ENVIRONMENTS.map(option => (
            <button
              key={option.id}
              type="button"
              role="tab"
              aria-selected={this.state.environment === option.id}
              onClick={() => this.setEnvironment(option.id)}
            >
              <strong>{option.label}</strong>
              <span>{option.copy}</span>
            </button>
          ))}
        </div>
        <div className="ios-channel-env-limit">
          <strong>Production limit</strong>
          <span>
            {limit.enabled === false
              ? 'Disabled'
              : `${limit.remainingToday ?? limit.limit ?? 10} of ${limit.limit ?? 10} live transactions remaining today`}
          </span>
        </div>
      </div>
    );
  }

  renderSandboxGuide() {
    const guide = this.state.sandboxGuide;
    if (!guide || this.state.environment !== 'SANDBOX') return null;
    const testAccounts = Array.isArray(guide.testAccounts) ? guide.testAccounts.slice(0, 6) : [];
    const envVars = guide.environmentVariables || {};
    return (
      <Card className="ios-channel-guide">
        <div>
          <span className="ios-channel-eyebrow">Developer sandbox</span>
          <h3 className="ios-section-title">First integration checklist</h3>
          <p className="ios-channel-subtitle">Use these values with the API guide to send your first signed collection or payout.</p>
        </div>
        <div className="ios-channel-guide-grid">
          <div>
            <strong>Base URL</strong>
            <code>{guide.sandboxBaseUrl}</code>
          </div>
          <div>
            <strong>Merchant number</strong>
            <code>{guide.merchantNumber}</code>
          </div>
          <div>
            <strong>Idempotency</strong>
            <code>{guide.idempotencyWindowHours || 24} hours</code>
          </div>
        </div>
        <div className="ios-channel-envvars">
          {Object.entries(envVars).slice(0, 6).map(([key, value]) => (
            <span key={key}><strong>{key}</strong><code>{String(value)}</code></span>
          ))}
        </div>
        <div className="ios-channel-test-list">
          {testAccounts.map(account => (
            <span key={account.account}>
              <code>{account.account}</code>
              <strong>{account.description}</strong>
              <em>{account.expected}</em>
            </span>
          ))}
        </div>
      </Card>
    );
  }

  renderSelected() {
    const selected = this.state.selected;
    if (!selected) {
      return (
        <Card className="ios-channel-empty">
          <h3 className="ios-section-title">Select a provider</h3>
          <p>Choose a channel to configure sandbox credentials, test readiness, and submit for production approval.</p>
        </Card>
      );
    }

    return (
      <Card className="ios-channel-panel">
        <Toolbar>
          <div>
            <h3 className="ios-section-title" style={{ margin: 0 }}>{selected.displayName}</h3>
            <p className="ios-channel-subtitle">{selected.countryCode} {selected.currencyCode} - {this.state.environment}</p>
          </div>
          <Toolbar.Spacer />
          <Badge tone={statusTone(selected.status)}>{selected.status || 'NOT_CONFIGURED'}</Badge>
        </Toolbar>
        {this.state.environment === 'SANDBOX' ? (
          <div className="ios-channel-sandbox-callout">
            <div>
              <strong>Guided sandbox mode</strong>
              <span>Blank endpoints use CPay's deterministic simulator; add URLs only when testing your own callback receiver.</span>
            </div>
            <Button variant="ghost" className="ios-btn--sm" onClick={() => this.applySandboxCredentials()}>Use sandbox credentials</Button>
          </div>
        ) : null}
        <div className="ios-channel-form">
          {this.fieldsFor(selected.channelCode).map((field) => (
            <TextField
              key={field}
              id={`channel-${this.state.environment}-${field}`}
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
          <Button variant="secondary" className="ios-btn--sm" loading={this.state.loading} onClick={() => this.submitForApproval()}>
            {this.state.environment === 'PRODUCTION' ? 'Submit approval' : 'Mark ready'}
          </Button>
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
            <p className="ios-channel-subtitle">Connect MTN, Airtel, M-Pesa, Yo! Payments, and other active settlement channels.</p>
          </div>
          <Toolbar.Spacer />
          <Button variant="ghost" className="ios-btn--sm" loading={this.state.loading} onClick={() => this.loadPage()}>Refresh cards</Button>
        </Toolbar>
        {this.renderEnvironmentSwitch()}
        {this.state.message ? <div className="ios-channel-message">{this.state.message}</div> : null}
        <div className="ios-channel-layout">
          <div className="ios-channel-list" aria-label="Payment channels">
            {this.state.channels.map((channel) => {
              const environmentChannel = environmentRecord(channel, this.state.environment);
              return (
                <button
                  key={channel.channelCode}
                  type="button"
                  className={`ios-channel-option ${this.state.selected?.channelCode === channel.channelCode ? 'ios-channel-option--active' : ''}`.trim()}
                  onClick={() => this.select(channel)}
                >
                  <strong>{channel.displayName}</strong>
                  <span>{channel.countryCode} {channel.currencyCode} - {this.state.environment}</span>
                  <Badge tone={statusTone(environmentChannel.status)}>{environmentChannel.status || 'NOT_CONFIGURED'}</Badge>
                </button>
              );
            })}
          </div>
          <div className="ios-channel-detail">
            {this.renderSelected()}
            {this.renderSandboxGuide()}
          </div>
        </div>
      </Card>
    );
  }
}

export default MerchantModulePaymentChannelsV2;
