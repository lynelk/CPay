import React from 'react';
import common from './Common';
import { Alert, Button, PasswordField, ProgressOverlay, Sheet, TextField } from '../ui';

function initialState(merchantNumber = '') {
  return {
    step: 'request',
    merchant_number: merchantNumber || '',
    email: '',
    verification_code: '',
    new_password: '',
    confirm_password: '',
    loading: false,
    error: '',
    message: '',
  };
}

function ForgotPasswordMerchant({ merchantNumber = '', showForgotPassword, onCloseDialog }) {
  const [state, setState] = React.useState(() => initialState(merchantNumber));

  React.useEffect(() => {
    if (showForgotPassword) {
      setState((prev) => ({ ...prev, merchant_number: merchantNumber || prev.merchant_number || '' }));
    }
  }, [merchantNumber, showForgotPassword]);

  function patch(values) {
    setState((prev) => ({ ...prev, ...values }));
  }

  function close() {
    setState(initialState(merchantNumber));
    onCloseDialog();
  }

  async function requestReset(event) {
    event.preventDefault();
    if (!state.merchant_number || !state.email) {
      patch({ error: 'Merchant number and email are required.', message: '' });
      return;
    }
    patch({ loading: true, error: '', message: '' });
    try {
      const response = await fetch(common.base_url + '/auth/requestMerchantUserResetPassword', {
        method: 'POST', mode: 'cors', cache: 'no-cache', credentials: 'include',
        headers: { 'Content-Type': 'application/json' }, redirect: 'follow', referrerPolicy: 'no-referrer',
        body: JSON.stringify({ email: state.email, merchant_number: state.merchant_number }),
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
      const response = await fetch(common.base_url + '/auth/resetPasswordMerchant', {
        method: 'POST', mode: 'cors', cache: 'no-cache', credentials: 'include',
        headers: { 'Content-Type': 'application/json' }, redirect: 'follow', referrerPolicy: 'no-referrer',
        body: JSON.stringify({
          email: state.email,
          merchant_number: state.merchant_number,
          verification_code: state.verification_code,
          new_password: state.new_password,
        }),
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
        title={state.step === 'request' ? 'Reset merchant password' : 'Complete password reset'}
        size="sm"
        footer={<>
          <Button variant="ghost" className="ios-btn--sm" type="button" onClick={close}>Cancel</Button>
          <Button variant="primary" className="ios-btn--sm" type="submit" form={state.step === 'request' ? 'forgot-merchant-request' : 'forgot-merchant-reset'} loading={state.loading}>Submit</Button>
        </>}
      >
        {state.error ? <Alert variant="error">{state.error}</Alert> : null}
        {state.message ? <Alert variant="success">{state.message}</Alert> : null}
        {state.step === 'request' ? (
          <form id="forgot-merchant-request" className="ios-form" onSubmit={requestReset} noValidate>
            <p style={{ color: 'var(--ios-text-secondary)', marginTop: 0 }}>Confirm your merchant account and email address.</p>
            <TextField id="forgot-merchant-number" label="Merchant Number" value={state.merchant_number} onValueChange={(value) => patch({ merchant_number: value })} autoComplete="off" />
            <TextField id="forgot-merchant-email" label="Email" value={state.email} onValueChange={(value) => patch({ email: value })} autoComplete="email" />
          </form>
        ) : (
          <form id="forgot-merchant-reset" className="ios-form" onSubmit={resetPassword} noValidate>
            <TextField id="forgot-merchant-code" label="Verification Code" value={state.verification_code} onValueChange={(value) => patch({ verification_code: value })} />
            <PasswordField id="forgot-merchant-new-password" label="New Password" value={state.new_password} onValueChange={(value) => patch({ new_password: value })} autoComplete="new-password" />
            <PasswordField id="forgot-merchant-confirm-password" label="Confirm Password" value={state.confirm_password} onValueChange={(value) => patch({ confirm_password: value })} autoComplete="new-password" />
          </form>
        )}
      </Sheet>
      <ProgressOverlay open={state.loading} message="Please wait" />
    </>
  );
}

export default ForgotPasswordMerchant;
