import React from 'react';

/**
 * Portal chrome primitives (iOS): a translucent sidebar + sticky top bar around
 * a scrolling content column. Replaces the hand-rolled `.cpay-shell` markup in
 * Layout.jsx / LayoutMerchant.jsx. The active screen is swapped in as children;
 * routing stays with the host component.
 */
export function Shell({
  navOpen = false,
  sidebar,
  topbar,
  children,
}: {
  navOpen?: boolean;
  sidebar: React.ReactNode;
  topbar: React.ReactNode;
  children: React.ReactNode;
}): React.ReactElement {
  return (
    <div className={`ios-shell ${navOpen ? 'ios-shell--nav-open' : ''}`.trim()}>
      {sidebar}
      <div className="ios-main">
        {topbar}
        {children}
      </div>
    </div>
  );
}

export function Sidebar({
  brand,
  children,
}: {
  brand: React.ReactNode;
  children: React.ReactNode;
}): React.ReactElement {
  return (
    <aside className="ios-sidebar" aria-label="Primary">
      {brand}
      <nav className="ios-nav">{children}</nav>
    </aside>
  );
}

export function Brand({
  logo,
  name,
  product,
}: {
  logo?: string;
  name: React.ReactNode;
  product?: React.ReactNode;
}): React.ReactElement {
  return (
    <div className="ios-brand">
      {logo ? <img className="ios-brand__logo" src={logo} alt="" /> : null}
      <div>
        <div className="ios-brand__name">{name}</div>
        {product ? <div className="ios-brand__product">{product}</div> : null}
      </div>
    </div>
  );
}

export function NavGroup({
  title,
  bottom = false,
  children,
}: {
  title?: React.ReactNode;
  bottom?: boolean;
  children: React.ReactNode;
}): React.ReactElement {
  return (
    <div className={`ios-nav-group ${bottom ? 'ios-nav-group--bottom' : ''}`.trim()}>
      {title ? <div className="ios-nav-group__title">{title}</div> : null}
      {children}
    </div>
  );
}

export function NavItem({
  icon,
  active = false,
  danger = false,
  onClick,
  children,
}: {
  icon?: React.ReactNode;
  active?: boolean;
  danger?: boolean;
  onClick?: () => void;
  children: React.ReactNode;
}): React.ReactElement {
  return (
    <button
      type="button"
      className={`ios-nav-item ${active ? 'ios-nav-item--active' : ''} ${danger ? 'ios-nav-item--danger' : ''}`.trim()}
      aria-current={active ? 'page' : undefined}
      onClick={onClick}
    >
      {icon ? <span className="ios-nav-item__icon">{icon}</span> : null}
      <span>{children}</span>
    </button>
  );
}

export function TopBar({
  left,
  right,
}: {
  left?: React.ReactNode;
  right?: React.ReactNode;
}): React.ReactElement {
  return (
    <header className="ios-topbar">
      <div className="ios-topbar__left">{left}</div>
      <div className="ios-topbar__right">{right}</div>
    </header>
  );
}

export function IconButton({
  label,
  onClick,
  children,
}: {
  label: string;
  onClick?: () => void;
  children: React.ReactNode;
}): React.ReactElement {
  return (
    <button type="button" className="ios-icon-btn" aria-label={label} title={label} onClick={onClick}>
      {children}
    </button>
  );
}

export function UserChip({
  name,
  meta,
}: {
  name: string;
  meta?: React.ReactNode;
}): React.ReactElement {
  const initials = name
    .split(/\s+/)
    .map((p) => p[0])
    .filter(Boolean)
    .slice(0, 2)
    .join('')
    .toUpperCase();
  return (
    <div className="ios-user-chip">
      <span className="ios-user-avatar">{initials || '?'}</span>
      <span className="ios-user-meta">
        <strong>{name}</strong>
        {meta ? <span>{meta}</span> : null}
      </span>
    </div>
  );
}

export function Page({
  children,
}: {
  children: React.ReactNode;
}): React.ReactElement {
  return <div className="ios-page">{children}</div>;
}
