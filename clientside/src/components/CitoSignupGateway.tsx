import React from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import MerchantSignup from './MerchantSignup';
import { apiFetch } from '../shared/api/httpClient';
import { apiUrl } from '../shared/config';
import '../styles/cito-access.css';

type SignupType = 'merchant' | 'access';

type AccessForm = {
  fullName: string;
  workEmail: string;
  organization: string;
  requestedAccessType: string;
  reason: string;
};

const emptyForm: AccessForm = {
  fullName: '',
  workEmail: '',
  organization: '',
  requestedAccessType: 'OPERATIONS',
  reason: '',
};

function signupTypeFrom(value: string | null): SignupType | null {
  return value === 'merchant' || value === 'access' ? value : null;
}

function AccessRequestForm(): React.ReactElement {
  const [form, setForm] = React.useState<AccessForm>(emptyForm);
  const [submitting, setSubmitting] = React.useState(false);
  const [message, setMessage] = React.useState('');
  const [error, setError] = React.useState('');

  const set = (field: keyof AccessForm) => (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => {
    setForm((current) => ({ ...current, [field]: event.target.value }));
  };

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setSubmitting(true);
    setError('');
    setMessage('');
    try {
      const response = await apiFetch(apiUrl('/api/public/access-requests'), {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(form),
      });
      const payload = await response.json().catch(() => ({}));
      if (!response.ok) {
        setError(payload.message || 'We could not submit the access request.');
        return;
      }
      setMessage(payload.message || 'Request received. Access remains pending authorized review.');
      setForm(emptyForm);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'We could not submit the access request.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <main className="cito-access-shell">
      <section className="cito-access-panel" aria-labelledby="cito-access-request-title">
        <Link className="cito-access-brand" to="/">Cito</Link>
        <p className="cito-access-eyebrow">Privileged access request</p>
        <h1 id="cito-access-request-title">Request a Cito account</h1>
        <p className="cito-access-lead">
          For administrators, staff, operations, finance, compliance, and partners. Submitting this form does not
          create a privileged account or grant permissions. An authorized administrator must review the request.
        </p>

        {message ? <div className="cito-access-success" role="status">{message}</div> : null}
        {error ? <div className="cito-access-error" role="alert">{error}</div> : null}

        <form className="cito-access-form" onSubmit={submit}>
          <label>Full name<input required minLength={2} maxLength={160} value={form.fullName} onChange={set('fullName')} autoComplete="name" /></label>
          <label>Work email<input required maxLength={254} type="email" value={form.workEmail} onChange={set('workEmail')} autoComplete="email" /></label>
          <label>Organization<input required minLength={2} maxLength={200} value={form.organization} onChange={set('organization')} autoComplete="organization" /></label>
          <label>
            Access area
            <select value={form.requestedAccessType} onChange={set('requestedAccessType')}>
              <option value="ADMINISTRATION">Administration</option>
              <option value="OPERATIONS">Operations</option>
              <option value="FINANCE">Finance</option>
              <option value="COMPLIANCE">Compliance</option>
              <option value="PARTNER">Partner</option>
              <option value="OTHER">Other approved access</option>
            </select>
          </label>
          <label>Business reason<textarea required minLength={10} maxLength={2000} rows={5} value={form.reason} onChange={set('reason')} /></label>
          <button className="cito-access-submit" type="submit" disabled={submitting}>{submitting ? 'Submitting…' : 'Submit access request'}</button>
        </form>

        <div className="cito-access-notice" role="note">
          No password is collected here. Approved users receive account provisioning through the controlled Cito
          administration process and then sign in through the Cito gateway.
        </div>
        <div className="cito-access-actions"><Link to="/login">Already have access? Sign in</Link><Link to="/signup">Choose another signup path</Link></div>
        <footer>© {new Date().getFullYear()} Core-Synergies</footer>
      </section>
    </main>
  );
}

function CitoSignupGateway(): React.ReactElement {
  const [searchParams] = useSearchParams();
  const type = signupTypeFrom(searchParams.get('type'));

  if (type === 'merchant') return <MerchantSignup />;
  if (type === 'access') return <AccessRequestForm />;

  return (
    <main className="cito-access-shell">
      <section className="cito-access-panel" aria-labelledby="cito-signup-title">
        <Link className="cito-access-brand" to="/">Cito</Link>
        <p className="cito-access-eyebrow">Account onboarding</p>
        <h1 id="cito-signup-title">Get started through Cito</h1>
        <p className="cito-access-lead">Choose the account path that matches how you will use the platform.</p>
        <div className="cito-access-options">
          <Link className="cito-access-option" to="/signup?type=merchant">
            <strong>Business or Merchant</strong>
            <span>Self-register a business account, verify your email, complete onboarding, and start in sandbox.</span>
            <small>Create merchant account →</small>
          </Link>
          <Link className="cito-access-option" to="/signup?type=access">
            <strong>Admin, staff, specialist, or partner</strong>
            <span>Request controlled access for administration, operations, finance, compliance, partner, or other approved duties.</span>
            <small>Request privileged access →</small>
          </Link>
        </div>
        <div className="cito-access-notice" role="note">Privileged roles cannot be self-assigned. Cito records the request as pending until an authorized reviewer approves and provisions the account.</div>
        <div className="cito-access-actions"><Link to="/login">Already registered? Sign in</Link><Link to="/">Return home</Link></div>
        <footer>© {new Date().getFullYear()} Core-Synergies</footer>
      </section>
    </main>
  );
}

export default CitoSignupGateway;
