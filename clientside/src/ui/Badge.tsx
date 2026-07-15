import React from 'react';

export type BadgeTone = 'neutral' | 'success' | 'danger' | 'warning' | 'info';

export function Badge({
  tone = 'neutral',
  children,
}: {
  tone?: BadgeTone;
  children: React.ReactNode;
}): React.ReactElement {
  return <span className={`ios-badge ios-badge--${tone}`}>{children}</span>;
}
