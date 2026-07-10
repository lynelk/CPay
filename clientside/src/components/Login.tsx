import React from 'react';
import { useNavigate } from 'react-router-dom';
import common from './Common';
import strings from './locale';
import ForgotPassword from './LoginForgotPassword';
import { AuthLayout, TextField, PasswordField, Button, Alert } from '../ui';

async function isLoggedIn(): Promise<boolean> {
  try {
    const response = await fetch(common.base_url + '/auth/isLoggedIn', {
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

function Login(): React.ReactElement {
  const navigate = useNavigate();
  const [username, setUsername] = React.useState('');
  const [password, setPassword] = React.useState('');
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState('');
  const [showForgot, setShowForgot] = React.useState(false);

  React.useEffect(() => {
    let active = true;
    isLoggedIn().then((ok) => {
      if (active && ok) navigate('/dashboard');
    });
    return () => {
      active = false;
    };
  }, [navigate]);

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setError('');
    if (!username || !password) {
      setError('Enter your username and password.');
      return;
    }
    setLoading(true);
    try {
      const response = await fetch(common.base_url + '/auth/authenticate', {
        method: 'POST',
        mode: 'cors',
        cache: 'no-cache',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        redirect: 'follow',
        referrerPolicy: 'no-referrer',
        body: JSON.stringify({ username, password }),
      });
      const res = JSON.parse(await response.text());
      if (res.code === '000') {
        localStorage.setItem('user', JSON.stringify(res.user));
        navigate('/dashboard');
      } else {
        setError(res.message || `Sign in failed (${res.code}).`);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Sign in failed.');
    } finally {
      setLoading(false);
    }
  }

  const invalid = Boolean(error) && (!username || !password);

  return (
    <AuthLayout
      className="ios-auth-admin"
      title={strings.portal_title}
      subtitle="Administrator access"
      asideTitle="Admin workspace"
      asideCopy="Operations, configuration, and reporting in one place."
      footer={`© ${new Date().getFullYear()} CPay`}
    >
      <form className="ios-form" onSubmit={handleSubmit} noValidate>
        {error ? <Alert variant="error">{error}</Alert> : null}
        <TextField
          id="admin-username"
          label="Username"
          value={username}
          onValueChange={setUsername}
          autoComplete="username"
          invalid={invalid}
        />
        <PasswordField
          id="admin-password"
          label="Password"
          value={password}
          onValueChange={setPassword}
          invalid={invalid}
        />
        <div className="ios-actions">
          <Button type="submit" variant="primary" loading={loading} loadingLabel="Signing in…">
            Sign in
          </Button>
          <Button type="button" variant="link" onClick={() => setShowForgot(true)}>
            Forgot my password?
          </Button>
        </div>
      </form>
      <ForgotPassword
        onCloseDialog={() => setShowForgot(false)}
        showForgotPassword={showForgot}
      />
    </AuthLayout>
  );
}

export default Login;
