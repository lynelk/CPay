import React from 'react';
import { Link, useNavigate } from 'react-router-dom';
import '../styles/cito-landing.css';

const DOCS_URL = 'https://lynelk.github.io/CPay/';
const SIGN_IN_PATH = '/login';
const SIGN_UP_PATH = '/signup';

const capabilities = [
  ['Collect payments', 'Receive customer payments across supported payment channels.'],
  ['Make payouts', 'Send funds to customers, suppliers, and beneficiaries through CPay.'],
  ['Payment links & invoices', 'Create hosted payment experiences without building checkout from scratch.'],
  ['Reconciliation', 'Track payment status, references, balances, and transaction outcomes centrally.'],
  ['Webhooks & automation', 'Connect transaction events to business workflows with verifiable callbacks.'],
  ['Developer APIs', 'Build with CPay API v2, OpenAPI, SDK helpers, Postman, and sandbox tooling.'],
] as const;

const audiences = [
  ['Businesses & merchants', 'Use Cito as the secure front door to payment and operational services.'],
  ['Platforms & developers', 'Integrate CPay and other Cito services through documented interfaces.'],
  ['Institutions', 'Centralize access to collections, reporting, controls, and reconciliation.'],
  ['Technology partners', 'Connect providers and value-added services through the Cito integration layer.'],
] as const;

const securityControls = [
  ['Signed API requests', 'Cryptographic request authentication for merchant integrations.'],
  ['Controlled environments', 'Separate sandbox and production workflows with explicit activation controls.'],
  ['Scoped access', 'Merchant and user access governed by session and authorization controls.'],
  ['Protected credentials', 'Sensitive channel configuration is encrypted and displayed only in masked form.'],
  ['Auditable operations', 'Important configuration and operational actions are recorded for review.'],
  ['Webhook security', 'Signing, delivery history, rotation, and replay controls for asynchronous events.'],
] as const;

function CitoLandingPage(): React.ReactElement {
  const navigate = useNavigate();

  React.useEffect(() => {
    const uiportal = new URL(window.location.href).searchParams.get('uiportal');
    if (uiportal === 'portal') navigate('/portal', { replace: true });
  }, [navigate]);

  return (
    <div className="cito-landing">
      <header className="cito-header">
        <a className="cito-brand" href="#top" aria-label="Cito home">
          <span className="cito-brand-mark" aria-hidden="true">C</span>
          <span>
            <strong>Cito</strong>
            <small>Gateway</small>
          </span>
        </a>

        <nav className="cito-nav" aria-label="Primary navigation">
          <a href="#cpay">Products</a>
          <a href="#about">About</a>
          <a href="#developers">Developers</a>
          <a href="#contact">Contact</a>
        </nav>

        <div className="cito-header-actions">
          <Link className="cito-button cito-button-quiet" to={SIGN_IN_PATH}>Sign in</Link>
          <Link className="cito-button cito-button-primary" to={SIGN_UP_PATH}>Get started</Link>
        </div>

        <details className="cito-mobile-menu">
          <summary aria-label="Open navigation">Menu</summary>
          <div className="cito-mobile-panel">
            <Link to={SIGN_UP_PATH}>Get started</Link>
            <Link to={SIGN_IN_PATH}>Sign in</Link>
            <a href="#cpay">Products</a>
            <a href="#developers">Developers</a>
            <a href="#about">About</a>
            <a href="#contact">Contact</a>
          </div>
        </details>
      </header>

      <main id="top">
        <section className="cito-hero cito-section">
          <div className="cito-hero-copy">
            <p className="cito-eyebrow">One secure gateway for connected business services</p>
            <h1>Connect your business through Cito.</h1>
            <p className="cito-lead">
              Cito is the gateway: one secure entry point for business services, integrations, and operations.
              CPay is Cito&apos;s payments service, providing collections, payouts, reconciliation, hosted payment
              experiences, and developer APIs.
            </p>
            <div className="cito-hero-actions">
              <Link className="cito-button cito-button-primary cito-button-large" to={SIGN_UP_PATH}>Create Cito account</Link>
              <Link className="cito-button cito-button-secondary cito-button-large" to={SIGN_IN_PATH}>Sign in to Cito</Link>
            </div>
            <a className="cito-text-link" href={DOCS_URL}>Developer? Explore the CPay API <span aria-hidden="true">→</span></a>
          </div>

          <aside className="cito-product-card" aria-label="Cito gateway services summary">
            <div className="cito-product-card-head">
              <div>
                <span className="cito-product-kicker">Cito</span>
                <h2>Gateway</h2>
              </div>
              <span className="cito-status"><span aria-hidden="true" /> Services available</span>
            </div>
            <p className="cito-product-intro">One access layer for Cito services. Payments are delivered through CPay.</p>
            <div className="cito-product-grid">
              <span>CPay · Collections</span>
              <span>CPay · Payouts</span>
              <span>Payment links</span>
              <span>Invoices</span>
              <span>Reconciliation</span>
              <span>Developer APIs</span>
            </div>
            <div className="cito-provider-row" aria-label="CPay supported payment networks">
              <strong>CPay connected channels</strong>
              <p>MTN MoMo · Airtel Money · Airtel OpenAPI · Safaricom M-Pesa · Yo! Payments</p>
            </div>
          </aside>
        </section>

        <section className="cito-trust-strip" aria-label="Platform strengths">
          <span>Secure Cito gateway</span>
          <span>Unified account access</span>
          <span>Built for developers</span>
          <span>Merchant self-service</span>
        </section>

        <section className="cito-section cito-about" id="about">
          <div className="cito-section-heading">
            <p className="cito-eyebrow">About Cito</p>
            <h2>The gateway between your business and connected digital services.</h2>
            <p>
              Cito Technologies builds digital infrastructure that helps organizations simplify access to payments,
              integrations, and operational services. Users enter through Cito, then access the services their account
              is entitled to use. CPay is the payments service within that broader Cito experience.
            </p>
          </div>
          <div className="cito-pillars">
            <article>
              <span>01</span>
              <h3>Gateway</h3>
              <p>One clear entry point for accounts, services, integrations, and operational access.</p>
            </article>
            <article>
              <span>02</span>
              <h3>Payments</h3>
              <p>Use CPay for collections, payouts, reconciliation, payment links, and invoices.</p>
            </article>
            <article>
              <span>03</span>
              <h3>Connectivity</h3>
              <p>Connect applications, providers, and customer experiences through APIs and webhooks.</p>
            </article>
          </div>
        </section>

        <section className="cito-section cito-cpay" id="cpay">
          <div className="cito-section-heading cito-section-heading-center">
            <p className="cito-eyebrow">CPay · Payments by Cito</p>
            <h2>Payment operations delivered through Cito.</h2>
            <p>
              CPay is Cito&apos;s payments service. It provides one controlled payments interface for collections,
              payouts, transaction management, reconciliation, and payment-provider integrations.
            </p>
          </div>
          <div className="cito-feature-grid">
            {capabilities.map(([title, copy], index) => (
              <article className="cito-feature-card" key={title}>
                <span>{String(index + 1).padStart(2, '0')}</span>
                <h3>{title}</h3>
                <p>{copy}</p>
              </article>
            ))}
          </div>
        </section>

        <section className="cito-section cito-how">
          <div className="cito-section-heading cito-section-heading-center">
            <p className="cito-eyebrow">How access works</p>
            <h2>Start with Cito. Activate the services you need.</h2>
          </div>
          <ol className="cito-steps">
            <li><span>01</span><div><h3>Create your Cito account</h3><p>Register your business and primary contact details.</p></div></li>
            <li><span>02</span><div><h3>Complete business setup</h3><p>Finish profile, verification, and approval requirements for your organization.</p></div></li>
            <li><span>03</span><div><h3>Configure CPay</h3><p>Connect supported payment channels and validate your integration in sandbox.</p></div></li>
            <li><span>04</span><div><h3>Activate approved services</h3><p>Move approved capabilities into production under Cito operational controls.</p></div></li>
          </ol>
        </section>

        <section className="cito-section cito-access" id="access">
          <div className="cito-section-heading cito-section-heading-center">
            <p className="cito-eyebrow">Access Cito</p>
            <h2>One front door for your account.</h2>
            <p>The sign-in and account-creation actions below route directly to the live Cito access screens.</p>
          </div>
          <div className="cito-access-grid">
            <article>
              <p className="cito-card-label">New to Cito?</p>
              <h3>Create your Cito business account.</h3>
              <p>Register first, verify your email, then configure CPay and other services available to your organization.</p>
              <Link className="cito-button cito-button-primary" to={SIGN_UP_PATH}>Create Cito account</Link>
            </article>
            <article>
              <p className="cito-card-label">Already registered?</p>
              <h3>Sign in to your Cito account.</h3>
              <p>Use your merchant account credentials to continue to the services and controls assigned to you.</p>
              <Link className="cito-button cito-button-secondary" to={SIGN_IN_PATH}>Sign in to Cito</Link>
            </article>
          </div>
        </section>

        <section className="cito-section cito-developers" id="developers">
          <div className="cito-developer-copy">
            <p className="cito-eyebrow">For developers</p>
            <h2>Integrate through Cito.</h2>
            <p>
              Cito provides the access layer; CPay provides the payments API. Build with documented APIs,
              first-party signing helpers, webhooks, and a controlled sandbox environment.
            </p>
            <div className="cito-developer-actions">
              <a className="cito-button cito-button-light" href={DOCS_URL}>CPay API documentation</a>
              <a className="cito-button cito-button-outline-light" href={`${DOCS_URL}site/index.html#getting-started`}>Quickstart</a>
            </div>
            <p className="cito-developer-resources">OpenAPI · Postman · Node.js · Python · PHP · Webhooks · Error catalog</p>
          </div>
          <div className="cito-quickstart" aria-label="CPay developer quickstart">
            <span className="cito-quickstart-label">CPay API v2</span>
            <ol>
              <li><span>01</span> Create your Cito account</li>
              <li><span>02</span> Configure a sandbox merchant</li>
              <li><span>03</span> Obtain and sign with merchant credentials</li>
              <li><span>04</span> Make a test collection</li>
              <li><span>05</span> Receive and verify a webhook</li>
              <li><span>06</span> Complete production readiness</li>
            </ol>
          </div>
        </section>

        <section className="cito-section cito-audiences">
          <div className="cito-section-heading cito-section-heading-center">
            <p className="cito-eyebrow">Who Cito is for</p>
            <h2>One gateway for organizations that need connected services.</h2>
          </div>
          <div className="cito-audience-grid">
            {audiences.map(([title, copy]) => (
              <article key={title}><h3>{title}</h3><p>{copy}</p></article>
            ))}
          </div>
        </section>

        <section className="cito-section cito-security">
          <div className="cito-section-heading">
            <p className="cito-eyebrow">Trust & control</p>
            <h2>Built for controlled business access and financial operations.</h2>
            <p>Security controls cover request authentication, environments, credential protection, audit trails, and callback delivery.</p>
          </div>
          <div className="cito-security-grid">
            {securityControls.map(([title, copy]) => (
              <article key={title}><h3>{title}</h3><p>{copy}</p></article>
            ))}
          </div>
        </section>

        <section className="cito-section cito-contact" id="contact">
          <div className="cito-section-heading">
            <p className="cito-eyebrow">Contact</p>
            <h2>Talk to Cito.</h2>
            <p>Choose the route that matches what you need and reach the appropriate team directly.</p>
          </div>
          <div className="cito-contact-grid">
            <a href="mailto:info@citotech.net"><span>Sales</span><strong>Discuss Cito services for your business</strong><small>info@citotech.net</small></a>
            <a href="mailto:support@citotech.net"><span>Support</span><strong>Get help with an existing Cito account</strong><small>support@citotech.net</small></a>
            <a href={DOCS_URL}><span>Developers</span><strong>Open CPay integration documentation</strong><small>CPay API v2 documentation</small></a>
          </div>
        </section>

        <section className="cito-final-cta">
          <div>
            <p className="cito-eyebrow">Start with Cito</p>
            <h2>Ready to connect your business?</h2>
            <p>Create your Cito account, access CPay in sandbox, and build your integrations from one gateway.</p>
          </div>
          <div className="cito-final-actions">
            <Link className="cito-button cito-button-light" to={SIGN_UP_PATH}>Create Cito account</Link>
            <Link className="cito-button cito-button-outline-light" to={SIGN_IN_PATH}>Sign in to Cito</Link>
          </div>
        </section>
      </main>

      <footer className="cito-footer">
        <div className="cito-footer-brand">
          <strong>Cito</strong>
          <span>The gateway to connected business services. CPay provides payment capabilities.</span>
        </div>
        <div className="cito-footer-links">
          <div><strong>Services</strong><a href="#cpay">CPay</a><a href="#access">Cito access</a></div>
          <div><strong>Developers</strong><a href={DOCS_URL}>CPay documentation</a><a href={`${DOCS_URL}Api/cpay-v2-openapi.yaml`}>OpenAPI</a></div>
          <div><strong>Company</strong><a href="#about">About Cito</a><a href="#contact">Contact</a></div>
          <div><strong>Access</strong><Link to={SIGN_UP_PATH}>Create account</Link><Link to={SIGN_IN_PATH}>Sign in</Link></div>
        </div>
        <div className="cito-footer-bottom">
          <span>© {new Date().getFullYear()} Cito Technologies</span>
          <span>Cito Gateway</span>
        </div>
      </footer>
    </div>
  );
}

export default CitoLandingPage;
