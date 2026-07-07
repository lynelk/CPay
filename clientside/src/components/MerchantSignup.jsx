import React from 'react';
import { withRouter } from 'react-router-dom';
import common from './Common';
import AuthShell from './AuthShell';

class MerchantSignupC extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      loading: false,
      message: '',
      result: null,
      form: {
        businessName: '',
        shortName: '',
        accountType: 'BUSINESS',
        contactName: '',
        email: '',
        phone: '',
        password: ''
      }
    };
  }

  change(field, value) {
    this.setState({ form: { ...this.state.form, [field]: value } });
  }

  async submit(event) {
    event.preventDefault();
    this.setState({ loading: true, message: '', result: null });
    try {
      const response = await fetch(common.base_url + '/api/v2/merchant-self-service/signup', {
        method: 'POST',
        mode: 'cors',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(this.state.form)
      });
      const data = await response.json();
      if (!response.ok || data.code !== '000') {
        this.setState({ loading: false, message: data.message || 'Signup could not be completed.' });
        return;
      }
      this.setState({ loading: false, result: data, message: data.message });
    } catch (error) {
      this.setState({ loading: false, message: error.message });
    }
  }

  input(label, field, type) {
    return (
      <label className="cpay-auth-native-field">
        <span>{label}</span>
        <input
          type={type || 'text'}
          value={this.state.form[field]}
          onChange={e => this.change(field, e.target.value)}
          required
        />
      </label>
    );
  }

  accountType() {
    return (
      <label className="cpay-auth-native-field">
        <span>Account type</span>
        <select value={this.state.form.accountType} onChange={e => this.change('accountType', e.target.value)} required>
          <option value="BUSINESS">Business</option>
          <option value="PERSONAL">Personal</option>
        </select>
      </label>
    );
  }

  render() {
    const { history } = this.props;
    const hasMessage = Boolean(this.state.message);
    return (
      <AuthShell
        className="cpay-auth-signup"
        title="Create merchant account"
        subtitle="Self-service onboarding"
        asideTitle="Merchant onboarding"
        asideCopy="Create access for collections, payouts, and notifications."
        footer="Copyright (c) 2019"
      >
        {hasMessage ? <div className={`cpay-auth-message ${this.state.result ? 'cpay-auth-message-success' : 'cpay-auth-message-error'}`}>{this.state.message}</div> : null}

        {this.state.result ? (
          <div className="cpay-auth-result">
            <h2>Registration submitted</h2>
            <dl>
              <div>
                <dt>Merchant account number</dt>
                <dd>{this.state.result.accountNumber}</dd>
              </div>
              <div>
                <dt>Status</dt>
                <dd>{this.state.result.merchantStatus}</dd>
              </div>
            </dl>
            <p>Use this account number with your email and password to log in.</p>
            <button className="cpay-auth-native-primary" onClick={() => history.push('/')}>Go to Login</button>
          </div>
        ) : (
          <form className="cpay-signup-form" onSubmit={this.submit.bind(this)}>
            <div className="cpay-signup-grid">
              {this.input('Business name', 'businessName')}
              {this.input('Short name', 'shortName')}
              {this.accountType()}
              {this.input('Primary contact', 'contactName')}
              {this.input('Email address', 'email', 'email')}
              {this.input('Phone number', 'phone')}
              {this.input('Password', 'password', 'password')}
            </div>
            <div className="cpay-auth-native-actions">
              <button className="cpay-auth-native-primary" disabled={this.state.loading} type="submit">
                {this.state.loading ? 'Submitting...' : 'Create Merchant Account'}
              </button>
              <button className="cpay-auth-native-secondary" type="button" onClick={() => history.push('/')}>Back to Login</button>
            </div>
          </form>
        )}
      </AuthShell>
    );
  }
}

const MerchantSignup = withRouter(MerchantSignupC);
export default MerchantSignup;
