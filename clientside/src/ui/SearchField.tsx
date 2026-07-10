import React from 'react';
import { SearchIcon } from './Icons';

export function SearchField({
  value,
  onValueChange,
  placeholder = 'Search',
  onSubmit,
  ariaLabel = 'Search',
}: {
  value: string;
  onValueChange: (value: string) => void;
  placeholder?: string;
  onSubmit?: (value: string) => void;
  ariaLabel?: string;
}): React.ReactElement {
  return (
    <div className="ios-search">
      <span className="ios-search__icon">
        <SearchIcon size={16} />
      </span>
      <input
        type="search"
        className="ios-input"
        value={value}
        placeholder={placeholder}
        aria-label={ariaLabel}
        onChange={(e) => onValueChange(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === 'Enter' && onSubmit) onSubmit(value);
        }}
      />
    </div>
  );
}
