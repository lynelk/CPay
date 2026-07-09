import React from 'react';
import { Spinner } from './Spinner';

type Variant = 'primary' | 'secondary' | 'link';

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  loading?: boolean;
  loadingLabel?: string;
}

export function Button({
  variant = 'primary',
  loading = false,
  loadingLabel,
  children,
  className = '',
  disabled,
  ...rest
}: ButtonProps): React.ReactElement {
  return (
    <button
      className={`ios-btn ios-btn--${variant} ${className}`.trim()}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...rest}
    >
      {loading ? (
        <>
          <Spinner />
          {loadingLabel ?? children}
        </>
      ) : (
        children
      )}
    </button>
  );
}
