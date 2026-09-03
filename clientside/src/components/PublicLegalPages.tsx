import React from 'react';
import { Link } from 'react-router-dom';
import { usePageMetadata } from '../shared/usePageMetadata';
import '../styles/cito-landing.css';

type LegalPage = 'privacy' | 'terms' | 'account-deletion';

function Header(): React.ReactElement {
  return (
    <header className="cito-header">
      <Link className="cito-brand" to="/">
        <span className="cito-brand-mark" aria-hidden="true">C</span>
        <span><strong>Cito</strong><small>Business Services</small></span>
      </Link>
      <nav className="cito-nav" aria-label="Primary navigation">
        <Link to="/payments">Payments</Link>
        <Link to="/billing">Billing</Link>
        <Link to="/developer-platform">Developers</Link>
        <Link to="/status">Status</Link>
      </nav>
      <div className="cito-header-actions">
        <Link className="cito-button cito-button-quiet" to="/bo">Sign in</Link>
        <Link className="cito-button cito-button-primary" to="/contact">Contact Cito</Link>
      </div>
    </header>
  );
}

function Footer(): React.ReactElement {
  return (
    <footer className="cito-footer">
      <div className="cito-footer-brand">
        <strong>Cito</strong>
        <span>A Core-Synergies business-services platform.</span>
      </div>
      <div className="cito-footer-links">
        <div><strong>Legal</strong><Link to="/privacy">Privacy</Link><Link to="/terms">Terms</Link><Link to="/account-deletion">Account deletion</Link></div>
        <div><strong>Support</strong><Link to="/contact">Contact</Link><Link to="/status">Status</Link></div>
      </div>
      <div className="cito-footer-bottom"><span>© {new Date().getFullYear()} Core-Synergies</span><span>Cito Business Services</span></div>
    </footer>
  );
}

function Privacy(): React.ReactElement {
  return (
    <>
      <p className="cito-lead">This policy explains how Cito Technologies, a Core-Synergies service, handles information when businesses and authorised users use Cito Business, Cito's web portals, APIs and support services.</p>
      <h2>Information Cito handles</h2>
      <p>Cito may process business identity and contact details, merchant account identifiers, authorised-user information, authentication and session data, transaction references and amounts, payment-channel and settlement information, service-entitlement records, support messages, security events, audit records and technical diagnostics.</p>
      <p>The mobile application does not persist passwords. It stores authenticated session cookies in operating-system secure storage and keeps CSRF tokens in memory to protect mutating requests.</p>
      <h2>Why Cito uses information</h2>
      <p>Cito uses information to authenticate users, provide entitled services, process and reconcile transactions, communicate service status, prevent fraud, meet regulatory and contractual duties, investigate incidents, respond to support cases, improve reliability and maintain auditable financial records.</p>
      <h2>Service providers and sharing</h2>
      <p>Information may be shared with payment, communications, identity, credit-reporting, banking, vending, infrastructure and professional-service providers only as needed to deliver approved services, meet legal duties or protect Cito and its users. Availability depends on the merchant's market, configuration, consent, commercial approval and entitlement.</p>
      <h2>Retention</h2>
      <p>Cito retains information for the period needed to provide services and satisfy accounting, tax, fraud-prevention, dispute, audit, contractual and regulatory obligations. Data that is not subject to a lawful retention requirement should be deleted or anonymised after the applicable operational period.</p>
      <h2>Security</h2>
      <p>Cito uses encrypted transport, scoped access, environment separation, protected credentials, audit trails and operational monitoring. Suspected security or privacy incidents should be reported promptly through the authenticated support workspace or the contact address below.</p>
      <h2>Your choices and rights</h2>
      <p>Subject to applicable law and the authority of the requesting user, you may request access, correction, restriction, objection, portability or deletion. Merchant administrators should first use the authenticated support process. Mobile users can initiate deletion under <strong>More → Settings &amp; security → Request account deletion</strong>.</p>
      <h2>Contact</h2>
      <p>Privacy and data-rights enquiries: <a href="mailto:support@citotech.net?subject=Cito%20privacy%20request">support@citotech.net</a>. Include the merchant account number but never include passwords, API secrets or payment credentials.</p>
    </>
  );
}

function Terms(): React.ReactElement {
  return (
    <>
      <p className="cito-lead">These terms govern access to Cito Business and related Cito services. Merchant-specific commercial agreements, provider rules and applicable laws may impose additional terms.</p>
      <h2>Authorised use</h2>
      <p>Users must be authorised by the relevant merchant or institution, provide accurate information, protect credentials and use Cito only for lawful business activity. Accounts and permissions may not be shared outside the approved organisation.</p>
      <h2>Services and availability</h2>
      <p>Cito may provide payments, communications, identity and credit intelligence, vending, billing, integrations and related operational services. A service displayed in Cito is not necessarily approved for production use. Provider configuration, certification, commercial approval, compliance review and merchant entitlement remain controlling.</p>
      <h2>Financial instructions</h2>
      <p>Users are responsible for checking beneficiaries, amounts, references, environment and authority before submitting financial instructions. Cito may reject, pause or review requests to protect financial integrity, comply with limits or respond to ambiguous provider outcomes.</p>
      <h2>Prohibited activity</h2>
      <p>Users must not misuse Cito, circumvent controls, interfere with service operation, submit unlawful transactions, reverse engineer protected services, access another tenant's information or introduce malicious code.</p>
      <h2>Suspension and termination</h2>
      <p>Cito may restrict access where required for security, compliance, non-payment, provider direction, contractual breach or legal obligation. Account closure does not erase records that Cito is legally or contractually required to retain.</p>
      <h2>Support and changes</h2>
      <p>Operational support is available through the authenticated Cito support workspace and <a href="mailto:support@citotech.net">support@citotech.net</a>. Cito may update these terms as services, laws and provider obligations change; material changes should be communicated through appropriate channels.</p>
    </>
  );
}

function AccountDeletion(): React.ReactElement {
  return (
    <>
      <p className="cito-lead">Cito Business users can request deletion from inside the authenticated mobile app. A public request route is also provided for users who cannot sign in.</p>
      <h2>Request deletion in the mobile app</h2>
      <ol>
        <li>Sign in to Cito Business.</li>
        <li>Open <strong>More</strong>.</li>
        <li>Select <strong>Settings &amp; security</strong>.</li>
        <li>Select <strong>Request account deletion</strong>.</li>
        <li>Review the retention notice and confirm.</li>
      </ol>
      <p>The app creates an authenticated case so Cito can verify the requesting user and the merchant account.</p>
      <h2>Request deletion without app access</h2>
      <p>Email <a href="mailto:support@citotech.net?subject=Cito%20account%20deletion%20request">support@citotech.net</a> using the email associated with the account. State the merchant account number, user email and whether the request concerns only the user profile or the entire merchant account. Do not send a password, PIN, API key, callback secret or payment credential.</p>
      <h2>What is deleted</h2>
      <p>After authority checks, Cito will delete or anonymise account and profile data that is not required for an active service, legitimate security purpose, contractual obligation or legal retention duty.</p>
      <h2>What may be retained</h2>
      <p>Transaction, ledger, settlement, tax, fraud-prevention, dispute, audit, security and regulatory records may need to be retained for legally mandated periods. Where records must be retained, Cito should restrict their use to the relevant obligation and remove ordinary account access.</p>
      <h2>Timing and confirmation</h2>
      <p>Cito will acknowledge the request, may ask for additional proof of authority and will communicate the outcome through the authenticated support case or verified email address.</p>
    </>
  );
}

export default function PublicLegalPage({ page }: { page: LegalPage }): React.ReactElement {
  const title = page === 'privacy' ? 'Privacy Policy' : page === 'terms' ? 'Terms of Service' : 'Account Deletion';
  const description = page === 'privacy'
    ? 'How Cito handles information across its mobile application and business-services platform.'
    : page === 'terms'
      ? 'Terms governing authorised use of Cito Business and related services.'
      : 'How Cito Business users can request deletion of an account and associated data.';
  usePageMetadata(title, description, `/${page}`);

  return (
    <div className="cito-landing">
      <Header />
      <main>
        <section className="cito-section">
          <div className="cito-section-heading">
            <p className="cito-eyebrow">Cito legal and account controls</p>
            <h1>{title}</h1>
            <p>Effective 4 September 2026</p>
          </div>
          <article className="cito-product-card" style={{ maxWidth: 900, margin: '0 auto' }}>
            {page === 'privacy' ? <Privacy /> : page === 'terms' ? <Terms /> : <AccountDeletion />}
          </article>
        </section>
      </main>
      <Footer />
    </div>
  );
}
