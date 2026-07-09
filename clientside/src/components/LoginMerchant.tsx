import React from 'react';
import { useNavigate } from 'react-router-dom';
import common from './Common';
import strings from './locale';
import ForgotPasswordMerchant from './LoginForgotPasswordMerchant';
import { AuthLayout, TextField, PasswordField, Button, Alert } from '../ui';

async function isLoggedIn(): Promise<boolean> {
  try {
    const response = await fetch(common.base_url + '/auth/isMerchantUserLoggedIn', {
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

  React.useEffect(() => {
    let active = true;
    const uiportal = new URL(window.location.href).searchParams.get('uiportal');
    if (uiportal === 'portal') {
      navigate('/portal');
      return;
    }
    isLoggedIn().then((ok) => {
      if (active && ok) navigate('/dashboardMerchant');
    });
    return () => {
      active = false;
    };
  }, [navigate]);

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setError('');
    if (!accountNumber || !username || !password) {
      setError('Enter your merchant account, username, and password.');
      return;
    }
    setLoading(true);
    try {
      const response = await fetch(common.base_url + '/auth/authenticateMerchantUser', {
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
        setError(res.message || `Sign in failed (${res.code}).`);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Sign in failed.');
    } finally {
      setLoading(false);
    }
  }

  const invalid = Boolean(error) && (!accountNumber || !username || !password);

  return (
    <AuthLayout
      className="ios-auth-merchant"
      title={strings.merchant_title}
      subtitle="Merchant access"
      asideTitle="Merchant workspace"
      asideCopy="Account access, balances, and activity in one place."
      footer={`© ${new Date().getFullYear()} CPay`}
    >
      <form className="ios-form" onSubmit={handleSubmit} noValidate>
        {error ? <Alert variant="error">{error}</Alert> : null}
        <TextField
          id="merchant-account"
          label="Merchant account"
          value={accountNumber}
          onValueChange={setAccountNumber}
          autoComplete="off"
          invalid={invalid}
        />
        <TextField
          id="merchant-username"
          label="Username"
          value={username}
          onValueChange={setUsername}
          autoComplete="username"
          invalid={invalid}
        />
        <PasswordField
          id="merchant-password"
          label="Password"
          value={password}
          onValueChange={setPassword}
          invalid={invalid}
        />
        <div className="ios-actions">
          <Button type="submit" variant="primary" loading={loading} loadingLabel="Signing in…">
            Sign in
          </Button>
          <Button type="button" variant="link" onClick={() => navigate('/signup')}>
            Create merchant account
          </Button>
          <Button type="button" variant="link" onClick={() => setShowForgot(true)}>
            Forgot my password?
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
