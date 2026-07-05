import React from 'react';
import { withRouter } from 'react-router-dom';
import common from './Common';
import Logo from '../media/images/gwlogo.png';

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
      <label style={{ display: 'block', marginBottom: 12 }}>
        <span style={{ display: 'block', fontWeight: 600, marginBottom: 4 }}>{label}</span>
        <input
          type={type || 'text'}
          value={this.state.form[field]}
          onChange={e => this.change(field, e.target.value)}
          style={{ width: '100%', padding: 10, border: '1px solid #ccc', borderRadius: 4 }}
          required
        />
      </label>
    );
  }

  render() {
    const { history } = this.props;
    return (
      <div style={{ maxWidth: 720, margin: '40px auto', padding: 24, border: '1px solid #ddd', borderRadius: 8 }}>
        <div style={{ textAlign: 'center', marginBottom: 20 }}>
          <img src={Logo} alt="CPay" style={{ width: 180 }} />
          <h2>Register your merchant account</h2>
          <p>Submit your business details, create your first administrator, then configure your payment channels after login.</p>
        </div>
        {this.state.message ? <p style={{ padding: 10, background: '#f5f5f5' }}>{this.state.message}</p> : null}
        {this.state.result ? (
          <div style={{ padding: 16, background: '#f8fafc', border: '1px solid #ddd', borderRadius: 6 }}>
            <h3>Registration submitted</h3>
            <p><strong>Merchant account number:</strong> {this.state.result.accountNumber}</p>
            <p><strong>Status:</strong> {this.state.result.merchantStatus}</p>
            <p>Use this account number with your email and password to log in. Production payments remain subject to approval.</p>
            <button onClick={() => history.push('/')} style={{ padding: '10px 16px' }}>Go to Login</button>
          </div>
        ) : (
          <form onSubmit={this.submit.bind(this)}>
            {this.input('Business name', 'businessName')}
            {this.input('Short name', 'shortName')}
            {this.input('Primary contact name', 'contactName')}
            {this.input('Email address', 'email', 'email')}
            {this.input('Phone number', 'phone')}
            {this.input('Password', 'password', 'password')}
            <button disabled={this.state.loading} type="submit" style={{ padding: '10px 16px', marginRight: 8 }}>
              {this.state.loading ? 'Submitting...' : 'Create Merchant Account'}
            </button>
            <button type="button" onClick={() => history.push('/')} style={{ padding: '10px 16px' }}>Back to Login</button>
          </form>
        )}
      </div>
    );
  }
}

const MerchantSignup = withRouter(MerchantSignupC);
export default MerchantSignup;
