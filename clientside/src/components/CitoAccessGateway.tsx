import React from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import Login from './Login';
import LoginMerchant from './LoginMerchant';
import '../styles/cito-access.css';

type Realm = 'merchant' | 'platform';

function realmFrom(value: string | null): Realm | null {
  return value === 'merchant' || value === 'platform' ? value : null;
}

function CitoAccessGateway(): React.ReactElement {
  const [searchParams] = useSearchParams();
  const realm = realmFrom(searchParams.get('realm'));

  // Retain realm deep links while /bo becomes the canonical secure entry point.
  if (realm === 'merchant') return <LoginMerchant />;
  if (realm === 'platform') return <Login />;

  return (
    <main className="cito-access-shell">
      <section className="cito-access-panel" aria-labelledby="cito-signin-title">
        <Link className="cito-access-brand" to="/" aria-label="Cito home">Cito</Link>
        <p className="cito-access-eyebrow">Secure account access</p>
        <h1 id="cito-signin-title">Sign in through Cito</h1>
        <p className="cito-access-lead">
          Choose the account context issued to you. This selection only chooses the authentication realm;
          permissions are determined by your approved account after authentication.
        </p>

        <div className="cito-access-options">
          <Link className="cito-access-option" to="/bo/partner">
            <strong>Business, Merchant or Customer</strong>
            <span>Access payments, reconciliation, billing, integrations, and partner operations.</span>
            <small>Continue to partner portal →</small>
          </Link>
          <Link className="cito-access-option" to="/bo/admin">
            <strong>Cito team or Administrator</strong>
            <span>For approved platform, operations, finance, compliance, support, and administration accounts.</span>
            <small>Continue to administration →</small>
          </Link>
        </div>

        <div className="cito-access-notice" role="note">
          Selecting an account type never grants a role. Privileged access requires an approved account and remains
          subject to the platform&apos;s authentication, MFA, session, and authorization controls.
        </div>

        <div className="cito-access-actions">
          <span>Need an account?</span>
          <Link to="/signup">Start through Cito</Link>
          <Link to="/">Return home</Link>
        </div>
        <footer>© {new Date().getFullYear()} Core-Synergies</footer>
      </section>
    </main>
  );
}

export default CitoAccessGateway;
