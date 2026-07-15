import React from 'react';
import Logo from '../media/images/gwlogo.png';

function AuthShell({ className = '', title, subtitle, children, footer, asideTitle, asideCopy }) {
  return (
    <div className={`cpay-auth-screen ${className}`.trim()}>
      <main className="cpay-auth-card" role="main">
        <aside className="cpay-auth-aside" aria-label="CPay access context">
          <img className="cpay-auth-brand-large" src={Logo} alt="CPay" />
          <h2>{asideTitle || 'CPay Operations'}</h2>
          {asideCopy ? <p>{asideCopy}</p> : null}
        </aside>

        <section className="cpay-auth-main">
          <header className="cpay-auth-header cpay-auth-header-centered">
            <img className="cpay-auth-logo" src={Logo} alt="CPay" />
            <div>
              <h1>{title}</h1>
              {subtitle ? <p>{subtitle}</p> : null}
            </div>
          </header>

          <div className="cpay-auth-content">
            {children}
          </div>

          <footer className="cpay-auth-footer">
            {footer || 'Copyright (c) 2019'}
          </footer>
        </section>
      </main>
    </div>
  );
}

export default AuthShell;
