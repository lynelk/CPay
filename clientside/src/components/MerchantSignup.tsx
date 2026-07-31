import React from 'react';
import { useNavigate } from 'react-router-dom';
import { AuthLayout, TextField, PasswordField, Button, Alert } from '../ui';
import type { AuthAsideBenefit, AuthAsideCard } from '../ui/AuthLayout';

import { apiFetch } from '../shared/api/httpClient';
import { apiUrl } from '../shared/config';

type MerchantSignupAppearance = Record<string, string>;

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

const defaultAppearance: MerchantSignupAppearance = {
  merchant_login_hero_image_url:
    'https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?auto=format&fit=crop&w=1600&q=80',
  merchant_login_hero_title: 'Merchant operations workspace',
  merchant_login_hero_copy: 'Create secure access for collections, payouts, and notifications.',
  merchant_login_payments_title: 'Payments',
  merchant_login_payments_status: 'Ready',
  merchant_login_payments_detail: 'Collections and payouts',
  merchant_login_communication_title: 'Communication',
  merchant_login_communication_detail: 'Alerts and SMS',
  merchant_login_verification_title: 'Verification',
  merchant_login_verification_detail: 'KYC guided setup',
  merchant_login_insights_title: 'Insights',
  merchant_login_insights_detail: 'Operational reports',
  merchant_login_support_title: 'Support',
  merchant_login_support_detail: "We're here to help",
  merchant_login_secure_title: 'Secure Platform',
  merchant_login_secure_copy: 'Enterprise-grade protection',
  merchant_login_benefit_insights_title: 'Real-time Insights',
  merchant_login_benefit_insights_copy: 'Data-driven decisions',
  merchant_login_control_title: 'Operational Control',
  merchant_login_control_copy: 'Manage with confidence',
  merchant_login_automation_title: 'Automation Ready',
  merchant_login_automation_copy: 'Powerful tools for efficiency',
  merchant_login_reliable_title: 'Reliable & Scalable',
  merchant_login_reliable_copy: 'Built for growth and trust',
};

function setting(appearance: MerchantSignupAppearance, key: string): string {
  const value = appearance[key]?.trim();
  return value || defaultAppearance[key] || '';
}

async function getSignupAppearance(): Promise<MerchantSignupAppearance> {
  const response = await apiFetch(apiUrl('/settings/public-login-appearance'), {
    method: 'GET',
    mode: 'cors',
    cache: 'no-cache',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    redirect: 'follow',
    referrerPolicy: 'no-referrer',
  });
  const res = await response.json();
  if (res.code !== '000' || !res.settings || typeof res.settings !== 'object') {
    throw new Error(res.message || 'Unable to load signup appearance.');
  }
  return { ...defaultAppearance, ...(res.settings as MerchantSignupAppearance) };
}

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
  const [appearance, setAppearance] = React.useState<MerchantSignupAppearance>(defaultAppearance);

  React.useEffect(() => {
    let active = true;
    getSignupAppearance()
      .then((settings) => {
        if (active) setAppearance(settings);
      })
      .catch(() => {
        if (active) setAppearance(defaultAppearance);
      });
    return () => {
      active = false;
    };
  }, []);

  function change<K extends keyof SignupForm>(field: K, value: SignupForm[K]) {
    setForm((prev) => ({ ...prev, [field]: value }));
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setLoading(true);
    setMessage('');
    setResult(null);
    try {
      const response = await apiFetch(apiUrl('/api/v2/merchant-self-service/signup'), {
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

  const asideCards: AuthAsideCard[] = [
    {
      id: 'payments',
      icon: 'cards',
      title: setting(appearance, 'merchant_login_payments_title'),
      eyebrow: setting(appearance, 'merchant_login_payments_status'),
      detail: setting(appearance, 'merchant_login_payments_detail'),
      tone: 'success',
    },
    {
      id: 'communication',
      icon: 'message',
      title: setting(appearance, 'merchant_login_communication_title'),
      detail: setting(appearance, 'merchant_login_communication_detail'),
    },
    {
      id: 'verification',
      icon: 'verification',
      title: setting(appearance, 'merchant_login_verification_title'),
      detail: setting(appearance, 'merchant_login_verification_detail'),
      tone: 'success',
    },
    {
      id: 'insights',
      icon: 'insights',
      title: setting(appearance, 'merchant_login_insights_title'),
      detail: setting(appearance, 'merchant_login_insights_detail'),
      tone: 'success',
    },
    {
      id: 'support',
      icon: 'support',
      title: setting(appearance, 'merchant_login_support_title'),
      detail: setting(appearance, 'merchant_login_support_detail'),
    },
  ];

  const asideBenefits: AuthAsideBenefit[] = [
    {
      icon: 'secure',
      title: setting(appearance, 'merchant_login_secure_title'),
      copy: setting(appearance, 'merchant_login_secure_copy'),
    },
    {
      icon: 'insights',
      title: setting(appearance, 'merchant_login_benefit_insights_title'),
      copy: setting(appearance, 'merchant_login_benefit_insights_copy'),
    },
    {
      icon: 'users',
      title: setting(appearance, 'merchant_login_control_title'),
      copy: setting(appearance, 'merchant_login_control_copy'),
    },
    {
      icon: 'fast',
      title: setting(appearance, 'merchant_login_automation_title'),
      copy: setting(appearance, 'merchant_login_automation_copy'),
    },
    {
      icon: 'reliable',
      title: setting(appearance, 'merchant_login_reliable_title'),
      copy: setting(appearance, 'merchant_login_reliable_copy'),
    },
  ];

  return (
    <AuthLayout
      className="ios-auth-merchant ios-auth-signup"
      title="Create merchant account"
      subtitle="Self-service onboarding"
      asideTitle={setting(appearance, 'merchant_login_hero_title')}
      asideCopy={setting(appearance, 'merchant_login_hero_copy')}
      asideVariant="media"
      asideImageUrl={setting(appearance, 'merchant_login_hero_image_url')}
      asideImageAlt="Merchant onboarding workspace"
      asideCards={asideCards}
      asideBenefits={asideBenefits}
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
        <form className="ios-form ios-signup-form" onSubmit={handleSubmit} noValidate>
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
