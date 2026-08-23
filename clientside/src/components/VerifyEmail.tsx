import React from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import strings from './locale';
import { AuthLayout, TextField, Button, Alert } from '../ui';

import { apiFetch } from '../shared/api/httpClient';
import { apiUrl } from '../shared/config';

interface VerifyEmailResult {
  code?: string;
  message?: string;
}

interface PrefillState {
  accountNumber?: string;
  email?: string;
}

async function readJsonResponse(response: Response): Promise<VerifyEmailResult> {
  const text = await response.text();
  if (!text.trim()) {
    return { code: 'EMPTY_RESPONSE', message: 'The server did not return a response.' };
  }
  try {
    return JSON.parse(text) as VerifyEmailResult;
  } catch {
    return { code: 'INVALID_RESPONSE', message: 'The server returned an invalid response.' };
  }
}

function VerifyEmail(): React.ReactElement {
  const navigate = useNavigate();
  const location = useLocation();
  const prefill = (location.state as PrefillState | null) ?? {};

  const [accountNumber, setAccountNumber] = React.useState(prefill.accountNumber ?? '');
  const [email, setEmail] = React.useState(prefill.email ?? '');
  const [code, setCode] = React.useState('');
  const [loading, setLoading] = React.useState(false);
  const [resending, setResending] = React.useState(false);
  const [error, setError] = React.useState('');
  const [info, setInfo] = React.useState('');
  const [verified, setVerified] = React.useState(false);

  React.useEffect(() => {
    if (prefill.accountNumber) setAccountNumber(prefill.accountNumber);
    if (prefill.email) setEmail(prefill.email);
  }, [prefill.accountNumber, prefill.email]);

  async function handleVerify(event: React.FormEvent) {
    event.preventDefault();
    setError('');
    setInfo('');
    if (!accountNumber.trim() || !email.trim() || !code.trim()) {
      setError(strings.verify_email_required);
      return;
    }
    setLoading(true);
    try {
      const response = await apiFetch(apiUrl('/api/v2/merchant-self-service/verify-email'), {
        method: 'POST',
        mode: 'cors',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        redirect: 'follow',
        referrerPolicy: 'no-referrer',
        body: JSON.stringify({
          merchantNumber: accountNumber.trim(),
          email: email.trim().toLowerCase(),
          code: code.trim(),
        }),
      });
      const result = await readJsonResponse(response);
      if (!response.ok || result.code !== '000') {
        setError(result.message || strings.verify_email_failed);
        return;
      }
      setVerified(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : strings.verify_email_failed);
    } finally {
      setLoading(false);
    }
  }

  async function handleResend() {
    setError('');
    setInfo('');
    if (!accountNumber.trim() || !email.trim()) {
      setError(strings.verify_email_required);
      return;
    }
    setResending(true);
    try {
      const response = await apiFetch(apiUrl('/api/v2/merchant-self-service/verify-email/resend'), {
        method: 'POST',
        mode: 'cors',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        redirect: 'follow',
        referrerPolicy: 'no-referrer',
        body: JSON.stringify({
          merchantNumber: accountNumber.trim(),
          email: email.trim().toLowerCase(),
        }),
      });
      const result = await readJsonResponse(response);
      if (!response.ok || result.code !== '000') {
        setError(result.message || strings.verify_email_failed);
        return;
      }
      setInfo(strings.verify_email_resend_sent);
    } catch (err) {
      setError(err instanceof Error ? err.message : strings.verify_email_failed);
    } finally {
      setResending(false);
    }
  }

  return (
    <AuthLayout
      className="ios-auth-merchant"
      title={strings.verify_email_title}
      subtitle={strings.verify_email_subtitle}
      asideTitle="Email verification"
      asideCopy="Confirm the address on your merchant account to secure your portal access."
      footer={`© ${new Date().getFullYear()} Core-Synergies`}
    >
      {verified ? (
        <div className="ios-result">
          <h2>{strings.verify_email_success}</h2>
          <p>{strings.verify_email_success_instructions}</p>
          <div className="ios-actions">
            <Button variant="primary" onClick={() => navigate('/login')}>
              {strings.go_to_login}
            </Button>
          </div>
        </div>
      ) : (
        <form className="ios-form" onSubmit={handleVerify} noValidate>
          {error ? <Alert variant="error">{error}</Alert> : null}
          {info ? <Alert variant="success">{info}</Alert> : null}
          <TextField
            id="verify-account"
            label={strings.merchant_account_label}
            value={accountNumber}
            onValueChange={setAccountNumber}
            autoComplete="off"
          />
          <TextField
            id="verify-email"
            label={strings.email_address_label}
            type="email"
            value={email}
            onValueChange={setEmail}
            autoComplete="email"
          />
          <TextField
            id="verify-code"
            label={strings.verification_code_label}
            value={code}
            onValueChange={setCode}
            autoComplete="one-time-code"
          />
          <div className="ios-actions">
            <Button type="submit" variant="primary" loading={loading} loadingLabel={strings.verifying}>
              {strings.verify_email}
            </Button>
            <Button type="button" variant="link" loading={resending} loadingLabel={strings.resending} onClick={handleResend}>
              {strings.resend_verification_code}
            </Button>
            <Button type="button" variant="link" onClick={() => navigate('/login')}>
              {strings.back_to_login}
            </Button>
          </div>
        </form>
      )}
    </AuthLayout>
  );
}

export default VerifyEmail;
