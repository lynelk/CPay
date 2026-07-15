import React from 'react';

export function Alert({
  variant = 'error',
  children,
}: {
  variant?: 'error' | 'success';
  children: React.ReactNode;
}): React.ReactElement {
  return (
    <div className={`ios-alert ios-alert--${variant}`} role="alert">
      {children}
    </div>
  );
}
