import React from 'react';
import { Link, useNavigate } from 'react-router-dom';
import '../styles/cito-landing.css';

const DOCS_URL = 'https://lynelk.github.io/CPay/';

const capabilities = [
  ['Collect payments', 'Receive customer payments across supported payment channels.'],
  ['Make payouts', 'Send funds to customers, suppliers, and beneficiaries from one controlled gateway.'],
  ['Payment links & invoices', 'Create hosted payment experiences without building checkout from scratch.'],
  ['Reconciliation', 'Track payment status, references, balances, and transaction outcomes centrally.'],
  ['Webhooks & automation', 'Connect transaction events to your business workflows with verifiable callbacks.'],
  ['Developer APIs', 'Build with CPay API v2, OpenAPI, SDK helpers, Postman, and sandbox tooling.'],
] as const;

const audiences = [
  ['Businesses & merchants', 'Accept and manage digital payments through one operational workspace.'],
  ['Platforms & developers', 'Embed payment capabilities into applications using documented APIs and SDKs.'],
  ['Institutions', 'Centralize collections, reporting, controls, and reconciliation.'],
  ['Technology partners', 'Connect providers and value-added services through a controlled integration layer.'],
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
            <small>CPay Gateway</small>
          </span>
        </a>

        <nav className="cito-nav" aria-label="Primary navigation">
          <a href="#cpay">Products</a>
          <a href="#about">About</a>
          <a href="#developers">Developers</a>
          <a href="#contact">Contact</a>
        </nav>

        <div className="cito-header-actions">
          <Link className="cito-button cito-button-quiet" to="/login">Sign in</Link>
          <Link className="cito-button cito-button-primary" to="/signup">Get started</Link>
        </div>

        <details className="cito-mobile-menu">
          <summary aria-label="Open navigation">Menu</summary>
          <div className="cito-mobile-panel">
            <Link to="/signup">Get started</Link>
            <Link to="/login">Sign in</Link>
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
            <p className="cito-eyebrow">Payments infrastructure built for connected businesses</p>
            <h1>One platform to collect, pay and connect.</h1>
            <p className="cito-lead">
              Cito gives businesses a secure gateway to digital payments and connected services through CPay.
              Accept payments, send payouts, reconcile transactions, and integrate your applications from one platform.
            </p>
            <div className="cito-hero-actions">
              <Link className="cito-button cito-button-primary cito-button-large" to="/signup">Get started</Link>
              <Link className="cito-button cito-button-secondary cito-button-large" to="/login">Sign in</Link>
            </div>
            <a className="cito-text-link" href={DOCS_URL}>Developer? Explore the CPay API <span aria-hidden="true">→</span></a>
          </div>

          <aside className="cito-product-card" aria-label="CPay capabilities summary">
            <div className="cito-product-card-head">
              <div>
                <span className="cito-product-kicker">Cito</span>
                <h2>CPay</h2>
              </div>
              <span className="cito-status"><span aria-hidden="true" /> Sandbox ready</span>
            </div>
            <p className="cito-product-intro">Payments, settlement operations, and developer access in one gateway.</p>
            <div className="cito-product-grid">
              <span>Collections</span>
              <span>Payouts</span>
              <span>Payment links</span>
              <span>Invoices</span>
              <span>Reconciliation</span>
              <span>APIs</span>
            </div>
            <div className="cito-provider-row" aria-label="Supported payment networks">
              <strong>Connected channels</strong>
              <p>MTN MoMo · Airtel Money · Airtel OpenAPI · Safaricom M-Pesa · Yo! Payments</p>
            </div>
          </aside>
        </section>

        <section className="cito-trust-strip" aria-label="Platform strengths">
          <span>Secure by design</span>
          <span>Multiple payment channels</span>
          <span>Built for developers</span>
          <span>Merchant self-service</span>
        </section>

        <section className="cito-section cito-about" id="about">
          <div className="cito-section-heading">
            <p className="cito-eyebrow">About Cito</p>
            <h2>Technology that connects businesses to opportunity.</h2>
            <p>
              Cito Technologies builds digital infrastructure that helps organizations simplify payments,
              integrations, and operational processes. CPay brings those capabilities together through a secure,
              extensible payments platform for businesses, institutions, developers, and technology partners.
            </p>
          </div>
          <div className="cito-pillars">
            <article>
              <span>01</span>
              <h3>Payments</h3>
              <p>Collect and distribute funds through connected payment channels.</p>
            </article>
            <article>
              <span>02</span>
              <h3>Automation</h3>
              <p>Replace manual transaction handling with programmable workflows and reconciliation.</p>
            </article>
            <article>
              <span>03</span>
              <h3>Connectivity</h3>
              <p>Connect applications, providers, and customer experiences through APIs.</p>
            </article>
          </div>
        </section>

        <section className="cito-section cito-cpay" id="cpay">
          <div className="cito-section-heading cito-section-heading-center">
            <p className="cito-eyebrow">Meet CPay</p>
            <h2>A single gateway for the payment operations that matter.</h2>
            <p>
              CPay gives businesses one controlled interface for collections, payouts, transaction management,
              reconciliation, and payment-provider integrations.
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
            <p className="cito-eyebrow">How it works</p>
            <h2>From account creation to production, without guesswork.</h2>
          </div>
          <ol className="cito-steps">
            <li><span>01</span><div><h3>Create your account</h3><p>Register your business and primary contact details.</p></div></li>
            <li><span>02</span><div><h3>Complete business setup</h3><p>Finish the profile, verification, and approval requirements for your organization.</p></div></li>
            <li><span>03</span><div><h3>Configure and test</h3><p>Connect supported channels and validate your integration in sandbox.</p></div></li>
            <li><span>04</span><div><h3>Activate production</h3><p>Move approved services to production under CPay operational controls.</p></div></li>
          </ol>
        </section>

        <section className="cito-section cito-access" id="access">
          <div className="cito-section-heading cito-section-heading-center">
            <p className="cito-eyebrow">Access Cito</p>
            <h2>One front door. The right workspace after sign-in.</h2>
            <p>Start a new business account or continue to the merchant workspace with your existing credentials.</p>
          </div>
          <div className="cito-access-grid">
            <article>
              <p className="cito-card-label">New to Cito?</p>
              <h3>Create a business account and begin setting up CPay.</h3>
              <p>Start in sandbox and complete the required checks before production activation.</p>
              <Link className="cito-button cito-button-primary" to="/signup">Create account</Link>
            </article>
            <article>
              <p className="cito-card-label">Already registered?</p>
              <h3>Access your CPay merchant workspace.</h3>
              <p>Sign in once and continue to the services and controls assigned to your merchant account.</p>
              <Link className="cito-button cito-button-secondary" to="/login">Sign in</Link>
            </article>
          </div>
        </section>

        <section className="cito-section cito-developers" id="developers">
          <div className="cito-developer-copy">
            <p className="cito-eyebrow">For developers</p>
            <h2>Built to integrate.</h2>
            <p>
              Connect CPay to your application using documented APIs, first-party signing helpers, and a controlled
              sandbox environment. Move from a first test transaction to production without changing integration patterns.
            </p>
            <div className="cito-developer-actions">
              <a className="cito-button cito-button-light" href={DOCS_URL}>API documentation</a>
              <a className="cito-button cito-button-outline-light" href={`${DOCS_URL}site/index.html#getting-started`}>Quickstart</a>
            </div>
            <p className="cito-developer-resources">OpenAPI · Postman · Node.js · Python · PHP · Webhooks · Error catalog</p>
          </div>
          <div className="cito-quickstart" aria-label="CPay developer quickstart">
            <span className="cito-quickstart-label">CPay API v2</span>
            <ol>
              <li><span>01</span> Create a sandbox merchant account</li>
              <li><span>02</span> Obtain merchant credentials</li>
              <li><span>03</span> Sign your request</li>
              <li><span>04</span> Make a test collection</li>
              <li><span>05</span> Receive and verify a webhook</li>
              <li><span>06</span> Complete production readiness</li>
            </ol>
          </div>
        </section>

        <section className="cito-section cito-audiences">
          <div className="cito-section-heading cito-section-heading-center">
            <p className="cito-eyebrow">Who it is for</p>
            <h2>Designed for organizations that move money.</h2>
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
            <h2>Built for financial operations.</h2>
            <p>Security controls are designed into request authentication, environment management, credential protection, audit trails, and callback delivery.</p>
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
            <a href="mailto:info@citotech.net"><span>Sales</span><strong>Discuss CPay for your business</strong><small>info@citotech.net</small></a>
            <a href="mailto:support@citotech.net"><span>Support</span><strong>Get help with an existing account</strong><small>support@citotech.net</small></a>
            <a href={DOCS_URL}><span>Developers</span><strong>Open integration documentation</strong><small>CPay API v2 documentation</small></a>
          </div>
        </section>

        <section className="cito-final-cta">
          <div>
            <p className="cito-eyebrow">Start with CPay</p>
            <h2>Ready to connect your business?</h2>
            <p>Create your Cito account, explore CPay in sandbox, and build your payment integration from one place.</p>
          </div>
          <div className="cito-final-actions">
            <Link className="cito-button cito-button-light" to="/signup">Get started</Link>
            <a className="cito-button cito-button-outline-light" href={DOCS_URL}>Explore API docs</a>
          </div>
        </section>
      </main>

      <footer className="cito-footer">
        <div className="cito-footer-brand">
          <strong>Cito</strong>
          <span>Connected financial infrastructure through CPay.</span>
        </div>
        <div className="cito-footer-links">
          <div><strong>Product</strong><a href="#cpay">CPay</a><a href="#access">Merchant access</a></div>
          <div><strong>Developers</strong><a href={DOCS_URL}>Documentation</a><a href={`${DOCS_URL}Api/cpay-v2-openapi.yaml`}>OpenAPI</a></div>
          <div><strong>Company</strong><a href="#about">About</a><a href="#contact">Contact</a></div>
          <div><strong>Access</strong><Link to="/signup">Create account</Link><Link to="/login">Sign in</Link></div>
        </div>
        <div className="cito-footer-bottom">
          <span>© {new Date().getFullYear()} Cito Technologies</span>
          <span>CPay Gateway</span>
        </div>
      </footer>
    </div>
  );
}

export default CitoLandingPage;
