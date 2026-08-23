import React from 'react';
import { useNavigate } from 'react-router-dom';
import strings from './locale';
import ForgotPasswordMerchant from './LoginForgotPasswordMerchant';
import { AuthLayout, TextField, PasswordField, Button, Alert } from '../ui';
import type { AuthAsideBenefit, AuthAsideCard } from '../ui/AuthLayout';

import { apiFetch } from '../shared/api/httpClient';
import { apiUrl } from '../shared/config';

type MerchantLoginAppearance = Record<string, string>;

const defaultAppearance: MerchantLoginAppearance = {
  merchant_login_hero_image_url:
    'https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?auto=format&fit=crop&w=1600&q=80',
  merchant_login_hero_title: 'Merchant operations workspace',
  merchant_login_hero_copy: 'Secure access to payments, insights, and support in one place.',
  merchant_login_payments_title: 'Payments',
  merchant_login_payments_status: 'Successful',
  merchant_login_payments_detail: 'UGX 250,000',
  merchant_login_communication_title: 'Communication',
  merchant_login_communication_detail: 'New message',
  merchant_login_verification_title: 'Verification',
  merchant_login_verification_detail: 'Identity verified',
  merchant_login_insights_title: 'Insights',
  merchant_login_insights_detail: '+28% this month',
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

function setting(appearance: MerchantLoginAppearance, key: string): string {
  const value = appearance[key]?.trim();
  return value || defaultAppearance[key] || '';
}

async function getMerchantLoginAppearance(): Promise<MerchantLoginAppearance> {
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
    throw new Error(res.message || 'Unable to load login appearance.');
  }
  return { ...defaultAppearance, ...(res.settings as MerchantLoginAppearance) };
}

async function isLoggedIn(): Promise<boolean> {
  try {
    const response = await apiFetch(apiUrl('/auth/isMerchantUserLoggedIn'), {
      method: 'POST',
      mode: 'cors',
      cache: 'no-cache',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      redirect: 'follow',
      referrerPolicy: 'no-referrer',
      body: JSON.stringify({}),
    });
    const res = await response.json();
    return res.code === '000' && res.message === 'true';
  } catch {
    return false;
  }
}

function LoginMerchant(): React.ReactElement {
  const navigate = useNavigate();
  const [accountNumber, setAccountNumber] = React.useState('');
  const [username, setUsername] = React.useState('');
  const [password, setPassword] = React.useState('');
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState('');
  const [showForgot, setShowForgot] = React.useState(false);
  const [unverified, setUnverified] = React.useState(false);
  const [appearance, setAppearance] = React.useState<MerchantLoginAppearance>(defaultAppearance);

  React.useEffect(() => {
    let active = true;
    const uiportal = new URL(window.location.href).searchParams.get('uiportal');
    if (uiportal === 'portal') {
      navigate('/login?realm=platform', { replace: true });
      return;
    }
    isLoggedIn().then((ok) => {
      if (active && ok) navigate('/dashboardMerchant');
    });
    return () => {
      active = false;
    };
  }, [navigate]);

  React.useEffect(() => {
    let active = true;
    getMerchantLoginAppearance()
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

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setError('');
    setUnverified(false);
    if (!accountNumber || !username || !password) {
      setError(strings.merchant_login_required);
      return;
    }
    setLoading(true);
    try {
      const response = await apiFetch(apiUrl('/auth/authenticateMerchantUser'), {
        method: 'POST',
        mode: 'cors',
        cache: 'no-cache',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        redirect: 'follow',
        referrerPolicy: 'no-referrer',
        body: JSON.stringify({ username, password, account_number: accountNumber }),
      });
      const res = JSON.parse(await response.text());
      if (res.code === '000') {
        localStorage.setItem('merchantUser', JSON.stringify(res.user));
        navigate('/dashboardMerchant');
      } else {
        setError(res.message || `${strings.sign_in_failed} (${res.code}).`);
        setUnverified(res.code === '147');
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : `${strings.sign_in_failed}.`);
    } finally {
      setLoading(false);
    }
  }

  const invalid = Boolean(error) && (!accountNumber || !username || !password);
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
      className="ios-auth-merchant"
      title={strings.merchant_title}
      subtitle={strings.merchant_access_subtitle}
      asideTitle={setting(appearance, 'merchant_login_hero_title')}
      asideCopy={setting(appearance, 'merchant_login_hero_copy')}
      asideVariant="media"
      asideImageUrl={setting(appearance, 'merchant_login_hero_image_url')}
      asideImageAlt="Merchant payment workspace"
      asideCards={asideCards}
      asideBenefits={asideBenefits}
      footer={`© ${new Date().getFullYear()} Core-Synergies`}
    >
      <form className="ios-form" onSubmit={handleSubmit} noValidate>
        {error ? <Alert variant="error">{error}</Alert> : null}
        <TextField
          id="merchant-account"
          label={strings.merchant_account_label}
          value={accountNumber}
          onValueChange={setAccountNumber}
          autoComplete="off"
          invalid={invalid}
        />
        <TextField
          id="merchant-username"
          label={strings.username_label}
          value={username}
          onValueChange={setUsername}
          autoComplete="username"
          invalid={invalid}
        />
        <PasswordField
          id="merchant-password"
          label={strings.password_label}
          value={password}
          onValueChange={setPassword}
          invalid={invalid}
        />
        {unverified ? (
          <div className="ios-verify-prompt">
            <Button
              type="button"
              variant="primary"
              onClick={() =>
                navigate('/verify-email', {
                  state: { accountNumber, email: username },
                })
              }
            >
              {strings.verify_email_link}
            </Button>
          </div>
        ) : null}
        <div className="ios-actions">
          <Button type="submit" variant="primary" loading={loading} loadingLabel={strings.signing_in}>
            {strings.sign_in}
          </Button>
          <Button type="button" variant="link" onClick={() => navigate('/signup')}>
            {strings.create_merchant_account}
          </Button>
          <Button type="button" variant="link" onClick={() => setShowForgot(true)}>
            {strings.forgot_password_link}
          </Button>
        </div>
      </form>
      <ForgotPasswordMerchant
        merchantNumber={accountNumber}
        onCloseDialog={() => setShowForgot(false)}
        showForgotPassword={showForgot}
      />
    </AuthLayout>
  );
}

export default LoginMerchant;
