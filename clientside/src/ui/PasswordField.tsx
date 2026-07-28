import React from 'react';

interface PasswordFieldProps {
  id: string;
  label: string;
  value: string;
  onValueChange: (value: string) => void;
  invalid?: boolean;
  autoComplete?: string;
  placeholder?: string;
}

export function PasswordField({
  id,
  label,
  value,
  onValueChange,
  invalid = false,
  autoComplete = 'current-password',
  placeholder,
}: PasswordFieldProps): React.ReactElement {
  const [visible, setVisible] = React.useState(false);
  return (
    <div className="ios-field">
      <label className="ios-field__label" htmlFor={id}>
        {label}
      </label>
      <div className="ios-password">
        <input
          id={id}
          type={visible ? 'text' : 'password'}
          value={value}
          onChange={(e) => onValueChange(e.target.value)}
          autoComplete={autoComplete}
          placeholder={placeholder}
          aria-invalid={invalid || undefined}
          className={`ios-input ${invalid ? 'ios-input--invalid' : ''}`.trim()}
        />
        <button
          type="button"
          className="ios-password__toggle"
          onClick={() => setVisible((v) => !v)}
          aria-label={visible ? 'Hide password' : 'Show password'}
          aria-pressed={visible}
        >
          {visible ? 'Hide' : 'Show'}
        </button>
      </div>
    </div>
  );
}
