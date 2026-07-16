import React from 'react';
import { useNavigate } from 'react-router-dom';
import common from './Common';
import { AuthLayout, TextField, PasswordField, Button, Alert } from '../ui';

interface SignupForm {
  businessName: string;
  shortName: string;
  accountType: string;
  contactName: string;
  email: string;
  phone: string;
  password: string;
}

interface SignupResult {
  accountNumber?: string;
  merchantStatus?: string;
  message?: string;
  code?: string;
}

const emptyForm: SignupForm = {
  businessName: '',
  shortName: '',
  accountType: 'BUSINESS',
  contactName: '',
  email: '',
  phone: '',
  password: '',
};

async function readJsonResponse(response: Response): Promise<SignupResult> {
  const text = await response.text();
  if (!text.trim()) {
    return { code: 'EMPTY_RESPONSE', message: 'The server did not return a registration response.' };
  }
  try {
    return JSON.parse(text) as SignupResult;
  } catch {
    return { code: 'INVALID_RESPONSE', message: 'The server returned an invalid registration response.' };
  }
}

function MerchantSignup(): React.ReactElement {
  const navigate = useNavigate();
  const [form, setForm] = React.useState<SignupForm>(emptyForm);
  const [loading, setLoading] = React.useState(false);
  const [message, setMessage] = React.useState('');
  const [result, setResult] = React.useState<SignupResult | null>(null);

  function change<K extends keyof SignupForm>(field: K, value: SignupForm[K]) {
    setForm((prev) => ({ ...prev, [field]: value }));
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setLoading(true);
    setMessage('');
    setResult(null);
    try {
      const response = await fetch(common.base_url + '/api/v2/merchant-self-service/signup', {
        method: 'POST',
        mode: 'cors',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(form),
      });
      const data = await readJsonResponse(response);
      if (!response.ok || data.code !== '000') {
        setMessage(data.message || 'Signup could not be completed.');
        return;
      }
      setResult(data);
      setMessage(data.message || '');
    } catch (err) {
      setMessage(err instanceof Error ? err.message : 'Signup could not be completed.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <AuthLayout
      className="ios-auth-signup"
      title="Create merchant account"
      subtitle="Self-service onboarding"
      asideTitle="Merchant onboarding"
      asideCopy="Create access for collections, payouts, and notifications."
      footer={`© ${new Date().getFullYear()} CPay`}
    >
      {message && !result ? <Alert variant="error">{message}</Alert> : null}

      {result ? (
        <div className="ios-result">
          <h2>Registration submitted</h2>
          <dl>
            <div>
              <dt>Merchant account number</dt>
              <dd>{result.accountNumber}</dd>
            </div>
            <div>
              <dt>Status</dt>
              <dd>{result.merchantStatus}</dd>
            </div>
          </dl>
          <p>Use this account number with your email and password to log in.</p>
          <Button variant="primary" onClick={() => navigate('/')}>
            Go to login
          </Button>
        </div>
      ) : (
        <form className="ios-form" onSubmit={handleSubmit} noValidate>
          <div className="ios-grid">
            <TextField id="su-business" label="Business name" value={form.businessName} onValueChange={(v) => change('businessName', v)} required />
            <TextField id="su-short" label="Short name" value={form.shortName} onValueChange={(v) => change('shortName', v)} required />
            <div className="ios-field">
              <label className="ios-field__label" htmlFor="su-type">Account type</label>
              <select
                id="su-type"
                className="ios-input ios-select"
                value={form.accountType}
                onChange={(e) => change('accountType', e.target.value)}
              >
                <option value="BUSINESS">Business</option>
                <option value="PERSONAL">Personal</option>
              </select>
            </div>
            <TextField id="su-contact" label="Primary contact" value={form.contactName} onValueChange={(v) => change('contactName', v)} required />
            <TextField id="su-email" label="Email address" type="email" value={form.email} onValueChange={(v) => change('email', v)} autoComplete="email" required />
            <TextField id="su-phone" label="Phone number" value={form.phone} onValueChange={(v) => change('phone', v)} autoComplete="tel" required />
            <PasswordField id="su-password" label="Password" value={form.password} onValueChange={(v) => change('password', v)} autoComplete="new-password" />
          </div>
          <div className="ios-actions">
            <Button type="submit" variant="primary" loading={loading} loadingLabel="Submitting…">
              Create merchant account
            </Button>
            <Button type="button" variant="link" onClick={() => navigate('/')}>
              Back to login
            </Button>
          </div>
        </form>
      )}
    </AuthLayout>
  );
}

export default MerchantSignup;
