import React from 'react';

export function PageHeader({
  breadcrumb,
  title,
  subtitle,
  actions,
}: {
  breadcrumb?: React.ReactNode;
  title: React.ReactNode;
  subtitle?: React.ReactNode;
  actions?: React.ReactNode;
}): React.ReactElement {
  return (
    <div className="ios-toolbar">
      <div className="ios-page-header">
        {breadcrumb ? <span className="ios-breadcrumb">{breadcrumb}</span> : null}
        <h1>{title}</h1>
        {subtitle ? <p>{subtitle}</p> : null}
      </div>
      <span className="ios-toolbar__spacer" />
      {actions}
    </div>
  );
}
