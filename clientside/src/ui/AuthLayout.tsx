import React from 'react';
import Logo from '../media/images/gwlogo.png';

interface AuthLayoutProps {
  title: string;
  subtitle?: string;
  asideTitle?: string;
  asideCopy?: string;
  footer?: React.ReactNode;
  className?: string;
  children: React.ReactNode;
}

/**
 * iOS-style authentication shell: a frosted, translucent card with a branded
 * aside. Replaces the rc-easyui-era AuthShell for migrated screens.
 */
export function AuthLayout({
  title,
  subtitle,
  asideTitle = 'CPay',
  asideCopy,
  footer,
  className = '',
  children,
}: AuthLayoutProps): React.ReactElement {
  return (
    <div className={`ios-auth ${className}`.trim()}>
      <main className="ios-auth__card" role="main">
        <aside className="ios-auth__aside" aria-label="CPay access context">
          <img src={Logo} alt="CPay" />
          <h2>{asideTitle}</h2>
          {asideCopy ? <p>{asideCopy}</p> : null}
        </aside>
        <section className="ios-auth__main">
          <header className="ios-auth__header">
            <img src={Logo} alt="CPay" />
            <h1 className="ios-auth__title">{title}</h1>
            {subtitle ? <p className="ios-auth__subtitle">{subtitle}</p> : null}
          </header>
          <div className="ios-auth__content">{children}</div>
          {footer ? <footer className="ios-auth__footer">{footer}</footer> : null}
        </section>
      </main>
    </div>
  );
}
