import React from 'react';

export function Spinner({ label = 'Loading' }: { label?: string }): React.ReactElement {
  return <span className="ios-spinner" role="status" aria-label={label} />;
}
