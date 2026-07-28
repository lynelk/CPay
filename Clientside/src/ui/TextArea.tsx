import React from 'react';

interface TextAreaProps
  extends Omit<React.TextareaHTMLAttributes<HTMLTextAreaElement>, 'onChange'> {
  id: string;
  label?: string;
  value: string;
  onValueChange: (value: string) => void;
  invalid?: boolean;
}

export function TextArea({
  id,
  label,
  value,
  onValueChange,
  invalid = false,
  rows = 4,
  className = '',
  ...rest
}: TextAreaProps): React.ReactElement {
  const field = (
    <textarea
      id={id}
      rows={rows}
      value={value}
      onChange={(e) => onValueChange(e.target.value)}
      aria-invalid={invalid || undefined}
      className={`ios-input ${invalid ? 'ios-input--invalid' : ''} ${className}`.trim()}
      style={{ height: 'auto', padding: '12px 16px', resize: 'vertical' }}
      {...rest}
    />
  );
  if (!label) return field;
  return (
    <div className="ios-field">
      <label className="ios-field__label" htmlFor={id}>
        {label}
      </label>
      {field}
    </div>
  );
}
