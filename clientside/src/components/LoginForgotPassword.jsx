import React from 'react';
import { Alert, Button, PasswordField, ProgressOverlay, Sheet, TextField } from '../ui';

import { apiFetch } from '../shared/api/httpClient';
import { apiUrl } from '../shared/config';

function initialState() {
  return {
    step: 'request',
    email: '',
    verification_code: '',
    new_password: '',
    confirm_password: '',
    loading: false,
    error: '',
    message: '',
  };
}

function ForgotPassword({ showForgotPassword, onCloseDialog }) {
  const [state, setState] = React.useState(initialState);

  function patch(values) {
    setState((prev) => ({ ...prev, ...values }));
  }

  function close() {
    setState(initialState());
    onCloseDialog();
  }

  async function requestReset(event) {
    event.preventDefault();
    if (!state.email) {
      patch({ error: 'Email is required.', message: '' });
      return;
    }
    patch({ loading: true, error: '', message: '' });
    try {
      const response = await apiFetch(apiUrl('/auth/requestResetPassword'), {
        method: 'POST', mode: 'cors', cache: 'no-cache', credentials: 'include',
        headers: { 'Content-Type': 'application/json' }, redirect: 'follow', referrerPolicy: 'no-referrer',
        body: JSON.stringify({ email: state.email }),
      });
      const res = JSON.parse(await response.text());
      if (res.code === '000') {
        patch({ step: 'reset', message: res.message || 'Verification code sent.', loading: false });
      } else {
        patch({ error: res.message || res.error || 'Unable to request password reset.', loading: false });
      }
    } catch (error) {
      patch({ error: error.message, loading: false });
    }
  }

  async function resetPassword(event) {
    event.preventDefault();
    if (!state.verification_code || !state.new_password || !state.confirm_password) {
      patch({ error: 'Verification code and new password are required.', message: '' });
      return;
    }
    if (state.new_password !== state.confirm_password) {
      patch({ error: 'The new password does not match the confirmation.', message: '' });
      return;
    }
    patch({ loading: true, error: '', message: '' });
    try {
      const response = await apiFetch(apiUrl('/auth/resetPassword'), {
        method: 'POST', mode: 'cors', cache: 'no-cache', credentials: 'include',
        headers: { 'Content-Type': 'application/json' }, redirect: 'follow', referrerPolicy: 'no-referrer',
        body: JSON.stringify({ email: state.email, verification_code: state.verification_code, new_password: state.new_password }),
      });
      const res = JSON.parse(await response.text());
      if (res.code === '000') {
        patch({ message: res.message || 'Password reset complete.', loading: false });
        close();
      } else {
        patch({ error: res.message || res.error || 'Unable to reset password.', loading: false });
      }
    } catch (error) {
      patch({ error: error.message, loading: false });
    }
  }

  return (
    <>
      <Sheet
        open={Boolean(showForgotPassword)}
        onClose={close}
        title={state.step === 'request' ? 'Reset password' : 'Complete password reset'}
        size="sm"
        footer={<>
          <Button variant="ghost" className="ios-btn--sm" type="button" onClick={close}>Cancel</Button>
          <Button variant="primary" className="ios-btn--sm" type="submit" form={state.step === 'request' ? 'forgot-admin-request' : 'forgot-admin-reset'} loading={state.loading}>Submit</Button>
        </>}
      >
        {state.error ? <Alert variant="error">{state.error}</Alert> : null}
        {state.message ? <Alert variant="success">{state.message}</Alert> : null}
        {state.step === 'request' ? (
          <form id="forgot-admin-request" className="ios-form" onSubmit={requestReset} noValidate>
            <p style={{ color: 'var(--ios-text-secondary)', marginTop: 0 }}>Enter your email address to receive a verification code.</p>
            <TextField id="forgot-admin-email" label="Email" value={state.email} onValueChange={(value) => patch({ email: value })} autoComplete="email" />
          </form>
        ) : (
          <form id="forgot-admin-reset" className="ios-form" onSubmit={resetPassword} noValidate>
            <TextField id="forgot-admin-code" label="Verification Code" value={state.verification_code} onValueChange={(value) => patch({ verification_code: value })} />
            <PasswordField id="forgot-admin-new-password" label="New Password" value={state.new_password} onValueChange={(value) => patch({ new_password: value })} autoComplete="new-password" />
            <PasswordField id="forgot-admin-confirm-password" label="Confirm Password" value={state.confirm_password} onValueChange={(value) => patch({ confirm_password: value })} autoComplete="new-password" />
          </form>
        )}
      </Sheet>
      <ProgressOverlay open={state.loading} message="Please wait" />
    </>
  );
}

export default ForgotPassword;
