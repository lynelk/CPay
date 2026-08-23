import React from 'react';

export type BadgeTone = 'neutral' | 'success' | 'danger' | 'error' | 'warning' | 'info';

export function Badge({
  tone = 'neutral',
  children,
}: {
  tone?: BadgeTone;
  children: React.ReactNode;
}): React.ReactElement {
  const classTone = tone === 'error' ? 'danger' : tone;
  return <span className={`ios-badge ios-badge--${classTone}`}>{children}</span>;
}
