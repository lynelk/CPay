import React from 'react';
import { Spinner } from './Spinner';

/**
 * Full-screen "please wait" overlay. Replaces rc-easyui ProgressBar + Dialog
 * used by components/Progress.jsx. If `value` (0–100) is provided a determinate
 * bar is shown; otherwise a spinner.
 */
export function ProgressOverlay({
  open,
  message = 'Please wait…',
  value,
}: {
  open: boolean;
  message?: string;
  value?: number;
}): React.ReactElement | null {
  if (!open) return null;
  return (
    <div className="ios-progress-overlay" role="alertdialog" aria-busy="true" aria-label={message}>
      <div className="ios-progress-card">
        {typeof value === 'number' ? (
          <div className="ios-progress-track">
            <div className="ios-progress-fill" style={{ width: `${Math.min(100, Math.max(0, value))}%` }} />
          </div>
        ) : (
          <Spinner label={message} />
        )}
        <span style={{ fontSize: 'var(--ios-fs-subhead)', color: 'var(--ios-text-secondary)' }}>{message}</span>
      </div>
    </div>
  );
}
