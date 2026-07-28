import React from 'react';

export function Tooltip({
  content,
  children,
}: {
  content: React.ReactNode;
  children: React.ReactNode;
}): React.ReactElement {
  return (
    <span className="ios-tooltip">
      {children}
      <span className="ios-tooltip__bubble" role="tooltip">
        {content}
      </span>
    </span>
  );
}
