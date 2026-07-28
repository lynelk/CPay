import React from 'react';

export function Card({
  children,
  flush = false,
  className = '',
  ...rest
}: { flush?: boolean } & React.HTMLAttributes<HTMLDivElement>): React.ReactElement {
  return (
    <div className={`ios-card ${flush ? 'ios-card--flush' : ''} ${className}`.trim()} {...rest}>
      {children}
    </div>
  );
}

export function Section({
  title,
  actions,
  children,
  className = '',
}: {
  title?: React.ReactNode;
  actions?: React.ReactNode;
  children: React.ReactNode;
  className?: string;
}): React.ReactElement {
  return (
    <Card className={className}>
      {(title || actions) && (
        <div className="ios-toolbar" style={{ marginBottom: 'var(--ios-space-4)' }}>
          {title ? <h2 className="ios-section-title" style={{ margin: 0 }}>{title}</h2> : null}
          <span className="ios-toolbar__spacer" />
          {actions}
        </div>
      )}
      {children}
    </Card>
  );
}

export function StatGrid({ children }: { children: React.ReactNode }): React.ReactElement {
  return <div className="ios-stat-grid">{children}</div>;
}

export function StatTile({
  label,
  value,
  delta,
  deltaDirection,
}: {
  label: React.ReactNode;
  value: React.ReactNode;
  delta?: React.ReactNode;
  deltaDirection?: 'up' | 'down';
}): React.ReactElement {
  return (
    <Card className="ios-stat">
      <span className="ios-stat__label">{label}</span>
      <span className="ios-stat__value">{value}</span>
      {delta != null ? (
        <span className={`ios-stat__delta ${deltaDirection ? `ios-stat__delta--${deltaDirection}` : ''}`.trim()}>
          {delta}
        </span>
      ) : null}
    </Card>
  );
}
