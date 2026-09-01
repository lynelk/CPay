import React from 'react';
import { useNavigate } from 'react-router-dom';
import strings from './locale';
import ForgotPassword from './LoginForgotPassword';
import { AuthLayout, TextField, PasswordField, Button, Alert } from '../ui';
import type { AuthAsideBenefit, AuthAsideCard } from '../ui/AuthLayout';

import { apiFetch } from '../shared/api/httpClient';
import { apiUrl } from '../shared/config';

type AdminLoginAppearance = Record<string, string>;

const defaultAppearance: AdminLoginAppearance = {
  admin_login_hero_image_url:
    'https://images.unsplash.com/photo-1551288049-bebda4e38f71?auto=format&fit=crop&w=1600&q=80',
  admin_login_hero_title: 'Powerful control. Smarter operations.',
  admin_login_hero_copy: 'Manage your platform, users, and transactions with confidence and clarity.',
  admin_login_approvals_title: 'Secure Platform',
  admin_login_users_title: 'User & Role Management',
  admin_login_analytics_title: 'Real-time Analytics',
  admin_login_merchant_title: 'System Management',
  admin_login_security_title: 'Audit & Compliance',
  admin_login_monitoring_title: 'Payments Monitoring',
  admin_login_support_title: 'Support',
  admin_login_system_title: 'System Settings',
  admin_login_secure_title: 'Secure',
  admin_login_secure_copy: 'Protect platform and data',
  admin_login_insights_title: 'Reliable',
  admin_login_insights_copy: 'High availability and performance',
  admin_login_control_title: 'Insightful',
  admin_login_control_copy: 'Real-time reports and analytics',
  admin_login_automation_title: 'Efficient',
  admin_login_automation_copy: 'Automate and simplify operations',
  admin_login_reliable_title: 'Compliant',
  admin_login_reliable_copy: 'Meet regulatory requirements',
};

function setting(appearance: AdminLoginAppearance, key: string): string {
  const value = appearance[key]?.trim();
  return value || defaultAppearance[key] || '';
}

async function getAdminLoginAppearance(): Promise<AdminLoginAppearance> {
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
  return { ...defaultAppearance, ...(res.settings as AdminLoginAppearance) };
}

async function isLoggedIn(): Promise<boolean> {
  try {
    const response = await apiFetch(apiUrl('/auth/isLoggedIn'), {
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
  const [newPassword, setNewPassword] = React.useState('');
  const [confirmPassword, setConfirmPassword] = React.useState('');
  const [passwordChangeRequired, setPasswordChangeRequired] = React.useState(false);
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState('');
  const [success, setSuccess] = React.useState('');
  const [showForgot, setShowForgot] = React.useState(false);
  const [appearance, setAppearance] = React.useState<AdminLoginAppearance>(defaultAppearance);

  React.useEffect(() => {
    let active = true;
    isLoggedIn().then((ok) => {
      if (active && ok) navigate('/dashboard');
    });
    return () => {
      active = false;
    };
  }, [navigate]);

  React.useEffect(() => {
    let active = true;
    getAdminLoginAppearance()
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
    setSuccess('');
    if (!username || !password) {
      setError(strings.admin_login_required);
      return;
    }
    setLoading(true);
    try {
      const response = await apiFetch(apiUrl('/auth/authenticate'), {
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
      } else if (res.code === 'PASSWORD_CHANGE_REQUIRED') {
        localStorage.removeItem('user');
        setPassword('');
        setPasswordChangeRequired(true);
      } else {
        setError(res.message || `${strings.sign_in_failed} (${res.code}).`);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : `${strings.sign_in_failed}.`);
    } finally {
      setLoading(false);
    }
  }

  async function handlePasswordChange(event: React.FormEvent) {
    event.preventDefault();
    setError('');
    setSuccess('');
    if (!newPassword || !confirmPassword) {
      setError(strings.new_password_required);
      return;
    }
    if (newPassword !== confirmPassword) {
      setError(strings.password_mismatch);
      return;
    }
    setLoading(true);
    try {
      const response = await apiFetch(apiUrl('/auth/completeInitialPasswordChange'), {
        method: 'POST',
        mode: 'cors',
        cache: 'no-cache',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        redirect: 'follow',
        referrerPolicy: 'no-referrer',
        body: JSON.stringify({
          new_password: newPassword,
          confirm_password: confirmPassword,
        }),
      });
      const res = JSON.parse(await response.text());
      if (res.code === '000') {
        setNewPassword('');
        setConfirmPassword('');
        setPasswordChangeRequired(false);
        setSuccess(res.message || strings.password_change_complete);
      } else {
        setError(res.message || strings.unable_reset_password);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : strings.unable_reset_password);
    } finally {
      setLoading(false);
    }
  }

  const invalid = Boolean(error) && (!username || !password);
  const passwordChangeInvalid = Boolean(error);
  const asideCards: AuthAsideCard[] = [
    { id: 'users', icon: 'users', title: setting(appearance, 'admin_login_users_title') },
    { id: 'approvals', icon: 'secure', title: setting(appearance, 'admin_login_approvals_title'), tone: 'success' },
    { id: 'analytics', icon: 'insights', title: setting(appearance, 'admin_login_analytics_title') },
    { id: 'system', icon: 'settings', title: setting(appearance, 'admin_login_merchant_title') },
    { id: 'security', icon: 'verification', title: setting(appearance, 'admin_login_security_title') },
  ];
  const asideBenefits: AuthAsideBenefit[] = [
    { icon: 'secure', title: setting(appearance, 'admin_login_secure_title'), copy: setting(appearance, 'admin_login_secure_copy') },
    { icon: 'insights', title: setting(appearance, 'admin_login_insights_title'), copy: setting(appearance, 'admin_login_insights_copy') },
    { icon: 'users', title: setting(appearance, 'admin_login_control_title'), copy: setting(appearance, 'admin_login_control_copy') },
    { icon: 'fast', title: setting(appearance, 'admin_login_automation_title'), copy: setting(appearance, 'admin_login_automation_copy') },
    { icon: 'reliable', title: setting(appearance, 'admin_login_reliable_title'), copy: setting(appearance, 'admin_login_reliable_copy') },
  ];

  return (
    <AuthLayout
      className="ios-auth-merchant ios-auth-admin"
      title={strings.portal_title}
      subtitle={strings.admin_access_subtitle}
      asideTitle={setting(appearance, 'admin_login_hero_title')}
      asideCopy={setting(appearance, 'admin_login_hero_copy')}
      asideVariant="media"
      asideImageUrl={setting(appearance, 'admin_login_hero_image_url')}
      asideImageAlt="Admin operations workspace"
      asideCards={asideCards}
      asideBenefits={asideBenefits}
      footer={`© ${new Date().getFullYear()} Core-Synergies`}
    >
      {passwordChangeRequired ? (
        <form className="ios-form" onSubmit={handlePasswordChange} noValidate>
          <Alert variant="warning">{strings.initial_password_change_instructions}</Alert>
          {error ? <Alert variant="error">{error}</Alert> : null}
          <PasswordField
            id="admin-new-password"
            label={strings.new_password_label}
            value={newPassword}
            onValueChange={setNewPassword}
            autoComplete="new-password"
            invalid={passwordChangeInvalid}
          />
          <PasswordField
            id="admin-confirm-password"
            label={strings.confirm_password_label}
            value={confirmPassword}
            onValueChange={setConfirmPassword}
            autoComplete="new-password"
            invalid={passwordChangeInvalid}
          />
          <div className="ios-actions">
            <Button
              type="submit"
              variant="primary"
              loading={loading}
              loadingLabel={strings.changing_password}
            >
              {strings.change_password}
            </Button>
          </div>
        </form>
      ) : (
        <form className="ios-form" onSubmit={handleSubmit} noValidate>
          {success ? <Alert variant="success">{success}</Alert> : null}
          {error ? <Alert variant="error">{error}</Alert> : null}
          <TextField
            id="admin-username"
            label={strings.username_label}
            value={username}
            onValueChange={setUsername}
            autoComplete="username"
            invalid={invalid}
          />
          <PasswordField
            id="admin-password"
            label={strings.password_label}
            value={password}
            onValueChange={setPassword}
            invalid={invalid}
          />
          <div className="ios-actions">
            <Button type="submit" variant="primary" loading={loading} loadingLabel={strings.signing_in}>
              {strings.sign_in}
            </Button>
            <Button type="button" variant="link" onClick={() => setShowForgot(true)}>
              {strings.forgot_password_link}
            </Button>
          </div>
        </form>
      )}
      <ForgotPassword
        onCloseDialog={() => setShowForgot(false)}
        showForgotPassword={showForgot}
      />
    </AuthLayout>
  );
}

export default Login;
